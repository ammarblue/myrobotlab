package org.myrobotlab.serial.jssc;

//import gnu.io.CommPortIdentifier;
import java.util.ArrayList;
import java.util.Collections;

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;

import org.apache.log4j.Logger;
import org.myrobotlab.framework.Service;
import org.myrobotlab.serial.SerialDevice;
import org.myrobotlab.serial.SerialDeviceException;
import org.myrobotlab.serial.SerialDeviceFrameworkFactory;

public class SerialDeviceFactoryJSSC implements SerialDeviceFrameworkFactory {

	public final static Logger log = Logger.getLogger(SerialDeviceFactoryJSSC.class.getCanonicalName());

	@Override
	public ArrayList<String> getSerialDeviceNames() {
		ArrayList<String> names = new ArrayList<String>();
		Collections.addAll(names, SerialPortList.getPortNames());
		return names;
	}

	public SerialDevice getSerialDevice(String name, int rate, int databits, int stopbits, int parity) throws SerialDeviceException {

		try {
			SerialDevice sd = new SerialDeviceJSSC(name); //FIXME ??? opens on construction, is that a problem???
			sd.setParams(rate, databits, stopbits, parity); // FIXME - reset params, is that a problem???
			return sd;
		} catch (Exception e) {
			Service.logException(e);
			e.printStackTrace();
		}
		return null;
	}

}