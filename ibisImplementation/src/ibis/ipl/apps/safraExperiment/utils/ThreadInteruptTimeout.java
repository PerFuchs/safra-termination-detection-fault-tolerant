package ibis.ipl.apps.safraExperiment.utils;

import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadInteruptTimeout implements Runnable {

  private final Thread thread;
  private final long timeout;

  private AtomicBoolean clear = new AtomicBoolean(false);

  public ThreadInteruptTimeout(Thread interuptableThreat, long timeout) {
    this.thread = interuptableThreat;
    this.timeout = timeout;
  }
  @Override
  public void run() {
    try {
      Thread.sleep(timeout);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    if (!clear.get()) {
      thread.interrupt();
    }
  }

  public void clear() {
    clear.set(true);
  }
}
