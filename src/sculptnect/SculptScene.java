package sculptnect;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.glu.gl2.GLUgl2;
import javax.vecmath.Point3i;
import javax.vecmath.Vector2f;

import shape.SphereGenerator;

public class SculptScene implements GLEventListener {
	VoxelGrid _grid;

	Vector2f _rotation = new Vector2f();

	@Override
	public void init(GLAutoDrawable drawable) {
		GL2 gl = (GL2) drawable.getGL();

		// Set background color to grayish
		gl.glClearColor(0.25f, 0.25f, 0.25f, 1.0f);

		// Enable depth testing
		gl.glEnable(GL2.GL_DEPTH_TEST);
		gl.glDepthFunc(GL2.GL_LEQUAL);

		gl.glPointSize(4.0f);

		// Enable lights and set shading
		gl.glShadeModel(GL2.GL_SMOOTH);
		gl.glEnable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_LIGHT1);

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
		int size = 512;
		_grid = new VoxelGrid(gl, size);

		// Add sphere shape to voxel grid
		SphereGenerator sphereGenerator = new SphereGenerator(
				VoxelGrid.VOXEL_GRID_CLAY, new Point3i(size / 2, size / 2,
						size / 2), size / 2 - 2);
		_grid.insertShape(sphereGenerator);
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

		// Remove some material randomly
		removeRandomSphere();

		// Set default vertex color
		gl.glColor3f(0.0f, 0.0f, 0.0f);

		// Clear screen
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
		gl.glLoadIdentity();

		// Move away from center
		gl.glTranslatef(0.0f, 0.0f, -2.0f);

		// Update voxelgrid rotation
		_grid.setXRotation(_rotation.x);
		_grid.setYRotation(_rotation.y);

		// Draw voxel grid
		_grid.draw(gl);

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
				(int) (Math.random() * _grid.width),
				(int) (Math.random() * _grid.height),
				(int) (Math.random() * _grid.depth));

		// Add sphere shape to voxel grid
		SphereGenerator sphereGenerator = new SphereGenerator(
				VoxelGrid.VOXEL_GRID_AIR, center, size);
		_grid.insertShape(sphereGenerator);
	}

}
