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

package org.myrobotlab.service.interfaces;


public interface SerialPort {

	public boolean send(int data);
	public boolean send(int[] data);
	public boolean send(byte[] data);
	public String readSerial (byte[] s);
	// setMinReadLength

	public boolean setSerialPortParams(int dataRate, int dataBits, int stopBits, int parity);
	

	/*
	public OutputStream getOuputStream();
	public InputStream getInputStream();
	public void addEventListener(SerialPortEventListener listener);
	public void notifyOnDataAvailable(boolean d);
	*/


}
