package sculptnect;

import javax.media.opengl.GL2;
import javax.vecmath.Point2f;
import javax.vecmath.Tuple2f;
import javax.vecmath.Tuple3i;

import shape.ShapeGenerator;

public class VoxelGrid {
	public static final byte VOXEL_GRID_NO_CHANGE = -1;
	public static final byte VOXEL_GRID_AIR = 0;
	public static final byte VOXEL_GRID_CLAY = 1;

	private byte[][][] _voxels;

	int width, height, depth;

	Tuple2f rotation = new Point2f();
	VoxelGridRender render;

	public static int[][] offsets = {
			//
			{ -1, 0, 0 }, //
			{ 1, 0, 0 }, //
			{ 0, -1, 0 }, //
			{ 0, 1, 0 }, //
			{ 0, 0, -1 }, //
			{ 0, 0, 1 }, //
	};

	public VoxelGrid(GL2 gl, int size) {
		this(gl, size, size, size);
	}

	public VoxelGrid(GL2 gl, int width, int height, int depth) {
		this.width = width;
		this.height = height;
		this.depth = depth;
		_voxels = new byte[width][height][depth];

		// Create render
		render = new VoxelGridRender(gl, this);
	}

	public byte getVoxel(int x, int y, int z) {
		return _voxels[x][y][z];
	}

	public void setVoxel(int x, int y, int z, byte value) {
		// Inform render that this voxel changed
		render.markVoxelDirty(x, y, z);

		_voxels[x][y][z] = value;
	}

	public boolean isAir(int x, int y, int z) {
		return _voxels[x][y][z] == VOXEL_GRID_AIR;
	}

	public void clear() {
		// Iterate through all voxels and set them all to air
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < depth; z++) {
					setVoxel(x, y, z, VOXEL_GRID_AIR);
				}
			}
		}
	}

	public void insertShape(ShapeGenerator generator) {
		Tuple3i center = generator.getCenter();
		Tuple3i size = generator.getSize();

		// Calculate bounds for the shape to insert
		int xmin = Math.max(0, center.x - size.x / 2);
		int xmax = Math.min(width, center.x + size.x / 2);
		int ymin = Math.max(0, center.y - size.y / 2);
		int ymax = Math.min(height, center.y + size.y / 2);
		int zmin = Math.max(0, center.z - size.z / 2);
		int zmax = Math.min(depth, center.z + size.z / 2);

		// Iterate through the bounds and insert generated value
		for (int x = xmin; x < xmax; x++) {
			for (int y = ymin; y < ymax; y++) {
				for (int z = zmin; z < zmax; z++) {
					byte value = generator.valueForVoxel(x, y, z);
					if (value != VOXEL_GRID_NO_CHANGE) {
						setVoxel(x, y, z, value);
					}
				}
			}
		}
	}

	public void setXRotation(float xRotation) {
		rotation.x = xRotation;
	}

	public void setYRotation(float yRotation) {
		rotation.y = yRotation;
	}

	public void draw(GL2 gl) {
		render.updateDirtyCells(gl);

		render.draw(gl);
	}

}
