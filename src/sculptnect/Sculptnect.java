package sculptnect;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Date;

import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLProfile;

import org.openkinect.freenect.Context;
import org.openkinect.freenect.DepthHandler;
import org.openkinect.freenect.Device;
import org.openkinect.freenect.FrameMode;
import org.openkinect.freenect.Freenect;

import com.jogamp.newt.event.KeyAdapter;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.MouseAdapter;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.util.FPSAnimator;

public class Sculptnect {
	private Context kinectContext = null;
	private Device kinect = null;

	private boolean dump = false;

	public Sculptnect() {
		// Set up Kinect
		kinectContext = Freenect.createContext();
		if (kinectContext.numDevices() > 0) {
			kinect = kinectContext.openDevice(0);
		} else {
			System.err.println("Error, no Kinect detected.");
		}

		GLProfile.initSingleton();
		GLProfile glp = GLProfile.getDefault();

		// Set the OpenGL canvas creation parameters
		GLCapabilities caps = new GLCapabilities(glp);
		caps.setRedBits(8);
		caps.setGreenBits(8);
		caps.setBlueBits(8);

		final SculptScene scene = new SculptScene();

		GLWindow window = GLWindow.create(caps);
		window.addGLEventListener(scene);

		// Add and start a display link
		final FPSAnimator animator = new FPSAnimator(window, 60, true);

		window.setSize(800, 800);
		window.setVisible(true);
		animator.start();

		// Add listener to respond to window closing
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowDestroyNotify(WindowEvent arg0) {
				exit(0);
			}
		});

		// Add key listener
		window.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				// Exit if ESC was pressed
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					exit(0);
				}
				if (e.getKeyCode() == KeyEvent.VK_SPACE) {
					dump = true;
				}
			}
		});

		// Create mouse listener
		MouseAdapter mouseAdapter = new MouseAdapter() {
			int prevX, prevY;

			@Override
			public void mousePressed(MouseEvent e) {
				prevX = e.getX();
				prevY = e.getY();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				scene.mouseDragged(prevX, prevY, e.getX(), e.getY());
				prevX = e.getX();
				prevY = e.getY();
			}
		};

		// Add the mouse listener
		window.addMouseListener(mouseAdapter);

		if (kinect != null) {
			// kinect.setDepthFormat(DepthFormat.);
			kinect.startDepth(new DepthHandler() {
				@Override
				public void onFrameReceived(FrameMode arg0, ByteBuffer arg1, int arg2) {
					if (dump) {
						// Dump a raw depth image
						arg1.rewind();
						FileOutputStream fos = null;
						try {
							fos = new FileOutputStream(new Date().getTime() + ".raw");
							while (arg1.remaining() > 0) {
								fos.write(arg1.get());
							}
							fos.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
						dump = false;
					}

					scene.updateKinect(arg1);
				}
			});
		} else {
			// Load a placeholder depth image for testing without Kinect
			try {
				InputStream is = getClass().getClassLoader().getResourceAsStream("kinect_depth.raw");
				byte depth[] = new byte[640 * 480 * 2];
				is.read(depth);
				scene.updateKinect(ByteBuffer.wrap(depth));

			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	public void exit(int exitCode) {
		// Clean up Kinect before exiting
		cleanup();

		System.exit(exitCode);
	}

	public void cleanup() {
		// Shut down Kinect
		if (kinectContext != null) {
			if (kinect != null) {
				kinect.close();
			}
		}
		kinectContext.shutdown();
	}

	public static void main(String[] args) throws InterruptedException {
		new Sculptnect();
	}
}
