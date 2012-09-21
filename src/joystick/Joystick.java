package joystick;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;

public class Joystick {
	private static final float ANALOG_DEAD_ZONE = 0.2f;

	private Controller controller;

	public Joystick(Controller controller) {
		this.controller = controller;
	}

	public void setJoystickListener(final JoystickListener listener) {
		(new Thread() {
			public void run() {
				while (true) {
					controller.poll();
					EventQueue queue = controller.getEventQueue();
					Event event = new Event();
					while (queue.getNextEvent(event)) {
						Component comp = event.getComponent();

						if (comp.isAnalog()) {
							float value = event.getValue();

							if (Math.abs(value) < ANALOG_DEAD_ZONE) {
								value = 0.0f;
							}

							listener.analogChanged(comp.getName(), value);
						} else {
							listener.buttonReceived(comp.getName(), event.getValue() > 0.5f);
						}
					}

					try {
						Thread.sleep(20);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}
}
