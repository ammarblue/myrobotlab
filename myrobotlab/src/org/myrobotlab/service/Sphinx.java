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
 * Dependencies:
 * sphinx4-1.0beta6
 * google recognition - a network connection is required
 * 
 * References:
 * Swapping Grammars - http://cmusphinx.sourceforge.net/wiki/sphinx4:swappinggrammars
 * 
 * http://cmusphinx.sourceforge.net/sphinx4/javadoc/edu/cmu/sphinx/jsgf/JSGFGrammar.html#loadJSGF(java.lang.String)
 * TODO - loadJSGF - The JSGF grammar specified by grammarName will be loaded from the base url (tossing out any previously loaded grammars)
 * 
 * 
 * */

package org.myrobotlab.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.speech.recognition.GrammarException;

import org.myrobotlab.framework.Message;
import org.myrobotlab.framework.Service;
import org.myrobotlab.logging.Level;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.LoggingFactory;
import org.myrobotlab.speech.DialogManager;
import org.myrobotlab.speech.NewGrammarDialogNodeBehavior;
import org.slf4j.Logger;

import edu.cmu.sphinx.frontend.util.Microphone;
import edu.cmu.sphinx.linguist.Linguist;
import edu.cmu.sphinx.recognizer.Recognizer;
import edu.cmu.sphinx.result.Result;
import edu.cmu.sphinx.util.TimerPool;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;

public class Sphinx extends Service {

	private static final long serialVersionUID = 1L;
	public final static Logger log = LoggerFactory.getLogger(Sphinx.class.getCanonicalName());

	Microphone microphone = null;
	ConfigurationManager cm = null;
	transient Recognizer recognizer = null;
	Thread listener = null;
	DialogManager dialogManager = null;
	transient SpeechProcessor speechProcessor = null;
	HashMap<String, Message> commandMap = new HashMap<String, Message>();
	private boolean isListening = false;
	private String lockPhrase = null;

	public Sphinx(String n) {
		super(n, Sphinx.class.getCanonicalName());
	}


	/**
	 * The main output for this service. "word" is the word recognized. This has
	 * to be setup before by calling createGrammar
	 * (" stop | go | new | grammar ") and a list of words or phrases to
	 * recognize.
	 * 
	 * @param word
	 * @return the word
	 */
	public String recognized(String word) {
		if (commandMap.containsKey(word)) {
			getOutbox().add(commandMap.get(word));
		}
		return word;
	}

	/**
	 * Event is sent when the listening Service is actually listening. There is
	 * some delay when it initially loads.
	 */
	public void listeningEvent() {
		return;
	}

	/**
	 * createGrammar must be called before the Service starts if a new grammar
	 * is needed
	 * 
	 * example: Sphinx.createGrammar ("ear", "stop | go | left | right | back");
	 * ear = Runtime.create("ear", "Sphinx")
	 * 
	 * @param filename
	 *            - name of the Service which will be utilizing this grammar
	 * @param grammar
	 *            - grammar content
	 * @return
	 */
	public boolean createGrammar(String grammar) {
		// FIXME - probably broken
		// get base simple.xml file - and modify it to
		// point to the correct .gram file
		String simplexml = getServiceResourceFile("simple.xml");
		// String grammarLocation = "file://" + cfgDir.replaceAll("\\\\", "/") +
		// "/";
		// simplexml = simplexml.replaceAll("resource:/resource/",
		// cfgDir.replaceAll("\\\\", "/"));
		simplexml = simplexml.replaceAll("resource:/resource/", ".myrobotlab");
		simplexml = simplexml.replaceAll("name=\"grammarName\" value=\"simple\"", "name=\"grammarName\" value=\"" + this.getName() + "\"");
		save("xml", simplexml);

		String gramdef = "#JSGF V1.0;\n" + "grammar " + getName() + ";\n" + "public <greet> = (" + grammar + ");";
		save("gram", gramdef);

		return true;
	}

	/**
	 * stopRecording - it does "work", however, the speech recognition part
	 * seems to degrade when startRecording is called. I have worked around this
	 * by not stopping the recording, but by not processing what was recognized
	 */
	public void stopRecording() {
		microphone.stopRecording();
		microphone.clear();
	}

	public void startRecording() {
		microphone.clear();
		microphone.startRecording();
	}

	public boolean isRecording() {
		return microphone.isRecording();
	}

	public void startService() {
		super.startService();
	}

	public void stopService() {
		stopListening();
		super.stopService();
	}

