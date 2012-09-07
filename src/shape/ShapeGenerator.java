package shape;

import javax.vecmath.Point3i;

public abstract class ShapeGenerator {
	Point3i _center;
	Point3i _size;

	public ShapeGenerator(Point3i center, Point3i size) {
		_center = new Point3i(center);
		_size = new Point3i(size);
	}

	abstract public byte valueForVoxel(int x, int y, int z);

	public Point3i getCenter() {
		return _center;
	}
	
	public Point3i getSize() {
		return _size;
	}
}
