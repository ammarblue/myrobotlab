package org.myrobotlab.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.Vector;

import org.myrobotlab.cmdline.CMDLine;
import org.myrobotlab.fileLib.FileIO;
import org.myrobotlab.framework.Encoder;
import org.myrobotlab.framework.MRLListener;
import org.myrobotlab.framework.Message;
import org.myrobotlab.framework.MethodEntry;
import org.myrobotlab.framework.Platform;
import org.myrobotlab.framework.Service;
import org.myrobotlab.framework.ServiceEnvironment;
import org.myrobotlab.framework.ServiceInfo;
import org.myrobotlab.image.SerializableImage;
import org.myrobotlab.logging.Appender;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.Logging;
import org.myrobotlab.logging.LoggingFactory;
import org.myrobotlab.net.HTTPRequest;
import org.myrobotlab.service.interfaces.Communicator;
import org.myrobotlab.service.interfaces.ServiceInterface;
import org.simpleframework.xml.Root;
import org.slf4j.Logger;

/**
 * 
 * Runtime is responsible for the creation and removal of all Services and the
 * associated static registries It maintains state information regarding
 * possible & running local Services It maintains state information regarding
 * foreign Runtimes It is a singleton and should be the only service of Runtime
 * running in a process The host and registry maps are used in routing
 * communication to the asppropriate service (be it local or remote) It will be
 * the first Service created It also wraps the real JVM Runtime object
 * 
 */
@Root
public class Runtime extends Service {
	final static private long serialVersionUID = 1L;

	// ---- rte members begin ----------------------------
	static private HashMap<URI, ServiceEnvironment> hosts = new HashMap<URI, ServiceEnvironment>();;
	static private HashMap<String, ServiceInterface> registry = new HashMap<String, ServiceInterface>();

	/**
	 * map to hide methods we are not interested in
	 */
	static private HashSet<String> hideMethods = new HashSet<String>();

	static private boolean needsRestart = false;

	static private String runtimeName;

	static private Date startDate = new Date();

	final static public String helpString = "java -Djava.library.path=./libraries/native/x86.32.windows org.myrobotlab.service.Runtime -service gui GUIService -logLevel INFO -logToConsole";

	// ---- rte members end ------------------------------

	private static long uniqueID = new Random(System.currentTimeMillis()).nextLong();

	// ---- Runtime members begin -----------------
	// TODO make this singleton - so Runtime.update works
	public final ServiceInfo serviceInfo = new ServiceInfo();

	/*
	 * @Element public String proxyHost;
	 * 
	 * @Element public String proxyPort;
	 * 
	 * @Element public String proxyUserName;
	 * 
	 * @Element public String proxyPassword;
	 */

	static ServiceInterface gui = null;
	// ---- Runtime members end -----------------

	public final static Logger log = LoggerFactory.getLogger(Runtime.class);

	/**
	 * Object used to synchronize initializing this singleton.
	 */
	private static final Object instanceLockObject = new Object();
	/**
	 * The singleton of this class.
	 */
	private static Runtime localInstance = null;

	// auto update begin ------
	// @Element
	// private static boolean isAutoUpdateEnabled = false;
	// default eveery 5 minutes
	private static int autoUpdateCheckIntervalSeconds = 300;

	public static void startAutoUpdate(int seconds) {
		Runtime runtime = Runtime.getInstance();
		// isAutoUpdateEnabled = true;
		autoUpdateCheckIntervalSeconds = seconds;
		// only runtime can auto-update
		// FIXME - re-implement but only start if there is a Task - runtime.timer.schedule(new AutoUpdate(), autoUpdateCheckIntervalSeconds * 1000);
	}

	public static String getVersion() {
		return FileIO.getResourceFile("version.txt");
	}

	public static String getUptime() {
		Date now = new Date();
		long diff = now.getTime() - startDate.getTime();

		long diffSeconds = diff / 1000 % 60;
		long diffMinutes = diff / (60 * 1000) % 60;
		long diffHours = diff / (60 * 60 * 1000) % 24;
		long diffDays = diff / (24 * 60 * 60 * 1000);

		StringBuffer sb = new StringBuffer();
		sb.append(diffDays).append(" days ").append(diffHours).append(" hours ").append(diffMinutes).append(" minutes ").append(diffSeconds).append(" seconds ");
		return sb.toString();
	}

	public static void stopAutoUpdate() {
		Runtime runtime = Runtime.getInstance();
		/* FIXME - re-implement but only start if there is a task
		runtime.timer.cancel();
		runtime.timer.purge();
		*/
	}

	// TODO deprecate - this can be handled more generally in a
	// AutoTask
	static class AutoUpdate extends TimerTask {

		@Override
		public void run() {
			Runtime runtime = Runtime.getInstance();
			runtime.info("starting auto-update check");

			String newVersion = Runtime.getBleedingEdgeVersionString();
			String currentVersion = FileIO.getResourceFile("version.txt");
			log.info(String.format("comparing new version %s with current version %s", newVersion, currentVersion));
			if (newVersion == null) {
				runtime.info("newVersion == null - nothing available");
			} else if (currentVersion.compareTo(newVersion) >= 0) {
				log.info("no updates");
				runtime.info("no updates available");
			} else {
				runtime.info(String.format("updating with %s", newVersion));
				// Custom button text
// FIXM re-implement but only start if you have a task runtime.timer.schedule(new AutoUpdate(), autoUpdateCheckIntervalSeconds * 1000);
				Runtime.getBleedingEdgeMyRobotLabJar();
				Runtime.restart("moveUpdate");

			}

			log.info("re-setting timer begin");
// FIXME - re-implement but only start if there is a task runtime.timer.schedule(new AutoUpdate(), autoUpdateCheckIntervalSeconds * 1000);
			log.info("re-setting timer end");
		}
	}

	/**
	 * Constructor.
	 * 
	 * @param n
	 */
	public Runtime(String n) {
		super(n);

		synchronized (instanceLockObject) {
			localInstance = this;
		}

		hideMethods.add("main");
		hideMethods.add("loadDefaultConfiguration");
		hideMethods.add("getDescription");
		hideMethods.add("run");
		hideMethods.add("access$0");

		// load the current set of possible service
		serviceInfo.getLocalServiceData();

		// starting this
		startService();
	}

	public Platform getLocalPlatform() {
		return getPlatform(null);
	}

	/**
	 * returns the platform type of a remote system
	 * @param uri - the access uri of the remote system
	 * @return Platform description
	 */
	public Platform getPlatform(URI uri) {
		ServiceEnvironment local = hosts.get(uri);
		if (local != null) {
			return local.platform;
		}

		error("can't get local platform in service environment");

		return null;
	}

	/**
	 * returns version string of MyRobotLab
	 * @return
	 */
	public String getLocalVersion() {
		return getVersion(null);
	}

	/**
	 * returns version string of MyRobotLab instance based on
	 * uri 
	 * e.g :
	 *      uri mrl://10.5.3.1:7777 may be a remote instance
	 *      null uri is local
	 * @param uri - key of ServiceEnvironment
	 * @return version string
	 */
	public String getVersion(URI uri) {
		ServiceEnvironment local = hosts.get(uri);
		if (local != null) {
			return local.version;
		}

		error("can't get local version in service environment");

		return null;
	}

	/**
	 * updates the myrobotlab.jar 
	 */
	synchronized public static void updateMyRobotLab() {
		Runtime.getInstance().info("updating myrobotlab.jar");
		Runtime.getBleedingEdgeMyRobotLabJar();
		Runtime.getInstance().info("moving jar");
		Runtime.restart("moveUpdate");
	}

