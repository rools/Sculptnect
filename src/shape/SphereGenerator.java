package shape;

import javax.vecmath.Point3i;

import sculptnect.VoxelGrid;

public class SphereGenerator extends ShapeGenerator {
	float radius;
	byte value;

	public SphereGenerator(byte value, Point3i center, float radius) {
		super(center, new Point3i((int) radius * 2, (int) radius * 2,
				(int) radius * 2));
		this.radius = radius;
		this.value = value;
	}

	@Override
	public byte valueForVoxel(int x, int y, int z) {
		float xp = x - getCenter().x;
		float yp = y - getCenter().y;
		float zp = z - getCenter().z;

		float r = xp * xp + yp * yp + zp * zp;
		if (r < radius * radius) {
			return value;
		} else {
			return VoxelGrid.VOXEL_GRID_NO_CHANGE;
		}
	}

	public void setValue(byte value) {
		this.value = value;
	}
}
