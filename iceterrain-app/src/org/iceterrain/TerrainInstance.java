package org.iceterrain;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.icelib.PageLocation;
import org.icelib.Point3D;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icescene.scene.AbstractSceneQueueLoadable;
import org.icescene.terrain.SaveableHeightMap;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.texture.Image;
import com.jme3.texture.image.ImageRaster;
import com.jme3.water.WaterFilter;

public class TerrainInstance extends AbstractSceneQueueLoadable<PageLocation> {

	private final static Logger LOG = Logger.getLogger(TerrainInstance.class.getName());
	private PageLocation page;
	private TerrainQuad quad;
	private WaterFilter water;
	private Spatial waterPlane;
	private RigidBodyControl scenePhysics;
	private boolean needsClutter = true;
	private Node node = new Node();
	private ImageRaster imageRaster;
	private final TerrainTemplateConfiguration terrainTemplate;
	private AbstractHeightMap heightmap;
	private Image coverage;
	private String coverageTextureMaterialKey;
	private boolean highDetail;
	private boolean needsSave;
	private String waterPlaneName;
	private boolean needsEdgeFix;

	public TerrainInstance(PageLocation page, TerrainTemplateConfiguration terrainTemplate) {
		if (terrainTemplate == null)
			throw new IllegalArgumentException();
		this.page = page;
		this.terrainTemplate = terrainTemplate;
	}

	public boolean isNeedsSave() {
		return needsSave;
	}

	public boolean setNeedsSave(boolean needsSave) {
		boolean wasNeedSave = this.needsSave;
		this.needsSave = needsSave;
		return wasNeedSave;
	}

	public float getWorldX() {
		return ((float) (page.x) * terrainTemplate.getPageWorldX()) + (terrainTemplate.getPageWorldX() / 2);
	}

	public float getWorldZ() {
		return ((float) (page.y) * terrainTemplate.getPageWorldZ()) + (terrainTemplate.getPageWorldZ() / 2);
	}

	public Vector2f worldToRelative(Vector2f location) {
		Vector2f centre = location.clone();
		centre.x -= getWorldX();
		centre.y -= getWorldZ();
		return centre;
	}

	public Vector2f relativeToWorld(Vector2f relativeLocation) {
		return new Vector2f(relativeLocation.x + (terrainTemplate.getPageWorldX() * page.x),
				relativeLocation.y + (terrainTemplate.getPageWorldZ() * page.y));
	}

	public boolean isHighDetail() {
		return highDetail;
	}

	public void setHighDetail(boolean highDetail) {
		this.highDetail = highDetail;
	}

	public ColorRGBA getCoverageAtPosition(Vector2f relativePos) {
		return getCoverageAtPosition(imageRaster, relativePos);
		// Vector2f pix = getCoveragePixelPosition(relativePos);
		// return getCoverageAtPixelPosition((int)pix.x, (int)pix.y);
	}

	public ColorRGBA getCoverageAtPosition(ImageRaster raster, Vector2f relativePos) {
		final int pixX = (int) ((relativePos.x / (float) terrainTemplate.getPageWorldX()) * 255f);
		final int pixY = 255 - (int) ((relativePos.y / (float) terrainTemplate.getPageWorldZ()) * 255f);
		try {
			return getCoverageAtPixelPosition(raster, pixX, pixY);
		} catch (IllegalArgumentException iae) {
			LOG.log(Level.SEVERE,
					String.format(
							"Failed to get coverage at relative position %s which works out to be %d,%d (page is %d / %d) = %s",
							relativePos, pixX, pixY, terrainTemplate.getPageWorldX(), terrainTemplate.getPageWorldZ(), getPage()));
			throw iae;
		}
		// Vector2f pix = getCoveragePixelPosition(relativePos);
		// return getCoverageAtPixelPosition((int)pix.x, (int)pix.y);
	}

	public ColorRGBA getCoverageAtPixelPosition(int x, int y) {
		return getCoverageAtPixelPosition(imageRaster, x, y);
	}

	public ColorRGBA getCoverageAtPixelPosition(ImageRaster raster, int x, int y) {
		try {
			return raster.getPixel(x, y);
		} catch (IllegalArgumentException iae) {
			LOG.log(Level.WARNING, String.format("Failed to get coverage at %d, %d.", x, y), iae);
			throw iae;
		}
	}

	public ColorRGBA getCoverageAtWorldPosition(Vector2f pos) {
		return getCoverageAtWorldPosition(imageRaster, pos);
	}

	public ColorRGBA getCoverageAtWorldPosition(ImageRaster raster, Vector2f pos) {
		try {
			return getCoverageAtPosition(raster, terrainTemplate.getPositionWithinTile(getPage(), pos));
		} catch (IllegalArgumentException iae) {
			LOG.log(Level.WARNING, String.format("Failed to get coverage at %s.", pos), iae);
			throw iae;
		}
	}

	public Vector2f getCoveragePixelPositionForWorldPosition(Vector2f pos) {
		return terrainTemplate == null || coverage == null ? null
				: getCoveragePixelPosition(terrainTemplate.getPositionWithinTile(getPage(), pos));
	}

