package org.iceterrain;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import org.apache.commons.lang3.StringUtils;
import org.icelib.ClientException;
import org.icelib.PageLocation;
import org.icescene.IcemoonAppState;
import org.icescene.SceneConfig;
import org.icescene.SceneConstants;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icescene.environment.EnvironmentLight;
import org.icescene.io.MouseManager;
import org.icescene.props.EntityFactory;
import org.iceskies.environment.EnvironmentSwitcherAppState;
import org.iceskies.environment.EnvironmentSwitcherAppState.EnvPriority;

import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public class TerrainAppState extends IcemoonAppState<IcemoonAppState<?>> implements PropertyChangeListener {

	private final static Logger LOG = Logger.getLogger(TerrainAppState.class.getName());
	private TerrainLoader.Listener terrainListener;
	protected Node mappableNode;
	protected final EntityFactory propFactory;
	protected final Node worldNode;
	protected final EnvironmentLight light;
	protected Node terrainGroupNode;
	protected TerrainLoader terrainLoader;
	protected PageLocation playerTile;
	protected Vector3f viewLocation;
	protected final MouseManager mouseManager;
	protected ClutterAppState clutter;

	public TerrainAppState(TerrainLoader terrainLoader, Preferences prefs, EnvironmentLight light, Node mappableNode,
			EntityFactory propFactory, Node worldNode, MouseManager mouseManager) {
		super(prefs);
		this.mouseManager = mouseManager;
		this.propFactory = propFactory;
		this.light = light;
		this.mappableNode = mappableNode;
		this.terrainLoader = terrainLoader;
		this.worldNode = worldNode;
		addPrefKeyPattern(SceneConfig.TERRAIN + ".*");
		// addPrefKeyPattern(SceneConfig.SCENE_BLOOM);
	}

	private void unloadOutOfRadiusTerrain(PageLocation location) {
		final int load = Math.min(terrainLoader.getDefaultTerrainTemplate().getLivePageMargin(), SceneConstants.GLOBAL_MAX_LOAD);
//		int unloadRadius = Math.max(SceneConstants.UNLOAD_PAGE_RADIUS, load + 1);
		int unloadRadius = load + 1;
		System.out.println("UNLOADING RAD: "+ unloadRadius + " LOAD: " + load);
		for (TerrainInstance el : terrainLoader.getLoaded()) {
			PageLocation pl = el.getPage();
			if (!location.equals(pl)
					&& (pl.x >= location.x + unloadRadius || pl.x <= location.x - unloadRadius || pl.y > location.y + unloadRadius || pl.y <= location.y
							- unloadRadius)) {
				terrainLoader.unload(el.getPage());
			}
		}
	}

	public EntityFactory getPropFactory() {
		return propFactory;
	}

	public Node getTerrainGroupNode() {
		return terrainGroupNode;
	}

	@Override
	protected final void postInitialize() {

		// Root terain node
		terrainGroupNode = new Node("TerrainNode");
		mappableNode.attachChild(terrainGroupNode);

		beforeTerrainInitialize();

		// Watch for terrain reloads
		terrainLoader.addListener(terrainListener = new TerrainLoader.Listener() {
			@Override
			public void terrainReload() {
				queueTerrainPagesLoad();
			}

			@Override
			public void tileLoaded(TerrainInstance instance) {
				doTileLoaded(instance);
			}

			@Override
			public void tileUnloaded(TerrainInstance instance) {
				doTileUnloaded(instance);
			}

			public void templateChanged(TerrainTemplateConfiguration templateConfiguration, Vector3f initialLocation,
					Quaternion initialRotation) {
				TerrainAppState.this.templateChanged(templateConfiguration);
				setEnvironment(templateConfiguration == null ? null : templateConfiguration.getEnvironment(),
						EnvPriority.DEFAULT_FOR_TERRAIN);
			}
		});

		// Attach any nodes of terrain that are already loaded
		for (TerrainInstance s : terrainLoader.getLoaded()) {
			doTileLoaded(s);
		}

		// Watch for light changes
		light.addPropertyChangeListener(this);

		// Queue first terrain
		queueTerrainPagesLoad();

		// Set initial environment
		if (!terrainLoader.isGlobalTerrainTemplate()) {
			setEnvironment(terrainLoader.getTerrainTemplate().getEnvironment(), EnvPriority.DEFAULT_FOR_TERRAIN);
		}

		onTerrainInitialize();

	}

	public Vector3f getPlayerViewLocation() {
		return viewLocation;
	}

	public PageLocation getPlayerTile() {
		return playerTile;
	}

	public TerrainLoader getTerrainLoader() {
		return terrainLoader;
	}

	public List<Future<TerrainInstance>> reloadTerrain() {
		terrainLoader.reloadTerrain();
		return queueTerrainPagesLoad();
	}

	protected TerrainTemplateConfiguration getConfigurationForLocation() {
		TerrainInstance page = playerTile == null ? null : getPage(playerTile);
		return page == null ? terrainLoader.getDefaultTerrainTemplate() : page.getTerrainTemplate();
	}

	public TerrainTemplateConfiguration getTerrainTemplate() {
		return terrainLoader.getDefaultTerrainTemplate();
	}

	protected void onTerrainInitialize() {
		// For subclasses to override
	}

	protected void beforeTerrainInitialize() {
		// For subclasses to override
	}

	@Override
	protected final void onCleanup() {
		terrainGroupNode.removeFromParent();
		terrainLoader.removeListener(terrainListener);
		light.removePropertyChangeListener(this);
		mappableNode.detachChild(terrainGroupNode);
		detachClutterIfAttached();
		onTerrainCleanup();
	}

	protected void doTileUnloaded(TerrainInstance instance) {
		if (!terrainLoader.hasTerrain()) {
			detachClutterIfAttached();
		}

		if (playerTile.equals(instance.getPage())) {
			// Set the environment
			setEnvironment(null, EnvPriority.TILE);
		}
	}

	protected void doTileLoaded(TerrainInstance instance) {
		// If there is no clutter, attach it now
		ClutterAppState cas = stateManager.getState(ClutterAppState.class);
		if (cas == null) {
			// We are clutters parent, start it
			stateManager.attach(clutter = new ClutterAppState(prefs, worldNode));
		}

		terrainGroupNode.attachChild(instance.getNode());

		TerrainTemplateConfiguration terrainTemplate = instance.getTerrainTemplate();
		if (playerTile.equals(terrainTemplate.getPage())) {
			// Set the environment
			setEnvironment(terrainTemplate.getEnvironment(), EnvPriority.TILE);
		}
	}

	protected void setEnvironment(String env, EnvPriority priority) {
		EnvironmentSwitcherAppState eas = app.getStateManager().getState(EnvironmentSwitcherAppState.class);
		if (eas == null) {
			LOG.warning(String.format("No %s, will not switch.", EnvironmentSwitcherAppState.class.getName()));
		} else {
			if (env == null || StringUtils.isBlank(env)) {
				eas.setEnvironment(priority, null);
			} else {
				eas.setEnvironment(priority, env);
			}
		}
	}

	protected void templateChanged(TerrainTemplateConfiguration templateConfiguration) {
	}

	protected void onTerrainCleanup() {
		// For subclasses to override
	}

	@Override
	protected void handlePrefUpdateSceneThread(PreferenceChangeEvent evt) {
		if (evt.getKey().equals(TerrainConfig.TERRAIN_LIT) || evt.getKey().equals(TerrainConfig.TERRAIN_LOD_CONTROL)
				|| evt.getKey().equals(TerrainConfig.TERRAIN_SMOOTH_SCALING)
				|| evt.getKey().equals(TerrainConfig.TERRAIN_WIREFRAME) || evt.getKey().equals(TerrainConfig.TERRAIN_HIGH_DETAIL)
				|| evt.getKey().equals(TerrainConfig.TERRAIN_TRI_PLANAR) || evt.getKey().equals(TerrainConfig.TERRAIN_PRETTY_WATER)) {
			for (TerrainInstance i : terrainLoader.getLoaded()) {
				try {
					terrainLoader.reconfigureTile(i);
				} catch (Exception e) {
					error("Failed to update terrain for " + i.getPage(), e);
				}
			}
		}
	}

	public AssetManager getAssetManager() {
		return assetManager;
	}

	@Override
	public void update(float tpf) {
	}

	public TerrainInstance getPage(PageLocation loc) {
		if (terrainLoader != null) {
			try {
				return terrainLoader.get(loc);
			} catch (Exception ex) {
				throw new ClientException(ex);
			}
		}
		return null;
	}

	public TerrainInstance getPageInstance(PageLocation location) {
		try {
			return terrainLoader.load(location).get();
		} catch (Exception ex) {
			throw new ClientException(ex);
		}
	}

	public Vector3f placeOnTerrain(Vector3f location) {
		return new Vector3f(location.x, terrainLoader.getHeightAtWorldPosition(new Vector2f(location.x, location.x)), location.z);
	}

	public void playerViewLocationChanged(Vector3f viewLocation) {
		this.viewLocation = viewLocation;
	}

	public void playerTileChanged(PageLocation location) {
		LOG.info(String.format("Changed tile to %s, queueing some terrain", location));
		this.playerTile = location;
		queueTerrainPagesLoad();
		TerrainTemplateConfiguration cfg = getConfigurationForLocation();
		if (cfg != null && cfg.getPage() != null) {
			setEnvironment(cfg.getEnvironment(), EnvPriority.TILE);
		}
	}

	private Future<TerrainInstance> queueTerrainPage(PageLocation location) {
		return terrainLoader.load(location);
	}

	protected List<Future<TerrainInstance>> queueTerrainPagesLoad() {
		List<Future<TerrainInstance>> futures = new ArrayList<Future<TerrainInstance>>();

		// First queue center page
		if (playerTile == null || playerTile.equals(PageLocation.UNSET)) {
			LOG.warning("Current tile is not known, no terrain will be queued");
			terrainLoader.unloadAll();
		} else {
			TerrainTemplateConfiguration terrainTemplate = terrainLoader.getDefaultTerrainTemplate();
			if (terrainTemplate != null && terrainTemplate.isIn(playerTile)) {
				Future<TerrainInstance> queueTerrainPage = queueTerrainPage(playerTile);
				if (queueTerrainPage != null) {
					futures.add(queueTerrainPage);
				}
				final int load = Math.min(terrainTemplate.getLivePageMargin(), SceneConstants.GLOBAL_MAX_LOAD);

				// Now queue the surrounding pages
				if (load > 0) {
					for (int x = Math.max(0, playerTile.x - load); x <= playerTile.x + load; x++) {
						for (int y = Math.max(0, playerTile.y - load); y <= playerTile.y + load; y++) {
							if (x != playerTile.x || y != playerTile.y) {
								final PageLocation pageLocation = new PageLocation(x, y);
								if (terrainTemplate.isIn(pageLocation)) {
									queueTerrainPage = queueTerrainPage(pageLocation);
									if (queueTerrainPage != null) {
										futures.add(queueTerrainPage);
									}
								}
							}
						}
					}
				}
			}

			// Unload any terrain that is now out of radius
			if (terrainLoader.isAnyLoaded()) {
				unloadOutOfRadiusTerrain(playerTile);
			}
		}

		return futures;
	}

	private void resetWaterPlane() {
	}

	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals(EnvironmentLight.PROP_SUN_COLOR)) {
			for (TerrainInstance pi : terrainLoader.getLoaded()) {
				if (pi.getWater() != null) {
					pi.getWater().setLightColor((ColorRGBA) evt.getNewValue());
				}
			}
		} else if (evt.getPropertyName().equals(EnvironmentLight.PROP_SUN_DIRECTION)) {
			for (TerrainInstance pi : terrainLoader.getLoaded()) {
				if (pi.getWater() != null) {
					pi.getWater().setLightDirection(light.getSunDirection());
				}
			}
		} else if (evt.getPropertyName().equals(EnvironmentLight.PROP_SUN_ENABLED)) {
			for (TerrainInstance pi : terrainLoader.getLoaded()) {
				if (pi.getWater() != null) {
					pi.getWater().setLightColor(ColorRGBA.Black);
				}
			}
		}
	}

	private void detachClutterIfAttached() {
		// There is no terrain, we don't need clutter any more
		if (clutter != null) {
			stateManager.detach(clutter);
		}
	}

	public static void detach(AppStateManager stateManager) {
		TerrainAppState tas = stateManager.getState(TerrainAppState.class);
		if (tas != null) {
			stateManager.detach(tas);
		}
	}
}
