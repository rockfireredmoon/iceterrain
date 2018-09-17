package org.iceterrain.brushes;

import org.iceterrain.AbstractBrush;
import org.iceterrain.TerrainInstance;
import org.iceterrain.TerrainLoader;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;

import icetone.core.undo.UndoManager;
import icetone.core.undo.UndoableCommand;

/**
 * Adjusts the A, R, G and B channels of a texture splat using a bitmap image as a mask
 * (and the configured rate). This should allow interesting terrain brushes to be created.
 * <p>
 * This brush expects a world position to paint at, it then locates the actual pixel on
 * the appropriate tile and adjusts the colour according to the channel in use
 */
public class TerrainSplatBrush extends AbstractBrush {
    
    public enum Channel {
        
        A, R, G, B
    }
    private Channel channel;
    
    public TerrainSplatBrush(AssetManager assetManager, UndoManager undoManager, TerrainLoader loader, int size, String path, Channel channel, float rate, float baseline) {
        super(assetManager, undoManager, loader, size, path, rate, baseline);
        this.channel = channel;
    }
    
    @Override
    protected float calcXStep(TerrainInstance instance) {
        return super.calcXStep(instance) / 2f;
    }
    
    @Override
    protected float calcYStep(TerrainInstance instance) {
        return super.calcYStep(instance) / 2f;
    }
    
    protected UndoableCommand paintPixel(final TerrainInstance instance, Vector2f center, final float amount, int paints) {
        final Vector2f pixel = instance.getCoveragePixelPositionForWorldPosition(center);
        if (pixel == null || pixel.x < 0 || pixel.y < 0 || pixel.x >= instance.getCoverage().getWidth() || pixel.y >= instance.getCoverage().getHeight()) {
            return null;
        }
        final ColorRGBA originalCoverage = instance.getCoverageAtPixelPosition((int) pixel.x, (int) pixel.y);
        return new UndoableCommand() {
            private boolean wasNeedsSave;

            public void undoCommand() {
                instance.setCoverageAtPixelPosition((int) pixel.x, (int) pixel.y, originalCoverage);
                if(!instance.setNeedsSave(wasNeedsSave)) {
                    instance.setNeedsSave(true);
                }
            }
            
            public void doCommand() {
                
                ColorRGBA coverage = originalCoverage.clone();
                /*
                 * Now adjust the current coverage pixel based on the color of the brush,
                 * the strength of the current brush pixel and the rate
                 */
                switch (channel) {
                    case A:
                        coverage.a = Math.min(1, coverage.a + amount);
                        coverage.r = Math.max(0, coverage.r - amount);
                        coverage.g = Math.max(0, coverage.g - amount);
                        coverage.b = Math.max(0, coverage.b - amount);
                        break;
                    case R:
                        coverage.a = Math.max(0, coverage.a - amount);
                        coverage.r = Math.min(1, coverage.r + amount);
                        coverage.g = Math.max(0, coverage.g - amount);
                        coverage.b = Math.max(0, coverage.b - amount);
                        break;
                    case G:
                        coverage.a = Math.max(0, coverage.a - amount);
                        coverage.r = Math.max(0, coverage.r - amount);
                        coverage.g = Math.min(1, coverage.g + amount);
                        coverage.b = Math.max(0, coverage.b - amount);
                        break;
                    case B:
                        coverage.a = Math.max(0, coverage.a - amount);
                        coverage.r = Math.max(0, coverage.r - amount);
                        coverage.g = Math.max(0, coverage.g - amount);
                        coverage.b = Math.min(1, coverage.b + amount);
                        break;
                }

                /*
                 * Finally set the new pixel 
                 */
                instance.setCoverageAtPixelPosition((int) pixel.x, (int) pixel.y, coverage);
                
                wasNeedsSave = instance.setNeedsSave(true);
            }
        };
        
        
    }
}
