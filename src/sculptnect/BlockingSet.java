package sculptnect;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class BlockingSet<T> {
	private final Set<T> set = new LinkedHashSet<T>();

	public synchronized void add(T x) {
		set.add(x);
		notify();
	}

	public synchronized void addAll(Collection<? extends T> collection) {
		set.addAll(collection);
		notifyAll();
	}

	public synchronized T poll(long timeoutNanos) throws InterruptedException {
		long startTime = System.nanoTime();
		long millis = timeoutNanos / 1000000;
		int nanos = (int) (timeoutNanos % 1000000);
		while (set.isEmpty()) {
			this.wait(millis, nanos);
			if (System.nanoTime() - startTime > timeoutNanos) {
				return null;
			}
		}

		T x = null;
		synchronized (set) {
			x = set.iterator().next();
			set.remove(x);
		}

		return x;
	}

	public synchronized T take() throws InterruptedException {
		while (set.isEmpty()) {
			this.wait();
		}

		T x = null;
		synchronized (set) {
			x = set.iterator().next();
			set.remove(x);
		}

		return x;
	}
}