	/**
	 * check if class is a Runtime class
	 * @param newService
	 * @return true if class == Runtime.class
	 */
	public static boolean isRuntime(Service newService) {
		return newService.getClass().equals(Runtime.class);
	}

	/**
	 * Get a handle to the Runtime singleton.
	 * 
	 * @return the Runtime
	 */
	public static Runtime getInstance() {
		if (localInstance == null) {
			synchronized (instanceLockObject) {
				if (localInstance == null) {
					// TODO should this be configurable?
					if (runtimeName == null) {
						// runtimeName = String.format("MRL%1$d", new
						// Random().nextInt(99999));
						runtimeName = "runtime";
					}
					localInstance = new Runtime(runtimeName);
				}
			}
		}
		return localInstance;
	}

	/**
	 * Stops all service-related running items. This releases the singleton
	 * referenced by this class, but it does not guarantee that the old service
	 * will be GC'd. FYI - if stopServices does not remove INSTANCE - it is not
	 * re-entrant in junit tests
	 */
	@Override
	public void stopService() {
		/* FIXME - re-implement but only start if you have a task
		if (timer != null) {
			timer.cancel(); // stop all scheduled jobs
			timer.purge();
		}
		*/
		super.stopService();
		localInstance = null;
	}

	/**
	 * Runtime singleton service
	 */
	@Override
	public String getDescription() {
		return "Runtime singleton service";
	}

	// ---------- Java Runtime wrapper functions begin --------
	/**
	 * Executes the specified command and arguments in a separate process.  
	 * Returns the exit value for the subprocess.
	 * @param params
	 * @return
	 */
	public int exec(String[] params) {
		java.lang.Runtime r = java.lang.Runtime.getRuntime();
		try {
			Process p = r.exec(params);
			return p.exitValue();
		} catch (IOException e) {
			logException(e);
		}

		return 0;
	}

	/**
	 * dorky pass-throughs to the real JVM Runtime
	 * 
	 * @return
	 */
	public static final long getTotalMemory() {
		return java.lang.Runtime.getRuntime().totalMemory();
	}

	/**
	 * Returns the amount of free memory in the Java Virtual Machine. 
	 * Calling the gc method may result in increasing the value returned by freeMemory.
	 * @return
	 */
	public static final long getFreeMemory() {
		return java.lang.Runtime.getRuntime().freeMemory();
	}

	// Reference - cpu utilization
	// http://www.javaworld.com/javaworld/javaqa/2002-11/01-qa-1108-cpu.html

	/**
	 * Returns the number of processors available to the Java virtual machine. 
	 * @return
	 */
	public static final int availableProcessors() {
		return java.lang.Runtime.getRuntime().availableProcessors();
	}

	/**
	 * big hammer - exits no ifs ands or butts
	 */
	public static final void exit() {
		java.lang.Runtime.getRuntime().exit(-1);
	}

	/**
	 * Terminates the currently running Java virtual machine by initiating its shutdown sequence. This method never returns normally. 
	 * The argument serves as a status code; by convention, a nonzero status code indicates abnormal termination 
	 * @param status
	 */
	public static final void exit(int status) {
		java.lang.Runtime.getRuntime().exit(status);
	}

	/**
	 * Runs the garbage collector. 
	 */
	public static final void gc() {
		java.lang.Runtime.getRuntime().gc();
	}

	/**
	 * 
	 * @param filename
	 */
	public static final void loadLibrary(String filename) {
		java.lang.Runtime.getRuntime().loadLibrary(filename);
	}

	// ---------- Java Runtime wrapper functions end --------
	/**
	 * 
	 * FIXME - need Platform information (reference from within service ???
	 * 
	 * @param url
	 * @param s
	 * @return
	 */
	public final static synchronized ServiceInterface register(ServiceInterface s, URI url) {
		ServiceEnvironment se = null;
		if (!hosts.containsKey(url)) {
			se = new ServiceEnvironment(url);
			hosts.put(url, se);
		} else {
			se = hosts.get(url);
		}

		if (se.serviceDirectory.containsKey(s.getName())) {
			log.info(String.format("attempting to register %1$s which is already registered in %2$s", s.getName(), url));
			if (localInstance != null) {
				localInstance.invoke("collision", s.getName());
				localInstance.error("collision registering %s", s.getName());
				Runtime.getInstance().info(String.format(" name collision with %s", s.getName()));
			}
			return s;
		}
		
		// REMOTE BROADCAST to all foreign environments
		// FIXME - Security determines what to export
		// for each gateway
		
		/*
		Vector<String> remoteGateways = getServicesFromInterface(Communicator.class.getCanonicalName());
		for (int ri = 0; ri < remoteGateways.size(); ++ri){
			String n = remoteGateways.get(ri);
			//Communicator gateway = (Communicator)registry.get(n);
			ServiceInterface gateway = registry.get(n);
			
			// for each JVM this gateway is is attached too
			for (Map.Entry<URI, ServiceEnvironment> o : hosts.entrySet()) {
				// Map.Entry<String,SerializableImage> pairs = o;
				URI uri = o.getKey();
				if (uri != null && gateway.getName().equals(uri.getHost())){
					log.info(String.format("gateway %s sending registration of %s remote to %s", gateway.getName(), s.getName(), uri));
					Runtime rt = Runtime.getInstance();
					// FIXME - Security determines what to export
					Message msg = rt.createMessage("", "register", s);
					((Communicator)gateway).sendRemote(uri, msg);
				}
			}
		}
		*/
		
		//ServiceInterface sw = new ServiceInterface(s, se.accessURL);
		se.serviceDirectory.put(s.getName(), s);
		registry.put(s.getName(), s); //  FIXME FIXME FIXME FIXME  !!!!!! pre-pend URI if not NULL !!! 
		if (localInstance != null) {
			localInstance.invoke("registered", s);
		}

		return s;
	}
	
	public final static String getCWD()
	{
		return System.getProperty("user.dir");
	}

	/**
	 * the bare bones needed to register - just a name - if that's the case a
	 * MessagingListingService is created. This would be created by a Java
	 * application which was not really a service
	 * 
	 * @param name
	 */
	/*
	 * deprecated - use Proxy service public static synchronized void
	 * register(String name) { MessageListenerService mls = new
	 * MessageListenerService(name); ServiceInterface sw = new
	 * ServiceInterface(mls);
	 * 
	 * // FIXME // can't this be static // seems a kudge here...
	 * Runtime.getInstance().register(sw); }
	 */

	/**
	 * called by remote/foreign systems to register a new service through a
	 * subscription
	 * 
	 * @param sw
	 */
	public synchronized void register(ServiceInterface sw) {
		log.debug(String.format("register(ServiceInterface %s)", sw.getName()));

		if (registry.containsKey(sw.getName())) {
			log.warn("{} already defined - will not re-register", sw.getName());
			return;
		}

		ServiceEnvironment se = hosts.get(sw.getHost());
		if (se == null) {
			error(String.format("no service environment for %s", sw.getHost()));
			return;
		}

		if (se.serviceDirectory.containsKey(sw.getName())) {
			log.info("{} already registered", sw.getName());
			return;
		}
		// FIXME - does the refrence of this service wrapper need to point back
		// to the
		// service environment its referencing?
		// sw.host = se; - can't do this because its final

		se.serviceDirectory.put(sw.getName(), sw);
		registry.put(sw.getName(), sw);
		if (localInstance != null) {
			localInstance.invoke("registered", sw);
		}

	}