	public Vector2f getCoveragePixelPosition(Vector2f relativePos) {
		float pX = (relativePos.x / terrainTemplate.getPageWorldX()) * (float) coverage.getWidth();
		float pZ = ((terrainTemplate.getPageWorldZ() - relativePos.y) / terrainTemplate.getPageWorldZ())
				* (float) coverage.getHeight();
		return new Vector2f((int) pX, (int) pZ);
	}

	public void setCoverageAtPosition(Vector2f relativePos, ColorRGBA color) {
		Vector2f pix = getCoveragePixelPosition(relativePos);
		setCoverageAtPixelPosition((int) pix.x, (int) pix.y, color);
	}

	public float getWorldWidth() {
		return ((float) terrainTemplate.getPageSize() - 1) * terrainTemplate.getPageScale();
	}

	public void setCoverageAtPixelPosition(int x, int y, ColorRGBA color) {
		imageRaster.setPixel(x, y, color);
	}

	public void setCoverageAtWorldPosition(Vector2f pos, ColorRGBA color) {
		setCoverageAtPosition(terrainTemplate.getPositionWithinTile(getPage(), pos), color);
	}

	public PageLocation getPage() {
		return page;
	}

	public ImageRaster getCoverageRaster() {
		return this.imageRaster;
	}

	public Image getCoverage() {
		return coverage;
	}

	public void setCoverage(Image image) {
		synchronized (image) {
			this.coverage = image;
			this.imageRaster = ImageRaster.create(image);
		}
	}

	public TerrainQuad getQuad() {
		return quad;
	}

	public Node getNode() {
		return node;
	}

	public void setNode(Node node) {
		this.node = node;
	}

	public WaterFilter getWater() {
		return water;
	}

	public Spatial getWaterPlane() {
		return waterPlane;
	}

	public void setQuad(TerrainQuad quad) {
		this.quad = quad;
	}

	public void setWaterPlaneName(String waterPlaneName) {
		this.waterPlaneName = waterPlaneName;
	}

	public String getWaterPlaneName() {
		return waterPlaneName;
	}

	public void setWater(WaterFilter water) {
		this.water = water;
	}

	public void setWaterPlane(Spatial waterPlane) {
		this.waterPlane = waterPlane;
	}

	public TerrainTemplateConfiguration getTerrainTemplate() {
		return terrainTemplate;
	}

	public float getHeightAtWorldPosition(Point3D pos) {
		return getHeightAtWorldPosition(new Vector2f(pos.x, pos.z));
	}

	public float getHeightAtWorldPosition(Vector2f pos) {
		return quad == null ? Float.MIN_VALUE : quad.getHeight(pos);
	}

	public Vector3f getSlopeAtWorldPosition(Vector2f pos) {
		try {
			return getQuad().getNormal(pos);
		} catch (Exception iobe) {
			LOG.warning(String.format("FIXME: Failed to get slope at terrain position. Probably sent bad position (%s)", pos));
			return null;
		}
	}

	void unload(FilterPostProcessor waterProcessor, PhysicsSpace physicsSpace) {
		LOG.info(String.format("Unloading %d, %d", page.x, page.y));
		if (node != null) {
			node.removeControl(scenePhysics);
			node.removeFromParent();
			node = null;
		}
		unloadWater(waterProcessor);
	}

	public void setPhysics(RigidBodyControl scenePhysics) {
		this.scenePhysics = scenePhysics;
	}

	public void setHeightmap(AbstractHeightMap heightmap) {
		this.heightmap = heightmap;
	}

	public AbstractHeightMap getHeightmap() {
		return heightmap;
	}

	public void setCoverageTextureMaterialKey(String coverageTextureMaterialKey) {
		this.coverageTextureMaterialKey = coverageTextureMaterialKey;
	}

	public String getCoverageTextureMaterialKey() {
		return coverageTextureMaterialKey;
	}

	public boolean isHeightmapAvailable() {
		return heightmap != null;
	}

	public void setLiquidElevation(float el) {
		if (water != null) {
			water.setWaterHeight(el);
		} else if (waterPlane != null) {
			waterPlane.setLocalTranslation(waterPlane.getLocalTranslation().x, el, waterPlane.getLocalTranslation().z);
		} else {
			LOG.warning(String.format("No water plane set, cannot set elevation to %f", el));
		}
	}

	public float getLiquidElevation() {
		if (water != null) {
			return water.getWaterHeight();
		} else if (waterPlane != null) {
			return waterPlane.getLocalTranslation().y;
		} else {
			return Float.MIN_VALUE;
		}
	}

	public void unloadWater(FilterPostProcessor waterProcessor) {
		if (waterPlane != null) {
			waterPlane.removeFromParent();
			waterPlane = null;
		}
		if (water != null && waterProcessor != null) {
			waterProcessor.removeFilter(water);
			water = null;
		}
		waterPlaneName = null;
	}

	public void copyQuadHeightmapToStoredHeightmap() {
		if (quad != null)
			((SaveableHeightMap) heightmap).setHeightData(quad.getHeightMap());
	}

	public void copyStoredHeightmapToQuad() {
		((TerrainQuadWrapper) quad).setHeights(heightmap.getHeightMap());
	}

	public void setNeedsEdgeFix(boolean needsEdgeFix) {
		this.needsEdgeFix = needsEdgeFix;
	}

	public boolean isNeedsEdgeFix() {
		return needsEdgeFix;
	}
}
