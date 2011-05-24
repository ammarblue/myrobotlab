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

package org.myrobotlab.comm;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;

import org.apache.log4j.Logger;
import org.myrobotlab.framework.Message;
import org.myrobotlab.framework.Outbox;
import org.myrobotlab.framework.RuntimeEnvironment;
import org.myrobotlab.framework.Service;
import org.myrobotlab.framework.ServiceDirectoryUpdate;
import org.myrobotlab.framework.ServiceWrapper;
import org.myrobotlab.service.interfaces.CommunicationInterface;
import org.myrobotlab.service.interfaces.Communicator;

public class CommunicationManager2  implements Serializable, CommunicationInterface{

	private static final long serialVersionUID = 1L;
	public final static Logger LOG = Logger.getLogger(CommunicationManager2.class.toString());
	Service myService = null;
	Outbox outbox = null;

	private Communicator comm = null;

	public CommunicationManager2(Service myService) {
		// set local private references
		this.myService = myService;
		this.outbox = myService.getOutbox();

		String communicatorClass ="org.myrobotlab.comm.CommObjectStreamOverUDP";
		LOG.info("instanciating a " + communicatorClass);
		Communicator c = (Communicator) Service.getNewInstance(communicatorClass, myService);

		outbox.setCommunicationManager(this);

		setComm(c);

	}

	public void send(final URL remoteURL, final Message msg) {
		getComm().send(remoteURL, msg);	
	}
	
	public void send(final Message msg) {
		
		//ServiceWrapper sw = RuntimeEnvironment.getService(myService.url, msg.name);
		ServiceWrapper sw = RuntimeEnvironment.getService(msg.name);
		//if (sw.host.accessURL != null && !sw.host.accessURL.equals(myService.url))
		if (sw.host.accessURL == null || sw.host.accessURL.equals(myService.url))
		{
			LOG.info("sending local");
			Message m = new Message(msg); // TODO UNECESSARY - BUT TOO SCARED TO REMOVE !!
			sw.get().in(m);			
		} else {
			LOG.info("sending " + msg.method + " remote");
			getComm().send(sw.host.accessURL, msg);			
		}

		/*
		if (!sw.isRemote()) {
			LOG.info("sending local");
			Message m = new Message(msg); // TODO UNECESSARY - BUT TOO SCARED TO REMOVE !!
			sw.get().in(m);
		} else {
			LOG.info("sending " + msg.method + " remote");
			getComm().send(msg);
		}
		*/
	}

	public void setComm(Communicator comm) {
		this.comm = comm;
	}

	public Communicator getComm() {
		return comm;
	}
	
	public  void registerServices(String hostAddress, int port, Message msg) 
	{
		try {
			ServiceDirectoryUpdate sdu = (ServiceDirectoryUpdate) msg.data[0];
			Socket socket = new Socket();// TODO - static way to do this?
			InetSocketAddress localAddr = (InetSocketAddress) socket.getLocalSocketAddress();
	
			StringBuffer sb = new StringBuffer();
			sb.append("http://");
			sb.append(hostAddress);
			sb.append(":");
			sb.append(port);
			
			sdu.remoteURL = new URL(sb.toString());
			
			// TODO - not needed
			/*
			sb = new StringBuffer();
			sb.append("http://");
			sb.append(localAddr.getAddress().getHostAddress());
			sb.append(":");
			sb.append(localAddr.getPort());
			*/
			
			//sdu.url = new URL(sb.toString());
			
			sdu.url = myService.url;
			
			sdu.serviceEnvironment.accessURL = sdu.remoteURL;
			
			LOG.info(myService.name + " recieved service directory update from " + sdu.remoteURL);
	
			if (RuntimeEnvironment.register(sdu.remoteURL, sdu.serviceEnvironment))
			{
				ServiceDirectoryUpdate echoLocal = new ServiceDirectoryUpdate();
				echoLocal.remoteURL = sdu.url;
				echoLocal.serviceEnvironment = RuntimeEnvironment.getLocalServices();
				
				myService.send (msg.sender, "registerServices", echoLocal);
			}
		
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}


}