	/**
	 * registers an initial ServiceEnvironment which is a complete set of
	 * Services from a foreign instance of MRL. It returns whether changes have
	 * been made. This is necessary to determine if the register should be
	 * echoed back.
	 * 
	 * @param uri
	 * @param s
	 * @return false if it already matches
	 */
	public static synchronized boolean register(ServiceEnvironment s) {
		URI uri = s.accessURL;
		
		if (!hosts.containsKey(uri)) {
			log.info(String.format("adding new ServiceEnvironment {}", uri));
		} else {
			// FIXME ? - replace regardless ???
			if (areEqual(s)) {
				log.info(String.format("ServiceEnvironment {} already exists - with same count and names", uri));
				return false;
			}
			log.info(String.format("replacing ServiceEnvironment {}", uri.toString()));
		}

		hosts.put(uri, s);

		Iterator<String> it = s.serviceDirectory.keySet().iterator();
		String serviceName;
		while (it.hasNext()) {
			serviceName = it.next();
			log.info(String.format("adding %s to registry", serviceName));
			ServiceInterface si = s.serviceDirectory.get(serviceName);
			// IMPORTANT - s.accessURL == service.host !! NORMALIZE !!!
			si.setHost(uri);
			
			registry.put(serviceName, si);
			localInstance.invoke("registered", s.serviceDirectory.get(serviceName));

			// if I find a runtime - subscribe this Runtime to remote Runtime
			if ("org.myrobotlab.service.Runtime".equals(s.getClass().getCanonicalName())) {
				log.info(String.format("found runtime %s", serviceName));
				localInstance.subscribe("registered", serviceName, "register", ServiceInterface.class);
			}

		}

		return true;
	}

	/**
	 * Checks if s has the same service directory content as the environment at
	 * url.
	 * 
	 * @param s
	 * @param url
	 * @return
	 */
	private static boolean areEqual(ServiceEnvironment s) {
		URI url = s.accessURL;
		ServiceEnvironment se = hosts.get(url);
		if (se.serviceDirectory.size() != s.serviceDirectory.size()) {
			return false;
		}

		s.serviceDirectory.keySet().iterator();
		Iterator<String> it = s.serviceDirectory.keySet().iterator();
		String serviceName;
		while (it.hasNext()) {
			serviceName = it.next();
			if (!se.serviceDirectory.containsKey(serviceName)) {
				return false;
			}
		}
		return true;
	}

	/*
	 * FIXME - possibly needed when the intent is to remove the registration of
	 * a foreign Service
	 */
	public static void unregister(URI url, String name) {
		if (!registry.containsKey(name)) {
			log.error(String.format("unregister %1$s does not exist in registry", name));
		} else {
			registry.remove(name);
		}

		if (!hosts.containsKey(url)) {
			log.error(String.format("unregister environment does note exist for %1$s.%2$s", url, name));
			return;
		}

		ServiceEnvironment se = hosts.get(url);

		if (!se.serviceDirectory.containsKey(name)) {
			log.error(String.format("unregister %2$s does note exist for %1$s.%2$s", url, name));
			return;
		}
		localInstance.invoke("released", se.serviceDirectory.get(name));
		se.serviceDirectory.remove(name);
	}

	/**
	 * unregister a service environment
	 * 
	 * @param url
	 */
	public static void unregisterAll(URI url) {
		if (!hosts.containsKey(url)) {
			log.error(String.format("unregisterAll %1$s does not exist", url));
			return;
		}

		ServiceEnvironment se = hosts.get(url);

		Iterator<String> it = se.serviceDirectory.keySet().iterator();
		String serviceName;
		while (it.hasNext()) {
			serviceName = it.next();
			unregister(url, serviceName);
		}
	}

	/**
	 * unregister everything
	 */
	public static void unregisterAll() {
		Iterator<URI> it = hosts.keySet().iterator();
		while (it.hasNext()) {
			unregisterAll(it.next());
		}
	}

	/**
	 * Gets the current total number of services registered
	 * services. This is the number of services in all Service
	 * Environments
	 * 
	 * @return
	 * total number of services
	 */
	public int getServiceCount() {
		int cnt = 0;
		Iterator<URI> it = hosts.keySet().iterator();
		ServiceEnvironment se;
		Iterator<String> it2;
		while (it.hasNext()) {
			se = hosts.get(it.next());
			it2 = se.serviceDirectory.keySet().iterator();
			while (it2.hasNext()) {
				++cnt;
				it2.next();
			}
		}
		return cnt;
	}

	/**
	 * 
	 * @return
	 */
	public int getServiceEnvironmentCount() {
		return hosts.size();
	}

	/**
	 * 
	 * @return
	 */
	public static ServiceEnvironment getLocalServices() {
		if (!hosts.containsKey(null)) {
			log.error("local (null) ServiceEnvironment does not exist");
			return null;
		}

		return hosts.get(null);
	}

	/**
	 * getLocalServicesForExport returns a filtered map of Service references to
	 * export to another instance of MRL. The objective of filtering may help
	 * resolve functionality, security, or technical issues. For example, the
	 * Dalvik JVM can only run certain Services. It would be error prone to
	 * export a GUIService to a jvm which does not support swing.
	 * 
	 * Since the map of Services is made for export - it is NOT a copy but
	 * references
	 * 
	 * The filtering is done by Service Type.. although in the future it could
	 * be extended to Service.getName()
	 * 
	 * @return
	 * 
	 * TODO DEPRECATE - SECURITY SERVICE SHOULD FILTER
	 */
	public static ServiceEnvironment getLocalServicesForExport() {
		if (!hosts.containsKey(null)) {
			log.error("local (null) ServiceEnvironment does not exist");
			return null;
		}

		ServiceEnvironment local = hosts.get(null);

		// URI is null but the "acceptor" will fill in the correct URI/ID
		ServiceEnvironment export = new ServiceEnvironment(null);

		Iterator<String> it = local.serviceDirectory.keySet().iterator();
		String name;
		ServiceInterface sw;
		while (it.hasNext()) {
			name = it.next();
			sw = local.serviceDirectory.get(name);
			if (sw.allowExport()) {
				//log.info(String.format("exporting service: %s of type %s", name, sw.getServiceType()));
				export.serviceDirectory.put(name, sw);  // FIXME  !! make note when XMPP or Remote Adapter pull it - they have to reset this !!
			} else {
				log.info(String.format("%s will not be exported", name));
				continue;
			}
		}

		return export;
	}

	public static boolean isLocal(String serviceName) {
		ServiceInterface sw = getService(serviceName);
		return sw.isLocal();
	}



	/**
	 * releases a service - stops the service, its threads,
	 * releases its resources, and removes registry entries
	 * @param name of the service to be released
	 * @return
	 * whether or not it successfully released the service
	 */
	public static boolean release(String name) /* release local Service */
	{
		log.warn("releasing service {}", name);
		ServiceInterface sw = getService(name);
		sw.stopService();
		registry.remove(name);
		ServiceEnvironment se = hosts.get(sw.getHost());
		se.serviceDirectory.remove(name);
		log.warn("released{}", name);
		return true;
	}

