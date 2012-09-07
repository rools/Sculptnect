public class VoxelGrid {
	public static final byte VOXEL_GRID_AIR = 0;
	public static final byte VOXEL_GRID_CLAY = 1;

	private byte[][][] _voxels;

	int width, height, depth;
	public int numVisibleVoxels;

	public static int[][] offsets = {
			//
			{ -1, 0, 0 }, //
			{ 1, 0, 0 }, //
			{ 0, -1, 0 }, //
			{ 0, 1, 0 }, //
			{ 0, 0, -1 }, //
			{ 0, 0, 1 }, //
	};

	public VoxelGrid(int size) {
		this(size, size, size);
	}

	public VoxelGrid(int width, int height, int depth) {
		this.width = width;
		this.height = height;
		this.depth = depth;
		_voxels = new byte[width][height][depth];
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
}
