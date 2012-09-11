package sculptnect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.glu.gl2.GLUgl2;
import javax.vecmath.Point3i;
import javax.vecmath.Vector2f;

import shape.SphereGenerator;

public class SculptScene implements GLEventListener {
	private final int VOXEL_GRID_SIZE = 300;
	private final short KINECT_NEAR_THRESHOLD = KinectUtils.metersToRawDepth(0.7f);
	private final short KINECT_FAR_THRESHOLD = KinectUtils.metersToRawDepth(1.4f);

	private VoxelGrid _grid;

	private Vector2f _rotation = new Vector2f((float) Math.PI * 0.5f, (float) Math.PI);

	private float depth[][] = new float[640][480];
	private float depthNormals[][][] = new float[640][480][3];

	@Override
	public void init(GLAutoDrawable drawable) {
		GL2 gl = (GL2) drawable.getGL();

		// Set background color to grayish
		gl.glClearColor(0.25f, 0.25f, 0.25f, 1.0f);

		// Enable depth testing
		gl.glEnable(GL2.GL_DEPTH_TEST);
		gl.glDepthFunc(GL2.GL_LEQUAL);

		// Enable lights and set shading
		gl.glShadeModel(GL2.GL_SMOOTH);
		gl.glDisable(GL2.GL_POINT_SMOOTH);
		gl.glEnable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_LIGHT1);
		gl.glEnable(GL2.GL_COLOR_MATERIAL);
		gl.glEnable(GL2.GL_BLEND);
		gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

		// Define light color and position
		float ambientColor[] = { 0.5f, 0.5f, 0.5f, 1.0f };
		float light = 0.0035f;
		float lightColor1[] = { light, light, light, 1.0f };
		float lightPos1[] = { 100000.0f, 100000.0f, 100000.0f, 1.0f };

		// Set light color and position
		gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, ambientColor, 0);
		gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_AMBIENT, ambientColor, 0);
		gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE, lightColor1, 0);
		gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, lightPos1, 0);

		// Create voxel grid
		int size = VOXEL_GRID_SIZE;
		_grid = new VoxelGrid(gl, size);

		// Add sphere shape to voxel grid
		SphereGenerator sphereGenerator = new SphereGenerator(VoxelGrid.VOXEL_GRID_CLAY, new Point3i(size / 2, size / 2, size / 2), size / 2 - 2);
		_grid.insertShape(sphereGenerator);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {

	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		GL2 gl = (GL2) drawable.getGL();

		// Set a perspective projection matrix
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		GLUgl2.createGLU(gl).gluPerspective(90.0f, (float) width / height, 0.1f, 1000.0f);

		gl.glMatrixMode(GL2.GL_MODELVIEW);
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		GL2 gl = (GL2) drawable.getGL();

		// Remove some material randomly
		removeRandomSphere();

		// Set default vertex color
		gl.glColor3f(0.0f, 0.0f, 0.0f);

		// Clear screen
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

		gl.glLoadIdentity();

		// Move away from center
		gl.glTranslatef(0.0f, 0.0f, -3.5f);

		// Scale down the grid to fit on screen
		gl.glScalef(2.0f / _grid.width, 2.0f / _grid.height, 2.0f / _grid.depth);

		// Rotate around x and y axes
		gl.glRotatef(_rotation.x * 57.2957795f, 1.0f, 0.0f, 0.0f);
		gl.glRotatef(_rotation.y * 57.2957795f, 0.0f, 1.0f, 0.0f);

		// Draw voxel grid
		gl.glPointSize(4.0f);
		gl.glPushMatrix();
		gl.glTranslatef(-VOXEL_GRID_SIZE * 0.5f, -VOXEL_GRID_SIZE * 0.5f, -VOXEL_GRID_SIZE * 0.5f);
		_grid.draw(gl);
		gl.glPopMatrix();

		// Draw Kinect depth map
		gl.glPointSize(1.0f);
		gl.glPushMatrix();
		gl.glTranslatef(-320.0f, -240.0f, -400.0f);
		gl.glColor4f(0.3f, 0.0f, 0.0f, 1.0f);
		gl.glBegin(GL.GL_POINTS);
		for (int x = 0; x < 640; ++x) {
			for (int y = 0; y < 480; ++y) {
				if (depth[x][y] > 0.0f) {
					gl.glColor4f(0.3f, 0.0f, 0.0f, depth[x][y]);
					gl.glNormal3fv(depthNormals[x][y], 0);
					gl.glVertex3f(x, 480 - y, depth[x][y] * 200);
				}
			}
		}
		gl.glEnd();
		gl.glPopMatrix();

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

	public void removeRandomSphere() {
		int size = (int) (Math.random() * 30);
		Point3i center = new Point3i(
		//
				(int) (Math.random() * _grid.width), (int) (Math.random() * _grid.height), (int) (Math.random() * _grid.depth));

		// Add sphere shape to voxel grid
		SphereGenerator sphereGenerator = new SphereGenerator(VoxelGrid.VOXEL_GRID_AIR, center, size);
		_grid.insertShape(sphereGenerator);
	}

	public void updateKinect(ByteBuffer depthBuffer) {
		depthBuffer.rewind();
		depthBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float max = 0.0f;
		float min = 100.0f;
		for (int y = 0; y < 480; ++y) {
			for (int x = 0; x < 640; ++x) {
				short rawDepth = depthBuffer.getShort();
				if (rawDepth < KINECT_NEAR_THRESHOLD || rawDepth > KINECT_FAR_THRESHOLD)
					depth[x][y] = 0.0f;
				else
					depth[x][y] = (KINECT_FAR_THRESHOLD - rawDepth) / (float) (KINECT_FAR_THRESHOLD - KINECT_NEAR_THRESHOLD);

				if (depth[x][y] > max)
					max = depth[x][y];
				if (depth[x][y] < min)
					min = depth[x][y];

			}
		}

		for (int x = 1; x < 639; ++x) {
			for (int y = 1; y < 479; ++y) {
				depthNormals[x][y][0] = depth[x + 1][y] - depth[x - 1][y];
				depthNormals[x][y][1] = depth[x][y + 1] - depth[x][y - 1];
				depthNormals[x][y][2] = 0.01f;

				float l = (float) Math.sqrt(depthNormals[x][y][0] * depthNormals[x][y][0] + depthNormals[x][y][1] * depthNormals[x][y][1] + depthNormals[x][y][2] * depthNormals[x][y][2]);

				depthNormals[x][y][0] /= l;
				depthNormals[x][y][1] /= l;
				depthNormals[x][y][2] /= l;
			}
		}
	}
}
