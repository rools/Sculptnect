import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;

public class Scene implements GLEventListener {

	@Override
	public void init(GLAutoDrawable arg0) {

	}

	@Override
	public void dispose(GLAutoDrawable arg0) {

	}

	@Override
	public void display(GLAutoDrawable arg0) {
		GL2 gl = (GL2) arg0.getGL();

		gl.glClear(GL.GL_COLOR_BUFFER_BIT);
	}

	@Override
	public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3,
			int arg4) {

	}

}
