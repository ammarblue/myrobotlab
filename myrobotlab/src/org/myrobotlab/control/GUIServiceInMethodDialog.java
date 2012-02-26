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

package org.myrobotlab.control;

import java.awt.BorderLayout;
import org.myrobotlab.service.Runtime;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;

import org.apache.log4j.Logger;
import org.myrobotlab.framework.MethodEntry;
import org.myrobotlab.framework.NotifyEntry;

import org.myrobotlab.service.interfaces.GUI;

import com.mxgraph.model.mxCell;
import com.mxgraph.view.mxGraph;

public class GUIServiceInMethodDialog extends JDialog  implements ActionListener  {
	
	public final static Logger LOG = Logger.getLogger(GUIServiceOutMethodDialog.class.getCanonicalName());
	
	private static final long serialVersionUID = 1L;

	GUI myService = null;
	GUIServiceGraphVertex v = null; // vertex who generated this dialog
	
	GUIServiceInMethodDialog (GUI myService, String title, GUIServiceGraphVertex v)
	{	super(myService.getFrame(), title, true);
		this.v = v;
		this.myService = myService;
	    JFrame parent = myService.getFrame();
	    if (parent != null) 
	    {
		      Dimension parentSize = parent.getSize(); 
		      Point p = parent.getLocation(); 
		      setLocation(p.x + parentSize.width / 4, p.y + parentSize.height / 4);
		}

	    
		TreeMap<String,MethodEntry> m = new TreeMap<String, MethodEntry>(Runtime.getMethodMap(v.name));
		//TreeMap<String,MethodEntry> m = new TreeMap<String, MethodEntry>(myService.getHostCFG().getMethodMap(v.getName()));
		//HashMap<String, MethodEntry> m = myService.getHostCFG().getMethodMap(serviceName);
		
		JComboBox combo = new JComboBox();
		combo.addActionListener(this);
		Iterator<String> sgi = m.keySet().iterator();
		combo.addItem(""); // add empty
		while (sgi.hasNext()) {
			String methodName = sgi.next();
			MethodEntry me = m.get(methodName);
			
			combo.addItem(formatOutMethod(me));
		}			
		
		getContentPane().add(combo, BorderLayout.SOUTH);
		
	    pack(); 
	    setVisible(true);

	}
	
	public String formatOutMethod(MethodEntry me)
	{
		StringBuffer ret = new StringBuffer();
		ret.append(me.name);
		if (me.parameterTypes != null)
		{
			ret.append(" (");
			for (int i = 0; i < me.parameterTypes.length; ++i)
			{
				String p = me.parameterTypes[i].getCanonicalName();
				String t[] = p.split("\\.");
				ret.append(t[t.length -1]);
				if (i < me.parameterTypes.length - 1)
				{
					ret.append(","); // TODO - NOT POSSIBLE TO CONNECT IN GUI - FILTER OUT?
				}
			}
			
			ret.append(")");
		}
		
		return ret.toString();
	}


	@Override
	public void actionPerformed(ActionEvent e) {
		JComboBox cb = (JComboBox)e.getSource();
        String method = (String)cb.getSelectedItem();
        LOG.error("method is " + method);
        myService.setDstServiceName(v.name);
        myService.setPeriod0(".");
        myService.setDstMethodName(method);
        
        LOG.info(e);
        
        //myService.srcMethodName = method.split(regex)
        //myService.parameterList =
        
        // TODO - send notify !!! 
        
        if (method != null && method.length() > 0)
        {
	        // clean up methods (TODO - this is bad and should be done correctly - at the source)
			//ne.getName() = myService.getDstServiceName();
			//ne.outMethod = myService.getSrcMethodName().split(" ")[0];
			//ne.inMethod = myService.getDstMethodName().split(" ")[0];
			NotifyEntry ne = new NotifyEntry(myService.getSrcMethodName().split(" ")[0],
					 myService.getDstServiceName(),
					 myService.getDstMethodName().split(" ")[0],
					 null // this is not being filled in - TODO - fix parameter list
					);
			
			LOG.error("NotifyEntry !!! " + ne);
/*			
			if (parameterType != null) {
				ne.paramTypes = new Class[]{parameterType};
			}
*/			
			// send the notification of new route to the target system
			String srcService = myService.getSrcServiceName();
			myService.send(srcService, "notify", ne);
			
			mxGraph graph = myService.getGraph();
			Object parent = graph.getDefaultParent();
			HashMap<String, mxCell> serviceCells = myService.getCells();
			graph.insertEdge(parent, null, GUIServiceGUI.formatMethodString(ne.outMethod, ne.paramTypes, ne.inMethod), serviceCells.get(srcService), serviceCells.get(ne.name));
			
	        this.dispose();
        }
	}	

}
