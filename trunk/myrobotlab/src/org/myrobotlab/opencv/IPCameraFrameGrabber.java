package org.myrobotlab.opencv;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;
import org.myrobotlab.framework.Service;

import com.googlecode.javacv.FrameGrabber;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

public class IPCameraFrameGrabber extends FrameGrabber {

	/*
	 * excellent reference - http://www.jpegcameras.com/
	 * foscam url http://host/videostream.cgi?user=username&pwd=password
	 * 			  http://192.168.0.59:60/videostream.cgi?user=admin&pwd=password
	 * android ip cam  http://192.168.0.57:8080/videofeed
	 */
	public final static Logger log = Logger.getLogger(IPCameraFrameGrabber.class.getCanonicalName());

	private URL url;
	private URLConnection connection;
	private InputStream input;
	private Map <String, List<String>> headerfields;
	private String boundryKey;

	public IPCameraFrameGrabber (String urlstr)
	{
		try {
			url = new URL (urlstr);
		} catch (MalformedURLException e) {
			log.error(e);
		}
	}
	
	
	@Override
	public void start()  {
		
		log.info("connecting to " + url);
		try {
			connection = url.openConnection();
		headerfields = connection.getHeaderFields();
		if (headerfields.containsKey("Content-Type"))
		{
			List<String> ct = headerfields.get("Content-Type");
			for (int i = 0; i < ct.size(); ++i)
			{
				String key = ct.get(i);
				int j = key.indexOf("boundary=");
				if (j != -1)
				{
					boundryKey= key.substring(j+9);
				}
			}
		}
		input = connection.getInputStream();
		} catch (IOException e) {
			Service.logException(e);
		}
	}

	@Override
	public void stop()  {
		
		//connection.
		try {
			input.close();
		input = null;
		connection = null;
		url = null;				
		} catch (IOException e) {
			Service.logException(e);
		}
	}

	@Override
	public void trigger() throws Exception {
	}

	@Override
	public IplImage grab()  {
			try {
				return IplImage.createFrom(grabBufferedImage());
			} catch (Exception e) {
				Service.logException(e);
			} catch (IOException e) {
				Service.logException(e);
			}
			return null;
	}
	
	public BufferedImage grabBufferedImage() throws Exception, IOException
	{
		byte[] buffer = new byte[4096];// MTU or JPG Frame Size?
		int n = -1;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
				
		StringBuffer sb = new StringBuffer();
		int total = 0;
		int c;
		// read http subheader
		while ((c = input.read()) != -1) {
			if (c > 0) {
				sb.append((char)c);
				if (c == 13)
				{
					sb.append((char)input.read());// '10'+
					c = input.read();
					sb.append((char)c);
					if (c == 13)
					{
						sb.append((char)input.read());// '10'
						break; // done with subheader
					}
					
				}
			}
		}
		// find embedded jpeg in stream
		String subheader = sb.toString();
		log.debug(subheader);
		int contentLength = -1;
		//if (boundryKey == null)
		//{
			// Yay! - server was nice and sent content length
			int c0 = subheader.indexOf("Content-Length: ");
			int c1 = subheader.indexOf('\r',c0);
			 c0 += 16;
			 contentLength = Integer.parseInt(subheader.substring(c0,c1));
			 log.debug("Content-Length: " + contentLength);
		//} else {
			
		//}
		
		// adaptive size - careful - don't want a 2G jpeg
		if (contentLength > buffer.length)
		{
			buffer = new byte[contentLength]; 
		}
				
		n = -1;
		total = 0;
		while ((n = input.read(buffer, 0, contentLength - total)) != -1) {
			total += n;
			baos.write(buffer, 0, n);
			
			if (total == contentLength) {
				break;
			}
		}
		
		baos.flush();
		//log.info("wrote " + baos.size() + "," + total);
		BufferedImage bi = ImageIO.read(new ByteArrayInputStream (baos.toByteArray()));
		return bi;	
	}

	@Override
	public void release() throws Exception {
	}

}