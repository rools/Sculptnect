package joystick;

import java.util.HashSet;
import java.util.Set;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;

public class Joystick {
	private static final float ANALOG_DEAD_ZONE = 0.2f;

	private Controller controller;
	
	private Set<String> inDeadZone = new HashSet<String>();

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

						String name = comp.getName();
						if (comp.isAnalog()) {
							float value = event.getValue();

							if (inDeadZone.contains(name)) {
								if (Math.abs(value) >= ANALOG_DEAD_ZONE) {
									listener.analogChanged(name, value);
									inDeadZone.remove(name);
								}
							} else {
								if (Math.abs(value) < ANALOG_DEAD_ZONE) {
									value = 0.0f;
									inDeadZone.add(name);
								}
								
								listener.analogChanged(name, value);
							}
						} else {
							listener.buttonReceived(name, event.getValue() > 0.5f);
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
