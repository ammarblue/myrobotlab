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
import static com.googlecode.javacv.jna.cvaux.cvCreateBGCodeBookModel;
import static com.googlecode.javacv.jna.cxcore.cvCreateImage;
import static com.googlecode.javacv.jna.cxcore.cvGetSize;

import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JTextField;

import org.apache.log4j.Logger;

import com.googlecode.javacv.jna.cvaux.CvBGCodeBookModel;
import com.googlecode.javacv.jna.cvaux.CvBGStatModel;
import com.googlecode.javacv.jna.cvaux.v21;
import com.googlecode.javacv.jna.cxcore.CvMemStorage;
import com.googlecode.javacv.jna.cxcore.IplImage;
import org.myrobotlab.service.OpenCV;

public class OpenCVFilterFGBG extends OpenCVFilter {

	public final static Logger LOG = Logger.getLogger(OpenCVFilterFGBG.class
			.getCanonicalName());

	IplImage buffer = null;
	BufferedImage frameBuffer = null;
	int convert = CV_BGR2HSV; // TODO - convert to all schemes
	JFrame myFrame = null;
	JTextField pixelsPerDegree = new JTextField("8.5"); // TODO - needs to pull
														// from SOHDARService
														// configuration
	CvMemStorage storage = null;

	public OpenCVFilterFGBG(OpenCV service, String name) {
		super(service, name);
	}

	@Override
	public BufferedImage display(IplImage image, Object[] data) {

		return bg_model.foreground.getBufferedImage();
	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void loadDefaultConfiguration() {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.myrobotlab.image.OpenCVFilter#process(com.googlecode.javacv.jna.cxcore
	 * .IplImage, java.util.HashMap)
	 * 
	 * void cvErode( const CvArr* A, CvArr* C, IplConvKernel* B=0, int
	 * iterations=1 ); A Source image. C Destination image. B Structuring
	 * element used for erosion. If it is NULL, a 3×3 rectangular structuring
	 * element is used. iterations Number of times erosion is applied. The
	 * function cvErode erodes the source image using the specified structuring
	 * element B that determines the shape of a pixel neighborhood over which
	 * the minimum is taken:
	 * 
	 * C=erode(A,B): C(x,y)=min((x',y') in B(x,y))A(x',y') The function supports
	 * the in-place mode when the source and destination pointers are the same.
	 * Erosion can be applied several times iterations parameter. Erosion on a
	 * color image means independent transformation of all the channels.
	 */

	CvBGStatModel bg_model = null;
	boolean update_bg_model = true;
	IplImage grey = null;
	CvBGCodeBookModel model = null;

	@Override
	public IplImage process(IplImage image) {

		/*
		 * // TODO static global class shared between filters ???? if (storage
		 * == null) { storage = cvCreateMemStorage(0); }
		 */

		if (buffer == null) {
			buffer = cvCreateImage(cvGetSize(image), 8, 3);
		}

		if (bg_model == null) {
			grey = cvCreateImage(cvGetSize(image), 8, 1);
			model = cvCreateBGCodeBookModel();

			// create BG model
			// Select parameters for Gaussian model.
			/*
			 * CvGaussBGStatModelParams params = new CvGaussBGStatModelParams();
			 * 
			 * params.win_size=2; params.n_gauss=5; params.bg_threshold=0.7;
			 * params.std_threshold=3.5; params.minArea=15;
			 * params.weight_init=0.05; params.variance_init=30;
			 */

			bg_model = v21.cvCreateGaussianBGModel(image, null);
			// bg_model = cvCreateFGDStatModel( temp );
		}

		v21.cvUpdateBGStatModel(image, bg_model, update_bg_model ? -1 : 0);
		// cvUpdateBGStatModel( image, bg_model, update_bg_model ? -1 : 0);
		// v21.cvCreateBGCodeBookModel();

		/*
		 * cvFindContours(bg_model.foreground, storage, contourPointer,
		 * sizeofCvContour, 0, CV_RETR_LIST); CvSeq contour =
		 * contourPointer.getStructure();
		 */

		// CvSeq.ByReference seq = bg_model.foreground_regions;

		return bg_model.foreground;
	}

}
