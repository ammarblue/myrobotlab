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
 * */

package org.myrobotlab.image;

import static com.googlecode.javacv.jna.cv.CV_BGR2HSV;
import static com.googlecode.javacv.jna.cv.cvCvtColor;
import static com.googlecode.javacv.jna.cxcore.cvCreateImage;
import static com.googlecode.javacv.jna.cxcore.cvGetSize;
import static com.googlecode.javacv.jna.cxcore.cvScalar;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import org.apache.log4j.Logger;

import com.googlecode.javacv.jna.cxcore;
import com.googlecode.javacv.jna.cxcore.CvScalar;
import com.googlecode.javacv.jna.cxcore.IplImage;
import org.myrobotlab.service.OpenCV;

public class OpenCVFilterInRange extends OpenCVFilter {
	public final static Logger LOG = Logger.getLogger(OpenCVFilterInRange.class.getCanonicalName());
	// http://cgi.cse.unsw.edu.au/~cs4411/wiki/index.php?title=OpenCV_Guide#Calculating_color_histograms

	IplImage hsv = null;
	
	IplImage hue = null;
	IplImage hueMask = null;
	
	IplImage value = null;
	IplImage valueMask = null;
	
	IplImage saturation = null;
	IplImage saturationMask = null;
	IplImage temp = null;
	
	IplImage mask = null;
	
	IplImage ret = null;

	
	BufferedImage frameBuffer = null;

	CvScalar hueMax = null;
	CvScalar hueMin = null;

	CvScalar valueMax = null;
	CvScalar valueMin = null;

	CvScalar saturationMax = null;
	CvScalar saturationMin = null;
	
	final static int HUE_MASK = 1;
	final static int VALUE_MASK = 2;
	final static int SATURATION_MASK = 4;
	
	public OpenCVFilterInRange(OpenCV service, String name) {
		super(service, name);
	}

	@Override
	public BufferedImage display(IplImage image, Object[] data) {

		return ret.getBufferedImage(); // TODO - ran out of memory here
	}

	@Override
	public String getDescription() {
		return null;
	}

	int useMask = 0;
	
	@Override
	public void loadDefaultConfiguration() {
		
		cfg.set("hueMin", 0.0f);
		cfg.set("hueMax", 256.0f);
		cfg.set("valueMin", 0.0f);
		cfg.set("valueMax", 256.0f);
		cfg.set("saturationMin", 0.0f);
		cfg.set("saturationMax", 256.0f);
		
		cfg.set("useHue", false);
		cfg.set("useValue", false);
		cfg.set("useSaturation", false);

		hueMin = cvScalar(cfg.getFloat("hueMin"), 0.0, 0.0, 0.0);
		hueMax = cvScalar(cfg.getFloat("hueMax"), 0.0, 0.0, 0.0);
		valueMin = cvScalar(cfg.getFloat("valueMin"), 0.0, 0.0, 0.0);
		valueMax = cvScalar(cfg.getFloat("valueMax"), 0.0, 0.0, 0.0);
		saturationMin = cvScalar(cfg.getFloat("saturationMin"), 0.0, 0.0, 0.0);
		saturationMax = cvScalar(cfg.getFloat("saturationMax"), 0.0, 0.0, 0.0);

	}

	public void samplePoint(MouseEvent event) {

		frameBuffer = hsv.getBufferedImage();
		int rgb = frameBuffer.getRGB(event.getX(), event.getY());
		Color c = new Color(rgb);
		LOG.error(event.getX() + "," + event.getY() + " h " + c.getRed()
				+ " s " + c.getGreen() + " v " + c.getBlue());
	}

