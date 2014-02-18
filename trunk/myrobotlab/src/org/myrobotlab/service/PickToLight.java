package org.myrobotlab.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.myrobotlab.framework.Message;
import org.myrobotlab.framework.Peers;
import org.myrobotlab.framework.Service;
import org.myrobotlab.logging.Level;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.Logging;
import org.myrobotlab.logging.LoggingFactory;
import org.myrobotlab.pickToLight.Controller;
import org.myrobotlab.pickToLight.Module;
import org.slf4j.Logger;

import com.pi4j.gpio.extension.pcf.PCF8574GpioProvider;
import com.pi4j.gpio.extension.pcf.PCF8574Pin;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

// bin calls
// moduleList calls
// setAllBoxesLEDs (on off)
// setBoxesOn(String list)
// setBoxesOff(String list)
// getBesSwitchState()
// displayString(boxlist, str)
// ZOD
// update uri
// blinkOff

/*
 * 108154 [WebSocketWorker-14] INFO  class org.myrobotlab.service.Runtime  - getLocalMacAddress
108158 [WebSocketWorker-14] INFO  class org.myrobotlab.service.Runtime  - mac address :
134108 [WebsocketSelector15] ERROR class org.myrobotlab.logging.Logging  - ------
java.lang.NullPointerException
        at org.java_websocket.WebSocketImpl.eot(WebSocketImpl.java:553)
        at org.java_websocket.SocketChannelIOHelper.read(SocketChannelIOHelper.java:18)
        at org.java_websocket.server.WebSocketServer.run(WebSocketServer.java:330)
        at java.lang.Thread.run(Thread.java:724)

WIll KILL WEBSERVER  !!!!
 */


// TODO - automated registration
// Polling / Sensor - important - check sensor state

// - read config in /boot/  - registration url including password - proxy ? 

/**
 * @author GroG
 * 
 *         C:\mrl\myrobotlab>xjc -d src -p org.myrobotlab.pickToLight
 *         PickToLightTypes.xsd
 * 
 */
public class PickToLight extends Service implements GpioPinListenerDigital {

	private static final long serialVersionUID = 1L;
	public final static Logger log = LoggerFactory.getLogger(PickToLight.class);

	transient public RasPi raspi;
	transient public WebGUI webgui;
	transient public Worker worker;

	//static HashMap<String, Module> modules = new HashMap<String, Module>();
	ConcurrentHashMap<String, Module> modules = new ConcurrentHashMap<String, Module>();
	transient HashMap<String, Worker> workers = new HashMap<String, Worker>();
	
	public final static String MODE_KITTING = "kitting";
	public final static String MODE_LEARNING = "learning";
	public final static String MODE_INSTALLING = "installing";
	public final static String MODE_STARTING = "starting";

	private String mode = "kitting";

	String messageGoodPick = "GOOD";

	// FIXME - you will need to decouple initialization until after raspibus is
	// set
	private int rasPiBus = 1;
	// FIXME - who will update me ?
	private String updateURL;
	private int blinkNumber = 5;
	private int blinkDelay = 300;
	
	transient Timer timer;

	public static Peers getPeers(String name) {
		Peers peers = new Peers(name);
		peers.put("raspi", "RasPi", "raspi");
		peers.put("webgui", "WebGUI", "web server interface");
		return peers;
	}
	
	class Task extends TimerTask {
		
		Message msg;
		int interval = 0;
		
		public Task(Task s){
			this.msg = s.msg;
			this.interval = s.interval;
		}
		
		public Task(int interval, String name, String method){
			this(interval, name, method, (Object[])null);
		}
		
		public Task(int interval, String name, String method, Object...data){
			this.msg = createMessage(name, method, data);
			this.interval = interval;
		}

		@Override
		public void run() {
			
			getInbox().add(msg);
			
			if (interval > 0){
				Task t = new Task(this);
				// clear history list - becomes "new" message
				t.msg.historyList.clear();
				timer.schedule(t, interval);
			}
		}
	}

	/**
	 * Worker is a PickToLight level thread which operates over (potentially)
	 * all of the service modules. Displays have their own
	 * 
	 */
	public class Worker extends Thread {

		public boolean isWorking = false;

		public static final String TASK = "TASK";

		public HashMap<String, Object> data = new HashMap<String, Object>();

		public Worker(String task) {
			super(task);
			data.put(TASK, task);
		}

