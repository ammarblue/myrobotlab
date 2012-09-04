package org.myrobotlab.control;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.myrobotlab.image.Util;
import org.myrobotlab.net.BareBonesBrowserLaunch;
import org.myrobotlab.service.GUIService;
import org.myrobotlab.service.Runtime;

public class AboutDialog extends JDialog implements ActionListener, MouseListener {

	private static final long serialVersionUID = 1L;
    JButton bleedingEdge = null;
    JButton noWorky = null;
    JButton ok = null;
    JFrame parent = null;
    JLabel versionLabel = new JLabel(Runtime.version());

	public AboutDialog(JFrame parent) {
	    super(parent, "about", true);
	    this.parent = parent;
	    if (parent != null) {
	      Dimension parentSize = parent.getSize(); 
	      Point p = parent.getLocation(); 
	      setLocation(p.x + parentSize.width / 4, p.y + parentSize.height / 4);
	    }

	    JPanel content = new JPanel(new BorderLayout());
	    getContentPane().add(content);
   
	    // picture
	    JLabel pic = new JLabel();
		ImageIcon icon = Util.getResourceIcon("mrl_logo_about_128.png");
		if (icon != null)
		{
			pic.setIcon(icon);	
		}
		content.add(pic, BorderLayout.WEST);
	    
		JPanel center = new JPanel(new GridBagLayout());
		GridBagConstraints gc = new GridBagConstraints();
		
	    JLabel link = new JLabel("<html><p align=center><a href=\"http://myrobotlab.org\">http://myrobotlab.org</a><html>");
	    link.addMouseListener(this);
	    content.add(center, BorderLayout.CENTER);
	    content.add(versionLabel, BorderLayout.SOUTH);
	    gc.gridx = 0;
	    gc.gridy = 0;
	    gc.gridwidth = 2;
	    center.add(link, gc);
	    gc.gridwidth = 1;
	    ++gc.gridy;
	    center.add(new JLabel("version "), gc);
	    ++gc.gridx;
	    center.add(versionLabel, gc);
	    
	    JPanel buttonPane = new JPanel();
	   
	    ok = new JButton("OK"); 
	    buttonPane.add(ok); 
	    ok.addActionListener(this);
	    
	    noWorky = new JButton("GroG, it \"no-worky\"!"); 
	    buttonPane.add(noWorky); 
	    noWorky.addActionListener(this);

	    bleedingEdge = new JButton("I feel lucky, give me the bleeding edge !"); 
	    buttonPane.add(bleedingEdge); 
	    bleedingEdge.addActionListener(this);
	    
	    getContentPane().add(buttonPane, BorderLayout.SOUTH);
	    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
	    pack(); 
	    setVisible(true);
	  }

	@Override
	  public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		
		if (source == ok)
		{
		    setVisible(false); 
		    dispose(); 			
		} else if (source == bleedingEdge)
		{
			String newVersion = Runtime.getBleedingEdgeVersionString();
			String currentVersion = Runtime.version();
			if (currentVersion.equals(newVersion))
			{
				JOptionPane.showMessageDialog(parent,
					    "There are no updates available at this time");
			} else {
				//Custom button text
				Object[] options = {"Yes, hit me daddy-O!",
				                    "No way, I'm scared"};
				int n = JOptionPane.showOptionDialog(parent,
				    String.format("A fresh new version is ready, do you want this one? %s",newVersion),
				    "Bleeding Edge Check",
				    JOptionPane.YES_NO_OPTION,
				    JOptionPane.QUESTION_MESSAGE,
				    null,
				    options,
				    options[0]);				
				if (n == JOptionPane.YES_OPTION)
				{
					Runtime.getBleedingEdgeMyRobotLabJar();
					versionLabel.setText(String.format("updating with %s", newVersion));
					GUIService.restart("moveUpdate");
				} else {
					versionLabel.setText("bwak bwak bwak... chicken!");
				}
				
			}
		}
	  }


	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseReleased(MouseEvent e) {
			BareBonesBrowserLaunch.openURL("http://myrobotlab.org");
	}
}		  