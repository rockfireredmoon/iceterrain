package org.iceterrain;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import org.icescene.Alarm;
import org.icescene.IcemoonAppState;
import org.icescene.IcesceneApp;
import org.icescene.NodeVisitor;
import org.icescene.SceneConfig;
import org.icescene.SceneConstants;
import org.icescene.clutter.TerrainClutterGrid;
import org.icescene.clutter.TerrainClutterHandler;
import org.icescene.clutter.TerrainClutterTile;
import org.icescene.configuration.TerrainClutterConfiguration;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icescene.props.AbstractProp;

import com.jme3.app.state.AppStateManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.texture.Image;
import com.jme3.texture.image.ImageRaster;

/**
 * Loads the clutters in tiles around the camera using height from the terrain,
 * deals with distance and density configuration, and unloads all clutter when
 * deactivated.
 */
public class ClutterAppState extends IcemoonAppState<TerrainAppState> {

	private static final Logger LOG = Logger.getLogger(ClutterAppState.class.getName());
	private TerrainLoader.Listener terrainListener;
	private TerrainClutterGrid clutterNode;
	private Alarm.AlarmTask clutterReloadTask;
	private TerrainClutterConfiguration cfg;
	private int clutterAmount;
	private float distance;
	private TerrainLoader terrainLoader;
	private final Node worldNode;
	private ShadowMode shadowMode = ShadowMode.Off;

	public ClutterAppState(Preferences pref, Node worldNode) {
		super(pref);
		this.worldNode = worldNode;
		addPrefKeyPattern(SceneConfig.SCENE_CLUTTER_DENSITY);
		addPrefKeyPattern(SceneConfig.SCENE_DISTANCE);
		addPrefKeyPattern(SceneConfig.SCENE_CLUTTER_SHADOW_MODE);
		addPrefKeyPattern(TerrainConfig.TERRAIN_LIT);

		shadowMode = ShadowMode.valueOf(pref.get(SceneConfig.SCENE_CLUTTER_SHADOW_MODE,
				SceneConfig.SCENE_CLUTTER_SHADOW_MODE_DEFAULT.name()));
	}

	@Override
	protected TerrainAppState onInitialize(AppStateManager stateManager, IcesceneApp app) {
		return stateManager.getState(TerrainAppState.class);
	}

