/**
 *                    
 * @author greg (at) myrobotlab.org
 *  
 * This file is part of MyRobotLab (http://myrobotlab.org).
 *
 * MyRobotLab is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version (subject to the "Classpath" exception
 * as provided in the LICENSE.txt file that accompanied this code).
 *
 * MyRobotLab is distributed in the hope that it will be useful or fun,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * All libraries in thirdParty bundle are subject to their own license
 * requirements - please refer to http://myrobotlab.org/libraries for 
 * details.
 * 
 * Enjoy !
 * 
 * The working part of this was eventually traced back to:
 * http://www.developer.com/java/other/article.php/1565671/Java-Sound-An-Introduction.htm
 * And I would like to give all well deserved credit to
 * Richard G. Baldwin's excellent and comprehensive tutorial regarding the many
 * details of sound and Java
 * 
 * */

package org.myrobotlab.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import javaFlacEncoder.FLAC_FileEncoder;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.myrobotlab.framework.Service;
import org.myrobotlab.speech.TranscriptionThread;


public class GoogleSTT extends Service {

	public final static Logger LOG = Logger.getLogger(GoogleSTT.class.getCanonicalName());
	private static final long serialVersionUID = 1L;
	
	// microphone capture
	boolean stopCapture = false;
	ByteArrayOutputStream byteArrayOutputStream;
	AudioFormat audioFormat;
	TargetDataLine targetDataLine;
	AudioInputStream audioInputStream;
	SourceDataLine sourceDataLine;

	// capture specifics - strategy is lowest size and best quality
	float sampleRate 		= 8000.0F; 	// 8000,11025,16000,22050,44100
	int sampleSizeInBits 	= 16;	 	// 8,16
	int channels 			= 1;		// 1,2 TODO - check for 2 & triangulation 
	boolean signed 			= true;		// true,false
	boolean bigEndian 		= false;
	double inputVolumeLevel = 0;
	int bytesPerSecond = (int)sampleRate * sampleSizeInBits * channels / 8;
	int rmsSampleRate = 8; // sample times per second 
	
	boolean debug = true;
	
	// transcribing
	public final static int RECORDING = 0;
	public final static int SUCCESS = 1;
	public final static int ERROR = 2;
	public final static int TRANSCRIBING = 3;
	FLAC_FileEncoder encoder; // TODO - encodes via file system - should allow just byte arrays from memory
	private String language = "en";
	transient TranscriptionThread transcription = null;

	public GoogleSTT(String n) {
		super(n, GoogleSTT.class.getCanonicalName());
		performanceTiming = true;
		encoder = new FLAC_FileEncoder();
	}

	@Override
	public void loadDefaultConfiguration() {
	}

