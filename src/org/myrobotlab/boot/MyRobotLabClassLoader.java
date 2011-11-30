package org.myrobotlab.boot;
/*
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials
 *   provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT OF OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THIS SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * You acknowledge that this software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.myrobotlab.cmdline.CMDLine;

/**
 * ClassLoading is one of the dark arts of Java.
 * 
 * A class loader for loading jar files, both local and remote.
 * Adapted from the Java Tutorial.
 *
 * http://java.sun.com/docs/books/tutorial/jar/api/index.html
 * 
 * @version 1.3 02/27/02
 * @author Mark Davidson
 * 
 * heavily modified by greg perry
 * It is difficult to determine through documentation (javadoc) or other sources of info whether
 * URLClassLoader manages a cache of classes currently invoked or a cache of URLs...
 * 
 * This was one of the most helpful and decidedly knowledgeable articles regarding 
 * class loading.
 * http://blogs.oracle.com/sundararajan/entry/understanding_java_class_loading
 * 
 * 
 * TODO:
 * 		is a Class cache necessary or does URLClassLoader do that?
 * 		file resource cache
 * 		setting default classloader Thread.currentThread().get....
 * 
 * References:
 * 		http://jcloader.sourceforge.net/gettingstarted.html - an alternate classloader
 * 		http://www.docjar.com/html/api/java/net/URLClassLoader.java.html
 * 
 */
public class MyRobotLabClassLoader extends URLClassLoader {

    private static MyRobotLabClassLoader loader = null;
    private static HashMap <String, URL> filecache = null;
    
    /** 
     * JarClassLoader() Null ctor DO NOT USE. This will result in an NPE if the class loader is
     * used. So this class loader isn't really Bean like.
     */
    /*
    public JarClassLoader(ClassLoader cl)  {
        super(new URL[]{}, cl);
    }
	*/
    
    /**
     * Creates a new JarClassLoader for the specified url.
     *
     * @param url The url of the jar file i.e. http://www.xxx.yyy/jarfile.jar
     *            or file:c:\foo\lib\testbeans.jar
     */
    public MyRobotLabClassLoader(URL url, ClassLoader cl) {
        super(new URL[] { url }, cl);
    }
    
    /** 
     * Adds the jar file with the following url into the class loader. This can be 
     * a local or network resource.
     * 
     * @param url The url of the jar file i.e. http://www.xxx.yyy/jarfile.jar
     *            or file:c:\foo\lib\testbeans.jar
     */
    public void addJarFile(URL url)  {
    	if (filecache == null)
    	{
    		filecache = new HashMap <String, URL> ();
    	}
    	
    	if (filecache.containsKey(url.toString()))
    	{
    		System.out.println(url.toString() + " already added to resource list");
    		return;
    	}
    	System.out.println("adding url " + url);
    	filecache.put(url.toString(), url);
        addURL(url);
    }

    public void addJarFiles(URL[] urls)  {
       for (int i = 0; i < urls.length; ++i)
       {
    	   System.out.println(urls[i]);
    	   addJarFile(urls[i]);
       }
    }
    
    
    /** 
     * Adds a jar file from the filesystems into the jar loader list.
     * 
     * @param jarfile The full path to the jar file.
     */
    public void addJarFile(String jarfile)  {
        try {
            URL url = new URL("file:" + jarfile);
            addURL(url);
        } catch (IOException ex) {
            Logger.getAnonymousLogger().log(Level.WARNING, "Error adding jar file", ex);
        }
    }
    
    //
    // Static methods for handling the shared instance of the JarClassLoader.
    //

    /** 
     * Returns the shared instance of the class loader.
     */
    public static MyRobotLabClassLoader getClassLoader()  {
        return loader;
    }
    
    /** 
     * Sets the static instance of the class loader.
     */
    public static void setClassLoader(MyRobotLabClassLoader cl)  {
        loader = cl;
    }
    
    /**
     *  refreshes the possible jars currently loaded with addURL
     *  this would be typially done after a new Service dependencies are down loaded by ivy.
     *  And the new resource jars need to be added to the classpath before the Service is loaded.
     *  Allows dynamic loading of a Service not currently on the local system
     */
    public static void refresh ()
    {
    	if (loader == null)
    	{
    		try {
				loader = new MyRobotLabClassLoader((new File("libraries/jar/myrobotlab.jar")).toURI().toURL(),
						ClassLoader.getSystemClassLoader()
						);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				return;
			}
    	}
    	if (loader != null)
    	{
    		loader.addJarFiles(getFileSystemURLs());
    	}
    }
    
	public static URL[] getFileSystemURLs() {
		System.out.println("getFileSystemURLs - scanning libraries/jar directory" );
		URL[] jars = null;
		try {
			//String path = "C:/Documents and Settings/grperry/mrl2/myrobotlab/dist/218.232M.20111124.0700/";
			//String path = "";
			// scan directory
			File librariesDir = new File("libraries/jar");
			File[] libraries = librariesDir.listFiles();
			jars = new URL[libraries.length];
			for (int i = 0; i < libraries.length; ++i) {
				System.out.println("adding " + libraries[i]);
				jars[i] = libraries[i].toURI().toURL();
			}

			// don't forget myrobotlab.jar !
			//jars[libraries.length] = (new File(path + "myrobotlab.jar")).toURI().toURL();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("here2");
		return jars;
	}

        
	public static void main(String[] args) throws Exception {
		
		MyRobotLabClassLoader loader = 
				new MyRobotLabClassLoader((new File("libraries/jar/myrobotlab.jar")).toURI().toURL(),
						ClassLoader.getSystemClassLoader());
		//Thread.currentThread().setContextClassLoader(loader);
		
		loader.addJarFiles(getFileSystemURLs());
		
		CMDLine cmdline = new CMDLine();
		cmdline.splitLine(args);
		
		if (cmdline.containsKey("-start"))
		{
			String startClass = cmdline.getSafeArgument("-start", 0, "");
			System.out.println("invoking " + startClass + ".main(String[] args)");	
			
			Class<?> cl = loader.loadClass(startClass);
			Method main = cl.getDeclaredMethod("main", String[].class);
			Object[] p = { args };
			main.invoke(cl, p);
			
		} else {
			System.out.println("incorrect parameters");
		}		
	}    
    
    
    
}