	/**
	 * function to swap grammars to allow sphinx a little more capability
	 * regarding "new words"
	 * 
	 * check http://cmusphinx.sourceforge.net/wiki/sphinx4:swappinggrammars
	 * 
	 * @param newGrammarName
	 * @throws PropertyException
	 * @throws InstantiationException
	 * @throws IOException
	 */
	void swapGrammar(String newGrammarName) throws PropertyException, InstantiationException, IOException {
		log.debug("Swapping to grammar " + newGrammarName);
		Linguist linguist = (Linguist) cm.lookup("flatLinguist");
		linguist.deallocate();
		// TODO - bundle sphinx4-1.0beta6
		// cm.setProperty("jsgfGrammar", "grammarName", newGrammarName);
		linguist.allocate();
	}
	
	void lockOutAllGrammarExcept(String lockPhrase)
	{
		this.lockPhrase  = lockPhrase;
	}
	
	void clearLock()
	{
		lockPhrase = null;
	}

	class SpeechProcessor extends Thread {
		Sphinx myService = null;
		public boolean isRunning = false;

		public SpeechProcessor(Sphinx myService) {
			super(myService.getName() + "_ear");
			this.myService = myService;
		}

		public void run() {

			try {
				isRunning = true;

				String newPath = cfgDir + File.separator + myService.getName() + ".xml";
				File localGramFile = new File(newPath);

				if (localGramFile.exists()) {
					cm = new ConfigurationManager(newPath);
				} else {
					// resource in jar default
					cm = new ConfigurationManager(this.getClass().getResource("/resource/Sphinx/simple.xml"));
				}

				// start the word recognizer
				recognizer = (Recognizer) cm.lookup("recognizer");
				recognizer.allocate();

				microphone = (Microphone) cm.lookup("microphone");
				if (!microphone.startRecording()) {
					log.error("Cannot start microphone.");
					recognizer.deallocate();
				}

				// loop the recognition until the program exits.
				isListening = true;
				while (isRunning) {

					log.info("listening");
					invoke("listeningEvent");

					Result result = recognizer.recognize();

					// log.error(result.getBestPronunciationResult());
					if (result != null) {
						String resultText = result.getBestFinalResultNoFiller();
						log.info("recognized: " + resultText + '\n');
						if (resultText.length() > 0 && isListening) {
							if (lockPhrase != null && !lockPhrase.equals(resultText))
							{
								log.info(String.format("but locked on %s", lockPhrase));
								continue;
							}
							invoke("recognized", resultText);
						}

					} else {
						try {
							Thread.sleep(300);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							logException(e);
						}
						// invoke("unrecognizedSpeech");
						log.error("I can't hear what you said.\n");
					}
				}
			} catch (Exception e) {
				logException(e);
			}
		}

	}

	public void stopListening() {
		isListening = false;
		if (speechProcessor != null) {
			speechProcessor.isRunning = false;
		}
		speechProcessor = null;
	}

	// FYI - grammar must be created BEFORE we start to listen
	public void startListening() {
		startListening(null); // use existing grammar
	}

	// FIXME - re-entrant - make it create new speechProcessor
	// assume its a new grammar
	public void startListening(String grammar) {
		if (speechProcessor != null) {
			log.warn("already listening");
			return;
		}
		if (grammar != null) {
			createGrammar(grammar);
		}
		speechProcessor = new SpeechProcessor(this);
		speechProcessor.start();
	}

	/**
	 * method to suppress recognition listening events This is important when
	 * Sphinx is listening --> then Speaking, typically you don't want Sphinx to
	 * listen to its own speech, it causes a feedback loop and with Sphinx not
	 * really very accurate, it leads to weirdness -- additionally it does not
	 * recreate the speech processor - so its not as heavy handed
	 */
	public void pauseListening() {
		isListening = false;
	}

	public void resumeListening() {
		isListening = true;
	}

	/**
	 * Defines the standard behavior for a node. The standard behavior is:
	 * <ul>
	 * <li>On entry the set of sentences that can be spoken is displayed.
	 * <li>On recognition if a tag returned contains the prefix 'dialog_' it
	 * indicates that control should transfer to another dialog node.
	 * </ul>
	 * 
	 * 
	 */
	class MyBehavior extends NewGrammarDialogNodeBehavior {

		private Collection<String> sampleUtterances;

		/** Executed when we are ready to recognize */
		public void onReady() {
			super.onReady();
			help();
		}

