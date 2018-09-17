package org.iceterrain;

import org.icelib.PageLocation;
import org.icescene.configuration.AbstractPropertiesConfiguration;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector2f;

/**
 * Loads and stores a Terrain template configuration file. Each template will have one of
 * these for the defaults, or any individual tile can have it's own overrides.
 */
public class TerrainPageConfiguration extends AbstractPropertiesConfiguration<TerrainPageConfiguration> {

    public final static String PAGE_SOURCE_HEIGHTMAP = "Heightmap";

    public static class LiquidPlaneConfiguration {

        private float elevation;
        private String material;
        private final float defaultElevation;
        private final String defaultMaterial;

        public LiquidPlaneConfiguration(float elevation, String material) {
            this.elevation = this.defaultElevation = elevation;
            this.material = this.defaultMaterial = material;
        }

        public String getMaterial() {
            return material;
        }

        public float getElevation() {
            return elevation;
        }

        public void setElevation(float elevation) {
            this.elevation = elevation;
        }

        public void reset() {
            this.elevation = defaultElevation;
            this.material = defaultMaterial;
        }

        public void setMaterial(String material) {
            this.material = material;
        }
    }
    private String customMaterialName;
    private String textureBaseFormat;
    private String textureCoverageFormat;
    private String textureSplatting0;
    private String textureSplatting1;
    private String textureSplatting2;
    private String textureSplatting3;
    private String perPageConfig;
    private String detailTexture;
    private int detailTile;
    private int asyncLoadRate;
    private int livePageMargin;
    private String pageSource;
    private int pageMaxX;
    private int pageMaxZ;
    private int heightmapRawSize;
    private int heightmapRawBpp;
    private int pageSize;
    private int tileSize;
    private int maxPixelError;
    private int pageWorldX;
    private int pageWorldZ;
    private int maxHeight;
    private int maxMipMapLevel;
    private boolean vertexNormals;
    private boolean vertexColors;
    private boolean useTriStrips;
    private boolean vertexProgramMorph;
    private float lodMorphStart;
    private String morphLODFactorParamName;
    private int morphLODFactorParamIndex;
    private String heightmapImageFormat;
    // Settable
    private LiquidPlaneConfiguration liquidPlane;

    public TerrainPageConfiguration(String resourceName, AssetManager classLoader) {
        this(resourceName, classLoader, null);
    }

    public TerrainPageConfiguration(String resourceName, AssetManager assetManager, TerrainPageConfiguration base) {
        super(assetManager, resourceName, base);

        // Set up either a new property sheet or copy the defaults
        if (base != null) {
            if (base.perPageConfig == null) {
                throw new IllegalArgumentException("Base template configuration must have a PerPageConfig set");
            }
        }
        load();
    }

    public final void load() {
        customMaterialName = get("CustomMaterialName");
        textureBaseFormat = get("Texture.Base");
        textureCoverageFormat = get("Texture.Coverage");
        textureSplatting0 = get("Texture.Splatting0");
        textureSplatting1 = get("Texture.Splatting1");
        textureSplatting2 = get("Texture.Splatting2");
        textureSplatting3 = get("Texture.Splatting3");
        perPageConfig = get("PerPageConfig");
        detailTexture = get("DetailTexture");
        asyncLoadRate = getInt("AsyncLoadRate");
        livePageMargin = getInt("LivePageMargin");
        detailTile = getInt("DetailTile");
        pageSource = get("PageSource");
        heightmapImageFormat = get("Heightmap.image");
        pageMaxX = getInt("PageMaxX");
        pageMaxZ = getInt("PageMaxZ");
        heightmapRawSize = getInt("Heightmap.raw.size");
        heightmapRawBpp = getInt("Heightmap.raw.bpp");
        pageSize = getInt("PageSize");
        tileSize = getInt("TileSize");
        maxPixelError = getInt("MaxPixelError");
        pageWorldX = getInt("PageWorldX");
        pageWorldZ = getInt("PageWorldZ");
        maxHeight = getInt("MaxHeight");
        maxMipMapLevel = getInt("MaxMipMapLevel");
        vertexNormals = getBoolean("VertexNormals");
        vertexColors = getBoolean("VertexColors");
        useTriStrips = getBoolean("UseTriStrips");
        vertexProgramMorph = getBoolean("VertexProgramMorph");
        lodMorphStart = getFloat("LODMorphStart");
        morphLODFactorParamName = get("MorphLODFactorParamName");
        morphLODFactorParamIndex = getInt("MorphLODFactorParamIndex");
        if (getBackingObject().containsKey("WaterPlane.Material")) {
            liquidPlane = new LiquidPlaneConfiguration(getFloat("WaterPlane.Elevation"), get("WaterPlane.Material"));
        } else {
            liquidPlane = null;
        }
    }

    public void setLiquidPlaneConfiguration(LiquidPlaneConfiguration liquidPlane) {
        this.liquidPlane = liquidPlane;
    }

    public boolean isIn(PageLocation location) {
        return location.x >= 0 && location.y >= 0 && location.x < getPageMaxX() && location.y < getPageMaxZ();
    }

    /**
     * Get how much to scale the terrain to make it fit one page.
     *
     * NOTE, this may need expanding if it turns out PF terrain can be odd sizes (I don't
     * think it is)
     *
     * @return
     */
    public float getPageScale() {
        return getPageWorldX() / (getPageSize() - 1);
    }

