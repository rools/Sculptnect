package sculptnect;

public class SculptMath {
	// Steps per degrees that the trigonometry tables are stored in
	private final static int TRIG_TABLE_STEPS = 10;

	private static float cosTable[] = new float[360 * TRIG_TABLE_STEPS];
	private static float sinTable[] = new float[360 * TRIG_TABLE_STEPS];

	static {
		for (int i = 0; i < 360 * TRIG_TABLE_STEPS; ++i) {
			cosTable[i] = (float) Math.cos(Math.toRadians((float) i / TRIG_TABLE_STEPS));
			sinTable[i] = (float) Math.sin(Math.toRadians((float) i / TRIG_TABLE_STEPS));
		}
	}

	/**
	 * Get the cosine of the given angle.
	 * 
	 * @param angle in degrees
	 * @return
	 */
	public static float cos(float angle) {
		return cosTable[(int) (angle * TRIG_TABLE_STEPS)];
	}

	/**
	 * Get the sine of the given angle.
	 * 
	 * @param angle in degrees
	 * @return
	 */
	public static float sin(float angle) {
		return sinTable[(int) (angle * TRIG_TABLE_STEPS)];
	}
}
