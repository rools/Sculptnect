package sculptnect;

import java.nio.FloatBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.vecmath.Tuple3i;
import javax.vecmath.Vector3f;

import com.jogamp.common.nio.Buffers;

public class VoxelGridRender {
	VoxelGrid _grid;

	int numVertices;
	int[] _buffer = new int[1];
	int _numSegments = 10;

	private class BufferCell {
		// The position of this cell in the buffer grid
		Tuple3i position;
		// The buffer object name used as handle in OpenGL
		int bufferName;
		// The number of indices this buffer object contains
		int numIndices;
	}

	public VoxelGridRender(GL2 gl, VoxelGrid grid) {
		_grid = grid;

		// Create buffer for point data
		gl.glGenBuffers(1, _buffer, 0);
	}

	public void markVoxelDirty(int x, int y, int z) {

	}

	public void draw(GL2 gl) {
		// Scale down the grid to fit on screen
		gl.glScalef(2.0f / _grid.width, 2.0f / _grid.height, 2.0f / _grid.depth);

		// Rotate around x and y axes
		gl.glRotatef(_grid.rotation.x * 57.2957795f, 1.0f, 0.0f, 0.0f);
		gl.glRotatef(_grid.rotation.y * 57.2957795f, 0.0f, 1.0f, 0.0f);

		// Position grid around origin for rotation around center
		gl.glTranslatef(-_grid.width / 2.0f, -_grid.height / 2.0f,
				-_grid.depth / 2.0f);

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

					if (xoff >= 0 && xoff < _grid.width && yoff >= 0
							&& yoff < _grid.height && zoff >= 0
							&& zoff < _grid.depth) {
						if (_grid.isAir(xoff, yoff, zoff)) {
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
		int bufferSize = _grid.numVisibleVoxels * 6;

		FloatBuffer fb = FloatBuffer.allocate(bufferSize);

		for (int x = 0; x < _grid.width; x++) {
			for (int y = 0; y < _grid.height; y++) {
				for (int z = 0; z < _grid.depth; z++) {
					// Skip voxel if it's empty
					if (_grid.isAir(x, y, z))
						continue;

					// Determine if voxel is completely inside by
					// examining its neighbors
					for (int i = 0; i < 6; i++) {
						int[] offset = VoxelGrid.offsets[i];
						int xoff = x + offset[0];
						int yoff = y + offset[1];
						int zoff = z + offset[2];

						boolean inside = xoff >= 0 && xoff < _grid.width
								&& yoff >= 0 && yoff < _grid.height
								&& zoff >= 0 && zoff < _grid.depth;
						if (!inside
								|| (_grid.getVoxel(xoff, yoff, zoff) != _grid
										.getVoxel(x, y, z))) {
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
