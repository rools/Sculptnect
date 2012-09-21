package joystick;

import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;

public class JoystickManager {
	/**
	 * Get a controller with the given type.
	 * 
	 * @param type The type of the joystick
	 * @return The first joystick of the given type. null if no joystick of the given type is found
	 */
	public static Joystick getJoystick(Controller.Type type) {
		Controller[] controllers = ControllerEnvironment.getDefaultEnvironment().getControllers();
		for (final Controller controller : controllers) {
			if (controller.getType() == type) {
				return new Joystick(controller);
			}
		}
		return null;
	}
}
