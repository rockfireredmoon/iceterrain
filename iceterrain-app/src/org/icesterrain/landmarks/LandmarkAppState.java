package org.icesterrain.landmarks;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import org.icescene.IcemoonAppState;
import org.icescene.SceneConstants;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icesquirrel.interpreter.SquirrelInterpretedTable;
import org.icesquirrel.runtime.SquirrelTable;
import org.iceterrain.TerrainLoader;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;

import icemoon.iceloader.ServerAssetManager;

public class LandmarkAppState extends IcemoonAppState<IcemoonAppState<IcemoonAppState<?>>> {
	final static Logger LOG = Logger.getLogger(LandmarkAppState.class.getName());

	public interface Listener {
		void landmarkChanged(Landmark landmark);

		void landmarkAdded(Landmark landmark);

		void landmarkRemoved(Landmark landmark);
	}

	private List<Landmark> landmarks = new ArrayList<>();
	private Preferences node;
	private TerrainLoader loader;
	private List<Listener> listeners = new ArrayList<>();
	private NodeChangeListener nodeChangeListener;
	private PreferenceChangeListener preferenceChangeListener;

	public LandmarkAppState(Preferences appPrefs, TerrainLoader loader) {
		super(appPrefs);
		node = appPrefs.node("landmarks");
		this.loader = loader;
	}

	public void addListener(Listener l) {
		listeners.add(l);
	}

	public void removeListener(Listener l) {
		listeners.remove(l);
	}

	public Preferences getLandmarkPreferences() {
		return node;
	}

	@Override
	protected void postInitialize() {

		try {
			// Load all of the default mappings from the asset index
			for (String n : ((ServerAssetManager) assetManager).getAssetNamesMatching(SceneConstants.TERRAIN_PATH
					+ "/.*/Landmarks\\.nut")) {
				AssetInfo info = assetManager.locateAsset(new AssetKey<>(n));
				InputStream in = info.openStream();
				try {
					SquirrelTable table = SquirrelInterpretedTable.table(in);
					for (Map.Entry<Object, Object> en : table.entrySet()) {
						String key = (String) en.getKey();
						Landmark lm = new Landmark(key, (SquirrelTable) en.getValue());
						landmarks.add(lm);
					}
				} finally {
					in.close();
				}
			}
		} catch (IOException ioe) {
			LOG.log(Level.WARNING, "Failed to load default landmarks.", ioe);
		}

		// Watch for preference change events and update landmarks
		preferenceChangeListener = new PreferenceChangeListener() {
			@Override
			public void preferenceChange(PreferenceChangeEvent evt) {
				LOG.info(String.format("Preference %s / %s changed to %s", evt.getNode().name(), evt.getKey(), evt.getNewValue()));
				Landmark lm = getLandmark(evt.getNode().name());
				lm.load(lm.getName(), evt.getNode());
				for (Listener l : listeners) {
					l.landmarkChanged(lm);
				}
			}
		};

		try {
			for (String k : node.childrenNames()) {
				Preferences lmNode = node.node(k);
				landmarks.add(new Landmark(k, lmNode));
				lmNode.addPreferenceChangeListener(preferenceChangeListener);
			}
		} catch (BackingStoreException bse) {
			throw new RuntimeException(bse);
		}

		node.addNodeChangeListener(nodeChangeListener = new NodeChangeListener() {

			@Override
			public void childRemoved(NodeChangeEvent evt) {
				LOG.info("Landmark preference node remove.");
				Landmark lm = getLandmark(evt.getChild().name());
				landmarks.remove(lm);
				for (Listener l : listeners) {
					l.landmarkRemoved(lm);
				}
			}

			@Override
			public void childAdded(NodeChangeEvent evt) {
				LOG.info("Landmark preference node added.");
				evt.getChild().addPreferenceChangeListener(preferenceChangeListener);
				Landmark lm = new Landmark(evt.getChild().name(), evt.getChild());
				landmarks.add(lm);
				for (Listener l : listeners) {
					l.landmarkAdded(lm);
				}
			}
		});
	}

	@Override
	protected void onCleanup() {
		super.onCleanup();
		node.removeNodeChangeListener(nodeChangeListener);
		try {
			for (String k : node.childrenNames()) {
				Preferences lmNode = node.node(k);
				lmNode.removePreferenceChangeListener(preferenceChangeListener);
			}
		} catch (BackingStoreException bse) {
			throw new RuntimeException(bse);
		}
	}

	public List<Landmark> getLandmarks() {
		return Collections.unmodifiableList(landmarks);
	}

	public void removeLandmark(Landmark landmark) {
		try {
			node.node(landmark.getName()).removeNode();
		} catch (BackingStoreException bse) {
			throw new RuntimeException(bse);
		}
	}

	public void addLandmark(Landmark landmark) {
		try {
			getLandmark(landmark.getName());
		} catch (IllegalArgumentException iae) {
			landmark.save(node.node(landmark.getName()));
			return;
		}
		throw new IllegalArgumentException(String.format("A landmark named '%s' already exists.", landmark.getName()));
	}

	public void warpTo(Landmark landmark) {
		TerrainTemplateConfiguration config = TerrainTemplateConfiguration.get(assetManager,
				TerrainTemplateConfiguration.toAssetPath(landmark.getTerrain()));
		if (!loader.setDefaultTerrainTemplate(config, landmark.getLocation(), landmark.getRotation())) {
			// Returns false when no new terrain template was loaded, we just
			// move the camera
			app.getCamera().setRotation(landmark.getRotation());
			app.getCamera().setLocation(landmark.getLocation());
		}

	}

	public Landmark getLandmark(String name) {
		for (Landmark lm : landmarks) {
			if (lm.getName().equals(name)) {
				return lm;
			}
		}
		throw new IllegalArgumentException(String.format("No landmark named '%s'.", name));
	}

}
