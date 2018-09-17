package org.iceterrain.landmarks;

import java.util.prefs.Preferences;

import org.icesquirrel.runtime.SquirrelArray;
import org.icesquirrel.runtime.SquirrelTable;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

public class Landmark {

	private Vector3f location = new Vector3f();
	private Quaternion rotation = new Quaternion();
	private String terrain = "Terrain-Blend";
	private String name;
	private boolean userLandmark = true;

	public Landmark() {

	}

	public Landmark(Vector3f location, Quaternion rotation, String terrain, String name) {
		super();
		this.location = location;
		this.rotation = rotation;
		this.terrain = terrain;
		this.name = name;
	}

	public Landmark(String name, Preferences prefs) {
		load(name, prefs);
	}

	public Landmark(String name, SquirrelTable table) {
		load(name, table);
	}
	
	public boolean isUser() {
		return userLandmark;
	}

	public void load(String name, SquirrelTable table) {
		this.name = name;
		terrain = (String) table.get("terrain", "Terrain-Blend");
		SquirrelArray loc = (SquirrelArray) table.get("location");
		location.x = ((Double) loc.get(0)).floatValue();
		location.y = ((Double) loc.get(1)).floatValue();
		location.z = ((Double) loc.get(2)).floatValue();
		SquirrelArray rot = (SquirrelArray) table.get("rotation");
		rotation.set(((Double) rot.get(0)).floatValue(), ((Double) rot.get(1)).floatValue(), ((Double) rot.get(2)).floatValue(),
				((Double) rot.get(3)).floatValue());
		userLandmark = false;
	}

	public SquirrelTable toTable() {
		SquirrelTable t = new SquirrelTable();
		t.put("terrain", terrain);
		t.put("location", new SquirrelArray(location.x, location.y, location.z));
		t.put("rotation", new SquirrelArray(rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW()));
		return t;
	}

	public void save(Preferences prefs) {
		prefs.put("terrain", terrain);
		prefs.putFloat("x", location.x);
		prefs.putFloat("y", location.y);
		prefs.putFloat("z", location.z);
		prefs.putFloat("qx", rotation.getX());
		prefs.putFloat("qy", rotation.getY());
		prefs.putFloat("qz", rotation.getZ());
		prefs.putFloat("qw", rotation.getW());
	}

	public void load(String name, Preferences prefs) {
		this.name = name;
		terrain = prefs.get("terrain", "Terrain-Blend");
		location.x = prefs.getFloat("x", 0);
		location.y = prefs.getFloat("y", 0);
		location.z = prefs.getFloat("z", 0);
		rotation.set(prefs.getFloat("qx", 0), prefs.getFloat("qy", 0), prefs.getFloat("qz", 0), prefs.getFloat("qw", 0));
	}

	public Quaternion getRotation() {
		return rotation;
	}

	public void setRotation(Quaternion rotation) {
		this.rotation = rotation;
	}

	public Vector3f getLocation() {
		return location;
	}

	public void setLocation(Vector3f location) {
		this.location = location;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTerrain() {
		return terrain;
	}

	public void setTerrain(String terrain) {
		this.terrain = terrain;
	}

}
