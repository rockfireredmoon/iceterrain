package org.iceterrain.brushes;

import java.util.List;

import org.icelib.UndoManager;
import org.iceterrain.HeightData;
import org.iceterrain.HeightDataKey;
import org.iceterrain.TerrainConstants;
import org.iceterrain.HeightDataKey.Op;
import org.iceterrain.TerrainInstance;
import org.iceterrain.TerrainLoader;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector2f;

/**
 * Smooths terrain
 */
public class TerrainSmoothBrush extends AbstractHeightBrush {
	private float avg;

	public TerrainSmoothBrush(AssetManager assetManager, UndoManager undoManager, TerrainLoader loader, int size, String path,
			float rate, float baseline) {
		super(assetManager, undoManager, loader, size, path, rate, baseline);
	}

	@Override
	protected void onBeforePaint(Vector2f worldCursor, TerrainInstance instance) {

		float xStepAmount = calcXStep(instance);
		float zStepAmount = calcYStep(instance);

		// Calculate the average to aim for
		float total = 0;
		for (int x = 0; x < size; x++) {
			for (int y = 0; y < size; y++) {
				final float offsetX = ((float) x - halfBrush) * xStepAmount;
				final float offsetZ = ((float) y - halfBrush) * zStepAmount;
				Vector2f point = new Vector2f(worldCursor.x + (offsetX), worldCursor.y + (offsetZ));
				total += instance.getHeightAtWorldPosition(point);
			}
		}
		avg = total / (size * size);
	}

	protected void applyUndo(HeightDataKey key, final List<Vector2f> locs, final List<Float> heights) {
		key.getTerrainInstance().getQuad().setHeight(locs, heights);
	}

	protected void applyDo(HeightDataKey key, final List<Vector2f> locs, final List<Float> heights) {
		key.getTerrainInstance().getQuad().setHeight(locs, heights);
	}

	protected UndoManager.UndoableCommand doPaintPixel(final TerrainInstance instance, final Vector2f point, final Vector2f centre,
			float amount, int paints) {
		final float currentHeight = instance.getQuad().getHeight(point);
		float amt = Math.min(amount * TerrainConstants.SMOOTH_BRUSH_FACTOR, 0.01f);
		final float newHeight = currentHeight < avg ? currentHeight + amt : currentHeight - amt ;
		
//		float diff = Float.isNaN(avg) ? 0 : avg - currentHeight;
//		float adj = diff * amount * TerrainConstants.SMOOTH_BRUSH_FACTOR * 0.0001f;
//		if(Math.abs(adj) > Math.abs(diff)) {
//			adj = diff;
//		}
//		final float newHeight = currentHeight + adj;
//		System.out.println("OLD: " + currentHeight + " NEW: " + newHeight + " ADJ: " + adj + " AMOUNT: " + amount + " DIFF: " +diff);
		if(currentHeight != newHeight) {
		System.out.println("OLD: " + currentHeight + " NEW: " + newHeight + " AVG: " + avg + " AMT: " + amt);
		}
		return new UndoManager.UndoableCommand() {
			private static final long serialVersionUID = 1L;

			public void undoCommand() {
				HeightData fhd = getHeightData(new HeightDataKey(Op.set, instance));
				fhd.heights.add(currentHeight);
				fhd.locs.add(centre);
			}

			public void doCommand() {
				HeightData fhd = getHeightData(new HeightDataKey(Op.set, instance));
				fhd.heights.add(newHeight);
				fhd.locs.add(centre);
			}
		};

	}

}
