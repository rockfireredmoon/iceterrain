package org.iceterrain;

import static java.lang.String.format;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.icelib.AbstractConfig;
import org.icelib.ClientException;
import org.icelib.PageLocation;
import org.icescene.IcesceneApp;
import org.icescene.SceneConfig;
import org.icescene.SceneConstants;
import org.icescene.assets.ExtendedMaterialKey;
import org.icescene.assets.ExtendedMaterialListKey;
import org.icescene.assets.ExtendedMaterialListKey.Lighting;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icescene.configuration.TerrainTemplateConfiguration.LiquidPlane;
import org.icescene.environment.EnvironmentLight;
import org.icescene.environment.PostProcessAppState;
import org.icescene.materials.water.WaterFilterCapable;
import org.icescene.scene.AbstractSceneQueue;
import org.icescene.terrain.SaveableWideImageBasedHeightMap;

import com.jme3.asset.AssetNotFoundException;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.MaterialList;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.terrain.geomipmap.NeighbourFinder;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.texture.Image;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import com.jme3.water.WaterFilter;

public class TerrainLoader extends AbstractSceneQueue<PageLocation, TerrainInstance> implements NeighbourFinder {

	private static final String LIT_TERRAIN_MATDEF = "MatDefs/Terrain/TerrainLighting.j3md";
//	private static final String LIT_TERRAIN_MATDEF = "MatDefs/Terrain/TerrainLighting2.j3md";
//	private static final String LIT_TERRAIN_MATDEF = "Common/MatDefs/Terrain/TerrainLighting.j3md";
	
	private static final Logger LOG = Logger.getLogger(TerrainLoader.class.getName());
	private TerrainTemplateConfiguration defaultTerrainTemplate;
	private List<Listener> listeners = new ArrayList<Listener>();
	private final TerrainTemplateConfiguration globalTerrainTemplate;
	private final EnvironmentLight light;
	private final Node gameNode;
	private boolean readOnly = true;

	public TerrainLoader(IcesceneApp app, EnvironmentLight light, Node gameNode) {
		super(app);
		this.light = light;
		this.gameNode = gameNode;
		globalTerrainTemplate = new TerrainTemplateConfiguration(app.getAssetManager(),
				SceneConstants.TERRAIN_PATH + "/Terrain-Common/Terrain-Default.cfg", null);
		globalTerrainTemplate.load();
	}

	private WaterFilter createWaterFilter(WaterFilterCapable waterConfig,
			final TerrainTemplateConfiguration terrainTemplate, float wuX, float wuY) {
		final WaterFilter water = new WaterFilter(gameNode,
				light.getSunDirection().mult(SceneConstants.DIRECTIONAL_LIGHT_SOURCE_DISTANCE));
		final ColorRGBA sunColor = waterConfig.getSunColor();
		water.setLightColor(sunColor == null ? light.getSun().getColor() : sunColor);
		water.setWindDirection(Vector2f.UNIT_XY);
		water.setRadius(terrainTemplate.getPageWorldX() / 2);
		water.setShapeType(WaterFilter.AreaShape.Square);
		float waterH = (float) terrainTemplate.getLiquidPlaneConfiguration().getElevation();
		water.setCenter(new Vector3f(wuX, waterH, wuY));
		water.setSunScale(waterConfig.getSunScale());
		water.setWaveScale(waterConfig.getWaveScale());
		water.setMaxAmplitude(waterConfig.getMaxAmplitude());
		// water.setColorExtinction(Vector3f.ZERO);
		water.setShininess(waterConfig.getShininess());
		water.setRefractionStrength(waterConfig.getRefraction());
		water.setSpeed(waterConfig.getSpeed());
		water.setWaterTransparency(waterConfig.getTransparency());
		water.setWaterColor(waterConfig.getColor());
		water.setDeepWaterColor(waterConfig.getDeepWaterColor());
		water.setWaterHeight(waterH);
		return water;
	}

	public interface Listener {

		void templateChanged(TerrainTemplateConfiguration templateConfiguration, Vector3f initialLocation,
				Quaternion initialRotation);

		void terrainReload();

		void tileLoaded(TerrainInstance instance);

		void tileUnloaded(TerrainInstance instance);
	}

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	/**
	 * Get the terrain instance at the provided world position, waiting for it
	 * to load if it is not already loaded. DO NOT call this from the queue, or
	 * deadlocks may occur
	 *
	 * @param pos
	 *            position
	 * @return terrain instance
	 */
	public TerrainInstance getPageInstanceAtWorldPosition(Vector2f pos) {
		if (defaultTerrainTemplate != null) {
			try {
				PageLocation s = defaultTerrainTemplate.getTile(pos);
				return load(s).get();
			} catch (Exception ex) {
				throw new ClientException(ex);
			}
		}
		return null;
	}

	// A default terrain template for when there is no terrain

	/**
	 * Get the terrain tempate for the player at their current position. This
	 * will either be the "Default" terrain template used when there is actually
	 * no terrain, or it will be either the root terrain template for the
	 * current terrain or the per-tile configuration if it exists.
	 *
	 * @return terrain template
	 */
	public TerrainTemplateConfiguration getTerrainTemplate() {
		return defaultTerrainTemplate == null ? globalTerrainTemplate : defaultTerrainTemplate;
	}

	public boolean isGlobalTerrainTemplate() {
		return getTerrainTemplate().equals(globalTerrainTemplate);
	}

	public TerrainTemplateConfiguration getGlobalTerrainTemplate() {
		return globalTerrainTemplate;
	}

	public ColorRGBA getCoverageAtWorldPosition(Vector2f pos) {
		final TerrainInstance pi = getPageAtWorldPosition(pos);
		return pi == null ? null : pi.getCoverageAtWorldPosition(pos);
	}

	public void adjustAtWorldPosition(Vector2f pos, float delta) {
		TerrainInstance ti = getPageAtWorldPosition(new Vector2f(pos.x, pos.y));
		adjustAtWorldPosition(ti, pos, delta);
	}

	public void adjustAtWorldPosition(TerrainInstance pi, Vector2f pos, float delta) {
		final TerrainTemplateConfiguration configuration = pi.getTerrainTemplate();
		Vector2f relativePos = configuration.getPositionWithinTile(pi.getPage(), pos);
		adjustAtPosition(pi, relativePos, delta);
	}

	public void adjustAtPosition(TerrainInstance pi, Vector2f relativePos, float delta) {
		final TerrainTemplateConfiguration configuration = pi.getTerrainTemplate();
		// Vector2f p = new Vector2f((int) ((relativePos.x / (float)
		// configuration.getPageWorldX()) * 255f), 255 - (int) ((relativePos.y /
		// (float) configuration.getPageWorldZ()) * 255f));
		pi.getQuad().adjustHeight(relativePos, delta);
	}

	public float getHeightAtWorldPosition(Vector2f pos) {
		final TerrainInstance wpos = getPageAtWorldPosition(pos);
		return wpos == null ? Float.MIN_VALUE : wpos.getHeightAtWorldPosition(pos);
	}

	public Vector3f getSlopeAtWorldPosition(Vector2f pos) {
		final TerrainInstance wpos = getPageAtWorldPosition(pos);
		return wpos == null ? null : wpos.getSlopeAtWorldPosition(pos);
	}

	public TerrainInstance getPageAtWorldPosition(Vector2f pos) {
		if (defaultTerrainTemplate != null) {
			try {
				PageLocation s = defaultTerrainTemplate.getTile(pos);
				return get(s);
			} catch (Exception ex) {
				throw new ClientException(ex);
			}
		}
		return null;
	}

	public boolean hasTerrain() {
		return defaultTerrainTemplate != null;
	}

	public TerrainTemplateConfiguration getDefaultTerrainTemplate() {
		return defaultTerrainTemplate;
	}

	public boolean isNeedsSave() {
		for (TerrainInstance i : loaded.values()) {
			if (i.isNeedsSave()) {
				System.err.println(i.getPage() + " needs a save!");
				return true;
			}
		}
		return false;
	}

