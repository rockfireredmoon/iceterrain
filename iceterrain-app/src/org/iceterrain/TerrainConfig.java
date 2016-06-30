package org.iceterrain;

import java.util.prefs.Preferences;

import org.icelib.AbstractConfig;
import org.icescene.SceneConfig;

public class TerrainConfig extends SceneConfig {
    
    public final static String TERRAIN_EDITOR = "editTerrain";
    public final static String TERRAIN_EDITOR_PAINT_BRUSH_SIZE = TERRAIN_EDITOR + "PaintBrushSize";
    public final static int TERRAIN_EDITOR_PAINT_BRUSH_SIZE_DEFAULT = 5;
    public final static String TERRAIN_EDITOR_PAINT_AMOUNT = TERRAIN_EDITOR + "PaintAmount";
    public final static float TERRAIN_EDITOR_PAINT_AMOUNT_DEFAULT = 10;
    public final static String TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE = TERRAIN_EDITOR + "FlattenBrushSize";
    public final static int TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE_DEFAULT = 5;
    public final static String TERRAIN_EDITOR_SPLAT_BRUSH_SIZE = TERRAIN_EDITOR + "SplatBrushSize";
    public final static int TERRAIN_EDITOR_SPLAT_BRUSH_SIZE_DEFAULT = 5;
    public final static String TERRAIN_EDITOR_SPLAT_RATE = TERRAIN_EDITOR + "SplatRate";
    public final static int TERRAIN_EDITOR_SPLAT_RATE_DEFAULT = 10;
    public final static String TERRAIN_EDITOR_BASELINE = TERRAIN_EDITOR + "BaseLine";
    public final static float TERRAIN_EDITOR_BASELINE_DEFAULT = 0f;
    public final static String TERRAIN_EDITOR_RESTRICT_BASELINE = TERRAIN_EDITOR + "RestrictToBaseLine";
    public final static boolean TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT = true;
    
    public static Object getDefaultValue(String key) {
        return AbstractConfig.getDefaultValue(TerrainConfig.class, key);
    }

    public static Preferences get() {
        return Preferences.userRoot().node(TerrainConstants.APPSETTINGS_NAME).node("game");
    }
}
