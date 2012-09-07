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

		// TEMP: Initializing the voxel grid to contain a sphere shape
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < depth; z++) {
					float xp = x / (float) width - 0.5f;
					float yp = y / (float) height - 0.5f;
					float zp = z / (float) depth - 0.5f;

					float r = xp * xp + yp * yp + zp * zp;
					if (r < 0.10f) {
						setVoxel(x, y, z, VOXEL_GRID_CLAY);
						numVisibleVoxels++;
					}
				}
			}
		}
	}

	public byte getVoxel(int x, int y, int z) {
		return _voxels[x][y][z];
	}

	public void setVoxel(int x, int y, int z, byte value) {
		_voxels[x][y][z] = value;
	}

	public boolean isAir(int x, int y, int z) {
		return _voxels[x][y][z] == VOXEL_GRID_AIR;
	}
}
