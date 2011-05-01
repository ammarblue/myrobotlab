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

package org.myrobotlab.test;

import org.apache.log4j.Logger;

import org.myrobotlab.service.Invoker;

public class MotorTest {

	public final static Logger LOG = Logger.getLogger(MotorTest.class
			.getCanonicalName());

	public static void main(String[] args) throws InterruptedException {

		// Arduino arduino = new Arduino("arduino");
		// arduino.start();
		Invoker invoker = new Invoker("invoker");
		invoker.startService();
		// GU

	}

}