	@Override
	protected void postInitialize() {
		loadConfig();
		terrainLoader = parent.getTerrainLoader();
		terrainLoader.addListener(terrainListener = new TerrainLoader.Listener() {
			@Override
			public void tileLoaded(TerrainInstance instance) {
				updateConfiguration();
				clutterNode.reload();
			}

			@Override
			public void tileUnloaded(TerrainInstance instance) {
				updateConfiguration();
				clutterNode.reload();
			}

			public void terrainReload() {
				updateConfiguration();
				clutterNode.reload();
			}

			public void templateChanged(TerrainTemplateConfiguration templateConfiguration, Vector3f initialLocation,
					Quaternion initialRotation) {
				// updateConfiguration();
				// clutterNode.reload();
			}
		});

		// Load the clutter definitions (contains meshes used for each terrain
		// splat)

		TerrainClutterHandler h = new TerrainClutterHandler() {
			public TerrainQuad getTerrainAt(TerrainClutterGrid grid, Vector2f world) {
				TerrainInstance ti = terrainLoader.getPageAtWorldPosition(world);
				return ti == null ? null : ti.getQuad();
			}

			public Collection<Node> createLayers(TerrainClutterGrid grid, TerrainClutterTile tile, Vector3f world) {
				List<Node> layers = new ArrayList<Node>();
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine(String.format("Creating clutter layers for %s (%s)", tile.getTile(), tile.getTerrainCell()));
				}
				final Map<String, AbstractProp> props = new HashMap<String, AbstractProp>();
				final TerrainInstance ti = terrainLoader.getPageAtWorldPosition(new Vector2f(world.x, world.z));

				if (ti != null) {

					try {
						// Take a copy of the current coverage image and use it.
						// This is an attempt to
						// avoid weirdness when loading coverage from a
						// different thread (i.e. this one)
						// when it has been written to by the scene thread (i.e.
						// when painting terrain
						// in the editor)
						Image coverageImg = app.enqueue(new Callable<Image>() {
							public Image call() throws Exception {
								Image coverage = ti.getCoverage();
								if (coverage == null) {
									return null;
								}
								final Image clone = coverage.clone();
								ByteBuffer data = coverage.getData().get(0).duplicate();
								clone.setData(new ArrayList<ByteBuffer>(Arrays.asList(data)));
								return clone;
							}
						}).get();
						if (coverageImg == null) {
							LOG.warning("No coverage image for " + ti.getPage());
						} else {
							ImageRaster coverageRaster = ImageRaster.create(coverageImg);

							TerrainTemplateConfiguration template = ti.getTerrainTemplate();
							Node[] layerArr = new Node[4];
							for (int i = 0; i < clutterAmount; i++) {
								Vector2f loc = new Vector2f(FastMath.rand.nextFloat() * grid.getTileSize(),
										FastMath.rand.nextFloat() * grid.getTileSize());
								Vector2f worldLoc = new Vector2f(world.x, world.z);
								Vector2f absLoc = worldLoc.add(loc);
								ColorRGBA pix = ti.getCoverageAtWorldPosition(coverageRaster, absLoc);

								if (pix.a > 0 && pix.a >= pix.r && pix.a >= pix.g && pix.a >= pix.b) {
									layerArr[0] = doSplat(layerArr[0], props, template.getTextureSplatting0(), pix.a, loc,
											worldLoc, template, ti, absLoc);
								} else if (pix.r > 0 && pix.r >= pix.a && pix.r >= pix.g && pix.r >= pix.b) {
									layerArr[1] = doSplat(layerArr[1], props, template.getTextureSplatting1(), pix.r, loc,
											worldLoc, template, ti, absLoc);
								} else if (pix.g > 0 && pix.g >= pix.a && pix.g >= pix.r && pix.g >= pix.b) {
									layerArr[2] = doSplat(layerArr[2], props, template.getTextureSplatting2(), pix.g, loc,
											worldLoc, template, ti, absLoc);
								} else if (pix.b > 0 && pix.b >= pix.a && pix.b >= pix.r && pix.b >= pix.g) {
									layerArr[3] = doSplat(layerArr[3], props, template.getTextureSplatting3(), pix.b, loc,
											worldLoc, template, ti, absLoc);
								} else if (pix.a > 0) {
									layerArr[0] = doSplat(layerArr[0], props, template.getTextureSplatting0(), pix.a, loc,
											worldLoc, template, ti, absLoc);
								} else if (pix.r > 0) {
									layerArr[1] = doSplat(layerArr[1], props, template.getTextureSplatting1(), pix.r, loc,
											worldLoc, template, ti, absLoc);
								} else if (pix.g > 0) {
									layerArr[2] = doSplat(layerArr[2], props, template.getTextureSplatting2(), pix.g, loc,
											worldLoc, template, ti, absLoc);
								} else if (pix.b > 0) {
									layerArr[3] = doSplat(layerArr[3], props, template.getTextureSplatting3(), pix.b, loc,
											worldLoc, template, ti, absLoc);
								}
							}
							for (Node n : layerArr) {
								if (n != null) {
									layers.add(n);
								}
							}
						}
					} catch (Exception e) {
						LOG.log(Level.SEVERE, "Failed to load clutter.", e);
					}
				}
				return layers;
			}

			private Node doSplat(Node layer, Map<String, AbstractProp> props, String splat, float density, Vector2f tileLoc,
					final Vector2f worldLoc, TerrainTemplateConfiguration template, final TerrainInstance instance,
					final Vector2f absLoc) {
				TerrainClutterConfiguration.ClutterDefinition def = TerrainClutterConfiguration.get(assetManager)
						.getClutterDefinition(splat);
				if (def != null) {
					// float height =
					// instance.getHeightAtWorldPosition(worldLoc);
					float height = instance.getHeightAtWorldPosition(absLoc);

					for (Map.Entry<String, Float> en : def.getMeshScales().entrySet()) {
						try {
							float rnd = FastMath.rand.nextFloat();
							if (rnd < en.getValue() * density) {
								int idx = en.getKey().lastIndexOf('.');
								if (layer == null) {
									layer = new Node();
								}

								final TerrainTemplateConfiguration.LiquidPlaneConfiguration liquidPlaneConfiguration = template
										.getLiquidPlaneConfiguration();
								if (liquidPlaneConfiguration != null && height < liquidPlaneConfiguration.getElevation()) {
									// Dont add clutter underwater
									continue;
								}

								// Set the initial position of the prop
								float maxSwayAngle = 5 * FastMath.DEG_TO_RAD;

								AbstractProp prop = props.get(en.getKey());
								if (prop == null) {
									if (LOG.isLoggable(Level.FINE)) {
										LOG.fine(String.format("Loading clutter prop %s", en.getKey()));
									}
									prop = parent.getPropFactory().getProp(
											String.format("%s", en.getKey().substring(0, idx)));
									prop.getSpatial().setShadowMode(shadowMode);
									;
									props.put(en.getKey(), prop);
								} else {
									configureDistance((Node) prop.getSpatial());
									prop = (AbstractProp) prop.clone();
								}

								// Rotate the prop to the angle of the terrain
								// at the point it will be placed
								Vector3f slope = instance.getSlopeAtWorldPosition(absLoc);
								if (slope != null) {

									Quaternion q= new Quaternion();
									q.fromAngleNormalAxis(90, slope);
									prop.getSpatial().setLocalRotation(q);
//									prop.getSpatial().rotate(0, -Vector3f.UNIT_X.angleBetween(slope),
//											-Vector3f.UNIT_Y.angleBetween(slope));
								}

								// Now add some random rotation
								prop.getSpatial().rotate((FastMath.rand.nextFloat() * maxSwayAngle * 2) - maxSwayAngle,
										FastMath.rand.nextFloat() * FastMath.TWO_PI,
										(FastMath.rand.nextFloat() * maxSwayAngle * 2) - maxSwayAngle);

								// Position and attach
								prop.getSpatial().setLocalTranslation(tileLoc.x, height, tileLoc.y);
								layer.attachChild(prop.getSpatial());
							}
						} catch (Exception e) {
							LOG.log(Level.SEVERE, "Failed to load clutter.", e);
						}
					}
				}
				return layer;
			}
		};

