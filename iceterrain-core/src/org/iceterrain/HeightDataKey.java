package org.iceterrain;

public class HeightDataKey {
	
	public enum Op {
		adjust, set
	}

	private TerrainInstance terrainInstance;
	private Op op;

	public HeightDataKey(Op op, TerrainInstance terrainInstance) {
		this.op = op;
		this.terrainInstance = terrainInstance;
	}

	public TerrainInstance getTerrainInstance() {
		return terrainInstance;
	}

	public void setTerrainInstance(TerrainInstance terrainInstance) {
		this.terrainInstance = terrainInstance;
	}

	public Op getOp() {
		return op;
	}

	public void setOp(Op op) {
		this.op = op;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((op == null) ? 0 : op.hashCode());
		result = prime * result + ((terrainInstance == null) ? 0 : terrainInstance.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		HeightDataKey other = (HeightDataKey) obj;
		if (op != other.op)
			return false;
		if (terrainInstance == null) {
			if (other.terrainInstance != null)
				return false;
		} else if (!terrainInstance.equals(other.terrainInstance))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "HeightDataKey [terrainInstance=" + terrainInstance + ", op=" + op + "]";
	}

}
