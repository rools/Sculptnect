import java.nio.FloatBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.glu.gl2.GLUgl2;
import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

import com.jogamp.common.nio.Buffers;

public class SculptScene implements GLEventListener {
	VoxelGrid _grid;

	Vector2f _rotation = new Vector2f();

	int numVertices;
	int[] _buffer = new int[1];

	@Override
	public void init(GLAutoDrawable drawable) {
		GL2 gl = (GL2) drawable.getGL();

		// Set background color to grayish
		gl.glClearColor(0.25f, 0.25f, 0.25f, 1.0f);

		// Create buffer for point data
		gl.glGenBuffers(1, _buffer, 0);

		// Enable depth testing
		gl.glEnable(GL2.GL_DEPTH_TEST);
		gl.glDepthFunc(GL2.GL_LEQUAL);

		gl.glPointSize(10.0f);

		// Enable lights and set shading
		gl.glShadeModel(GL2.GL_SMOOTH);
		gl.glEnable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_LIGHT1);

		// Define light color and position
		float ambientColor[] = { 0.5f, 0.5f, 0.5f, 1.0f };
		float lightColor1[] = { 0.02f, 0.02f, 0.02f, 1.0f };
		float lightPos1[] = { 100.0f, 100.0f, 100.0f, 1.0f };

		// Set light color and position
		gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, ambientColor, 0);
		gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_AMBIENT, ambientColor, 0);
		gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE, lightColor1, 0);
		gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, lightPos1, 0);

		// Create voxel grid
		_grid = new VoxelGrid(100);
		updateVisibleVoxels(gl);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {

	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width,
			int height) {
		GL2 gl = (GL2) drawable.getGL();

		// Set a perspective projection matrix
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		GLUgl2.createGLU(gl).gluPerspective(90.0f, 1.0f, 0.1f, 1000.0f);

		gl.glMatrixMode(GL2.GL_MODELVIEW);
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		GL2 gl = (GL2) drawable.getGL();

		// Set default vertex color
		gl.glColor3f(0.0f, 0.0f, 0.0f);

		// Clear screen
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
		gl.glLoadIdentity();

		// Move away from center
		gl.glTranslatef(0.0f, 0.0f, -2.0f);

		// Scale down the grid to fit on screen
		gl.glScalef(2.0f / _grid.width, 2.0f / _grid.height, 2.0f / _grid.depth);

		// Rotate around x and y axes
		gl.glRotatef(_rotation.x * 57.2957795f, 1.0f, 0.0f, 0.0f);
		gl.glRotatef(_rotation.y * 57.2957795f, 0.0f, 1.0f, 0.0f);

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

		// Disable lighting to draw axis lines
		gl.glDisable(GL2.GL_LIGHTING);
		
		// Draw coordinate axes
		gl.glBegin(GL2.GL_LINES);
		// X
		gl.glColor3f(1.0f, 0.0f, 0.0f);
		gl.glVertex3f(0.0f, 0.0f, 0.0f);
		gl.glVertex3f(_grid.width, 0.0f, 0.0f);

		// Y
		gl.glColor3f(0.0f, 1.0f, 0.0f);
		gl.glVertex3f(0.0f, 0.0f, 0.0f);
		gl.glVertex3f(0.0f, _grid.height, 0.0f);

		// Z
		gl.glColor3f(0.0f, 0.0f, 1.0f);
		gl.glVertex3f(0.0f, 0.0f, 0.0f);
		gl.glVertex3f(0.0f, 0.0f, _grid.depth);
		gl.glEnd();
		
		// Enable light again
		gl.glEnable(GL2.GL_LIGHTING);
	}

	public void mouseDragged(int prevX, int prevY, int x, int y) {
		_rotation.x += (y - prevY) / 100.0;
		_rotation.y += (x - prevX) / 100.0;

		_rotation.x = Math.max((float) -Math.PI / 2.0f, _rotation.x);
		_rotation.x = Math.min((float) Math.PI / 2.0f, _rotation.x);
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

	void updateVisibleVoxels(GL2 gl) {
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
