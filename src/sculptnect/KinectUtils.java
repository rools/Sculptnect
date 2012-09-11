package sculptnect;

public class KinectUtils {
	public static float rawDepthToMeters(short rawDepth) {
		if (rawDepth < 2047) {
			return (float) (1.0 / ((double) (rawDepth) * -0.0030711016 + 3.3309495161));
		}
		return 0.0f;
	}

	public static short metersToRawDepth(float meters) {
		return (short) (((1.0 / meters) - 3.3309495161) / (-0.0030711016));
	}
}