		public void run() {
			try {

				if (!data.containsKey(TASK)) {
					log.error("task is required");
					return;
				}
				String task = (String) data.get(TASK);
				isWorking = true;

				while (isWorking) {

					switch (task) {

					case "cycleAll":

						TreeMap<String, Module> sorted = new TreeMap<String, Module>(modules);
						for (Map.Entry<String, Module> o : sorted.entrySet()) {
							o.getValue().cycle((String) data.get("msg"));
						}
						isWorking = false;
						break;

					default:
						log.error(String.format("don't know how to handle task %s", task));
						break;
					}

				}
			} catch (Exception e) {
				isWorking = false;
			}
		}
	}

	public PickToLight(String n) {
		super(n);
		webgui = (WebGUI) createPeer("webgui");
		webgui.autoStartBrowser(false);
		webgui.useLocalResources(true);
		raspi = (RasPi) createPeer("raspi");
	}

	public String getVersion() {
		return Runtime.getVersion();
	}

	@Override
	public String getDescription() {
		return "Pick to light system";
	}

	public boolean kitToLight(String xmlKit) {
		return true;
	}

	public void createModules() {

		Integer[] devices = scanI2CDevices();

		// rather heavy handed no?
		modules.clear();
		
		log.info(String.format("found %d devices", devices.length));

		for (int i = 0; i < devices.length; ++i) {
			int deviceAddress = devices[i];
			// FIXME - kludge to work with our prototype
			// addresses of displays are above 100
			/*
			 * if (deviceAddress > 100) { createModule(rasPiBus, deviceAddress);
			 * }
			 */

			createModule(rasPiBus, deviceAddress);

		}

		// FIXME - kludge gor proto-type hardware
		/*
		 * if (Platform.isArm()) { createPCF8574(rasPiBus, 0x27); }
		 */
	}

	public boolean createModule(int bus, int address) {
		String key = makeKey(address);
		log.info(String.format("create module key %s (bus %d address %d)", key, bus, address));
		Module box = new Module(bus, address);
		modules.put(key, box);
		return true;
	}

	public void ledOn(Integer address) {
		String key = makeKey(address);
		log.info("ledOn address {}", key);
		if (modules.containsKey(key)) {
			modules.get(key).ledOn();
		} else {
			// FIXME - Service Error Cache !!!! - IN GLOBAL getModule !
			error("ledOn could not find module %d", key);
		}
	}

	public void ledOff(Integer address) {
		String key = makeKey(address);
		log.info("ledOff address {}", key);
		if (modules.containsKey(key)) {
			modules.get(key).ledOff();
		} else {
			// FIXME - Service Error Cache !!!! - IN GLOBAL getModule !
			error("ledOff could not find module %d", key);
		}
	}

	public void ledsAllOn() {
		TreeMap<String, Module> sorted = new TreeMap<String, Module>(modules);
		for (Map.Entry<String, Module> o : sorted.entrySet()) {
			o.getValue().ledOn();
		}
	}

	public void ledsAllOff() {
		TreeMap<String, Module> sorted = new TreeMap<String, Module>(modules);
		for (Map.Entry<String, Module> o : sorted.entrySet()) {
			o.getValue().ledOff();
		}
	}

	public String display(Integer address, String msg) {
		String key = makeKey(address);
		if (modules.containsKey(key)) {
			modules.get(key).display(msg);
			return msg;
		} else {
			String err = String.format("display could not find module %d", key);
			log.error(err);
			return err;
		}
	}

	// public final
	// IN MEMORY ERROR LIST !!!!!!
	// getErrors( Error - key - detail - time )

	public boolean cycleIPAddress() {
		Controller c = getController();
		String ip = c.getIpAddress();
		if (ip == null || ip.length() == 0) {
			error("could not get ip");
			return false;
		}
		cycleAll(ip);
		return true;
	}

	public String displayAll(String msg) {

		for (Map.Entry<String, Module> o : modules.entrySet()) {
			Module mc = o.getValue();
			mc.display(msg);
		}

		return msg;
	}

	// FIXME normalize splitting code
	public String display(String moduleList, String value) {
		if (moduleList == null) {
			log.error("box list is null");
			return "box list is null";
		}
		String[] list = moduleList.split(" ");
		for (int i = 0; i < list.length; ++i) {
			try {
				String strKey = list[i].trim();
				if (strKey.length() > 0) {

					String key = makeKey(Integer.parseInt(strKey));
					if (modules.containsKey(key)) {
						modules.get(key).display(value);
					} else {
						log.error(String.format("display could not find module %s", strKey));
					}
				}
			} catch (Exception e) {
				Logging.logException(e);
			}
		}
		return moduleList;
	}

