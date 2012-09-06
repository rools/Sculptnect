import org.openkinect.freenect.Context;
import org.openkinect.freenect.Device;
import org.openkinect.freenect.Freenect;

public class Sculptnect {
	private Context kinectContext = null;
	private Device kinect = null;

	public Sculptnect() {
		// Set up Kinect
		kinectContext = Freenect.createContext();
		if (kinectContext.numDevices() > 0) {
			kinect = kinectContext.openDevice(0);
		} else {
			System.err.println("Error, no Kinect detected.");
		}
	}

	// TODO: Call this function before the program quits
	public void cleanup() {
		// Shut down Kinect
		if (kinectContext != null)
			if (kinect != null) {
				kinect.close();
			}
		kinectContext.shutdown();
	}

	public static void main(String[] args) throws InterruptedException {
		new Sculptnect();
	}
}
