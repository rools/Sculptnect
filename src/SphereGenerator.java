import javax.vecmath.Point3i;

public class SphereGenerator extends ShapeGenerator {
	float _radius;

	public SphereGenerator(Point3i center, float radius) {
		super(center, new Point3i());
		_radius = radius;
	}

	@Override
	public byte valueForVoxel(int x, int y, int z) {
		float xp = x - getCenter().x;
		float yp = y - getCenter().y;
		float zp = z - getCenter().z;

		float r = xp * xp + yp * yp + zp * zp;
		if (r < _radius * _radius) {
			return VoxelGrid.VOXEL_GRID_CLAY;
		} else {
			return VoxelGrid.VOXEL_GRID_AIR;
		}
	}

}