	public boolean setDefaultTerrainTemplate(TerrainTemplateConfiguration defaultTerrainTemplate,
			Vector3f initalLocation, Quaternion initialRotation) {

		if (!Objects.equals(this.defaultTerrainTemplate, defaultTerrainTemplate)) {
			// First pause loader to stop any more tiles getting loaded
			pause();

			/*
			 * Now unload all the existing terrain tiles, this should propogate
			 * to clutter and anything else attached to the terrain (props,
			 * creatures etc)
			 */
			unloadAll();

			// Switch to the new template
			this.defaultTerrainTemplate = defaultTerrainTemplate;

			/*
			 * Resume loading
			 */
			resume();

			/*
			 * Tell everyone interested about the change of terrain template.
			 * One of these should set the camera to the initial location, and
			 * call back to the terrain loader to load start loading tiles again
			 */
			for (int i = listeners.size() - 1; i >= 0; i--) {
				listeners.get(i).templateChanged(defaultTerrainTemplate, initalLocation, initialRotation);
			}
			return true;
		}
		return false;
	}

	@Override
	public String getTaskName(PageLocation page) {
		return String.format("Terrain %d, %d", page.x, page.y);
	}

	public void reloadTerrain() {
		unloadAll();
		clearQueue();
		for (int i = listeners.size() - 1; i >= 0; i--) {
			listeners.get(i).terrainReload();
		}
	}

	public boolean isTileAvailableAtWorldPosition(Vector2f pos) {
		return getDefaultTerrainTemplate() != null && get(getDefaultTerrainTemplate().getTile(pos)) != null;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setTerrainTemplate(String terrainTemplate) {
		setTerrain(terrainTemplate);
		// reloadTerrain();
	}

	public void syncEdges(TerrainInstance thisInstance) {
		LOG.info(String.format("Syncing edges for %s", thisInstance.getPage()));

		TerrainTemplateConfiguration thisTemplate = thisInstance.getTerrainTemplate();
		TerrainQuadWrapper thisQuad = (TerrainQuadWrapper) thisInstance.getQuad();

		float wuPerPixel = (float) thisTemplate.getPageWorldZ() / (float) thisTemplate.getPageSize();

		TerrainQuadWrapper rightQuad = (TerrainQuadWrapper) getRightQuad(thisQuad);
		if (rightQuad != null) {
			TerrainInstance rightInstance = rightQuad.getInstance();
			for (float i = 0; i < thisTemplate.getPageWorldZ(); i += wuPerPixel) {
				float y = i + (thisInstance.getPage().y * thisTemplate.getPageWorldZ());
				Vector2f pos = new Vector2f((thisTemplate.getPageWorldX() * (thisInstance.getPage().x + 1)) - 1, y);
				Vector2f cen = thisInstance.worldToRelative(pos);
				float hh = thisQuad.getHeightmapHeight(cen);
				Vector2f rpos = new Vector2f(rightQuad.getInstance().getPage().x
						* rightQuad.getInstance().getTerrainTemplate().getPageWorldX(), y);
				rpos = rightInstance.worldToRelative(rpos);
				rightQuad.setHeight(rpos, hh);
			}
		}
		TerrainQuadWrapper leftQuad = (TerrainQuadWrapper) getLeftQuad(thisQuad);
		if (leftQuad != null) {
			TerrainInstance leftInstance = leftQuad.getInstance();
			TerrainTemplateConfiguration leftTemplate = leftInstance.getTerrainTemplate();
			for (float i = 0; i < thisTemplate.getPageWorldZ(); i += wuPerPixel) {
				float y = i + (thisInstance.getPage().y * thisTemplate.getPageWorldZ());
				Vector2f pos = new Vector2f(thisTemplate.getPageWorldX() * thisInstance.getPage().x, y);
				Vector2f cen = thisInstance.worldToRelative(pos);
				float hh = thisQuad.getHeightmapHeight(cen);
				Vector2f rpos = new Vector2f((leftTemplate.getPageWorldX() * (leftInstance.getPage().x + 1)) - 1, y);
				rpos = leftInstance.worldToRelative(rpos);
				leftQuad.setHeight(rpos, hh);
			}
		}

		wuPerPixel = (float) thisTemplate.getPageWorldX() / (float) thisTemplate.getPageSize();

		TerrainQuadWrapper topQuad = (TerrainQuadWrapper) getTopQuad(thisQuad);
		if (topQuad != null) {
			TerrainInstance topInstance = topQuad.getInstance();
			TerrainTemplateConfiguration topTemplate = topInstance.getTerrainTemplate();
			for (float i = 0; i < thisTemplate.getPageWorldX(); i += wuPerPixel) {
				float x = i + (thisInstance.getPage().x * thisTemplate.getPageWorldX());
				Vector2f pos = new Vector2f(x, (thisTemplate.getPageWorldZ() * (thisInstance.getPage().y + 1)) - 1);
				Vector2f cen = thisInstance.worldToRelative(pos);
				float hh = thisQuad.getHeightmapHeight(cen);
				Vector2f rpos = new Vector2f(x, topQuad.getInstance().getPage().y * topTemplate.getPageWorldZ());
				rpos = topInstance.worldToRelative(rpos);
				topQuad.setHeight(rpos, hh);
			}
		}
		TerrainQuadWrapper bottomQuad = (TerrainQuadWrapper) getDownQuad(thisQuad);
		if (bottomQuad != null) {
			TerrainInstance bottomInstance = bottomQuad.getInstance();
			TerrainTemplateConfiguration bottomTemplate = bottomInstance.getTerrainTemplate();
			for (float i = 0; i < thisTemplate.getPageWorldX(); i += wuPerPixel) {
				float x = i + (thisInstance.getPage().x * thisTemplate.getPageWorldX());
				Vector2f pos = new Vector2f(x, thisTemplate.getPageWorldZ() * thisInstance.getPage().y);
				Vector2f cen = thisInstance.worldToRelative(pos);
				float hh = thisQuad.getHeightmapHeight(cen);
				Vector2f rpos = new Vector2f(x,
						(bottomTemplate.getPageWorldZ() * (bottomInstance.getPage().y + 1)) - 1);
				rpos = bottomInstance.worldToRelative(rpos);
				bottomQuad.setHeight(rpos, hh);
			}
		}
	}

	public boolean XXsyncEdges(TerrainInstance thisInstance) {
		TerrainTemplateConfiguration thisTemplate = thisInstance.getTerrainTemplate();
		TerrainQuadWrapper thisQuad = (TerrainQuadWrapper) thisInstance.getQuad();
		AbstractHeightMap thisMap = thisInstance.getHeightmap();
		boolean changed = false;
		float f, t;

		TerrainQuadWrapper rightQuad = (TerrainQuadWrapper) getRightQuad(thisQuad);
		if (rightQuad != null) {
			TerrainInstance rightInstance = rightQuad.getInstance();
			AbstractHeightMap rightMap = rightInstance.getHeightmap();
			for (int i = 0; i < thisTemplate.getPageSize(); i++) {
				f = rightMap.getTrueHeightAtPoint(0, i);
				t = thisMap.getTrueHeightAtPoint(thisTemplate.getPageSize() - 1, i);
				if (f != t) {
					thisMap.setHeightAtPoint(f, thisTemplate.getPageSize() - 1, i);
					changed = true;
				}
			}
		}
		TerrainQuadWrapper leftQuad = (TerrainQuadWrapper) getLeftQuad(thisQuad);
		if (leftQuad != null) {
			TerrainInstance leftInstance = leftQuad.getInstance();
			AbstractHeightMap leftMap = leftInstance.getHeightmap();
			for (int i = 0; i < thisTemplate.getPageSize(); i++) {
				f = leftMap.getTrueHeightAtPoint(thisTemplate.getPageSize() - 1, i);
				t = thisMap.getTrueHeightAtPoint(0, i);
				if (f != t) {
					leftMap.setHeightAtPoint(f, thisTemplate.getPageSize() - 1, i);
					changed = true;
				}
			}
		}

		TerrainQuadWrapper downQuad = (TerrainQuadWrapper) getDownQuad(thisQuad);
		if (downQuad != null) {
			TerrainInstance downInstance = downQuad.getInstance();
			AbstractHeightMap downMap = downInstance.getHeightmap();
			for (int i = 0; i < thisTemplate.getPageSize(); i++) {
				f = downMap.getTrueHeightAtPoint(i, thisTemplate.getPageSize() - 1);
				t = thisMap.getTrueHeightAtPoint(i, 0);
				if (f != t) {
					thisMap.setHeightAtPoint(f, i, 0);
					changed = true;
				}
			}
		}

		TerrainQuadWrapper topQuad = (TerrainQuadWrapper) getTopQuad(thisQuad);
		if (topQuad != null) {
			TerrainInstance topInstance = topQuad.getInstance();
			AbstractHeightMap topMap = topInstance.getHeightmap();
			for (int i = 0; i < thisTemplate.getPageSize(); i++) {
				f = topMap.getTrueHeightAtPoint(i, 0);
				t = thisMap.getTrueHeightAtPoint(i, thisTemplate.getPageSize() - 1);
				if (f != t) {
					thisMap.setHeightAtPoint(f, i, thisTemplate.getPageSize() - 1);
					changed = true;
				}
			}

		}

		return changed;
	}

	public void XXXsyncEdges(TerrainInstance thisInstance) {
		TerrainTemplateConfiguration thisTemplate = thisInstance.getTerrainTemplate();
		TerrainQuadWrapper thisQuad = (TerrainQuadWrapper) thisInstance.getQuad();

		float wuPerPixel = (float) thisTemplate.getPageWorldZ() / (float) thisTemplate.getPageSize();

		TerrainQuadWrapper rightQuad = (TerrainQuadWrapper) getRightQuad(thisQuad);
		if (rightQuad != null) {
			TerrainInstance rightInstance = rightQuad.getInstance();
			for (float i = 0; i < thisTemplate.getPageWorldZ(); i += wuPerPixel) {
				float y = i + (thisInstance.getPage().y * thisTemplate.getPageWorldZ());
				Vector2f pos = new Vector2f((thisTemplate.getPageWorldX() * (thisInstance.getPage().x + 1)) - 1, y);
				Vector2f cen = thisInstance.worldToRelative(pos);
				float hh = thisQuad.getHeightmapHeight(cen);
				Vector2f rpos = new Vector2f(
						rightInstance.getPage().x * rightInstance.getTerrainTemplate().getPageWorldX(), y);
				rpos = rightInstance.worldToRelative(rpos);
				rightQuad.setHeight(rpos, hh);
			}
		}
		TerrainQuadWrapper leftQuad = (TerrainQuadWrapper) getLeftQuad(thisQuad);
		if (leftQuad != null) {
			TerrainInstance leftInstance = leftQuad.getInstance();
			TerrainTemplateConfiguration leftTemplate = leftInstance.getTerrainTemplate();
			for (float i = 0; i < thisTemplate.getPageWorldZ(); i += wuPerPixel) {
				float y = i + (thisInstance.getPage().y * thisTemplate.getPageWorldZ());
				Vector2f pos = new Vector2f(thisTemplate.getPageWorldX() * thisInstance.getPage().x, y);
				Vector2f cen = thisInstance.worldToRelative(pos);
				float hh = thisQuad.getHeightmapHeight(cen);
				Vector2f rpos = new Vector2f((leftTemplate.getPageWorldX() * (leftInstance.getPage().x + 1)) - 1, y);
				rpos = leftInstance.worldToRelative(rpos);
				leftQuad.setHeight(rpos, hh);
			}
		}

		wuPerPixel = (float) thisTemplate.getPageWorldX() / (float) thisTemplate.getPageSize();

		TerrainQuadWrapper topQuad = (TerrainQuadWrapper) getTopQuad(thisQuad);
		if (topQuad != null) {
			TerrainInstance topInstance = topQuad.getInstance();
			TerrainTemplateConfiguration topTemplate = topInstance.getTerrainTemplate();
			for (float i = 0; i < thisTemplate.getPageWorldX(); i += wuPerPixel) {
				float x = i + (thisInstance.getPage().x * thisTemplate.getPageWorldX());
				Vector2f pos = new Vector2f(x, (thisTemplate.getPageWorldZ() * (thisInstance.getPage().y + 1)) - 1);
				Vector2f cen = thisInstance.worldToRelative(pos);
				float hh = thisQuad.getHeightmapHeight(cen);
				Vector2f rpos = new Vector2f(x, topQuad.getInstance().getPage().y * topTemplate.getPageWorldZ());
				rpos = topInstance.worldToRelative(rpos);
				topQuad.setHeight(rpos, hh);
			}
		}
		TerrainQuadWrapper bottomQuad = (TerrainQuadWrapper) getDownQuad(thisQuad);
		if (bottomQuad != null) {
			TerrainInstance bottomInstance = bottomQuad.getInstance();
			TerrainTemplateConfiguration bottomTemplate = bottomInstance.getTerrainTemplate();
			for (float i = 0; i < thisTemplate.getPageWorldX(); i += wuPerPixel) {
				float x = i + (thisInstance.getPage().x * thisTemplate.getPageWorldX());
				Vector2f pos = new Vector2f(x, thisTemplate.getPageWorldZ() * thisInstance.getPage().y);
				Vector2f cen = thisInstance.worldToRelative(pos);
				float hh = thisQuad.getHeightmapHeight(cen);
				Vector2f rpos = new Vector2f(x,
						(bottomTemplate.getPageWorldZ() * (bottomInstance.getPage().y + 1)) - 1);
				rpos = bottomInstance.worldToRelative(rpos);
				bottomQuad.setHeight(rpos, hh);
			}
		}
	}

	@Override
	protected TerrainInstance doReload(PageLocation page) {
		TerrainTemplateConfiguration terrainTemplate = defaultTerrainTemplate;

		if (defaultTerrainTemplate.getPerPageConfig() != null) {
			String name = defaultTerrainTemplate
					.absolutize(format(defaultTerrainTemplate.getPerPageConfig(), page.x, page.y));
			try {
				terrainTemplate = TerrainTemplateConfiguration.get(app.getAssetManager(), name, defaultTerrainTemplate);
			} catch (AssetNotFoundException re) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine(String.format("No terrain configuration found for %s", page));
				}
			}
		}

