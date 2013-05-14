package org.myrobotlab.service.interfaces;

import java.awt.image.BufferedImage;

import org.myrobotlab.framework.Service;
import org.myrobotlab.image.SerializableImage;

public abstract class VideoSource extends Service{

	private static final long serialVersionUID = 1L;

	public VideoSource(String name) {
		super(name, VideoSink.class.getCanonicalName());
	}
		
	public VideoSource(String name, String canonicalName) {
		super(name, canonicalName);
	}

	public boolean attach(VideoSink vs)
	{
		vs.subscribe("publishDisplay", getName(), "publishDisplay", SerializableImage.class);
		return true;
	}

	public boolean detach(VideoSink vs){
		vs.unsubscribe("publishDisplay", getName(), "publishDisplay", SerializableImage.class);
		return true;
	}
	
	public abstract SerializableImage publishDisplay(String source, BufferedImage img);

	
}