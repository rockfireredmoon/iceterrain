package org.iceterrain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.jme3.math.Vector2f;

@SuppressWarnings("serial")
public class HeightData implements Serializable {
	
	public List<Vector2f> locs = new ArrayList<Vector2f>();
	public List<Float> heights = new ArrayList<Float>();
	public boolean wasNeedsSave;

}