		// clutterNode = new TerrainClutterGrid(app, h,
		// Constants.CLUTTER_LOAD_RADIUS) {
		clutterNode = new TerrainClutterGrid(app, h, SceneConstants.CLUTTER_LOAD_RADIUS, 1920, 15) {
			@Override
			protected Vector3f getViewWorldTranslation() {
				return ClutterAppState.this.getParent().getPlayerViewLocation();
			}
		};
		clutterNode.checkForViewTileChange();
		clutterNode.getLoader().setExecutor(app.getWorldLoaderExecutorService());
		clutterNode.getLoader().setStopExecutorOnClose(false);
		updateConfiguration();

		// Attach clutter node to scene
		worldNode.attachChild(clutterNode);
	}

	/**
	 * Get the node all clutter is attached to.
	 *
	 * @return clutter node
	 */
	public Node getClutterNode() {
		return clutterNode;
	}

	@Override
	protected void onCleanup() {
		clutterNode.close();
		worldNode.detachChild(clutterNode);
		terrainLoader.removeListener(terrainListener);
	}

	/**
	 * Reload all clutter, but don't do it immediately. If another call is made
	 * to this method the previous reload will be cancelled. This is useful for
	 * things such as preference listeners for clutter configuration.
	 */
	public void timedReload() {
		stopReload();
		clutterReloadTask = app.getAlarm().timed(new Callable<Void>() {
			public Void call() throws Exception {
				reloadAll();
				return null;
			}
		}, 2f);
	}

	/**
	 * Reload all clutter now.
	 *
	 */
	public void reloadAll() {
		stopReload();
		clutterNode.reset();
	}

	public boolean isClutterLoaded() {
		return clutterNode.getLoader().isAnyLoaded();
	}

	/**
	 * Unload all clutter now.
	 *
	 */
	public void unloadAll() {
		stopReload();
		clutterNode.getLoader().unloadAll();
	}

	@Override
	protected void handlePrefUpdateSceneThread(PreferenceChangeEvent evt) {
		loadConfig();
		if (evt.getKey().equals(SceneConfig.SCENE_DISTANCE)) {
			configureDistance(clutterNode);
		}  
		else {
			if(evt.getKey().equals(TerrainConfig.TERRAIN_LIT)) {
				// TODO A bit brute force - look for ways to only clear cache of clutter (or reconfigure their materials)
				((DesktopAssetManager)app.getAssetManager()).clearCache();
			}
			else if(evt.getKey().equals(SceneConfig.SCENE_CLUTTER_SHADOW_MODE)) {
				shadowMode = ShadowMode.valueOf(evt.getNewValue());
			}
			updateConfiguration();
			timedReload();
		}
	}

	private void configureDistance(Node node) {
		new NodeVisitor(node).visit(new NodeVisitor.Visit() {
			public void visit(Spatial node) {
				if (node instanceof Geometry) {
					final Material material = ((Geometry) node).getMaterial();
					MatParam p = material.getParam("FadeEnabled");
					if (p != null && p.getValue().equals(true)) {
						material.setFloat("FadeEnd", distance);
					}
				}
			}
		});
	}

	private void stopReload() {
		if (clutterReloadTask != null) {
			clutterReloadTask.cancel();
		}
	}

	private void updateConfiguration() {

		// TODO base the clutter load radius on the distance. we can avoid some
		// tile loads if the distance is low

		Vector3f world = getParent().getPlayerViewLocation();
		if (world != null) {
			TerrainInstance ti = terrainLoader.getPageAtWorldPosition(new Vector2f(world.x, world.z));
			if (ti != null) {
				clutterNode.setTerrainCellSize(ti.getTerrainTemplate().getPageWorldX());
			} else {
				clutterNode.setTerrainCellSize(1920);
			}
			clutterNode.setTileSize(clutterNode.getTerrainCellSize() / (float) SceneConstants.CLUTTER_TILES);
		}

		clutterAmount = ((int) ((prefs.getFloat(SceneConfig.SCENE_CLUTTER_DENSITY, SceneConfig.SCENE_CLUTTER_DENSITY_DEFAULT)
				* clutterNode.getTileSize() * SceneConstants.CLUTTER_GLOBAL_DENSITY)));

	}

	private void loadConfig() {
		distance = prefs.getFloat(SceneConfig.SCENE_DISTANCE, SceneConfig.SCENE_DISTANCE_DEFAULT);
	}
}