	/**
	 * 
	 * @param url
	 * @return
	 */
	public static boolean release(URI url) /* release process environment */
	{
		boolean ret = true;
		ServiceEnvironment se = hosts.get(url);
		if (se == null) {
			log.warn("attempt to release {} not successful - it does not exist", url);
			return false;
		}
		log.info(String.format("releasing url %1$s", url));
		String[] services = (String[]) se.serviceDirectory.keySet().toArray(new String[se.serviceDirectory.keySet().size()]);
		String runtimeName = null;
		ServiceInterface service;
		for (int i = 0; i < services.length; ++i) {
			service = registry.get(services[i]);
			if (service != null && "Runtime".equals(service.getSimpleName())) {
				runtimeName = service.getName();
				log.info(String.format("delaying release of Runtime %1$s", runtimeName));
				continue;
			}
			ret &= release(services[i]);
		}

		if (runtimeName != null) {
			ret &= release(runtimeName);
		}

		return ret;
	}

	/**
	 * 
	 * release all local services
	 * 
	 * FIXME - there "should" be an order to releasing the correct way would be
	 * to save the Runtime for last and broadcast all the services being
	 * released
	 * 
	 * FIXME - send SHUTDOWN event to all running services with a timeout period
	 * - end with System.exit()
	 */
	public static void releaseAll() /* local only? YES !!! LOCAL ONLY !! */
	{
		log.debug("releaseAll");

		// FIXME - release all by calling sub methods & normalize code

		// FIXME - this is a bit of a lie
		// broadcasting the info all services are released before releasing them
		// but you can't send the info if everything has been released :P

		ServiceEnvironment se = hosts.get(null); // local services only
		if (se == null) {
			log.info("releaseAll called when everything is released, all done here");
			return;
		}
		Iterator<String> seit = se.serviceDirectory.keySet().iterator();
		String serviceName;
		ServiceInterface sw;
		while (seit.hasNext()) {
			serviceName = seit.next();
			sw = se.serviceDirectory.get(serviceName);
			localInstance.invoke("released", se.serviceDirectory.get(serviceName));
		}

		seit = se.serviceDirectory.keySet().iterator();
		while (seit.hasNext()) {
			serviceName = seit.next();
			sw = se.serviceDirectory.get(serviceName);
			log.info(String.format("stopping service %s/%s", se.accessURL, serviceName));

			if (sw == null) {
				log.warn("unknown type and/or remote service");
				continue;
			}
			sw.stopService();
		}

		log.info("clearing hosts environments");
		hosts.clear();

		log.info("clearing registry");
		registry.clear();
	}

	/**
	 * 
	 * @return
	 */
	public static HashMap<String, ServiceInterface> getRegistry() {
		return registry;// FIXME should return copy
	}

	/**
	 * 
	 * @param url
	 * @return
	 */
	public static ServiceEnvironment getServiceEnvironment(URI url) {
		if (hosts.containsKey(url)) {
			return hosts.get(url); // FIXME should return copy
		}
		return null;
	}

	/**
	 * 
	 * @return
	 */
	public static HashMap<URI, ServiceEnvironment> getServiceEnvironments() {
		return new HashMap<URI, ServiceEnvironment>(hosts);
	}

	/**
	 * 
	 * @param serviceName
	 * @return
	 */
	public static HashMap<String, MethodEntry> getMethodMap(String serviceName) {
		if (!registry.containsKey(serviceName)) {
			log.error(String.format("%1$s not in registry - can not return method map", serviceName));
			return null;
		}

		HashMap<String, MethodEntry> ret = new HashMap<String, MethodEntry>();
		ServiceInterface sw = registry.get(serviceName);

		Class<?> c = sw.getClass();
		Method[] methods = c.getDeclaredMethods();

		Method m;
		MethodEntry me;
		String s;
		for (int i = 0; i < methods.length; ++i) {
			m = methods[i];

			if (hideMethods.contains(m.getName())) {
				continue;
			}
			me = new MethodEntry();
			me.name = m.getName();
			me.parameterTypes = m.getParameterTypes();
			me.returnType = m.getReturnType();
			s = MethodEntry.getSignature(me.name, me.parameterTypes, me.returnType);
			ret.put(s, me);
		}

		return ret;
	}

	/**
	 * DEPRICATE 
	 * saves binary definition of MRL
	 * @param filename
	 * @return
	 */
	public static boolean save(String filename) {
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {

			// ServiceEnvironment se = getLocalServices();

			fos = new FileOutputStream(filename);
			out = new ObjectOutputStream(fos);

			out.writeObject(hosts);

			out.writeObject(registry);
			out.writeObject(hideMethods);
			out.flush();
		} catch (Exception e) {
			Logging.logException(e);
			return false;
		} finally {
			if (out != null) {
				try {
					// todo - should we flush first?
					out.close();
				} catch (Exception e) {
				}
			}
		}

		return true;
	}

	/**
	 * DEPRICATE 
	 * loads binary definition of MRL
	 * @param filename
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static boolean load(String filename) {
		ObjectInputStream in = null;
		try {
			FileInputStream fis;
			fis = new FileInputStream(filename);
			in = new ObjectInputStream(fis);
			// instance = (RuntimeEnvironment)in.readObject();
			hosts = (HashMap<URI, ServiceEnvironment>) in.readObject();
			registry = (HashMap<String, ServiceInterface>) in.readObject();
			hideMethods = (HashSet<String>) in.readObject();
		} catch (Exception e) {
			Logging.logException(e);
			return false;
		} finally {
			if (in != null) {
				try {
					// TODO do we need to flush first?
					in.close();
				} catch (Exception e) {
				}
			}
		}

		return true;
	}

	/**
	 * 
	 */
	public static void startLocalServices() {
		// boolean hasGUI = false;
		// GUI gui = null;
		ServiceEnvironment se = getLocalServices();
		Iterator<String> it = se.serviceDirectory.keySet().iterator();
		String serviceName;
		ServiceInterface sw;
		while (it.hasNext()) {
			serviceName = it.next();
			sw = se.serviceDirectory.get(serviceName);
			sw.startService();
			/*
			 * if (sw.service.getClass().getSuperclass().equals(GUI.class)) {
			 * gui = (GUI)sw.service; hasGUI = true; }
			 */
		}
		/*
		 * if (hasGUI) { gui.display(); }
		 */
	}

	/**
	 * a method which returns a xml representation of all the listeners and
	 * routes in the runtime system
	 * 
	 * @return
	 */
	public static String dumpNotifyEntries() {
		ServiceEnvironment se = getLocalServices();
		
		Map<String, ServiceInterface> sorted = new TreeMap<String, ServiceInterface>(se.serviceDirectory);

		Iterator<String> it = sorted.keySet().iterator();
		String serviceName;
		ServiceInterface sw;
		String n;
		ArrayList<MRLListener> nes;
		MRLListener listener;

		StringBuffer sb = new StringBuffer().append("<NotifyEntries>");
		while (it.hasNext()) {
			serviceName = it.next();
			sw = sorted.get(serviceName);
			sb.append("<service name=\"").append(sw.getName()).append("\" serviceEnironment=\"").append(sw.getHost()).append("\">");
			ArrayList<String> nlks = sw.getNotifyListKeySet();
			if (nlks != null) {
				Iterator<String> nit = nlks.iterator();

				while (nit.hasNext()) {
					n = nit.next();
					sb.append("<addListener map=\"").append(n).append("\">");
					nes = sw.getNotifyList(n);
					for (int i = 0; i < nes.size(); ++i) {
						listener = nes.get(i);
						sb.append("<MRLListener outMethod=\"").append(listener.outMethod).append("\" name=\"").append(listener.name).append("\" inMethod=\"")
								.append(listener.outMethod).append("\" />");
					}
					sb.append("</addListener>");
				}
			}
			sb.append("</service>");

		}
		sb.append("</NotifyEntries>");

		return sb.toString();
	}