		/**
		 * Displays the help message for this node. Currently we display the
		 * name of the node and the list of sentences that can be spoken.
		 */
		protected void help() {
			System.out.println(" ======== " + getGrammarName() + " =======");
			dumpSampleUtterances();
			System.out.println(" =================================");
		}

		/**
		 * Executed when the recognizer generates a result. Returns the name of
		 * the next dialog node to become active, or null if we should stay in
		 * this node
		 * 
		 * @param result
		 *            the recongition result
		 * @return the name of the next dialog node or null if control should
		 *         remain in the current node.
		 */
		public String onRecognize(Result result) throws GrammarException {
			String tag = super.onRecognize(result);

			if (tag != null) {
				System.out.println("\n " + result.getBestFinalResultNoFiller() + '\n');
				if (tag.equals("exit")) {
					System.out.println("Goodbye! Thanks for visiting!\n");
					System.exit(0);
				}
				if (tag.equals("help")) {
					help();
				} else if (tag.equals("stats")) {
					TimerPool.dumpAll();
				} else if (tag.startsWith("goto_")) {
					return tag.replaceFirst("goto_", "");
				} else if (tag.startsWith("browse")) {
					execute(tag);
				}
			} else {
				System.out.println("\n Oops! didn't hear you.\n");
			}
			return null;
		}

		/**
		 * execute the given command
		 * 
		 * @param cmd
		 *            the command to execute
		 */
		private void execute(String cmd) {
			try {
				java.lang.Runtime.getRuntime().exec(cmd);
			} catch (IOException e) {
				// if we can't run the command, just fall back to
				// a non-working demo.
			}
		}

		/**
		 * Collects the set of possible utterances.
		 * <p/>
		 * TODO: Note the current implementation just generates a large set of
		 * random utterances and tosses away any duplicates. There's no
		 * guarantee that this will generate all of the possible utterances.
		 * (yep, this is a hack)
		 * 
		 * @return the set of sample utterances
		 */
		private Collection<String> collectSampleUtterances() {
			Set<String> set = new HashSet<String>();
			for (int i = 0; i < 100; i++) {
				/*
				 * FIXME - broken String s = getGrammar().getRandomSentence();
				 * if (!set.contains(s)) { set.add(s); }
				 */
			}

			List<String> sampleList = new ArrayList<String>(set);
			Collections.sort(sampleList);
			return sampleList;
		}

		/** Dumps out the set of sample utterances for this node */
		private void dumpSampleUtterances() {
			if (sampleUtterances == null) {
				sampleUtterances = collectSampleUtterances();
			}

			for (String sampleUtterance : sampleUtterances) {
				System.out.println("  " + sampleUtterance);
			}
		}

		/**
		 * Indicated that the grammar has changed and the collection of sample
		 * utterances should be regenerated.
		 */
		protected void grammarChanged() {
			sampleUtterances = null;
		}
	}

	@Override
	public String getToolTip() {
		return "<html>speech recoginition service wrapping Sphinx 4</html>";
	}

	/**
	 * an inbound port for Speaking Services (TTS) - which suppress listening
	 * such that a system will not listen when its talking, otherwise a feedback
	 * loop can occur
	 * 
	 * @param b
	 * @return
	 */
	public synchronized boolean isSpeaking(Boolean talking) {
		if (talking) {
			isListening = false;
			log.warn("I'm talking so I'm not listening"); // Gawd, ain't that
															// the truth !
		} else {
			isListening = true;
			log.warn("I'm not talking so I'm listening"); // mebbe
		}
		return talking;
	}

	// TODO - make "Speech" interface if desired
	public boolean attach(Speech mouth) {

		// if I'm speaking - I shouldn't be listening
		subscribe("isSpeaking", mouth.getName(), "isSpeaking", Boolean.class);

		log.info(String.format("attached Speech service %s to Sphinx service %s with default message routes", mouth.getName(), getName()));
		return true;
	}

	public void onCommand(String command, String targetName, String targetMethod, Object... data) {
		Message msg = new Message();
		msg.name = targetName;
		msg.method = targetMethod;
		msg.data = data;

		commandMap.put(command, msg);
	}

	public static void main(String[] args) {

		LoggingFactory.getInstance().configure();
		LoggingFactory.getInstance().setLevel(Level.DEBUG);

		Sphinx ear = (Sphinx)Runtime.createAndStart("ear", "Sphinx");
		ear.startListening("hello | up | down | yes | no");
		//Sphinx ear = new Sphinx("ear");
		// ear.createGrammar("hello | up | down | yes | no");
		//ear.startService();

	}

}