	@Override
	public IplImage process(IplImage image) {

		ret = image;

		if (hsv == null) {
			hsv = cvCreateImage(cvGetSize(image), 8, 3);
			hue = cvCreateImage(cvGetSize(image), 8, 1);
			hueMask = cvCreateImage(cvGetSize(image), 8, 1);
			value = cvCreateImage(cvGetSize(image), 8, 1);
			valueMask = cvCreateImage(cvGetSize(image), 8, 1);
			saturation = cvCreateImage(cvGetSize(image), 8, 1);
			saturationMask = cvCreateImage(cvGetSize(image), 8, 1);
			temp = cvCreateImage(cvGetSize(image), 8, 1);
			mask = cvCreateImage(cvGetSize(image), 8, 1);
		}
		
		// load up desired mask case
		useMask = cfg.getBoolean("useSaturation")?1:0;
		useMask = useMask << 1;
		useMask =  useMask | (cfg.getBoolean("useValue")?1:0);
		useMask = useMask << 1;
		useMask = useMask | (cfg.getBoolean("useHue")?1:0);
		
		if (image == null) {
			LOG.error("image is null");
		}

		// convert to more stable HSV
		//cvCvtColor(image, hsv, CV_RGB2HSV); // # 41
		// #define  CV_BGR2HSV     40 - not defined in javacv
		cvCvtColor(image, hsv, CV_BGR2HSV);   

		if ((useMask & HUE_MASK) == 1)
		{
			// copy out hue
			cxcore.cvSetImageCOI(hsv, 1);
			cxcore.cvCopy(hsv, hue);

			// cfg values if changed
			if (hueMin.getMagnitude() != cfg.getFloat("hueMin")
					|| hueMax.getMagnitude() != cfg.getFloat("hueMax")) {
				hueMin = cvScalar(cfg.getFloat("hueMin"), 0.0, 0.0, 0.0);
				hueMax = cvScalar(cfg.getFloat("hueMax"), 0.0, 0.0, 0.0);
			}

			// create hue mask
			cxcore.cvInRangeS(hue, hueMin.byValue(), hueMax.byValue(), hueMask);
		}

		if ((useMask & VALUE_MASK) == 2)
		{
			// copy out value
			cxcore.cvSetImageCOI(hsv, 3);
			cxcore.cvCopy(hsv, value);

			// look for changed config - update if changed
			if (valueMin.getMagnitude() != cfg.getFloat("valueMin")
					|| valueMax.getMagnitude() != cfg.getFloat("valueMax")) {
				valueMin = cvScalar(cfg.getFloat("valueMin"), 0.0, 0.0, 0.0);
				valueMax = cvScalar(cfg.getFloat("valueMax"), 0.0, 0.0, 0.0);
			}
	
			// create value mask
			cxcore.cvInRangeS(value, valueMin.byValue(), valueMax.byValue(), valueMask);
		}			
			
		if ((useMask & SATURATION_MASK) == 4)
		{
			// copy out saturation
			cxcore.cvSetImageCOI(hsv, 2);
			cxcore.cvCopy(hsv, saturation);

			// look for changed config - update if changed
			if (saturationMin.getMagnitude() != cfg.getFloat("saturationMin")
					|| saturationMax.getMagnitude() != cfg.getFloat("saturationMax")) {
				saturationMin = cvScalar(cfg.getFloat("saturationMin"), 0.0, 0.0, 0.0);
				saturationMax = cvScalar(cfg.getFloat("saturationMax"), 0.0, 0.0, 0.0);
			}

			// create saturation mask
			cxcore.cvInRangeS(saturation, saturationMin.byValue(), saturationMax.byValue(), saturationMask);
		}
				
		switch (useMask)
		{
			case 0: // !hue !value !sat
				ret = image;
				break;				
			
			case 1: // hue !value !sat
				ret = hueMask;
				break;				
			
			case 2: // !hue value !sat
				ret = valueMask;
				break;				
			
			case 3: // hue value !sat
				cxcore.cvAnd(hueMask, valueMask, mask, null);
				ret = mask;
				break;

			case 4: // !hue !value sat
				ret = saturationMask;
				break;

			case 5: // hue !value sat
				cxcore.cvAnd(hueMask, saturationMask, mask, null);
				//cxcore.cvAnd(saturationMask, hueMask, mask, null);
				ret = mask;
				break;

			case 6: // !hue value sat
				cxcore.cvAnd(valueMask, saturationMask, mask, null);
				ret = mask;
				break;
				
			case 7: // hue value sat
				cxcore.cvAnd(hueMask, valueMask, temp, null);
				cxcore.cvAnd(temp, saturationMask, mask, null); // ??
				ret = mask;
				break;
				
			default:
				LOG.error("unknown useMask " + useMask);
				break;
		}
		
		return ret;

	}

}