	/**
	 * 
	 * @param interfaceName
	 * @return
	 */
	public static Vector<String> getServicesFromInterface(String interfaceName) {
		Vector<String> ret = new Vector<String>();

		Iterator<String> it = registry.keySet().iterator();
		String serviceName;
		ServiceInterface sw;
		Class<?> c;
		Class<?>[] interfaces;
		Class<?> m;
		while (it.hasNext()) {
			serviceName = it.next();
			sw = registry.get(serviceName);
			c = sw.getClass();
			interfaces = c.getInterfaces();
			for (int i = 0; i < interfaces.length; ++i) {
				m = interfaces[i];

				if (m.getCanonicalName().equals(interfaceName)) {
					ret.add(sw.getName());
				}
			}
		}

		return ret;
	}

	/*
	 * 
	 * Implementation - now back in Service
	 * 
	 * // new transfer state fns public static Service copyState (Service local,
	 * Service remote) { if (local == remote) return local;
	 * 
	 * return local; } // the assumption is remote has been serialized and the
	 * // top level fields need to be merged over public static Object deepCopy
	 * (Object local, Object remote) { if (local == remote) return local;
	 * 
	 * return local; }
	 */
	public static void requestRestart() {
		needsRestart = true;
	}

	/**
	 * 
	 * @return
	 */
	public static boolean needsRestart() {
		return needsRestart;
	}

	// ---------------- callback events begin -------------
	/**
	 * registration event
	 * 
	 * @param filename
	 *            - the name of the Service which was successfully registered
	 * @return
	 */
	public ServiceInterface registered(ServiceInterface sw) {
		return sw;
	}

	/**
	 * release event
	 * 
	 * @param filename
	 *            - the name of the Service which was successfully released
	 * @return
	 */
	public ServiceInterface released(ServiceInterface sw) {
		return sw;
	}

	/**
	 * collision event - when a registration is attempted but there is a name
	 * collision
	 * 
	 * @param name
	 *            - the name of the two Services with the same name
	 * @return
	 */
	public String collision(String name) {
		return name;
	}

	// ---------------- callback events end -------------

	// ---------------- Runtime begin --------------

	/**
	 * prints help to the console
	 */
	static void mainHelp() {
		System.out.println(String.format("Runtime %s", FileIO.getResourceFile("version.txt")));
		System.out.println("-h       			# help ");
		System.out.println("-v        			# print version");
		System.out.println("-update  			# force update");
		System.out.println("-runtimeName  		# rename the Runtime service - prevents multiple instance name collisions");
		System.out.println("-logToConsole       # redirects logging to console");
		System.out.println("-logLevel        	# log level [DEBUG | INFO | WARNING | ERROR | FATAL]");
		System.out.println("-service [Service Name] [Service Type] ...  # create and start list of services, e.g. -service gui GUIService");
		System.out.println("example:");
		System.out.println(helpString);
	}

	/**
	 * creates and starts service from a cmd line object
	 * @param cmdline
	 * data object from the cmd line
	 */
	public final static void createAndStartServices(CMDLine cmdline) {

		System.out.println(String.format("createAndStartServices service count %1$d", cmdline.getArgumentCount("-service") / 2));

		if (cmdline.getArgumentCount("-service") > 0 && cmdline.getArgumentCount("-service") % 2 == 0) {

			for (int i = 0; i < cmdline.getArgumentCount("-service"); i += 2) {

				log.info(String.format("attempting to invoke : org.myrobotlab.service.%1$s named %2$s", cmdline.getSafeArgument("-service", i + 1, ""),
						cmdline.getSafeArgument("-service", i, "")));

				String name = cmdline.getSafeArgument("-service", i, "");
				String type = cmdline.getSafeArgument("-service", i + 1, "");
				ServiceInterface s = Runtime.create(name, type);

				if (s != null) {
					s.startService();
				} else {
					log.error(String.format("could not create service %1$s %2$s", name, type));
				}

				// if the service has a display
				// delay the display untill all Services have
				// been created
				if (s.hasDisplay()) {
					gui = s;
				}
			}
			// if the system is going to have a display
			// display it
			if (gui != null) {
				gui.display();
			}
			return;
		} /*
		 * LIST ???
		 * 
		 * else if (cmdline.hasSwitch("-list")) { Runtime runtime =
		 * Runtime.getInstance(); if (runtime == null) {
		 * 
		 * } else { System.out.println(getServiceSimpleNames()); } return; }
		 */
		mainHelp();
	}

	/**
	 * Returns an array of all the simple type names of all the possible services.
	 * The data originates from the repo's serviceData.xml file
	 * https://code.google.com/p/myrobotlab/source/browse/trunk/myrobotlab/thirdParty/repo/serviceData.xml
	 * 
	 * There is a local one distributed with the install zip
	 * When a "update" is forced, MRL will try to download the latest copy from the repo.
	 * 
	 * The serviceData.xml lists all service types, dependencies, categories and other
	 * relevant information regarding service creation
	 * 
	 * @return
	 */
	public String[] getServiceSimpleNames() {
		return getServiceSimpleNames("all");
	}

	/**
	 * 
	 * @param filter
	 * @return
	 */
	public String[] getServiceSimpleNames(String filter) {
		return serviceInfo.getSimpleNames(filter);
	}

	/**
	 * 
	 * initially I thought that is would be a good idea to dynamically load
	 * Services and append their definitions to the class path. This would
	 * "theoretically" be done with ivy to get/download the appropriate
	 * dependent jars from the repo. Then use a custom ClassLoader to load the
	 * new service.
	 * 
	 * Ivy works for downloading the appropriate jars & artifacts However, the
	 * ClassLoader became very problematic
	 * 
	 * There is much mis-information around ClassLoaders. The most knowledgeable
	 * article I have found has been this one :
	 * http://blogs.oracle.com/sundararajan
	 * /entry/understanding_java_class_loading
	 * 
	 * Overall it became a huge PITA with really very little reward. The
	 * consequence is all Services' dependencies and categories are defined here
	 * rather than the appropriate Service class.
	 * 
	 * @return
	 */

	/**
	 * @param name
	 *            - name of Service to be removed and whos resources will be
	 *            released
	 */
	static public void releaseService(String name) {
		Runtime.release(name);
	}

