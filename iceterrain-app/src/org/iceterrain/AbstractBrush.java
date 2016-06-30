package org.iceterrain;

import java.nio.ByteBuffer;

import org.icelib.UndoManager;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.image.ImageRaster;
import com.zero_separation.plugins.imagepainter.ImagePainter;

/**
 * Brushes are used to paint terrain heightmaps and splats.
 */
public abstract class AbstractBrush {

	private final Image image;
	protected ImageRaster raster;
	private float rate;
	protected final float halfBrush;
	protected final TerrainLoader loader;
	protected final UndoManager undoManager;
	protected final int size;
	protected float baseline;

	public AbstractBrush(AssetManager assetManager, UndoManager undoManager, TerrainLoader loader, int size, String path,
			float rate, float baseline) {
		this.undoManager = undoManager;
		this.size = size;
		this.loader = loader;
		this.rate = rate;
		this.baseline = baseline;

		// For a 1 pixel brush, just dynamically create the brush image
		if (size == 1) {
			image = new Image(Image.Format.ABGR8, 1, 1, ByteBuffer.allocateDirect(4));
			raster = ImageRaster.create(image);
			ImagePainter ip = new ImagePainter(raster);
			ip.wipe(ColorRGBA.White);
		} else {

			// Get a raster
			Image brushImage = assetManager.loadTexture(path).getImage();
			raster = ImageRaster.create(brushImage);
			if (brushImage.getWidth() == size && brushImage.getHeight() == size) {
				// Optimisation if the brush image size is the same as the
				// requested brush size
				image = brushImage;
			} else {
				// Get a scaled instance of the brush imagh
				ByteBuffer data = ByteBuffer.allocateDirect(size * size * 4);
				image = new Image(brushImage.getFormat(), size, size, data);
				ImagePainter ip = new ImagePainter(image);
				ip.paintStretchedSubImage(0, 0, size, size, raster, ImagePainter.BlendMode.SET, 1f, 0, 0, brushImage.getWidth(),
						brushImage.getHeight());
				raster = ImageRaster.create(image);
			}
		}

		// Some variables that only need to be calculated once
		halfBrush = (((int) size) / 2);
	}

	public void paint(Vector2f worldCursor, float tpf, int paints) {
		ColorRGBA brushAmount;
		TerrainInstance instance;
		float amount;
		UndoManager.UndoableCompoundCommand commandGroup = createCommandGroup();
		instance = loader.getPageInstanceAtWorldPosition(worldCursor);

		float xStepAmount = calcXStep(instance);
		float zStepAmount = calcYStep(instance);

		onBeforePaint(worldCursor, instance);

		for (int x = 0; x < size; x++) {
			for (int y = 0; y < size; y++) {
				brushAmount = raster.getPixel(x, y);
				amount = brushAmount.a * rate * tpf;

				/* Adjust the relative position we actually paint to so it is
				 centered around the middle of the brush */
				final float offsetX = ((float) x - halfBrush) * xStepAmount;
				final float offsetZ = ((float) y - halfBrush) * zStepAmount;
				Vector2f point = new Vector2f(worldCursor.x + (offsetX), worldCursor.y + (offsetZ));

				/*
				 * Get the page the cursor is on. We do this for every pixel as it
				 * may cross tile boundaries.
				 * 
				 * TODO could probably do with optimisation here
				 */
				instance = loader.getPageInstanceAtWorldPosition(point);
				if (instance != null && instance.getQuad() != null) {
					// Process the pixel
					final UndoManager.UndoableCommand paintPixel = paintPixel(instance, point, amount, paints);
					if (paintPixel != null) {
						commandGroup.add(paintPixel);
					}
				}
			}
		}
		undoManager.storeAndExecute(commandGroup);

//		if (instance.isNeedsEdgeFix()) {
//			instance.copyQuadHeightmapToStoredHeightmap();
//			if (loader.syncEdges(instance)) {
//				instance.copyStoredHeightmapToQuad();
//			}
//		}

	}

	protected void onBeforePaint(Vector2f worldCursor, TerrainInstance instance) {
	}

	protected abstract UndoManager.UndoableCommand paintPixel(TerrainInstance instance, Vector2f point, float amount, int paints);

	public Image getImage() {
		return image;
	}

	protected UndoManager.UndoableCompoundCommand createCommandGroup() {
		UndoManager.UndoableCompoundCommand commandGroup = new UndoManager.UndoableCompoundCommand();
		return commandGroup;
	}

	protected float calcXStep(TerrainInstance instance) {
		return ((Node) instance.getQuad()).getWorldScale().x;
	}

	protected float calcYStep(TerrainInstance instance) {
		return ((Node) instance.getQuad()).getWorldScale().z;
	}

	static class PaintCommand implements UndoManager.UndoableCommand {

		public void undoCommand() {
		}

		public void doCommand() {
		}
	}
}
