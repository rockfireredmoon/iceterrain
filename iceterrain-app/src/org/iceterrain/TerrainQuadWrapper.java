package org.iceterrain;

import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.terrain.geomipmap.TerrainQuad;

public class TerrainQuadWrapper extends TerrainQuad {

	private TerrainInstance instance;

	public TerrainQuadWrapper(TerrainInstance instance) {
		this.instance = instance;
	}

	public TerrainQuadWrapper(TerrainInstance instance, String name, int patchSize, int totalSize, float[] heightMap) {
		super(name, patchSize, totalSize, heightMap);
		this.instance = instance;
	}

	public TerrainQuadWrapper(TerrainInstance instance, String name, int patchSize, int quadSize, Vector3f scale,
			float[] heightMap, int totalSize, Vector2f offset, float offsetAmount) {
		super(name, patchSize, quadSize, scale, heightMap, totalSize, offset, offsetAmount);
		this.instance = instance;
	}

	public void setHeights(float[] heightmap) {
		Material mat = getMaterial();
		detachAllChildren();
        split(patchSize, heightmap);
        setMaterial(mat);
        updateNormals();
	}

	public TerrainInstance getInstance() {
		return instance;
	}

}