		final TerrainInstance pageInstance = new TerrainInstance(page, terrainTemplate);
		LOG.info(String.format("Loading terrain for %d, %d", page.x, page.y));
		// Do we have a per-page config for this?
		try {

			// Get the custom material
			Material terrainMat = null;

			// // Adjustable texture scale
			// // float texScale = Math.min(256, Math.max(0,
			// defaultConfiguration.getDetailTile() +
			// Config.get().getFloat(Config.TERRAIN_SCALE_ADJUST,
			// Config.TERRAIN_SCALE_ADJUST_DEFAULT)));
			// float texScale =
			// Config.get().getFloat(Config.TERRAIN_SCALE_ADJUST,
			// Config.TERRAIN_SCALE_ADJUST_DEFAULT) - 4f;
			// if(texScale < 1) {
			// texScale = 1f/ Math.abs(texScale - 1);
			// }
			// else {
			// texScale = (float)Math.pow(2, (int)texScale);
			// }

			float alphaScale = 1f;

			// Splats
			String coverageName = format(defaultTerrainTemplate.getTextureCoverageFormat(), page.x, page.y);

			// Tri-planar?
			int tp = app.getPreferences().getInt(SceneConfig.TERRAIN_TRI_PLANAR,
					SceneConfig.TERRAIN_TRI_PLANAR_DEFAULT);
			if (tp == AbstractConfig.DEFAULT) {
				tp = terrainTemplate.isUseTriStrips() ? AbstractConfig.TRUE : AbstractConfig.FALSE;
			}

			// Lit ?

			pageInstance.setHighDetail(app.getPreferences().getBoolean(SceneConfig.TERRAIN_HIGH_DETAIL, true));
			if (app.getPreferences().getBoolean(SceneConfig.TERRAIN_LIT, SceneConfig.TERRAIN_LIT_DEFAULT)) {
				terrainMat = new Material(app.getAssetManager(), LIT_TERRAIN_MATDEF);

				// try {
				final Texture img = app.getAssetManager()
						.loadTexture(terrainTemplate.getAssetFolder() + "/" + coverageName);
				terrainMat.setTexture("AlphaMap", img);
				pageInstance.setCoverageTextureMaterialKey("AlphaMap");

				// Store the image for the ClutterApPState to examine when
				// it is placing clutter props
				pageInstance.setCoverage(img.getImage());
				// } catch (AssetNotFoundException anfe) {
				// terrainMat.setTexture("AlphaMap",
				// app.getAssetManager().loadTexture(SceneConstants.TERRAIN_PATH
				// + "/Terrain-Common/Flat.png"));
				// }

				// Store we are using high detail in the page instance, this can
				// be used later when switching
				// between high and low dtail
				if (pageInstance.isHighDetail()) {
					if (tp == SceneConfig.TRUE) {
						terrainMat.setBoolean("useTriPlanarMapping", true);

						// planar textures don't use the mesh's texture
						// coordinates but real world coordinates,
						// so we need to convert these texture coordinate scales
						// into real world scales so it looks
						// the same when we switch to/from tr-planar mode

						float tpScale = (1f / (float) (((float) terrainTemplate.getPageSize() - 1f))) * 32f;

						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine(String.format("Scales: tp=%6.5f", tpScale));
						}

						loadLitSplat(terrainMat, 0, terrainTemplate.getTextureSplatting0(), terrainTemplate, tpScale);
						loadLitSplat(terrainMat, 1, terrainTemplate.getTextureSplatting1(), terrainTemplate, tpScale);
						loadLitSplat(terrainMat, 2, terrainTemplate.getTextureSplatting2(), terrainTemplate, tpScale);
						loadLitSplat(terrainMat, 3, terrainTemplate.getTextureSplatting3(), terrainTemplate, tpScale);
					} else {
						loadLitSplat(terrainMat, 0, terrainTemplate.getTextureSplatting0(), terrainTemplate, 32);
						loadLitSplat(terrainMat, 1, terrainTemplate.getTextureSplatting1(), terrainTemplate, 32);
						loadLitSplat(terrainMat, 2, terrainTemplate.getTextureSplatting2(), terrainTemplate, 32);
						loadLitSplat(terrainMat, 3, terrainTemplate.getTextureSplatting3(), terrainTemplate, 32);
					}
				} else {
					loadLitTexture(terrainMat, 0, String
							.format(terrainTemplate.absolutize(terrainTemplate.getTextureBaseFormat()), page.x, page.y),
							terrainTemplate, 1);
				}
			} else {

				terrainMat = new Material(app.getAssetManager(),
						"MatDefs/" + terrainTemplate.getCustomMaterialName() + ".j3md");

				// Get the Coverage texture (Alpha in JME terminology)
				Texture coverageTexture;
				// try {
				coverageTexture = app.getAssetManager()
						.loadTexture(terrainTemplate.getAssetFolder() + "/" + coverageName);
				// } catch (AssetNotFoundException anfe) {
				// coverageTexture = app.getAssetManager().loadTexture(
				// SceneConstants.TERRAIN_PATH +
				// "/Terrain-Common/Flat-Coverage.png");
				// }
				pageInstance.setCoverageTextureMaterialKey("Alpha");
				terrainMat.setTexture("Alpha", coverageTexture);
				terrainMat.setFloat("AlphaScale", alphaScale);

				// Store the image for the ClutterApPState to examine when it is
				// placing clutter props
				pageInstance.setCoverage(coverageTexture.getImage());

				if (app.getPreferences().getBoolean(SceneConfig.TERRAIN_HIGH_DETAIL, true)) {

					if (tp == SceneConfig.TRUE) {
						terrainMat.setBoolean("useTriPlanarMapping", true);

						// planar textures don't use the mesh's texture
						// coordinates but real world coordinates,
						// so we need to convert these texture coordinate scales
						// into real world scales so it looks
						// the same when we switch to/from tr-planar mode

						float tpScale = (1f / (float) (((float) terrainTemplate.getPageSize() - 1f))) * 32;

						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine(String.format("    Scales: tp=%6.5f", tpScale));
						}

						// A
						loadUnlitSplat(terrainMat, 1, terrainTemplate.getTextureSplatting0(), terrainTemplate, tpScale);
						// R
						loadUnlitSplat(terrainMat, 2, terrainTemplate.getTextureSplatting1(), terrainTemplate, tpScale);
						// G
						loadUnlitSplat(terrainMat, 3, terrainTemplate.getTextureSplatting2(), terrainTemplate, tpScale);
						// B
						loadUnlitSplat(terrainMat, 4, terrainTemplate.getTextureSplatting3(), terrainTemplate, tpScale);
					} else {
						// A
						loadUnlitSplat(terrainMat, 1, terrainTemplate.getTextureSplatting0(), terrainTemplate, 32);
						// R
						loadUnlitSplat(terrainMat, 2, terrainTemplate.getTextureSplatting1(), terrainTemplate, 32);
						// G
						loadUnlitSplat(terrainMat, 3, terrainTemplate.getTextureSplatting2(), terrainTemplate, 32);
						// B
						loadUnlitSplat(terrainMat, 4, terrainTemplate.getTextureSplatting3(), terrainTemplate, 32);
					}
				} else {
					loadUnlitTexture(terrainMat, 1, String
							.format(terrainTemplate.absolutize(terrainTemplate.getTextureBaseFormat()), page.x, page.y),
							terrainTemplate, 1);
				}
			}

			// Wireframe?
			if (app.getPreferences().getBoolean(SceneConfig.TERRAIN_WIREFRAME, SceneConfig.TERRAIN_WIREFRAME_DEFAULT)) {
				terrainMat.getAdditionalRenderState().setWireframe(true);
			}

			// Heightmap
			if (TerrainTemplateConfiguration.PAGE_SOURCE_HEIGHTMAP.equals(terrainTemplate.getPageSource())) {

				final float pageScale = terrainTemplate.getPageScale();

				AbstractHeightMap heightmap = createHeightMap(pageInstance);

				final float wuX = pageInstance.getWorldX();
				final float wuZ = pageInstance.getWorldZ();

				// The TerrainQuad itself. This is scaled to the size of one
				// page
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine(String.format("    TerrainQuad(%d,%d) scaling by %6.3f", terrainTemplate.getTileSize(),
							terrainTemplate.getPageSize(), pageScale));
				}
				final TerrainQuadWrapper quad = new TerrainQuadWrapper(pageInstance, "terrain",
						terrainTemplate.getTileSize(), terrainTemplate.getPageSize(), heightmap.getHeightMap());
				// quad.setNeighbourFinder(this);
				pageInstance.setQuad(quad);
				pageInstance.setHeightmap(heightmap);

				quad.setShadowMode(RenderQueue.ShadowMode.Receive);
				quad.setQueueBucket(RenderQueue.Bucket.Opaque);
				quad.setMaterial(terrainMat);
				// quad.setLocalScale(pageScale,
				// terrainTemplate.getPageHeightScale(), pageScale);
				quad.setLocalScale(pageScale, 1f, pageScale);
				addOrRemoveLODControl(quad);

				// Attach to scene on scene thread
				app.enqueue(new Callable<Void>() {
					public Void call() {
						final Node node = pageInstance.getNode();
						// Add the scaled terrain to a Node. This makes placing
						// clutter and other stuff easier as we don't have to
						// worry about any scale translation
						node.attachChild(quad);
						node.setLocalTranslation(wuX, 0, wuZ);

						syncEdges(pageInstance);
						// if (syncEdges(pageInstance)) {
						// LOG.info("There are edges to sync, refreshing from
						// heightmap data");
						// ((TerrainQuadWrapper)
						// pageInstance.getQuad()).setHeights(pageInstance.getHeightmap().getHeightMap());
						// }

						// Notify
						onSceneLoaded(pageInstance);
						for (int i = listeners.size() - 1; i >= 0; i--) {
							listeners.get(i).tileLoaded(pageInstance);
						}

						// Physics of terrain
						final BulletAppState physicsState = app.getStateManager().getState(BulletAppState.class);
						if (physicsState != null) {
							RigidBodyControl scenePhy = new RigidBodyControl(0f);
							node.addControl(scenePhy);
							physicsState.getPhysicsSpace().add(node);
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine(format("    lt: @%s, wt: %s, ts: %s, ws: %s, page scale %6.3f",
										node.getLocalTranslation(), node.getWorldTranslation(), node.getLocalScale(),
										node.getWorldScale(), pageScale));
							}
							pageInstance.setPhysics(scenePhy);
						} else {
							LOG.info("No physics to add terrain to.");
						}

						return null;
					}
				});

				// Water
				reconfigureWaterPlane(pageInstance);

			} else {
				throw new IllegalArgumentException("Unknown page source " + terrainTemplate.getPageSource());
			}
		} catch (AssetNotFoundException anfe) {
			LOG.log(Level.FINE, format("No terrain for %d,%d. %s", page.x, page.y, anfe.getMessage()));
		} catch (Exception e) {
			LOG.log(Level.SEVERE, format("Failed to load terrain for %d,%d", page.x, page.y), e);
		} finally {
			onModelLoaded(pageInstance);
		}
		return pageInstance;
	}

	protected AbstractHeightMap createHeightMap(TerrainInstance instance) {
		String heightmapName = format(instance.getTerrainTemplate().getHeightmapImageFormat(), instance.getPage().x,
				instance.getPage().y);
		Image heightMapImage;
		final String heightMapPath = instance.getTerrainTemplate().getAssetFolder() + "/" + heightmapName;
		heightMapImage = app.getAssetManager().loadTexture(heightMapPath).getImage();

		AbstractHeightMap heightmap = null;
		try {
			LOG.info(String.format("Creating heightmap %s from image of %s of %dx%d for depth %d",
					heightMapPath, heightMapImage.getFormat(), heightMapImage.getWidth(), heightMapImage.getHeight(),
					heightMapImage.getDepth()));
			if (heightMapImage.getFormat() == Format.Luminance16F) {
				heightmap = new SaveableWideImageBasedHeightMap(heightMapImage,
						65535f / (float) instance.getTerrainTemplate().getMaxHeight());
			} else {
				throw new IOException(
						"Only 16 bit greyscale currently supported for heightmap images (image  has depth of "
								+ heightMapImage.getDepth() + ".");
				// heightmap = new SaveableImageBasedHeightMap(heightMapImage,
				// SceneConstants.HEIGHTMAP_SCALE);
			}
			heightmap.load();

			return heightmap;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Will adjust the tiles material (and other parameters) based on current
	 * preferences. This should be called when preferences change, as it can
	 * reconfigure the quad without destroying any current edits of either the
	 * heightmap or the coverage.
	 *
	 * @param instance
	 *            terrain page
	 */
	public void reconfigureTile(TerrainInstance instance) {
		TerrainQuad quad = instance.getQuad();
		PageLocation page = instance.getPage();
		float alphaScale = 1f;
		Material mat = quad == null ? null : quad.getMaterial();
		boolean wantLit = app.getPreferences().getBoolean(SceneConfig.TERRAIN_LIT, SceneConfig.TERRAIN_LIT_DEFAULT);
		final TerrainTemplateConfiguration terrainTemplate = instance.getTerrainTemplate();
		String coverageName = format(terrainTemplate.getTextureCoverageFormat(), page.x, page.y);

		// Tri-planar?
		int tp = app.getPreferences().getInt(SceneConfig.TERRAIN_TRI_PLANAR, SceneConfig.TERRAIN_TRI_PLANAR_DEFAULT);
		if (tp == AbstractConfig.DEFAULT) {
			tp = terrainTemplate.isUseTriStrips() ? AbstractConfig.TRUE : AbstractConfig.FALSE;
		}
		boolean useTp = tp == AbstractConfig.TRUE;

		// Determine whether lit or unlit should be used
		if (mat != null && wantLit && mat.getParam("DiffuseMap") == null) {
			activateLit(page, instance, mat, terrainTemplate, coverageName, useTp, quad);
		} else if (mat != null && !wantLit && mat.getParam("Tex1") == null) {
			activateUnlit(page, instance, terrainTemplate, mat, coverageName, alphaScale, useTp, quad);
		} else {
			final boolean wantHighDetail = app.getPreferences().getBoolean(SceneConfig.TERRAIN_HIGH_DETAIL, true);
			// Check for high detail change
			if (wantHighDetail != instance.isHighDetail()) {
				recreateMaterial(mat, page, instance, terrainTemplate, coverageName, alphaScale, useTp, quad);
			} else {
				// Check for tri-planar change
				if (useTp != isUsingTriPlanar(mat)) {
					LOG.info("Recreating because tri-planar changed");
					recreateMaterial(mat, page, instance, terrainTemplate, coverageName, alphaScale, useTp, quad);
				}
			}
		}

		// Material instance may have changed
		if (quad != null) {
			mat = quad.getMaterial();

			int i = 0;

			// Reconfig the textures and texture scaling
			if (mat.getParam("DiffuseMap") == null) {
				// Unlit
				for (String s : new String[] { "Tex1", "Tex2", "Tex3", "Tex4" }) {
					Texture tex = null;
					switch (i++) {
					case 0:
						tex = app.getAssetManager().loadTexture(SceneConstants.TERRAIN_PATH + "/Terrain-Common/"
								+ terrainTemplate.getTextureSplatting0());
						mat.setTexture("Tex1", tex);
						break;
					case 1:
						tex = app.getAssetManager().loadTexture(SceneConstants.TERRAIN_PATH + "/Terrain-Common/"
								+ terrainTemplate.getTextureSplatting1());
						mat.setTexture("Tex2", tex);
						break;
					case 2:
						tex = app.getAssetManager().loadTexture(SceneConstants.TERRAIN_PATH + "/Terrain-Common/"
								+ terrainTemplate.getTextureSplatting2());
						mat.setTexture("Tex3", tex);
						break;
					case 3:
						tex = app.getAssetManager().loadTexture(SceneConstants.TERRAIN_PATH + "/Terrain-Common/"
								+ terrainTemplate.getTextureSplatting3());
						mat.setTexture("Tex4", tex);
						break;
					}
					tex.setWrap(Texture.WrapMode.Repeat);
					configureTexScaling(mat, s);
				}
			} else {
				// Lit
				for (String s : new String[] { "DiffuseMap", "DiffuseMap_1", "DiffuseMap_2", "DiffuseMap_3" }) {
					Texture tex = null;
					switch (i++) {
					case 0:
						tex = app.getAssetManager().loadTexture(SceneConstants.TERRAIN_PATH + "/Terrain-Common/"
								+ terrainTemplate.getTextureSplatting0());
						mat.setTexture("DiffuseMap", tex);
						break;
					case 1:
						tex = app.getAssetManager().loadTexture(SceneConstants.TERRAIN_PATH + "/Terrain-Common/"
								+ terrainTemplate.getTextureSplatting1());
						mat.setTexture("DiffuseMap_1", tex);
						break;
					case 2:
						tex = app.getAssetManager().loadTexture(SceneConstants.TERRAIN_PATH + "/Terrain-Common/"
								+ terrainTemplate.getTextureSplatting2());
						mat.setTexture("DiffuseMap_2", tex);
						break;
					case 3:
						tex = app.getAssetManager().loadTexture(SceneConstants.TERRAIN_PATH + "/Terrain-Common/"
								+ terrainTemplate.getTextureSplatting3());
						mat.setTexture("DiffuseMap_3", tex);
						break;
					}
					tex.setWrap(Texture.WrapMode.Repeat);
					configureTexScaling(mat, s);
				}
			}

			// LOD
			addOrRemoveLODControl(quad);

			// Wireframe
			quad.getMaterial().getAdditionalRenderState().setWireframe(app.getPreferences()
					.getBoolean(TerrainConfig.TERRAIN_WIREFRAME, TerrainConfig.TERRAIN_WIREFRAME_DEFAULT));
		}

		// Water
		reconfigureWaterPlane(instance);
	}

	private void recreateMaterial(Material mat, PageLocation page, TerrainInstance instance,
			final TerrainTemplateConfiguration terrainTemplate, String coverageName, float alphaScale, boolean useTp,
			TerrainQuad quad) {
		if (instance.isNeedsSave()) {
			throw new IllegalStateException(String.format(
					"Cannot change this terrain option for tile %d,%d as there are unsaved terrain edits. The tile will be refreshed when saved.",
					page.x, page.y));
		}
		if (mat.getParam("DiffuseMap") == null) {
			activateUnlit(page, instance, terrainTemplate, null, coverageName, alphaScale, useTp, quad);
		} else {
			activateLit(page, instance, null, terrainTemplate, coverageName, useTp, quad);
		}
	}

	private void addOrRemoveLODControl(final TerrainQuad quad) {
		// TODO terrain config file has some LOD stuff
		final boolean enabled = app.getPreferences().getBoolean(SceneConfig.TERRAIN_LOD_CONTROL,
				SceneConfig.TERRAIN_LOD_CONTROL_DEFAULT);
		if (enabled && quad.getControl(TerrainLodControl.class) == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Enabling LOD");
			}
			TerrainLodControl lodControl = new TerrainLodControl(quad, app.getCamera());
			quad.addControl(lodControl);
		} else if (!enabled && quad.getControl(TerrainLodControl.class) != null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Disable LOD");
			}
			quad.removeControl(TerrainLodControl.class);
		}
	}

	private void configureTexScaling(Material mat, String key) {
		final MatParamTexture textureParam = mat.getTextureParam(key);
		if (textureParam != null) {
			Texture tex = textureParam.getTextureValue();
			if (app.getPreferences().getBoolean(SceneConfig.TERRAIN_SMOOTH_SCALING,
					SceneConfig.TERRAIN_SMOOTH_SCALING_DEFAULT)) {
				tex.setMagFilter(Texture.MagFilter.Bilinear);
				tex.setMinFilter(Texture.MinFilter.BilinearNearestMipMap);
			} else {
				tex.setMagFilter(Texture.MagFilter.Nearest);
				tex.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
			}
		}
	}

	private void activateUnlit(PageLocation page, TerrainInstance instance,
			final TerrainTemplateConfiguration terrainTemplate, Material mat, String coverageName, float alphaScale,
			boolean useTp, TerrainQuad quad) {
		LOG.info(String.format("Activating unlit terrain for %s (tri-planar %s)", page, useTp));
		// Activate unlit terrain
		instance.setCoverageTextureMaterialKey("Alpha");
		Material newMat = new Material(app.getAssetManager(),
				"MatDefs/" + terrainTemplate.getCustomMaterialName() + ".j3md");
		final boolean wantHighDetail = app.getPreferences().getBoolean(SceneConfig.TERRAIN_HIGH_DETAIL, true);

		if (mat == null) {
			LOG.info(String.format("Creating new unlit material for %s", page));
			// No existing material
			final Texture img = app.getAssetManager()
					.loadTexture(terrainTemplate.getAssetFolder() + "/" + coverageName);
			newMat.setTexture("Alpha", img);
			instance.setCoverage(img.getImage());
			instance.setHighDetail(wantHighDetail);
			createUnlitSplats(wantHighDetail, useTp, newMat, terrainTemplate);
		} else {
			LOG.info(String.format("Reusing existing material %s", page));
			// Build from existing material // Build from existing material
			newMat.setTexture("Alpha", mat.getTextureParam("AlphaMap").getTextureValue());
			boolean usingTp = isUsingTriPlanar(mat);
			if (useTp != usingTp) {
				// If the tri-planar settings has changed we must reload
				// everything
				if (instance.isNeedsSave()) {
					throw new IllegalStateException(
							"Cannot change tri-planer settings for this tile, as there are unsaved changes.");
				}
				createUnlitSplats(wantHighDetail, useTp, newMat, terrainTemplate);
			} else if (wantHighDetail && instance.isHighDetail()) {
				// High detail
				if (mat.getTextureParam("DiffuseMap") != null) {
					newMat.setTexture("Tex1", mat.getTextureParam("DiffuseMap").getTextureValue());
					newMat.setFloat("Tex1Scale", (Float) mat.getParam("DiffuseMap_0_scale").getValue());
				}
				if (mat.getTextureParam("DiffuseMap_1") != null) {
					newMat.setTexture("Tex2", mat.getTextureParam("DiffuseMap_1").getTextureValue());
					newMat.setFloat("Tex2Scale", (Float) mat.getParam("DiffuseMap_1_scale").getValue());
				}
				if (mat.getTextureParam("DiffuseMap_2") != null) {
					newMat.setTexture("Tex3", mat.getTextureParam("DiffuseMap_2").getTextureValue());
					newMat.setFloat("Tex3Scale", (Float) mat.getParam("DiffuseMap_2_scale").getValue());
				}
				if (mat.getTextureParam("DiffuseMap_3") != null) {
					newMat.setTexture("Tex4", mat.getTextureParam("DiffuseMap_3").getTextureValue());
					newMat.setFloat("Tex4Scale", (Float) mat.getParam("DiffuseMap_3_scale").getValue());
				}
				newMat.setBoolean("useTriPlanarMapping", useTp);
			} else if (!wantHighDetail && !instance.isHighDetail()) {// Low
																		// detail
				newMat.setBoolean("useTriPlanarMapping", false);
				if (mat.getTextureParam("DiffuseMap") != null) {
					newMat.setTexture("Tex1", mat.getTextureParam("DiffuseMap").getTextureValue());
				}
			} else {
				// High detail has changed, unfortunately the terrain editor
				// does
				// not yet support low detail, even rendering is a bit crap. Im
				// not sure if
				// i care though
				if (instance.isNeedsSave()) {
					throw new IllegalStateException(
							"Cannot change detail settings for this tile, as there are unsaved changes.");
				}
				instance.setHighDetail(wantHighDetail);
				createUnlitSplats(wantHighDetail, useTp, newMat, terrainTemplate);
			}
		}
		newMat.setFloat("AlphaScale", alphaScale);
		quad.setMaterial(newMat);
	}

	private void activateLit(PageLocation page, TerrainInstance instance, Material mat,
			final TerrainTemplateConfiguration terrainTemplate, String coverageName, boolean useTp, TerrainQuad quad) {
		// Activate Lit terrain
		LOG.info(String.format("Activating lit terrain for %s (tri-planar %s)", page, useTp));

		instance.setCoverageTextureMaterialKey("AlphaMap");
		Material newMat = new Material(app.getAssetManager(), LIT_TERRAIN_MATDEF);
		final boolean wantHighDetail = app.getPreferences().getBoolean(SceneConfig.TERRAIN_HIGH_DETAIL, true);
		if (mat == null) {
			// No existing material
			LOG.info(String.format("Creating new lit material for %s", page));
			final Texture img = app.getAssetManager()
					.loadTexture(terrainTemplate.getAssetFolder() + "/" + coverageName);
			newMat.setTexture("AlphaMap", img);
			instance.setCoverage(img.getImage());
			createLitSplats(wantHighDetail, useTp, newMat, terrainTemplate, page);
		} else {
			// Build from existing material
			LOG.info(String.format("Reusing existing material for %s", page));
			newMat.setTexture("AlphaMap", mat.getTextureParam("Alpha").getTextureValue());

			boolean usingTp = isUsingTriPlanar(mat);
			if (useTp != usingTp) {
				// If the tri-planar settings has changed we must reload
				// everything
				if (instance.isNeedsSave()) {
					throw new IllegalStateException(
							"Cannot change tri-planer settings for this tile, as there are unsaved changes.");
				}
				createLitSplats(wantHighDetail, useTp, newMat, terrainTemplate, page);
			} else if (wantHighDetail && instance.isHighDetail()) {
				// High detail
				if (mat.getTextureParam("Tex1") != null) {
					newMat.setTexture("DiffuseMap", mat.getTextureParam("Tex1").getTextureValue());
					newMat.setFloat("DiffuseMap_0_scale", (Float) mat.getParam("Tex1Scale").getValue());
				}
				if (mat.getTextureParam("Tex2") != null) {
					newMat.setTexture("DiffuseMap_1", mat.getTextureParam("Tex2").getTextureValue());
					newMat.setFloat("DiffuseMap_1_scale", (Float) mat.getParam("Tex2Scale").getValue());
				}
				if (mat.getTextureParam("Tex3") != null) {
					newMat.setTexture("DiffuseMap_2", mat.getTextureParam("Tex3").getTextureValue());
					newMat.setFloat("DiffuseMap_2_scale", (Float) mat.getParam("Tex3Scale").getValue());
				}
				if (mat.getTextureParam("Tex4") != null) {
					newMat.setTexture("DiffuseMap_3", mat.getTextureParam("Tex4").getTextureValue());
					newMat.setFloat("DiffuseMap_3_scale", (Float) mat.getParam("Tex4Scale").getValue());
				}
				newMat.setBoolean("useTriPlanarMapping", useTp);
			} else if (!wantHighDetail && !instance.isHighDetail()) {
				// Low detail
				newMat.setBoolean("useTriPlanarMapping", false);
				if (mat.getTextureParam("Tex1") != null) {
					newMat.setTexture("DiffuseMap", mat.getTextureParam("Tex1").getTextureValue());
				}
			} else {
				// High detail has changed, unfortunately the terrain editor
				// does
				// not yet support low detail, even rendering is a bit crap. Im
				// not sure if
				// i care though
				if (instance.isNeedsSave()) {
					throw new IllegalStateException(
							"Cannot change detail settings for this tile, as there are unsaved changes.");
				}
				instance.setHighDetail(wantHighDetail);
				createLitSplats(wantHighDetail, useTp, newMat, terrainTemplate, page);
			}
		}

		//
		quad.setMaterial(newMat);
	}

	private void createUnlitSplats(final boolean wantsHighDetail, boolean useTp, Material newMat,
			final TerrainTemplateConfiguration terrainTemplate) {
		if (wantsHighDetail) {
			if (useTp) {
				newMat.setBoolean("useTriPlanarMapping", true);

				// planar textures don't use the mesh's texture coordinates but
				// real world coordinates,
				// so we need to convert these texture coordinate scales into
				// real world scales so it looks
				// the same when we switch to/from tr-planar mode

				float tpScale = (1f / (float) (((float) terrainTemplate.getPageSize() - 1f))) * 32;

				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine(String.format("Scales: tp=%6.5f", tpScale));
				}
				// A
				loadUnlitSplat(newMat, 1, terrainTemplate.getTextureSplatting0(), terrainTemplate, tpScale);
				// R
				loadUnlitSplat(newMat, 2, terrainTemplate.getTextureSplatting1(), terrainTemplate, tpScale);
				// G
				loadUnlitSplat(newMat, 3, terrainTemplate.getTextureSplatting2(), terrainTemplate, tpScale);
				// B
				loadUnlitSplat(newMat, 4, terrainTemplate.getTextureSplatting3(), terrainTemplate, tpScale);
			} else {
				// A
				loadUnlitSplat(newMat, 1, terrainTemplate.getTextureSplatting0(), terrainTemplate, 32);
				// R
				loadUnlitSplat(newMat, 2, terrainTemplate.getTextureSplatting1(), terrainTemplate, 32);
				// G
				loadUnlitSplat(newMat, 3, terrainTemplate.getTextureSplatting2(), terrainTemplate, 32);
				// B
				loadUnlitSplat(newMat, 4, terrainTemplate.getTextureSplatting3(), terrainTemplate, 32);
			}
		}
	}

	private void createLitSplats(final boolean wantHighDetail, boolean useTp, Material newMat,
			final TerrainTemplateConfiguration terrainTemplate, PageLocation page) {
		if (wantHighDetail) {
			if (useTp) {
				newMat.setBoolean("useTriPlanarMapping", true);

				// planar textures don't use the mesh's texture coordinates but
				// real world coordinates,
				// so we need to convert these texture coordinate scales into
				// real world scales so it looks
				// the same when we switch to/from tr-planar mode

				float tpScale = (1f / (float) (((float) terrainTemplate.getPageSize() - 1f))) * 32;

				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine(String.format("Scales: tp=%6.5f", tpScale));
				}

				loadLitSplat(newMat, 0, terrainTemplate.getTextureSplatting0(), terrainTemplate, tpScale);
				loadLitSplat(newMat, 1, terrainTemplate.getTextureSplatting1(), terrainTemplate, tpScale);
				loadLitSplat(newMat, 2, terrainTemplate.getTextureSplatting2(), terrainTemplate, tpScale);
				loadLitSplat(newMat, 3, terrainTemplate.getTextureSplatting3(), terrainTemplate, tpScale);
			} else {
				loadLitSplat(newMat, 0, terrainTemplate.getTextureSplatting0(), terrainTemplate, 32);
				loadLitSplat(newMat, 1, terrainTemplate.getTextureSplatting1(), terrainTemplate, 32);
				loadLitSplat(newMat, 2, terrainTemplate.getTextureSplatting2(), terrainTemplate, 32);
				loadLitSplat(newMat, 3, terrainTemplate.getTextureSplatting3(), terrainTemplate, 32);
			}
		} else {
			loadLitTexture(newMat, 0,
					String.format(terrainTemplate.absolutize(terrainTemplate.getTextureBaseFormat()), page.x, page.y),
					terrainTemplate, 1);
		}
	}

	private void loadUnlitSplat(Material terrainMat, int index, String name, TerrainTemplateConfiguration configuration,
			float scale) {
		loadUnlitTexture(terrainMat, index, SceneConstants.TERRAIN_PATH + "/Terrain-Common/" + name, configuration,
				scale);
	}

	private void loadUnlitTexture(Material terrainMat, int index, String path,
			TerrainTemplateConfiguration configuration, float scale) {
		Texture texture = app.getAssetManager().loadTexture(path);
		texture.setWrap(Texture.WrapMode.Repeat);
		if (!app.getPreferences().getBoolean(SceneConfig.TERRAIN_SMOOTH_SCALING,
				SceneConfig.TERRAIN_SMOOTH_SCALING_DEFAULT)) {
			texture.setMagFilter(Texture.MagFilter.Nearest);
			texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
		}
		terrainMat.setTexture("Tex" + index, texture);
		terrainMat.setFloat("Tex" + index + "Scale", scale);
	}

	private boolean isUsingTriPlanar(Material mat) {
		return mat != null && mat.getParam("useTriPlanarMapping") != null
				&& Boolean.TRUE.equals(mat.getParam("useTriPlanarMapping").getValue());
	}

	private void loadLitSplat(Material terrainMat, int index, String name, TerrainTemplateConfiguration configuration,
			float scale) {
		loadLitTexture(terrainMat, index, SceneConstants.TERRAIN_PATH + "/Terrain-Common/" + name, configuration,
				scale);
	}

	private void loadLitTexture(Material terrainMat, int index, String path, TerrainTemplateConfiguration configuration,
			float scale) {
		Texture texture = app.getAssetManager().loadTexture(path);
		texture.setWrap(Texture.WrapMode.Repeat);
		if (!app.getPreferences().getBoolean(SceneConfig.TERRAIN_SMOOTH_SCALING,
				SceneConfig.TERRAIN_SMOOTH_SCALING_DEFAULT)) {
			texture.setMagFilter(Texture.MagFilter.Nearest);
			texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
		}
		// TODO - this is silly
		if (index == 0) {
			terrainMat.setTexture("DiffuseMap", texture);
		} else {
			terrainMat.setTexture("DiffuseMap_" + index, texture);
		}
		terrainMat.setFloat("DiffuseMap_" + index + "_scale", scale);
	}

	private void reconfigureWaterPlane(final TerrainInstance pageInstance) {

		final TerrainTemplateConfiguration terrainTemplate = pageInstance.getTerrainTemplate();
		final PostProcessAppState pp = app.getStateManager().getState(PostProcessAppState.class);
		final TerrainTemplateConfiguration.LiquidPlaneConfiguration liquidPlaneConfiguration = terrainTemplate
				.getLiquidPlaneConfiguration();

		// Determine heights
		float currentHeight = pageInstance.getLiquidElevation();
		float requiredHeight = liquidPlaneConfiguration == null ? Float.MIN_VALUE
				: liquidPlaneConfiguration.getElevation();

		// Determine plants
		LiquidPlane currentPlane = pageInstance.getWaterPlaneName();
		LiquidPlane requiredPlane = liquidPlaneConfiguration == null ? null : liquidPlaneConfiguration.getMaterial();
		if (!Objects.equals(currentPlane, requiredPlane)) {
			LOG.info(String.format("Reloading liquid plane because plane in use has changed from %s to %s",
					String.valueOf(currentPlane), String.valueOf(requiredPlane)));
		}

		// Pretty water
		boolean wantsPrettyWater = app.getPreferences().getBoolean(SceneConfig.TERRAIN_PRETTY_WATER,
				SceneConfig.TERRAIN_PRETTY_WATER_DEFAULT);
		boolean havePrettyWater = pageInstance.getWater() != null;
		if (wantsPrettyWater != havePrettyWater) {
			if (wantsPrettyWater) {
				LOG.info("Reloading liquid plane because pretty water configuration has been enabled");
			} else {
				LOG.info("Reloading liquid plane because pretty water configuration has been disabled");
			}
		}

		// Water lighting
		Lighting waterLighting = app.getPreferences().getBoolean(SceneConfig.TERRAIN_LIT,
				SceneConfig.TERRAIN_LIT_DEFAULT) ? ExtendedMaterialListKey.Lighting.LIT
						: ExtendedMaterialListKey.Lighting.UNLIT;
		Lighting currentWaterLighting = pageInstance.getWaterPlane() != null
				? ((ExtendedMaterialKey) ((Material) ((Geometry) pageInstance.getWaterPlane()).getMaterial()).getKey())
						.getListKey().getLighting()
				: null;

		if (wantsPrettyWater != havePrettyWater || !Objects.equals(currentPlane, requiredPlane)
				|| !Objects.equals(waterLighting, currentWaterLighting)) {

			// If the plane or the water type has changed, load the new one
			if (currentPlane != null) {
				pageInstance.unloadWater(pp == null ? null : pp.getPostProcessor());
			}

			if (requiredPlane != null) {
				pageInstance.setWaterPlaneName(requiredPlane);

				// Load the new water plane
				try {
					boolean foundWater = false;
					if (pp != null && app.getPreferences().getBoolean(SceneConfig.TERRAIN_PRETTY_WATER,
							SceneConfig.TERRAIN_PRETTY_WATER_DEFAULT)) {

						String className = WaterFilterCapable.class.getPackage().getName() + "."
								+ liquidPlaneConfiguration.getMaterial().toMaterialName() + "WaterFilter";
						try {
							Class<? extends WaterFilterCapable> clazz = (Class<? extends WaterFilterCapable>) Class
									.forName(className);
							WaterFilterCapable waterConfig = clazz.newInstance();
							final WaterFilter water = createWaterFilter(waterConfig, terrainTemplate,
									pageInstance.getWorldX(), pageInstance.getWorldZ());
							pageInstance.setWater(water);
							// Attach to scene on scene thread
							app.enqueue(new Callable<Void>() {
								public Void call() {
									pp.addFilter(water);
									return null;
								}
							});
							foundWater = true;

						} catch (ClassNotFoundException cnfe) {
							LOG.info(String.format("No pretty water for %s (%s)",
									liquidPlaneConfiguration.getMaterial(), className));
						}

					}

					if (!foundWater) {
						// try {
						// Class<? extends LiquidPlane> clazz = (Class<? extends
						// LiquidPlane>) Class.forName(LiquidPlane.class
						// .getPackage().getName() + "." +
						// liquidPlaneConfiguration.getMaterial());
						// LiquidPlane liquidMaterial =
						// clazz.getConstructor(PageLocation.class,
						// TerrainTemplateConfiguration.class,
						// AssetManager.class).newInstance(pageInstance.getPage(),
						// terrainTemplate, app.getAssetManager());

						ExtendedMaterialListKey key = new ExtendedMaterialListKey(
								String.format("%s/Terrain-Common/TerrainWater.material", SceneConstants.TERRAIN_PATH));
						key.setLighting(waterLighting);
						MaterialList liquidMaterial = (MaterialList) app.getAssetManager().loadAsset(key);

						Box b = new Box((float) terrainTemplate.getPageWorldX() / 2, 0.1f,
								(float) terrainTemplate.getPageWorldZ() / 2);
						final Geometry geom = new Geometry("Box", b);
						LiquidPlane liquidPlane = terrainTemplate.getLiquidPlaneConfiguration().getMaterial();
						LOG.info(String.format("Reconfiguring water using %s (%s)", liquidPlane, waterLighting));
						Material material = liquidMaterial.get(liquidPlane.toMaterialName());
						if (material == null) {
							throw new AssetNotFoundException(
									String.format("No liquid plane material %s", liquidPlane.toMaterialName()));
						}
						geom.setMaterial(material);
						float hp = terrainTemplate.getPageWorldX() / 2f;
						geom.setLocalTranslation(0, liquidPlaneConfiguration.getElevation(), 0);
						pageInstance.setWaterPlane(geom);

						// Attach to scene on scene thread
						app.enqueue(new Callable<Void>() {
							public Void call() {
								pageInstance.getNode().attachChild(geom);
								return null;
							}
						});
						// } catch (ClassNotFoundException cnfe) {
						// LOG.log(Level.SEVERE,
						// "Could not find implementation for water plane
						// material.",
						// cnfe);
						// }
					}
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Failed to load water plane.", e);
				}

			}
		} else if (currentHeight != requiredHeight) {
			// Just the elevation has change
			if (requiredHeight != Float.MIN_VALUE) {
				pageInstance.setLiquidElevation(requiredHeight);
			}
		}
	}

	protected void onModelLoaded(TerrainInstance pageInstance) {
		// Invoked after terrain assets loaded
	}

	protected void onSceneUnloaded(TerrainInstance pageInstance) {
		// Invoked after terrain removed from scene
	}

	protected void onSceneLoaded(TerrainInstance pageInstance) {
		// Invoked after terrain added to scene
	}

	@Override
	protected TerrainInstance doUnload(TerrainInstance instance) {
		BulletAppState as = app.getStateManager().getState(BulletAppState.class);
		PostProcessAppState pp = app.getStateManager().getState(PostProcessAppState.class);
		instance.unload(pp == null ? null : pp.getPostProcessor(), as == null ? null : as.getPhysicsSpace());
		for (int i = listeners.size() - 1; i >= 0; i--) {
			listeners.get(i).tileUnloaded(instance);
		}
		onSceneUnloaded(instance);
		return instance;
	}

	private void setTerrain(String requiredTerrain) {
		if (StringUtils.isNotBlank(requiredTerrain)) {
			LOG.info(String.format("Loading terrain template %s", requiredTerrain));
			String terrainConfigPath = requiredTerrain.replace("#", "/");
			setDefaultTerrainTemplate(
					TerrainTemplateConfiguration.get(app.getAssetManager(),
							String.format("%s/%s", SceneConstants.TERRAIN_PATH, terrainConfigPath)),
					new Vector3f(0, 0, 0), null);
		} else {
			LOG.info("Clearing terrain template.");
			setDefaultTerrainTemplate(null, new Vector3f(0, 0, 0), null);
		}
	}

	@Override
	public TerrainQuad getRightQuad(TerrainQuad center) {
		TerrainQuadWrapper w = (TerrainQuadWrapper) center;
		try {
			TerrainInstance ti = get(w.getInstance().getPage().east());
			// TerrainInstance ti =
			// load(w.getInstance().getPage().east()).get();
			if (ti != null) {
				return ti.getQuad();
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to load top quad.", e);
		}
		return null;
	}

	@Override
	public TerrainQuad getLeftQuad(TerrainQuad center) {
		TerrainQuadWrapper w = (TerrainQuadWrapper) center;
		try {
			TerrainInstance ti = get(w.getInstance().getPage().west());
			// TerrainInstance ti =
			// load(w.getInstance().getPage().west()).get();
			if (ti != null) {
				return ti.getQuad();
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to load top quad.", e);
		}
		return null;
	}

	@Override
	public TerrainQuad getTopQuad(TerrainQuad center) {
		TerrainQuadWrapper w = (TerrainQuadWrapper) center;
		try {
			TerrainInstance ti = get(w.getInstance().getPage().north());
			// TerrainInstance ti =
			// load(w.getInstance().getPage().north()).get();
			if (ti != null) {
				return ti.getQuad();
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to load top quad.", e);
		}
		return null;
	}

	@Override
	public TerrainQuad getDownQuad(TerrainQuad center) {
		TerrainQuadWrapper w = (TerrainQuadWrapper) center;
		try {
			TerrainInstance ti = get(w.getInstance().getPage().south());
			// TerrainInstance ti =
			// load(w.getInstance().getPage().south()).get();
			if (ti != null) {
				return ti.getQuad();
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to load top quad.", e);
		}
		return null;
	}
}