	/**
	 * single location for key generation - in case other parts are add in a
	 * composite key
	 * 
	 * @param address
	 * @return
	 */
	public String makeKey(Integer address) {
		return makeKey(rasPiBus, address);
	}

	public String makeKey(Integer bus, Integer address) {
		// return String.format("%d.%d", bus, address);
		return String.format("%d", address);
	}

	// DEPRECATE ???
	@Override
	public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
		// display pin state on console

		System.out.println(" --> GPIO PIN STATE CHANGE: " + event.getPin() + " [" + event.getPin().getName() + "]" + " = " + event.getState());
		GpioPin pin = event.getPin();

		/*
		 * if (pin.getName().equals("GPIO 0")) {
		 * modules.get("01").blinkOff("ok"); } else if
		 * (pin.getName().equals("GPIO 1")) { modules.get("02").blinkOff("ok");
		 * } else if (pin.getName().equals("GPIO 2")) {
		 * modules.get("03").blinkOff("ok"); } else if
		 * (pin.getName().equals("GPIO 3")) { modules.get("04").blinkOff("ok");
		 * }
		 */

		// if (pin.getName().equals(anObject))
	}

	public PCF8574GpioProvider createPCF8574(Integer bus, Integer address) {
		try {

			// System.out.println("<--Pi4J--> PCF8574 GPIO Example ... started.");

			log.info(String.format("PCF8574 - begin - bus %d address %d", bus, address));

			// create gpio controller
			GpioController gpio = GpioFactory.getInstance();

			// create custom MCP23017 GPIO provider
			// PCF8574GpioProvider gpioProvider = new
			// PCF8574GpioProvider(I2CBus.BUS_1,
			// PCF8574GpioProvider.PCF8574A_0x3F);
			PCF8574GpioProvider gpioProvider = new PCF8574GpioProvider(bus, address);

			// provision gpio input pins from MCP23017
			GpioPinDigitalInput myInputs[] = { gpio.provisionDigitalInputPin(gpioProvider, PCF8574Pin.GPIO_00), gpio.provisionDigitalInputPin(gpioProvider, PCF8574Pin.GPIO_01),
					gpio.provisionDigitalInputPin(gpioProvider, PCF8574Pin.GPIO_02), gpio.provisionDigitalInputPin(gpioProvider, PCF8574Pin.GPIO_03),
					gpio.provisionDigitalInputPin(gpioProvider, PCF8574Pin.GPIO_04), gpio.provisionDigitalInputPin(gpioProvider, PCF8574Pin.GPIO_05),
					gpio.provisionDigitalInputPin(gpioProvider, PCF8574Pin.GPIO_06), gpio.provisionDigitalInputPin(gpioProvider, PCF8574Pin.GPIO_07) };

			// create and register gpio pin listener
			log.info(String.format("PCF8574 - end - bus %d address %d", bus, address));

			return gpioProvider;

		} catch (Exception e) {
			Logging.logException(e);
		}

		return null;
	}

	public Integer[] scanI2CDevices() {
		ArrayList<Integer> ret = new ArrayList<Integer>();

		// our modules don't have addresses above 56
		Integer[] all = raspi.scanI2CDevices(rasPiBus);
		for (int i = 0; i < all.length; ++i) {
			Integer address = all[i];
			if (address > 56) {
				continue;
			}

			ret.add(all[i]);
		}

		return ret.toArray(new Integer[ret.size()]);
	}

	public void startService() {
		super.startService();
		raspi.startService();
		webgui.startService();
		createModules();
	}

	public Controller getController() {

		try {
			Controller controller = new Controller();

			controller.setVersion(Runtime.getVersion());
			controller.setName(getName());
			
			String ip = "";
			String mac = "";

			ArrayList<String> addresses = Runtime.getLocalAddresses();
			if (addresses.size() != 1) {
				log.error(String.format("incorrect number of ip addresses %d", addresses.size()));
			} 
			
			if (!addresses.isEmpty()){
				ip = addresses.get(0);
			}

			ArrayList<String> macs = Runtime.getLocalHardwareAddresses();
			if (macs.size() != 1) {
				log.error(String.format("incorrect number of mac addresses %d", addresses.size()));
			} 
			if (!macs.isEmpty()){
				mac = macs.get(0);
			}
			
			controller.setIpAddress(ip);
			controller.setMacAddress(mac);
			
			controller.setModules(modules);

			return controller;
		} catch (Exception e) {
			Logging.logException(e);
		}
		return null;
	}

	// ------------ TODO - IMPLEMENT - BEGIN ----------------------
	public String update(String url) {
		// TODO - auto-update
		return "TODO - auto update";
	}

	public String update() {
		return update(updateURL);
	}

	public void drawColon(Integer bus, Integer address, boolean draw) {

	}

	public int setBrightness(Integer address, Integer level) {
		return level;
	}

	public Module getModule(Integer address) {
		return getModule(rasPiBus, address);
	}

	public Module getModule(Integer bus, Integer address) {
		String key = makeKey(bus, address);
		if (!modules.containsKey(key)) {
			log.error(String.format("get module - could not find module with key %s", key));
			return null;
		}
		return modules.get(key);
	}

	// ---- cycling message on individual module begin ----
	public void cycle(Integer address, String msg) {
		cycle(address, msg, 300);
	}

	public void cycle(Integer address, String msg, Integer delay) {
		getModule(address).cycle(msg, delay);
	}

	public void cycleStop(Integer address) {
		getModule(address).cycleStop();
	}

	public void cycleAll(String msg) {
		cycleAll(msg, 300);
	}

	public void cycleAll(String msg, int delay) {
		TreeMap<String, Module> sorted = new TreeMap<String, Module>(modules);
		for (Map.Entry<String, Module> o : sorted.entrySet()) {
			o.getValue().cycle(msg, delay);
		}
	}

	public void cycleAllStop() {
		TreeMap<String, Module> sorted = new TreeMap<String, Module>(modules);
		for (Map.Entry<String, Module> o : sorted.entrySet()) {
			o.getValue().cycleStop();
		}
	}

	public void clearAll() {
		TreeMap<String, Module> sorted = new TreeMap<String, Module>(modules);
		for (Map.Entry<String, Module> o : sorted.entrySet()) {
			o.getValue().clear();
		}
	}

	public void displayI2CAddresses() {
		for (Map.Entry<String, Module> o : modules.entrySet()) {
			o.getValue().display(o.getKey());
		}
	}

	public void blinkOn(Integer address, String msg) {
		blinkOn(address, msg, blinkNumber, blinkDelay);
	}

	public void blinkOn(Integer address, String msg, int blinkNumber, int blinkDelay) {
		getModule(address).blinkOn(msg, blinkNumber, blinkDelay);
	}
	
	public void blinkOff(Integer address, String msg) {
		blinkOff(address, msg, blinkNumber, blinkDelay);
	}

	public void blinkOff(Integer address, String msg, int blinkNumber, int blinkDelay) {
		getModule(address).blinkOff(msg, blinkNumber, blinkDelay);
	}


	public void startWorker(String key) {
		if (workers.containsKey("cycleAll")) {
			if (worker != null) {
				worker.isWorking = false;
				worker.interrupt();
				worker = null;
			}
		}
	}

	public void stopWorker(String key) {
		if (workers.containsKey("cycleAll")) {
			if (worker != null) {
				worker.isWorking = false;
				worker.interrupt();
				worker = null;
			}
		}
	}
	
	public void autoRefreshI2CDisplay()
	{
		addLocalTask(1*1000, "refreshI2CDisplay");
	}
	
	public void refreshI2CDisplay()
	{
		createModules();
		displayI2CAddresses();
	}

	// ---- cycling message on individual module end ----

	// need a complex type
	public String kitToLight(ArrayList<String> kit) {
		return "";
	}

	public void writeToDisplay(int address, byte b0, byte b1, byte b2, byte b3) {
		try {

			I2CBus i2cbus = I2CFactory.getInstance(rasPiBus);
			I2CDevice device = i2cbus.getDevice(address);
			device.write(address, (byte) 0x80);

			I2CDevice display = i2cbus.getDevice(0x38);
			display.write(new byte[] { 0, 0x17, b0, b1, b2, b3 }, 0, 6);

			device.write(address, (byte) 0x83);

		} catch (Exception e) {
			Logging.logException(e);
		}
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	final public static String soapTemplate = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tem=\"http://tempuri.org/\"><soapenv:Header/><soapenv:Body><tem:RegisterController><tem:Name>%s</tem:Name><tem:MACAddress>%s</tem:MACAddress><tem:IPAddress>%s</tem:IPAddress><tem:I2CAddresses></tem:I2CAddresses></tem:RegisterController></soapenv:Body></soapenv:Envelope>";

	public String register() {
		try {
			DefaultHttpClient httpclient = new DefaultHttpClient();
			List<String> authpref = new ArrayList<String>();
			authpref.add(AuthPolicy.NTLM);
			httpclient.getParams().setParameter(AuthPNames.TARGET_AUTH_PREF, authpref);
			NTCredentials creds = new NTCredentials("MESSystem", "D@1ml3r2011", "", "Freightliner");
			httpclient.getCredentialsProvider().setCredentials(AuthScope.ANY, creds);

			// HttpHost target = new HttpHost("localhost", 80, "http");

			// Make sure the same context is used to execute logically related
			// requests
			HttpContext localContext = new BasicHttpContext();

			// Execute a cheap method first. This will trigger NTLM
			// authentication
			// WORKS !! HttpGet httpget = new
			// HttpGet("http://ttnacvdd018a:9501/MES/Materials/SoapService.svc");

			// HttpPost post = new
			// HttpPost("http://ttnacvdd018a:9501/MES/Materials/SoapService.svc");

			HttpPost post = new HttpPost("http://ttnacvdd018a:9501/MES/Materials/SoapService.svc");

			// Runtime.getLocalAddress();

			//String body = String.format(soapTemplate, "name", Runtime.getLocalMacAddress2(), "52.7.77.77");

			// ,"utf-8"
			/*
			 * <tem:I2CAddresses> <!--Zero or more repetitions:-->
			 * <arr:string>?</arr:string> </tem:I2CAddresses>
			 */

			// HttpResponse response = httpclient.execute(target, httpget,
			// localContext);
			HttpResponse response = httpclient.execute(post, localContext);
			HttpEntity entity = response.getEntity();
			String ret = EntityUtils.toString(entity);
			log.info(ret);
			return ret;

			// parse the response - check
		} catch (Exception e) {
			Logging.logException(e);
			return e.getMessage();
		}

	}
	
	public void timerPurge()
	{
		if (timer != null){
			timer.cancel(); 
			timer.purge();
		}
	}
	
	public void addLocalTask(int interval, String method){
		if (timer == null) {
			timer = new Timer(String.format("%s.timer", getName()));
		}
		
		Task task = new Task(interval, getName(), method);
		timer.schedule(task, 0);
	}

	// ------------ TODO - IMPLEMENT - END ----------------------

	public static void main(String[] args) {
		LoggingFactory.getInstance().configure();
		LoggingFactory.getInstance().setLevel(Level.DEBUG);

		PickToLight pick = new PickToLight("pick.1");
		pick.startService();
		pick.autoRefreshI2CDisplay();
		
		
		boolean ret = true;
		if (ret){
			return;
		}
		
		pick.register();
		pick.createModules();
		
		Controller controller = pick.getController();
		pick.startService();
		
		int selector = 0x83; // IR selected - LED OFF

		/*
		int MASK_DISPLAY = 0x01;
		int MASK_LED = 0x02;
		int MASK_SENSOR = 0x80;
		*/
		
		log.info(String.format("0x%s", Integer.toHexString(selector)));
		selector &= ~Module.MASK_LED; // LED ON
		log.info(String.format("0x%s", Integer.toHexString(selector)));
		selector |= Module.MASK_LED; // LED OFF
		log.info(String.format("0x%s", Integer.toHexString(selector)));


		ArrayList<String> ips = Runtime.getLocalAddresses();

		for (int i = 0; i < ips.size(); ++i) {
			log.info(ips.get(i));
		}

		ips = Runtime.getLocalHardwareAddresses();

		for (int i = 0; i < ips.size(); ++i) {
			log.info(ips.get(i));
		}



		// Controller2 c = pick.getController();
		// log.info("{}", c);

		// String binList = pickToLight.getBoxList();
		// pickToLight.display(binList, "helo");
		/*
		 * pickToLight.display("01", "1234"); pickToLight.display(" 01 02 03 ",
		 * "1234  1"); pickToLight.display("01 03", " 1234"); //
		 * pickToLight.display(binList, "1234 ");
		 */

		// Runtime.createAndStart("web", "WebGUI");

		// Runtime.createAndStart("webgui", "WebGUI");
		/*
		 * GUIService gui = new GUIService("gui"); gui.startService();
		 */

	}

}
