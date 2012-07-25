/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.myrobotlab.arduino.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

import org.myrobotlab.arduino.PApplet;
import org.myrobotlab.arduino.Serial;
import org.myrobotlab.arduino.compiler.MessageConsumer;
import org.myrobotlab.arduino.compiler.Preferences2;
import org.myrobotlab.serial.SerialDeviceException;
import org.myrobotlab.service.Arduino;

public class SerialMonitor extends JFrame implements MessageConsumer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Arduino myArduino;
	private Serial serial;
	private String port;
	private JTextArea textArea;
	private JScrollPane scrollPane;
	private JTextField textField;
	private JButton sendButton;
	private JCheckBox autoscrollBox;
	private JComboBox lineEndings;
	private JComboBox serialRates;
	private int serialRate;

	public SerialMonitor(String port, Arduino myArduino) {
		super(port);
		this.myArduino = myArduino;

		this.port = port;

		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				closeSerialPort();
			}
		});

		// obvious, no?
		KeyStroke wc = Editor.WINDOW_CLOSE_KEYSTROKE;
		getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(wc, "close");
		getRootPane().getActionMap().put("close", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				closeSerialPort();
				setVisible(false);
			}
		});

		getContentPane().setLayout(new BorderLayout());

		Font font = Theme.getFont("console.font");

		textArea = new JTextArea(16, 40);
		textArea.setEditable(false);
		textArea.setFont(font);

		// don't automatically update the caret. that way we can manually decide
		// whether or not to do so based on the autoscroll checkbox.
		((DefaultCaret) textArea.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

		scrollPane = new JScrollPane(textArea);

		getContentPane().add(scrollPane, BorderLayout.CENTER);

		JPanel pane = new JPanel();
		pane.setLayout(new BoxLayout(pane, BoxLayout.X_AXIS));
		pane.setBorder(new EmptyBorder(4, 4, 4, 4));

		textField = new JTextField(40);
		textField.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				send(textField.getText());
				textField.setText("");
			}
		});

		sendButton = new JButton("Send");
		sendButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				send(textField.getText());
				textField.setText("");
			}
		});

		pane.add(textField);
		pane.add(Box.createRigidArea(new Dimension(4, 0)));
		pane.add(sendButton);

		getContentPane().add(pane, BorderLayout.NORTH);

		pane = new JPanel();
		pane.setLayout(new BoxLayout(pane, BoxLayout.X_AXIS));
		pane.setBorder(new EmptyBorder(4, 4, 4, 4));

		autoscrollBox = new JCheckBox("Autoscroll", true);

		lineEndings = new JComboBox(new String[] { "No line ending", "Newline", "Carriage return", "Both NL & CR" });
		lineEndings.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				Preferences2.setInteger("serial.line_ending", lineEndings.getSelectedIndex());
			}
		});
		if (Preferences2.get("serial.line_ending") != null) {
			lineEndings.setSelectedIndex(Preferences2.getInteger("serial.line_ending"));
		}
		lineEndings.setMaximumSize(lineEndings.getMinimumSize());

		String[] serialRateStrings = { "300", "1200", "2400", "4800", "9600", "14400", "19200", "28800", "38400",
				"57600", "115200" };

		serialRates = new JComboBox();
		for (int i = 0; i < serialRateStrings.length; i++)
			serialRates.addItem(serialRateStrings[i] + " baud");

		serialRate = Preferences2.getInteger("serial.debug_rate");
		serialRates.setSelectedItem(serialRate + " baud");
		serialRates.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				String wholeString = (String) serialRates.getSelectedItem();
				String rateString = wholeString.substring(0, wholeString.indexOf(' '));
				serialRate = Integer.parseInt(rateString);
				Preferences2.set("serial.debug_rate", rateString);
				closeSerialPort();
				try {
					openSerialPort();
				} catch (SerialDeviceException e) {
					System.err.println(e);
				}
			}
		});

		serialRates.setMaximumSize(serialRates.getMinimumSize());

		pane.add(autoscrollBox);
		pane.add(Box.createHorizontalGlue());
		pane.add(lineEndings);
		pane.add(Box.createRigidArea(new Dimension(8, 0)));
		pane.add(serialRates);

		getContentPane().add(pane, BorderLayout.SOUTH);

		pack();

		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		if (Preferences2.get("last.screen.height") != null) {
			// if screen size has changed, the window coordinates no longer
			// make sense, so don't use them unless they're identical
			int screenW = Preferences2.getInteger("last.screen.width");
			int screenH = Preferences2.getInteger("last.screen.height");
			if ((screen.width == screenW) && (screen.height == screenH)) {
				String locationStr = Preferences2.get("last.serial.location");
				if (locationStr != null) {
					int[] location = PApplet.parseInt(PApplet.split(locationStr, ','));
					setPlacement(location);
				}
			}
		}
	}

	protected void setPlacement(int[] location) {
		setBounds(location[0], location[1], location[2], location[3]);
	}

	protected int[] getPlacement() {
		int[] location = new int[4];

		// Get the dimensions of the Frame
		Rectangle bounds = getBounds();
		location[0] = bounds.x;
		location[1] = bounds.y;
		location[2] = bounds.width;
		location[3] = bounds.height;

		return location;
	}

	private void send(String s) {
		if (serial != null) {
			switch (lineEndings.getSelectedIndex()) {
			case 1:
				s += "\n";
				break;
			case 2:
				s += "\r";
				break;
			case 3:
				s += "\r\n";
				break;
			}
			serial.write(s);
		}
	}

	public void openSerialPort() throws SerialDeviceException {
		if (serial != null)
			return;

		serial = new Serial(port, serialRate, myArduino);
		serial.addListener(this);
	}

	public void closeSerialPort() {
		if (serial != null) {
			int[] location = getPlacement();
			String locationStr = PApplet.join(PApplet.str(location), ",");
			Preferences2.set("last.serial.location", locationStr);
			textArea.setText("");
			serial.dispose();
			serial = null;
		}
	}

	public void message(final String s) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				textArea.append(s);
				if (autoscrollBox.isSelected()) {
					textArea.setCaretPosition(textArea.getDocument().getLength());
				}
			}
		});
	}
}