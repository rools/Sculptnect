package sculptnect;

import javax.media.opengl.GL2;
import javax.vecmath.Point2f;
import javax.vecmath.Tuple2f;

import shape.ShapeGenerator;

public class VoxelGrid {
	public static final byte VOXEL_GRID_AIR = 0;
	public static final byte VOXEL_GRID_CLAY = 1;

	private byte[][][] _voxels;

	int width, height, depth;
	public int numVisibleVoxels;

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
		if (_voxels[x][y][z] == VOXEL_GRID_AIR && value != VOXEL_GRID_AIR) {
			numVisibleVoxels++;
		}
		_voxels[x][y][z] = value;
	}

	public boolean isAir(int x, int y, int z) {
		return _voxels[x][y][z] == VOXEL_GRID_AIR;
	}

	public void clear() {
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < depth; z++) {
					setVoxel(x, y, z, VOXEL_GRID_AIR);
				}
			}
		}
	}

	public void insertShape(ShapeGenerator generator) {
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < depth; z++) {
					setVoxel(x, y, z, generator.valueForVoxel(x, y, z));
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
		render.updateBuffers(gl);
		
		render.draw(gl);
	}

}
