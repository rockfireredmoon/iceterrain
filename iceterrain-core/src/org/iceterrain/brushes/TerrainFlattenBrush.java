package org.iceterrain.brushes;

import java.util.List;

import org.iceterrain.HeightData;
import org.iceterrain.HeightDataKey;
import org.iceterrain.HeightDataKey.Op;
import org.iceterrain.TerrainInstance;
import org.iceterrain.TerrainLoader;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector2f;

import icetone.core.undo.UndoManager;
import icetone.core.undo.UndoableCommand;

/**
 * Adjusts the heightmap using a bitmap image as a mask (and the configured
 * rate). This
 * should allow interesting terrain brushes to be created.
 * <p>
 * This brush expects a world position to paint at, it then sets the height at
 * that position.
 */
public class TerrainFlattenBrush extends AbstractHeightBrush {

	private final float elevation;

	public TerrainFlattenBrush(AssetManager assetManager, UndoManager undoManager, TerrainLoader loader, int size, float elevation,
			String path, float rate, float baseline) {
		super(assetManager, undoManager, loader, size, path, rate, baseline);
		this.elevation = elevation;
	}

	protected void applyUndo(HeightDataKey key, final List<Vector2f> locs, final List<Float> heights) {
		key.getTerrainInstance().getQuad().setHeight(locs, heights);
	}

	protected void applyDo(HeightDataKey key, final List<Vector2f> locs, final List<Float> heights) {
		key.getTerrainInstance().getQuad().setHeight(locs, heights);
	}

	protected UndoableCommand doPaintPixel(final TerrainInstance instance, final Vector2f point, final Vector2f centre,
			float amount, int paints) {
		final float oldHeight = instance.getQuad().getHeight(point);
		final float newHeight = elevation;
		return new FlattenCommand(instance, newHeight, centre, oldHeight);

	}

	public final class FlattenCommand implements UndoableCommand {
		private final TerrainInstance instance;
		private final float newHeight;
		private final Vector2f centre;
		private final float oldHeight;
		private static final long serialVersionUID = 1L;

		public FlattenCommand(TerrainInstance instance, float newHeight, Vector2f centre, float oldHeight) {
			this.instance = instance;
			this.newHeight = newHeight;
			this.centre = centre;
			this.oldHeight = oldHeight;
		}

		public void undoCommand() {
			HeightData fhd = getHeightData(new HeightDataKey(Op.set, instance));
			fhd.heights.add(oldHeight);
			fhd.locs.add(centre);
		}

		public void doCommand() {
			HeightData fhd = getHeightData(new HeightDataKey(Op.set, instance));
			fhd.heights.add(newHeight);
			fhd.locs.add(centre);

		}
	}
}
