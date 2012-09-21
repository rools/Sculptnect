package joystick;

public interface JoystickListener {
	void buttonReceived(String button, boolean value);

	void analogChanged(String analog, float value);
}