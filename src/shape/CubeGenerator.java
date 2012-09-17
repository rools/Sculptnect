package shape;

import javax.vecmath.Point3i;

import sculptnect.VoxelGrid;

public class CubeGenerator extends ShapeGenerator {
	int size;
	byte value;

	public CubeGenerator(byte value, Point3i center, int size) {
		super(center, new Point3i((int) size * 2, (int) size * 2, (int) size * 2));
		this.size = size;
		this.value = value;
	}

	@Override
	public byte valueForVoxel(int x, int y, int z) {
		int xp = x - getCenter().x;
		int yp = y - getCenter().y;
		int zp = z - getCenter().z;

		if (Math.abs(xp) < size || Math.abs(yp) < size || Math.abs(zp) < size) {
			return VoxelGrid.VOXEL_GRID_CLAY;
		} else {
			return VoxelGrid.VOXEL_GRID_NO_CHANGE;
		}
	}

	public void setValue(byte value) {
		this.value = value;
	}
}