	/**
	 * Main starting method of MyRobotLab
	 * Parses command line options
	 * 
	 * -h help
	 * -v version
	 * -list 
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		CMDLine cmdline = new CMDLine();
		cmdline.splitLine(args);

		Logging logging = LoggingFactory.getInstance();

		try {

			if (cmdline.containsKey("-h") || cmdline.containsKey("--help")) {
				mainHelp();
				return;
			}

			if (cmdline.containsKey("-v") || cmdline.containsKey("--version")) {
				System.out.print(FileIO.getResourceFile("version.txt"));
				return;
			}
			if (cmdline.containsKey("-runtimeName")) {
				runtimeName = cmdline.getSafeArgument("-runtimeName", 0, "MRL");
			}

			if (cmdline.containsKey("-logToConsole")) {
				logging.addAppender(Appender.CONSOLE);
			} else if (cmdline.containsKey("-logToRemote")) {
				String host = cmdline.getSafeArgument("-logToRemote", 0, "localhost");
				String port = cmdline.getSafeArgument("-logToRemote", 1, "4445");
				logging.addAppender(Appender.REMOTE, host, port);
			} else {
				if (cmdline.containsKey("-multiLog")) {
					logging.addAppender(Appender.FILE, "multiLog", null);
				} else {
					logging.addAppender(Appender.FILE);
				}
			}

			if (cmdline.containsKey("-autoUpdate")) {
				startAutoUpdate(autoUpdateCheckIntervalSeconds);
			}

			logging.setLevel(cmdline.getSafeArgument("-logLevel", 0, "INFO"));

			log.info(cmdline.toString());

			// LINUX LD_LIBRARY_PATH MUST BE EXPORTED - NO OTHER SOLUTION FOUND
			// hack to reconcile the different ways os handle and expect
			// "PATH & LD_LIBRARY_PATH" to be handled
			// found here -
			// http://blog.cedarsoft.com/2010/11/setting-java-library-path-programmatically/
			// but does not work

			if (cmdline.containsKey("-update")) {
				// force all updates
				ArrayList<String> services = cmdline.getArgumentList("-update");

				if (services.size() == 0) {
					Runtime runtime = Runtime.getInstance();
					if (runtime != null) {
						runtime.updateAll();
					} else {
						log.error("runtime is null");
					}
					return;
				} else {
					// TODO - do specific service install
					log.error("not implemented yet");
				}
			} else {
				createAndStartServices(cmdline);
			}

			invokeCommands(cmdline);

			// outbound - auto-connect
			if (cmdline.containsKey("-connect")) {
				String host = cmdline.getSafeArgument("-connect", 0, "localhost");
				String portStr = cmdline.getSafeArgument("-connect", 1, "6767");
				int port = Integer.parseInt(portStr);
				Runtime.getInstance().connect(null, null, host, port);
			}

		} catch (Exception e) {
			Logging.logException(e);
			System.out.print(Logging.stackToString(e));
			Service.sleep(2000);
		}
	}

	static public void invokeCommands(CMDLine cmdline) {
		int argCount = cmdline.getArgumentCount("-invoke");
		if (argCount > 1) {

			StringBuffer params = new StringBuffer();

			ArrayList<String> invokeList = cmdline.getArgumentList("-invoke");
			Object[] data = new Object[argCount - 2];
			for (int i = 2; i < argCount; ++i) {
				data[i - 2] = invokeList.get(i);
				params.append(String.format("%s ", invokeList.get(i)));
			}

			String name = cmdline.getArgument("-invoke", 0);
			String method = cmdline.getArgument("-invoke", 1);

			log.info(String.format("attempting to invoke : %s.%s(%s)\n", name, method, params.toString()));
			getInstance().send(name, method, data);

		}

	}

	static public ServiceInterface createAndStart(String name, String type) {
		ServiceInterface s = create(name, type);
		s.startService();
		s.display();
		return s;
	}

	
	/**
	 * 
	 * @param name
	 * @param type
	 * @return
	 */
	static public synchronized ServiceInterface create(String name, String type) {
		return create(name, "org.myrobotlab.service.", type);
	}

	/**
	 * @param name
	 *            - name of Service
	 * @param pkgName
	 *            - package of Service in case Services are created in different
	 *            packages
	 * @param type
	 *            - type of Service
	 * @return
	 */
	static public synchronized ServiceInterface create(String name, String pkgName, String type) {
		try {
			log.debug("Runtime.create - Class.forName");
			// get String Class
			String typeName = pkgName + type;
			// Class<?> cl = Class.forName(typeName);
			// Class<?> cl = Class.forName(typeName, false,
			// ClassLoader.getSystemClassLoader());
			return createService(name, typeName);
		} catch (Exception e) {
			Logging.logException(e);
		}
		return null;
	}

	/**
	 * FIXME - deprecate - require calling code to implement loop - support only
	 * the single update(fullTypeName) - that way calling code can handle
	 * detailed info such as reporting to gui/console which components are being
	 * updated and which have errors in the update process. Will need a list of
	 * all or filtered ArrayList<fullTypeName>
	 * 
	 * update - force system to check for all dependencies of all possible
	 * Services - Ivy will attempt to check & fufill dependencies by downloading
	 * jars from the repo
	 */
	public void updateAll() {

		boolean getNewRepoData = true;

		// TODO - have it return list of data objects "errors" so
		// events can be generated
		serviceInfo.clearErrors();

		// FIXME - not needed - set defaults [update all = true]
		if (getNewRepoData) {
			serviceInfo.getRepoFile("serviceData.xml");
		}
		if (!serviceInfo.hasErrors()) {
			serviceInfo.update();
			Runtime.getBleedingEdgeMyRobotLabJar();
			Runtime.restart("moveUpdate");
		}

		List<String> errors = serviceInfo.getErrors();
		for (int i = 0; i < errors.size(); ++i) {
			error(errors.get(i));
		}
	}

	/**
	 * publishing point of Ivy sub system - sends event failedDependency when the
	 * retrieve report for a Service fails
	 * 
	 * @param dep
	 * @return
	 */
	public String failedDependency(String dep) {
		return dep;
	}

	/**
	 * @param name
	 * @param cls
	 * @return
	 */
	static public synchronized ServiceInterface createService(String name, String fullTypeName) {
		log.info(String.format("Runtime.createService %s", name));
		if (name == null || name.length() == 0 || fullTypeName == null || fullTypeName.length() == 0) {
			log.error(String.format("%1$s not a type or %2$s not defined ", fullTypeName, name));
			return null;
		}

		ServiceInterface sw = Runtime.getService(name);
		if (sw != null) {
			log.debug(String.format("service %1$s already exists", name));
			return sw;
		}

		try {

			// TODO - determine if there have been new classes added from ivy
			log.debug("ABOUT TO LOAD CLASS");
			// TODO reduce the amount of log calls and put them in one log
			// statement
			log.info("loader for this class " + Runtime.class.getClassLoader().getClass().getCanonicalName());
			log.info("parent " + Runtime.class.getClassLoader().getParent().getClass().getCanonicalName());
			log.info("system class loader " + ClassLoader.getSystemClassLoader());
			log.info("parent should be null" + ClassLoader.getSystemClassLoader().getParent().getClass().getCanonicalName());
			log.info("thread context " + Thread.currentThread().getContextClassLoader().getClass().getCanonicalName());
			log.info("thread context parent " + Thread.currentThread().getContextClassLoader().getParent().getClass().getCanonicalName());
			log.info("refreshing classloader");

			Runtime runtime = Runtime.getInstance();
			if (!runtime.isInstalled(fullTypeName)) {
				runtime.error("%s is not installed - please install it", fullTypeName);
				// return null;
			}

			Class<?> cls = Class.forName(fullTypeName);
			Constructor<?> constructor = cls.getConstructor(new Class[] { String.class });

			// create an instance
			Object newService = constructor.newInstance(new Object[] { name });
			log.info("returning " + fullTypeName);
			return (Service) newService;
		} catch (Exception e) {
			Logging.logException(e);
		}
		return null;
	}

	// ---------------- Runtime end --------------