	private AudioFormat getAudioFormat() {		
		return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);		
	}

	// TODO - refactor and normalize
	public void captureAudio() {
		try {
			audioFormat = getAudioFormat();
			LOG.info("sample rate         " + sampleRate);
			LOG.info("channels            " + channels);
			LOG.info("sample size in bits " + sampleSizeInBits);
			LOG.info("signed              " + signed);
			LOG.info("bigEndian           " + bigEndian);
			LOG.info("data rate is " + bytesPerSecond + " bytes per second");
			DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
			targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
			targetDataLine.open(audioFormat);
			targetDataLine.start();

			// capture from microphone
			Thread captureThread = new Thread(new CaptureThread());
			captureThread.start();
		} catch (Exception e) {
			LOG.error(Service.stackToString(e));
		}
	}

	public void stopAudioCapture()
	{
		stopCapture = true;
	}
	
	public ByteArrayOutputStream publishCapture()
	{
		return byteArrayOutputStream;
	}
	
	
	// Write data to the internal buffer of the data line
	// where it will be delivered to the speaker. 
	// volumeRMS((double[])tempBuffer);
	
	// copy the sample into a double for rms
	// http://www.jsresources.org/faq_audio.html#calculate_power
	// rms = sqrt( (x0^2 + x1^2 + x2^2 + x3^2) / 4)
	// convert sampleSizeInBits/8 to double (bucket)
	
	// conversions - http://stackoverflow.com/questions/1026761/how-to-convert-a-byte-array-to-its-numeric-value-java
	// http://www.daniweb.com/software-development/java/code/216874
	
	public static double rms(double[] nums){
		  double ms = 0;
		  for (int i = 0; i < nums.length; i++)
		   ms += nums[i] * nums[i];
		  ms /= nums.length;
		  return Math.sqrt(ms);
	}
	
	double runningRMS = 0;
	
	double thresholdRMSDifference = 0;
	
	
	class CaptureThread extends Thread {
		// An arbitrary-size temporary holding
		// buffer
		byte buffer[] = new byte[bytesPerSecond/rmsSampleRate];
		int bytesPerSample = sampleSizeInBits/8;
		//double rmsSample = new double[];

		public void run() {
			LOG.info("starting capture with " + buffer.length + " byte buffer length");
			byteArrayOutputStream = new ByteArrayOutputStream();
			stopCapture = false;
			try {

				while (!stopCapture) {

					int cnt = targetDataLine.read(buffer, 0, buffer.length);
					// LOG.info("level " + targetDataLine.getLevel()); not implemented - bummer !

					double ms = 0;
					// rms
					for (int i = 0; i < buffer.length/bytesPerSample; ++i)
					{
						// data type conversion
						// signed 2 byte to int
						// little Indian
						
						int v;
						v  = (int)(0xff & buffer[i] << 8) | (int)(0xff & buffer[i+1] << 0);
						
						ms += v;
						/*
						for (int j = 0; j < bytesPerSample; ++j)
						{
							// (long)(0xff & buffer[7]) << (j*8)
						}
						*/
						
						
					}
					ms /= buffer.length/bytesPerSample;
					double rms = Math.sqrt(ms);
					
					LOG.info("root mean square " + rms + " for " + buffer.length/bytesPerSample + " samples");
					
					// && isListening && thresholdReached && (listenTime < minListenTime)
					if (cnt > 0) {
						byteArrayOutputStream.write(buffer, 0, cnt);
					}// end if
				}// end while
				
				byteArrayOutputStream.close();
				
				saveWavAsFile(byteArrayOutputStream.toByteArray(), audioFormat, "test2.wav");
				encoder.encode(new File("test2.wav"), new File("test2.flac"));
				transcribe("test2.flac");
				
			} catch (Exception e) {
				LOG.error(Service.stackToString(e));
			}
		}
	}

	private void transcribe(String path) {
		// only interrupt if available
		// transcription.interrupt();
		
		logTime("begin");
		TranscriptionThread transcription = new TranscriptionThread(language);
		transcription.debug = debug;
		logTime("pre start");
		transcription.start();
		logTime("post start");
		transcription.startTranscription(path);
		logTime("post trans");
		
		//threads.add(transcription);
	}	
	
	public static void saveWavAsFile(byte[] byte_array, AudioFormat audioFormat, String file) {
		try {
			long length = (long)(byte_array.length / audioFormat.getFrameSize());
			ByteArrayInputStream bais = new ByteArrayInputStream(byte_array);
			AudioInputStream audioInputStreamTemp = new AudioInputStream(bais, audioFormat, length);
			File fileOut = new File(file);
			AudioFileFormat.Type fileType = AudioFileFormat.Type.WAVE;
	 
			if (AudioSystem.isFileTypeSupported(fileType, audioInputStreamTemp)) {
				AudioSystem.write(audioInputStreamTemp, fileType, fileOut);
			}
		} catch(Exception e) { }
	}	
	
	
/*	
	public double volumeRMS(double[] raw) {
	    double sum = 0d;
	    if (raw.length==0) {
	        return sum;
	    } else {
	        for (int ii=0; ii<raw.length; ii++) {
	            sum += raw[ii];
	        }
	    }
	    double average = sum/raw.length;

	    double[] meanSquare = new double[raw.length];
	    double sumMeanSquare = 0d;
	    for (int ii=0; ii<raw.length; ii++) {
	        sumMeanSquare += Math.pow(raw[ii]-average,2d);
	        meanSquare[ii] = sumMeanSquare;
	    }
	    double averageMeanSquare = sumMeanSquare/raw.length;
	    double rootMeanSquare = Math.pow(averageMeanSquare,0.5d);

	    return rootMeanSquare;
	}
*/	
	public static double toDouble(byte[] data) {
	    if (data == null || data.length != 8) return 0x0;
	    return Double.longBitsToDouble(toLong(data));
	}
	
	public static long toLong(byte[] data) {
	    if (data == null || data.length != 8) return 0x0;
	    return (long)(
	            (long)(0xff & data[0]) << 56  |
	            (long)(0xff & data[1]) << 48  |
	            (long)(0xff & data[2]) << 40  |
	            (long)(0xff & data[3]) << 32  |
	            (long)(0xff & data[4]) << 24  |
	            (long)(0xff & data[5]) << 16  |
	            (long)(0xff & data[6]) << 8   |
	            (long)(0xff & data[7]) << 0
	            );
	}	
	
	
/*	
	public synchronized float level()
	  {
	    float level = 0;
	    for (int i = 0; i < samples.length; i++)
	    {
	      level += (samples[i] * samples[i]);
	    }
	    level /= samples.length;
	    level = (float) Math.sqrt(level);
	    return level;
	  }
*/		 
	@Override
	public String getToolTip() {		
		return "Uses the Google Speech To Text service";
	}

	public static void main(String[] args) {
		org.apache.log4j.BasicConfigurator.configure();
		Logger.getRootLogger().setLevel(Level.DEBUG);
		
		GoogleSTT stt = new GoogleSTT("stt");
		//stt.startService();
		stt.captureAudio();
		stt.stopAudioCapture();
	}
	
	
}
