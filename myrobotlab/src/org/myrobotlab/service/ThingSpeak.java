package org.myrobotlab.service;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerFactory;
import org.myrobotlab.framework.Service;
import org.myrobotlab.net.HTTPRequest;
import org.myrobotlab.service.data.Pin;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;


/**
 * @author GroG & 
 * 
 *         References : http://community.thingspeak.com/documentation/api/
 * 
 */

@Root
public class ThingSpeak extends Service {

	private static final long serialVersionUID = 1L;

//	public final static Logger log = LoggerFactory.getLogger(ThingSpeak.class.getCanonicalName());
	public final static Logger log = Logger.getLogger(ThingSpeak.class.getCanonicalName());
	// http://api.thingspeak.com/update?key=AO4DMKQZY4RLWNNU&field1=pin&field2=A0&field3=value&field4=345&status=boink6

	String updateURL = "http://api.thingspeak.com/update";
	@Element
	String writeKey = "";

	long lastUpdate = 0;

	@Element
	int intervalSeconds = 15;


	public Integer getIntervalSeconds() {
		return intervalSeconds;
	}

	public void setIntervalSeconds(int intervalSeconds) {
		this.intervalSeconds = intervalSeconds;
	}

	public ThingSpeak(String n) {
		super(n, ThingSpeak.class.getCanonicalName());
		load();
	}
	
	public void saveConfig()
	{
		save();
	}

	@Override
	public void loadDefaultConfiguration() {
	}

	@Override
	public String getToolTip() {
		return "used as a general template";
	}
	
	// TODO - add averaging & timing info
	
	public Integer update(Object[] data) {
		String result = "0";
		try {
			
			if (System.currentTimeMillis() - lastUpdate < intervalSeconds * 1000)
			{
				log.debug(String.format("not ready for posting - must be >= %d seconds", intervalSeconds));
				return 0;
			}
			
			if (data.length > 8)
			{
				log.warn("data array is larger than 8 - post will be truncated");
			}
			
			StringBuffer url = new StringBuffer();
			url.append(String.format("%s?key=%s", updateURL, writeKey));	
			
			for (int i = 0; i < data.length && i < 8; ++i)
			{
				Object o = data[i];
				url.append(String.format("&field%d=%s", i+1, o.toString()));
			}
			
			HTTPRequest request = new HTTPRequest(url.toString());
			result = request.getString();
			log.info(String.format("ThingSpeak returned %s",result));
			
		} catch (IOException e) {
			Service.logException(e);
		}

		Integer ret = Integer.parseInt(result);
		if (ret > 0)
		{
			lastUpdate = System.currentTimeMillis();
		}
		return ret;
	}
	
	public Integer update (Integer data)
	{
		return update (new Object[]{data});
	}
	
	public Integer update (String data)
	{
		return update (new Object[]{data});
	}
	
	public Integer update(Pin pin)
	{
		return update(new Object[]{pin.pin, pin.value});
	}
	
	
	public String getWriteKey() {
		return writeKey;
	}

	public void setWriteKey(String writeKey) {
		this.writeKey = writeKey;
	}
	
	public long getLastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(long lastUpdate) {
		this.lastUpdate = lastUpdate;
	}
	


	public static void main(String[] args) {
		//org.apache.log4j.BasicConfigurator.configure();
		//Logger.getRootLogger().setLevel(Level.WARN);

		//BasicConfigurator.
		
		log.info("hello");
		
		ThingSpeak thingSpeak = new ThingSpeak("thingSpeak");
		thingSpeak.update(33);
		thingSpeak.startService();

		/*
		 * GUIService gui = new GUIService("gui"); gui.startService();
		 * gui.display();
		 */
	}

}
