package org.iceterrain.brushes;

import java.util.List;

import org.icelib.UndoManager;
import org.icescene.SceneConstants;
import org.icescene.terrain.SaveableHeightMap;
import org.iceterrain.HeightData;
import org.iceterrain.HeightDataKey;
import org.iceterrain.TerrainConstants;
import org.iceterrain.HeightDataKey.Op;
import org.iceterrain.TerrainInstance;
import org.iceterrain.TerrainLoader;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector2f;

/**
 * Adjusts the heightmap using a bitmap image as a mask (and the configured
 * rate). This should allow interesting terrain brushes to be created.
 * <p>
 * This brush expects a world position to paint at, it then locates the actual
 * pixel on the appropriate tile and adjusts the colour according to the channel
 * in use
 */
public class TerrainHeightBrush extends AbstractHeightBrush {

	public TerrainHeightBrush(AssetManager assetManager, UndoManager undoManager, TerrainLoader loader, int size, String path,
			float rate, float baseline) {
		super(assetManager, undoManager, loader, size, path, rate, baseline);
	}

	protected void applyUndo(HeightDataKey key, final List<Vector2f> locs, final List<Float> heights) {
		switch (key.getOp()) {
		case adjust:
			key.getTerrainInstance().getQuad().adjustHeight(locs, heights);
			break;
		case set:
			key.getTerrainInstance().getQuad().setHeight(locs, heights);
			break;
		}
	}

	protected void applyDo(HeightDataKey key, final List<Vector2f> locs, final List<Float> heights) {
		switch (key.getOp()) {
		case adjust:
			key.getTerrainInstance().getQuad().adjustHeight(locs, heights);
			break;
		case set:
			key.getTerrainInstance().getQuad().setHeight(locs, heights);
			break;
		}
	}   

	protected UndoManager.UndoableCommand doPaintPixel(final TerrainInstance instance, final Vector2f point, final Vector2f centre,
			final float amount, int paints) {
		//return new HeightCommand(instance, amount / size, centre);
		return new HeightCommand(instance, amount * ((SaveableHeightMap)instance.getHeightmap()).getHeightScale() * TerrainConstants.HEIGHT_BRUSH_FACTOR, centre);
	}

	@SuppressWarnings("serial")
	public final class HeightCommand implements UndoManager.UndoableCommand {
		private final TerrainInstance instance;
		private final float famount;
		private final Vector2f centre;

		public HeightCommand(TerrainInstance instance, float famount, Vector2f centre) {
			this.instance = instance;
			this.famount = famount;
			this.centre = centre;
		}

		public void undoCommand() {
			HeightData hd = getHeightData(new HeightDataKey(Op.adjust, instance));
			hd.heights.add(-famount);
			hd.locs.add(centre);
		}

		public void doCommand() {
			HeightData hd = getHeightData(new HeightDataKey(Op.adjust, instance));
			hd.heights.add(famount);
			hd.locs.add(centre);
		}

	}
}
