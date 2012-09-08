package sculptnect;

import java.nio.FloatBuffer;
import java.util.HashSet;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.vecmath.Point3i;
import javax.vecmath.Tuple3i;
import javax.vecmath.Vector3f;

import com.jogamp.common.nio.Buffers;

public class VoxelGridRender {
	private static final int CELL_SIZE = 64;

	VoxelGrid grid;
	BufferCell[][][] bufferCells;
	HashSet<BufferCell> dirtyCells;

	Tuple3i dimensions = new Point3i();

	int numVertices;
	int[] _buffer = new int[1];

	private class BufferCell {
		// The position of this cell in the buffer grid
		Tuple3i position = new Point3i();

		// The lower indices of the voxel grid for this cell
		Tuple3i lowerIndices = new Point3i();
		// The upper indices of the voxel grid for this cell
		Tuple3i upperIndices = new Point3i();

		// The buffer object name used as handle in OpenGL
		int bufferName;
		// The number of indices this buffer object contains
		int numIndices;

		// Marks this cell as dirty or not
		boolean dirty;
	}

	public VoxelGridRender(GL2 gl, VoxelGrid grid) {
		this.grid = grid;

		// Create buffer for point data
		gl.glGenBuffers(1, _buffer, 0);

		// Calculate buffer dimensions
		dimensions.x = (int) Math.ceil((double) grid.width / CELL_SIZE);
		dimensions.y = (int) Math.ceil((double) grid.height / CELL_SIZE);
		dimensions.z = (int) Math.ceil((double) grid.depth / CELL_SIZE);

		// Create the buffer cells and initialize them
		bufferCells = new BufferCell[dimensions.x][dimensions.y][dimensions.z];
		for (int x = 0; x < dimensions.x; x++) {
			for (int y = 0; y < dimensions.y; y++) {
				for (int z = 0; z < dimensions.z; z++) {
					BufferCell cell = new BufferCell();
					bufferCells[x][y][z] = cell;

					// Set cell position and indices bounds
					cell.position.set(x, y, z);
					cell.lowerIndices.set(x * CELL_SIZE, y * CELL_SIZE, z
							* CELL_SIZE);
					cell.upperIndices.set(
							Math.min((x + 1) * CELL_SIZE, grid.width),
							Math.min((y + 1) * CELL_SIZE, grid.height),
							Math.min((z + 1) * CELL_SIZE, grid.depth));

					// Generate and set a buffer object name for this cell
					int[] buf = new int[1];
					gl.glGenBuffers(1, buf, 0);
					cell.bufferName = buf[0];
				}
			}
		}
	}

	public void markVoxelDirty(int x, int y, int z) {

	}

	public void draw(GL2 gl) {
		// Scale down the grid to fit on screen
		gl.glScalef(2.0f / grid.width, 2.0f / grid.height, 2.0f / grid.depth);

		// Rotate around x and y axes
		gl.glRotatef(grid.rotation.x * 57.2957795f, 1.0f, 0.0f, 0.0f);
		gl.glRotatef(grid.rotation.y * 57.2957795f, 0.0f, 1.0f, 0.0f);

		// Position grid around origin for rotation around center
		gl.glTranslatef(-grid.width / 2.0f, -grid.height / 2.0f,
				-grid.depth / 2.0f);

		// Enable the vertex and normal arrays
		gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL2.GL_NORMAL_ARRAY);

		// Bind buffer containing voxel points and normals
		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, _buffer[0]);

		// Specify vertex and normal data
		int stride = Buffers.SIZEOF_FLOAT * 6;
		gl.glVertexPointer(3, GL2.GL_FLOAT, stride, 0);
		gl.glNormalPointer(GL2.GL_FLOAT, stride, Buffers.SIZEOF_FLOAT * 3);

		// Draw the points
		gl.glDrawArrays(GL2.GL_POINTS, 0, numVertices);

		// Unbind the buffer data
		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);

		// Disable vertex and normal arrays
		gl.glDisableClientState(GL2.GL_NORMAL_ARRAY);
		gl.glDisableClientState(GL2.GL_VERTEX_ARRAY);

	}

	Vector3f normalForVoxel(int x, int y, int z, Vector3f normal) {
		float numAdded = 0;
		normal.set(0, 0, 0);
		for (int xd = -1; xd < 2; xd++) {
			for (int yd = -1; yd < 2; yd++) {
				for (int zd = -1; zd < 2; zd++) {
					int xoff = x + xd;
					int yoff = y + yd;
					int zoff = z + zd;

					if (xoff >= 0 && xoff < grid.width && yoff >= 0
							&& yoff < grid.height && zoff >= 0
							&& zoff < grid.depth) {
						if (grid.isAir(xoff, yoff, zoff)) {
							normal.x += xd;
							normal.y += yd;
							normal.z += zd;
							numAdded++;
						}
					}
				}
			}
		}

		normal.scale(1.0f / numAdded);
		normal.normalize();

		return normal;
	}

	void updateBuffers(GL2 gl) {
		numVertices = 0;
		// Each visible voxel needs 6 floats of storage - 3 coordinate, 3 normal
		int bufferSize = grid.numVisibleVoxels * 6;

		FloatBuffer fb = FloatBuffer.allocate(bufferSize);

		Vector3f normal = new Vector3f();
		for (int x = 0; x < grid.width; x++) {
			for (int y = 0; y < grid.height; y++) {
				for (int z = 0; z < grid.depth; z++) {
					// Skip voxel if it's empty
					if (grid.isAir(x, y, z))
						continue;

					// Determine if voxel is completely inside by
					// examining its neighbors
					for (int i = 0; i < 6; i++) {
						int[] offset = VoxelGrid.offsets[i];
						int xoff = x + offset[0];
						int yoff = y + offset[1];
						int zoff = z + offset[2];

						boolean inside = xoff >= 0 && xoff < grid.width
								&& yoff >= 0 && yoff < grid.height && zoff >= 0
								&& zoff < grid.depth;
						if (!inside
								|| (grid.getVoxel(xoff, yoff, zoff) != grid
										.getVoxel(x, y, z))) {
							numVertices++;

							// put vertex data into buffer
							fb.put(x);
							fb.put(y);
							fb.put(z);

							// put normal for the vertex into buffer
							Vector3f n = normalForVoxel(x, y, z, normal);
							fb.put(n.x);
							fb.put(n.y);
							fb.put(n.z);

							break;
						}
					}
				}
			}
		}

		fb.rewind();

		System.out.println("Buffering " + numVertices * 6 + " floats");

		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, _buffer[0]);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, numVertices * 6
				* Buffers.SIZEOF_FLOAT, fb, GL.GL_STATIC_DRAW);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
	}
}