	/**
	 * 
	 * @return
	 */
	public static String dump() {
		StringBuffer sb = new StringBuffer().append("\nhosts:\n");

		Map<URI, ServiceEnvironment> sorted = hosts;// new TreeMap<URI, ServiceEnvironment>(hosts); - tree map doesnt allow null
		
		Iterator<URI> hkeys = sorted.keySet().iterator();
		URI url;
		ServiceEnvironment se;
		Iterator<String> it2;
		String serviceName;
		ServiceInterface sw;
		while (hkeys.hasNext()) {
			url = hkeys.next();
			se = hosts.get(url);
			sb.append("\t").append(url);
			
			// good check :)
			if ((se.accessURL != url) && (!url.equals(se.accessURL))) {
				sb.append(" key not equal to data ").append(se.accessURL);
			}
			sb.append("\n");

			// Service Environment
			Map<String, ServiceInterface> sorted2 = new TreeMap<String, ServiceInterface>(se.serviceDirectory);
			it2 = sorted2.keySet().iterator();
			while (it2.hasNext()) {
				serviceName = it2.next();
				sw = sorted2.get(serviceName);
				sb.append("\t\t").append(serviceName);
				sb.append("\n");
			}
		}

		sb.append("\nregistry:");

		Map<String, ServiceInterface> sorted3 = new TreeMap<String, ServiceInterface>(registry);
		Iterator<String> rkeys = sorted3.keySet().iterator();
		while (rkeys.hasNext()) {
			serviceName = rkeys.next();
			sw = sorted3.get(serviceName);
			sb.append("\n").append(serviceName).append(" ").append(sw.getHost());
		}

		return sb.toString();
	}

	/**
	 * this method attempts to connect to the repo and populate information
	 * regarding the latest ServiceDescriptors and their latest dependencies
	 */
	public void checkForUpdates() {

		// assume your dealing with the local system
		Runtime runtime = getInstance();

		// serviceInfo.getRepoServiceData();
		// get local data
		runtime.serviceInfo.getLocalServiceData();

		// get remote data
		runtime.serviceInfo.getRepoData();

		// addListener ready for updates
		getInstance().invoke("proposedUpdates", serviceInfo);
	}

	/**
	 * this method is an event notifier that there were updates found
	 */
	public ServiceInfo proposedUpdates(ServiceInfo si) {
		return si;
	}

	/**
	 * 
	 * @param fullTypeName
	 */
	public void update(String fullTypeName) {
		serviceInfo.getLocalServiceData();
		serviceInfo.resolve(fullTypeName);
	}

	/**
	 * published events
	 * 
	 * @param className
	 * @return
	 */
	public String resolveBegin(String className) {
		return className;
	}

	/**
	 * 
	 * @param errors
	 * @return
	 */
	public List<String> resolveError(List<String> errors) {
		return errors;
	}

	/**
	 * 
	 * @param className
	 * @return
	 */
	public String resolveSuccess(String className) {
		return className;
	}

	/**
	 * event fired when a new artifact is download
	 * 
	 * @param module
	 * @return
	 */
	public String newArtifactsDownloaded(String module) {
		return module;
	}

	/**
	 * 
	 */
	public void resolveEnd() {
	}

	// FIXME - you don't need that many "typed" messages - resolve,
	// resolveError, ... etc
	// just use & parse "message"

	public static String message(String msg) {
		getInstance().invoke("publishMessage", msg);
		log.info(msg);
		return msg;
	}

	public String publishMessage(String msg) {
		return msg;
	}

	public static String getBleedingEdgeVersionString() {
		try {
			//
			// String listURL =
			// "http://code.google.com/p/myrobotlab/downloads/list?can=2&q=&sort=uploaded&colspec=Filename%20Summary%20Uploaded%20ReleaseDate%20Size%20DownloadCount";
			String listURL = "http://code.google.com/p/myrobotlab/downloads/list?can=1&q=&colspec=Filename+Summary+Uploaded+ReleaseDate+Size+DownloadCount";
			log.info(String.format("getting list of dist %s", listURL));
			HTTPRequest http;
			http = new HTTPRequest(listURL);
			String s = http.getString();
			log.info(String.format("recieved [%s]", s));
			log.info("parsing");
			String myrobotlabBleedingEdge = "myrobotlab.bleeding.edge.";
			int p0 = s.indexOf(myrobotlabBleedingEdge);
			if (p0 > 0) {
				p0 += myrobotlabBleedingEdge.length();
				int p1 = s.indexOf(".jar", p0);
				String intermediate = s.substring(p0, p1);
				log.info(intermediate);
				return intermediate.trim();
			} else {
				log.error(String.format("could not parse results for %s in getBleedingEdgeVersionString", listURL));
			}
		} catch (Exception e) {
			Logging.logException(e);
		}

		return null;
	}

	public static void getBleedingEdgeMyRobotLabJar() {

		try {
			log.info("getBleedingEdgeMyRobotLabJar");
			String version = getBleedingEdgeVersionString();
			String latestMRLJar = "http://myrobotlab.googlecode.com/files/myrobotlab.bleeding.edge." + version + ".jar";
			log.info(String.format("getting latest build from %s", latestMRLJar));
			HTTPRequest zip = new HTTPRequest(latestMRLJar);
			byte[] jarfile = zip.getBinary();

			File updateDir = new File("update");
			updateDir.mkdir();
			File backupDir = new File("backup");
			backupDir.mkdir();

			FileOutputStream out = new FileOutputStream("update/myrobotlab.jar");
			try {
				out.write(jarfile);
				log.info("getBleedingEdgeMyRobotLabJar - done - since there is an update you will probably want to run scripts/update.(sh)(bat) to replace the jar");
			} catch (Exception e) {
				Logging.logException(e);
			} finally {
				out.close();
			}

			//

		} catch (IOException e) {
			Logging.logException(e);
		}
	}

	// References :
	// http://java.dzone.com/articles/programmatically-restart-java
	static public void restart(String restartScript) {
		log.info("new components - restart?");

		Runtime.releaseAll();
		try {
			if (restartScript == null) {
				if (Platform.isWindows()) {
					java.lang.Runtime.getRuntime().exec("cmd /c start myrobotlab.bat");
				} else {
					java.lang.Runtime.getRuntime().exec("./myrobotlab.sh");
				}
			} else {
				if (Platform.isWindows()) {
					java.lang.Runtime.getRuntime().exec(String.format("cmd /c start scripts\\%s.cmd", restartScript));
				} else {
					String command = String.format("./scripts/%s.sh", restartScript);
					File exe = new File(command); // FIXME - NORMALIZE !!!!!
					if (!exe.setExecutable(true)) {
						log.error(String.format("could not set %s to executable permissions", command));
					}
					java.lang.Runtime.getRuntime().exec(command);
				}
			}
		} catch (Exception ex) {
			Logging.logException(ex);
		}
		System.exit(0);

	}
	
	/**
	 * 
	 * @param name
	 * @return
	 */
	public static ServiceInterface getService(String name) {

		if (!registry.containsKey(name)) {
			log.debug(String.format("service %s does not exist", name));
			return null;
		}

		return registry.get(name);
	}

	/**
	 * save all configuration from all local services
	 * 
	 * @return
	 */
	static public boolean saveAll() {
		boolean ret = true;
		ServiceEnvironment se = getLocalServices();
		Iterator<String> it = se.serviceDirectory.keySet().iterator();
		String serviceName;
		while (it.hasNext()) {
			serviceName = it.next();
			ServiceInterface sw = se.serviceDirectory.get(serviceName);
			ret &= sw.save();
		}

		return ret;
	}

	/**
	 * load all configuration from all local services
	 * 
	 * @return
	 */
	static public boolean loadAll() {
		boolean ret = true;
		ServiceEnvironment se = getLocalServices();
		Iterator<String> it = se.serviceDirectory.keySet().iterator();
		String serviceName;
		while (it.hasNext()) {
			serviceName = it.next();
			ServiceInterface sw = se.serviceDirectory.get(serviceName);
			ret &= sw.load();
		}

		return ret;
	}

	public ServiceInfo getServiceInfo() {
		return serviceInfo;
	}

