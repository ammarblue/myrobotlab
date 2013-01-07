package org.myrobotlab.image;

import static com.googlecode.javacv.cpp.opencv_core.cvResetImageROI;
import static com.googlecode.javacv.cpp.opencv_core.cvSetImageROI;

import java.awt.Rectangle;
import java.io.Serializable;
import java.util.Date;

import com.googlecode.javacv.cpp.opencv_core.CvRect;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

public class KinectImageNode implements Serializable {
	private static final long serialVersionUID = 1L;
	public int ID = 0;
	public Date timestamp = new Date();

	// won't serialize - need type conversion
	public transient IplImage cvCameraFrame = null;
	public transient IplImage cvMask = null;
	public transient IplImage cvCropped = null;
	public CvRect cvBoundingBox = null;
	// public transient IplImage cvGrayFrame = null;

	public SerializableImage cameraFrame = null;
	public SerializableImage mask = null;
	public SerializableImage cropped = null;
	// public Rectangle boudingBox = null;
	public Rectangle boundingBox = null;
	public SerializableImage template = null;
	public String imageFilePath = null;

	public int lastGoodFitIndex = 0;

	public IplImage getTemplate() {
		cvSetImageROI(cvMask, cvBoundingBox); // 615-8 = to remove right hand
												// band
		IplImage template = cvMask.clone(); //
		cvResetImageROI(cvMask);
		return template;
	}

	public void convertToSerializableTypes() {
		cameraFrame = new SerializableImage(cvCameraFrame.getBufferedImage());
		mask = new SerializableImage(cvMask.getBufferedImage());
	}

}
