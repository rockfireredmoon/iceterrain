package org.iceterrain.brushes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.iceterrain.AbstractBrush;
import org.iceterrain.HeightData;
import org.iceterrain.HeightDataKey;
import org.iceterrain.TerrainInstance;
import org.iceterrain.TerrainLoader;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;

import icetone.core.undo.UndoManager;
import icetone.core.undo.UndoableCommand;
import icetone.core.undo.UndoableCompoundCommand;

public abstract class AbstractHeightBrush extends AbstractBrush {

	private ThreadLocal<Map<HeightDataKey, HeightData>> data = new ThreadLocal<>();

	public AbstractHeightBrush(AssetManager assetManager, UndoManager undoManager, TerrainLoader loader, int size, String path,
			float rate, float baseline) {
		super(assetManager, undoManager, loader, size, path, rate, baseline);
	}

	protected Map<HeightDataKey, HeightData> getMap() {
		Map<HeightDataKey, HeightData> map = data.get();
		if (map == null) {
			map = new HashMap<HeightDataKey, HeightData>();
			data.set(map);
		}
		return map;
	}

	@Override
	protected final UndoableCommand paintPixel(TerrainInstance instance, Vector2f point, float amount, int paints) {
		// return doPaintPixel(instance, point, instance.worldToRelative(point),
		// amount * (float) instance.getTerrainTemplate().getMaxHeight() /
		// TerrainConstants.HEIGHT_BRUSH_FACTOR, paints);

		return doPaintPixel(instance, point, instance.worldToRelative(point), amount, paints);
	}

	protected Vector2f absoluteHeightmapPosition(TerrainInstance instance, Vector2f pixel) {
		return new Vector2f(((int) instance.getPage().x * instance.getTerrainTemplate().getPageSize()) + pixel.x,
				((int) instance.getPage().y * instance.getTerrainTemplate().getPageSize()) + pixel.y);
	}

	protected Vector2f relativeHeightmapPosition(TerrainInstance instance, Vector2f pixel) {
		return new Vector2f((int) pixel.x % instance.getTerrainTemplate().getPageSize(),
				(int) pixel.y % instance.getTerrainTemplate().getPageSize());
	}

	protected Vector2f getWorldPositionForHeightmapPosition(TerrainInstance instance, Vector2f point) {
		return new Vector2f((point.x / instance.getTerrainTemplate().getPageSize()) * instance.getTerrainTemplate().getPageWorldX(),
				(point.y / instance.getTerrainTemplate().getPageSize()) * instance.getTerrainTemplate().getPageWorldZ());
	}

	protected Vector2f getHeightmapPositionForWorldPosition(TerrainInstance instance, Vector2f point) {
		return new Vector2f((point.x / instance.getTerrainTemplate().getPageWorldX()) * instance.getTerrainTemplate().getPageSize(),
				(point.y / instance.getTerrainTemplate().getPageWorldZ()) * instance.getTerrainTemplate().getPageSize());
	}

	protected abstract UndoableCommand doPaintPixel(TerrainInstance instance, Vector2f point, Vector2f center,
			float amount, int paints);

	@Override
	protected UndoableCompoundCommand createCommandGroup() {
		return new UndoableCompoundCommand() {
			private static final long serialVersionUID = 1L;

			@Override
			public void undoCommand() {
				super.undoCommand();
				Map<HeightDataKey, HeightData> map = getMap();
				for (Map.Entry<HeightDataKey, HeightData> en : map.entrySet()) {
					final List<Vector2f> locs = en.getValue().locs;
					final List<Float> heights = en.getValue().heights;
					applyUndo(en.getKey(), locs, heights);
					if (!en.getKey().getTerrainInstance().setNeedsSave(en.getValue().wasNeedsSave)) {
						en.getKey().getTerrainInstance().setNeedsSave(true);
					}
					en.getKey().getTerrainInstance().setNeedsEdgeFix(true);

					// for new collisions
					((Node) en.getKey().getTerrainInstance().getQuad()).updateModelBound();
				}
				map.clear();
				data.remove();
			}

			@Override
			public void doCommand() {
				super.doCommand();
				Map<HeightDataKey, HeightData> map = getMap();

				for (Map.Entry<HeightDataKey, HeightData> en : map.entrySet()) {

					// float baseHeight = baseline /
					// en.getKey().getTerrainInstance().getQuad().getWorldScale().y;

					List<Vector2f> locs = en.getValue().locs;
					List<Float> heights = en.getValue().heights;

					// if(baseline != Float.MIN_VALUE) {
					// List<Float> newHeights = new
					// ArrayList<Float>(heights.size());
					// for(Float f : heights) {
					// // newHeights.add(Math.max(baseHeight, f));
					// System.out.println("TH of " + f + " against base of " +
					// baseHeight + " (" +baseline + " / " +
					// en.getKey().getQuad().getWorldScale().y + ")");
					// newHeights.add(f);
					// }
					// heights = newHeights;
					// }

					applyDo(en.getKey(), locs, heights);

					en.getValue().wasNeedsSave = en.getKey().getTerrainInstance().setNeedsSave(true);
					en.getKey().getTerrainInstance().setNeedsEdgeFix(true);

					// for new collisions
					((Node) en.getKey().getTerrainInstance().getQuad()).updateModelBound();
				}
				map.clear();
				data.remove();

			}
		};
	}

	protected HeightData getHeightData(HeightDataKey hdk) {
		Map<HeightDataKey, HeightData> map = getMap();
		HeightData hd = map.get(hdk);
		if (hd == null) {
			hd = new HeightData();
			map.put(hdk, hd);
		}
		return hd;
	}

	protected abstract void applyUndo(HeightDataKey key, final List<Vector2f> locs, final List<Float> heights);

	protected abstract void applyDo(HeightDataKey key, final List<Vector2f> locs, final List<Float> heights);
}
