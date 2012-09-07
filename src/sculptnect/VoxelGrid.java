package sculptnect;

import java.nio.FloatBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.vecmath.Point2f;
import javax.vecmath.Tuple2f;
import javax.vecmath.Vector3f;

import shape.ShapeGenerator;

import com.jogamp.common.nio.Buffers;

public class VoxelGrid {
	public static final byte VOXEL_GRID_AIR = 0;
	public static final byte VOXEL_GRID_CLAY = 1;

	private byte[][][] _voxels;

	int width, height, depth;
	public int numVisibleVoxels;

	Tuple2f rotation = new Point2f();

	int numVertices;
	int[] _buffer = new int[1];

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

		// Create buffer for point data
		gl.glGenBuffers(1, _buffer, 0);
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
		// Scale down the grid to fit on screen
		gl.glScalef(2.0f / width, 2.0f / height, 2.0f / depth);

		// Rotate around x and y axes
		gl.glRotatef(rotation.x * 57.2957795f, 1.0f, 0.0f, 0.0f);
		gl.glRotatef(rotation.y * 57.2957795f, 0.0f, 1.0f, 0.0f);

		// Position grid around origin for rotation around center
		gl.glTranslatef(-width / 2.0f, -height / 2.0f, -depth / 2.0f);

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

	Vector3f normalForVoxel(int x, int y, int z) {
		Vector3f normalTemp = new Vector3f();

		float numAdded = 0;
		for (int xd = -1; xd < 2; xd++) {
			for (int yd = -1; yd < 2; yd++) {
				for (int zd = -1; zd < 2; zd++) {
					int xoff = x + xd;
					int yoff = y + yd;
					int zoff = z + zd;

					if (xoff >= 0 && xoff < width && yoff >= 0 && yoff < height
							&& zoff >= 0 && zoff < depth) {
						if (isAir(xoff, yoff, zoff)) {
							normalTemp.x += xd;
							normalTemp.y += yd;
							normalTemp.z += zd;
							numAdded++;
						}
					}
				}
			}
		}

		normalTemp.scale(1.0f / numAdded);
		normalTemp.normalize();

		return normalTemp;
	}

	void updateBuffers(GL2 gl) {
		numVertices = 0;
		// Each visible voxel needs 6 floats of storage - 3 coordinate, 3 normal
		int bufferSize = numVisibleVoxels * 6;

		FloatBuffer fb = FloatBuffer.allocate(bufferSize);

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < depth; z++) {
					// Skip voxel if it's empty
					if (isAir(x, y, z))
						continue;

					// Determine if voxel is completely inside by
					// examining its neighbors
					for (int i = 0; i < 6; i++) {
						int[] offset = VoxelGrid.offsets[i];
						int xoff = x + offset[0];
						int yoff = y + offset[1];
						int zoff = z + offset[2];

						boolean inside = xoff >= 0 && xoff < width && yoff >= 0
								&& yoff < height && zoff >= 0 && zoff < depth;
						if (!inside
								|| (getVoxel(xoff, yoff, zoff) != getVoxel(x,
										y, z))) {
							numVertices++;

							// put vertex data into buffer
							fb.put(x);
							fb.put(y);
							fb.put(z);

							// put normal for the vertex into buffer
							Vector3f n = normalForVoxel(x, y, z);
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