	// public static String newRMLInstanceCommandLineTemplate =
	// " -classpath \"./libraries/jar/*%1$s./libraries/jar/%2$s/*%1$s%3$s\" -Djava.library.path=\"./libraries/native/%2$s%1$s\" org.myrobotlab.service.Runtime -service %4$s -logToConsole";

	public static ArrayList<String> buildMLRCommandLine(HashMap<String, String> services, String runtimeName) {

		ArrayList<String> ret = new ArrayList<String>();

		// library path
		String platform = String.format("%s.%s.%s", Platform.getArch(), Platform.getBitness(), Platform.getOS());
		String libraryPath = String.format("-Djava.library.path=\"./libraries/native/%s\"", platform);
		ret.add(libraryPath);

		// class path
		String systemClassPath = System.getProperty("java.class.path");
		ret.add("-classpath");
		String classPath = String.format("./libraries/jar/*%1$s./libraries/jar/%2$s/*%1$s%3$s", Platform.getClassPathSeperator(), platform, systemClassPath);
		ret.add(classPath);

		ret.add("org.myrobotlab.service.Runtime");

		// ----- application level params --------------

		// services
		if (services.size() > 0) {
			ret.add("-service");
			for (Map.Entry<String, String> o : services.entrySet()) {
				ret.add(o.getKey());
				ret.add(o.getValue());
			}
		}

		// runtime name
		if (runtimeName != null) {
			ret.add("-runtimeName");
			ret.add(runtimeName);
		}

		// logLevel

		// logToConsole
		// ret.add("-logToConsole");

		return ret;
	}

	// TODO - load it up in gui & send it out
	public static void spawnRemoteMRL(String runtimeName) {
		HashMap<String, String> services = new HashMap<String, String>();
		services.put("remote", "RemoteAdapter");
		newMRLInstance(buildMLRCommandLine(services, runtimeName));
	}

	public static void newMRLInstance(ArrayList<String> args) {
		try {

			String separator = System.getProperty("file.separator");
			// String classpath = System.getProperty("java.class.path");
			String path = System.getProperty("java.home") + separator + "bin" + separator + "java";
			// ProcessBuilder processBuilder = new ProcessBuilder(path, "-cp",
			// classpath, clazz.getCanonicalName());
			args.add(0, path);

			log.info(Arrays.toString(args.toArray()));
			ProcessBuilder processBuilder = new ProcessBuilder(args);
			processBuilder.directory(new File(System.getProperty("user.dir")));
			processBuilder.redirectErrorStream(true);
			Process process = processBuilder.start();
			// System.out.print(process.getOutputStream());

			// TODO - Read out dir output
			/*
			 * InputStream is = process.getInputStream(); InputStreamReader isr
			 * = new InputStreamReader(is); BufferedReader br = new
			 * BufferedReader(isr); String line;
			 * //System.out.printf("Output of running %s is:\n",
			 * Arrays.toString(buildMLRCommandLine())); while ((line =
			 * br.readLine()) != null) { System.out.println(line); }
			 */

			// Wait to get exit value
			/*
			 * try { int exitValue = process.waitFor();
			 * System.out.println("\n\nExit Value is " + exitValue); } catch
			 * (InterruptedException e) { // TODO Auto-generated catch block
			 * e.printStackTrace(); }
			 * 
			 * process.waitFor(); System.out.println("Fin");
			 */
		} catch (Exception e) {
			Logging.logException(e);
		}
	}

	public static void startSecondJVM(Class<? extends Object> clazz, boolean redirectStream) throws Exception {
		System.out.println(clazz.getCanonicalName());
		String separator = System.getProperty("file.separator");
		String classpath = System.getProperty("java.class.path");
		String path = System.getProperty("java.home") + separator + "bin" + separator + "java";
		ProcessBuilder processBuilder = new ProcessBuilder(path, "-cp", classpath, clazz.getCanonicalName());
		processBuilder.redirectErrorStream(redirectStream);
		Process process = processBuilder.start();
		process.waitFor();
		System.out.println("Fin");
	}

	/**
	 * Inbound registerServices is the method initially called to contact a
	 * remote process. Often is will be a GUI sending this request over the wire
	 * to be received in another process by a RemoteAdapter. if the registration
	 * of foreign Services is successful, the request is echoed back to the
	 * sender, such that all Services ready for export are shared.
	 * 
	 * it is also invoked by RemoteAdapters as they recieve the initial request
	 * the remote URI is filled in from the other end of the socket info Since
	 * the foreign Services do not necessarily know how they are identified the
	 * ServiceEnvironment & Service accessURL are filled in here
	 * 
	 * @param hostAddress
	 * @param port
	 * @param msg
	 * 
	 * currently this is a message from the remote system wrapped in a message
	 * from a local network communicator service
	 * 
	 */
	/*
	public void registerServices(Message msg) {
		if (msg.data.length == 0 || !(msg.data[0] instanceof ServiceEnvironment)) {
			log.error("inbound registerServices not valid");
			return;
		}
		try {
			ServiceEnvironment mrlInstance = (ServiceEnvironment) msg.data[0];

			if (!Runtime.register(mrlInstance)) {
				// if nothing new return with echo
				return;
			}
			// if successfully registered with "new" Services - echo a
			// registration back
			ServiceEnvironment echoLocal = Runtime.getLocalServicesForExport();
			// send echo of local services back to sender
			send(msg.sender, "registerServices", echoLocal);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logException(e);
		}
	}
	*/

	/**
	 * unique id's are need for sendBlocking - to uniquely identify the message
	 * this is a method to support that - it is unique within a process, but not
	 * accross processes
	 * 
	 * @return a unique id
	 */
	public static synchronized long getUniqueID() {
		++uniqueID;
		return uniqueID;
	}

	public boolean isInstalled(String fullTypeName) {
		return !getServiceInfo().hasUnfulfilledDependencies(fullTypeName);
	}

	public boolean noWorky() {
		try {
			String ret = HTTPRequest.postFile("http://myrobotlab.org/myrobotlab_log/postLogFile.php", "runtime", "file", new File("myrobotlab.log"));
			if (ret.contains("Upload:")) {
				info("noWorky successfully sent - our crack team of experts will check it out !");
				return true;
			}
		} catch (Exception e) {
			Logging.logException(e);
		}
		error("the noWorky didn't worky !");
		return false;
	}

	static public ArrayList<String> getLocalAddresses() throws SocketException {
		ArrayList<String> ret = new ArrayList<String>();
		Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
		while (interfaces.hasMoreElements()) {
			NetworkInterface current = interfaces.nextElement();
			System.out.println(current);
			if (!current.isUp() || current.isLoopback() || current.isVirtual())
			{
				log.info("skipping interface is down, a loopback or virtual");
				continue;
			}
			Enumeration<InetAddress> addresses = current.getInetAddresses();
			while (addresses.hasMoreElements()) {
				InetAddress currentAddress = addresses.nextElement();
				if (currentAddress.isLoopbackAddress())
				{
					log.info("skipping loopback address");
					continue;
				}
				System.out.println(currentAddress.getHostAddress());
				ret.add(currentAddress.getHostAddress());
			}
		}
		
		return ret;
	}

	public static List<ServiceInterface> getServices() {
		// QUESTION - why isn't registry just a treemap ?
		TreeMap<String, ServiceInterface> sorted = new TreeMap<String, ServiceInterface>(registry);
		List<ServiceInterface> list = new ArrayList<ServiceInterface>(sorted.values());
		return list;
	}
	
	/*
	public static String getLogFile() {
		Logging.
		FileIO.fileToString(file);
	}
	*/

}