    /**
     * Conver X and Z world coordinates to X and Y tile co-ordinates.
     *
     * @param worldCoords world
     * @return X and Y tile co-ordinates
     */
    public PageLocation getTile(Vector2f worldCoords) {
        return new PageLocation((int) (worldCoords.x / (float) pageWorldX), (int) (worldCoords.y / (float) pageWorldZ));
    }

    public Vector2f getPositionWithinTile(PageLocation tile, Vector2f worldCoords) {
        return new Vector2f(worldCoords.x - (tile.x * pageWorldX), worldCoords.y - (tile.y * pageWorldZ));
    }

    public LiquidPlaneConfiguration getLiquidPlaneConfiguration() {
        return liquidPlane;
    }

    public void setWaterPlane(LiquidPlaneConfiguration waterPlane) {
        this.liquidPlane = waterPlane;
    }

    public String getCustomMaterialName() {
        return customMaterialName;
    }

    public String getTextureBaseFormat() {
        return textureBaseFormat;
    }

    public String getTextureCoverageFormat() {
        return textureCoverageFormat;
    }

    public String getTextureSplatting0() {
        return textureSplatting0;
    }

    public String getTextureSplatting1() {
        return textureSplatting1;
    }

    public String getTextureSplatting2() {
        return textureSplatting2;
    }

    public String getTextureSplatting3() {
        return textureSplatting3;
    }

    public String getPerPageConfig() {
        return perPageConfig;
    }

    public String getDetailTexture() {
        return detailTexture;
    }

    public int getAsyncLoadRate() {
        return asyncLoadRate;
    }

    public int getLivePageMargin() {
        return livePageMargin;
    }

    public int getDetailTile() {
        return detailTile;
    }

    public String getPageSource() {
        return pageSource;
    }

    public int getPageMaxX() {
        return pageMaxX;
    }

    public int getPageMaxZ() {
        return pageMaxZ;
    }

    public int getHeightmapRawSize() {
        return heightmapRawSize;
    }

    public int getHeightmapRawBpp() {
        return heightmapRawBpp;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTileSize() {
        return tileSize;
    }

    public int getMaxPixelError() {
        return maxPixelError;
    }

    public int getPageWorldX() {
        return pageWorldX;
    }

    public int getPageWorldZ() {
        return pageWorldZ;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public int getMaxMipMapLevel() {
        return maxMipMapLevel;
    }

    public boolean isVertexNormals() {
        return vertexNormals;
    }

    public boolean isVertexColors() {
        return vertexColors;
    }

    public boolean isUseTriStrips() {
        return useTriStrips;
    }

    public boolean isVertexProgramMorph() {
        return vertexProgramMorph;
    }

    public float getLodMorphStart() {
        return lodMorphStart;
    }

    public String getMorphLODFactorParamName() {
        return morphLODFactorParamName;
    }

    public int getMorphLODFactorParamIndex() {
        return morphLODFactorParamIndex;
    }

    public String getHeightmapImageFormat() {
        return heightmapImageFormat;
    }

    @Override
    protected void fill(boolean partial) {
        put("Texture.Splatting0", textureSplatting0);
        put("Texture.Splatting1", textureSplatting1);
        put("Texture.Splatting2", textureSplatting2);
        put("Texture.Splatting3", textureSplatting3);


        if (base == null) {

            // Only for default
            put("CustomMaterialName", customMaterialName);
            put("Texture.Base", textureBaseFormat);
            put("Texture.Coverage", textureCoverageFormat);
            put("PerPageConfig", perPageConfig);
            put("DetailTexture", detailTexture);
            put("AsyncLoadRate", asyncLoadRate);
            put("LivePageMargin", livePageMargin);
            put("DetailTile", detailTile);
            put("PageSource", pageSource);
            put("Heightmap.image", heightmapImageFormat);
            put("PageMaxX", pageMaxX);
            put("PageMaxZ", pageMaxZ);
            put("Heightmap.raw.size", heightmapRawSize);
            put("Heightmap.raw.bpp", heightmapRawBpp);
            put("PageSize", pageSize);
            put("TileSize", tileSize);
            put("MaxPixelError", maxPixelError);
            put("PageWorldX", pageWorldX);
            put("PageWorldZ", pageWorldZ);
            put("MaxHeight", maxHeight);
            put("MaxMipMapLevel", maxMipMapLevel);
            put("VertexNormals", vertexNormals);
            put("VertexColors", vertexColors);
            put("UseTriStrips", useTriStrips);
            put("VertexProgramMorph", vertexProgramMorph);
            put("LODMorphStart", lodMorphStart);
            put("MorphLODFactorParamName", morphLODFactorParamName);
            put("MorphLODFactorParamIndex", morphLODFactorParamIndex);
        }

        if (base != null) {
            // Only for per page config
            if (liquidPlane != null) {
                put("WaterPlane.Material", liquidPlane.getMaterial());
                put("WaterPlane.Elevation", liquidPlane.getElevation());
            } else {
                getBackingObject().remove("WaterPlane.Material");
                getBackingObject().remove("WaterPlane.Elevation");
            }
        }
    }
}
