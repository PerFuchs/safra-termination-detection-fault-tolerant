package ibis.ipl.apps.safraExperiment.utils.barrier;

import java.io.IOException;

/**
 * This is not a real barrier. It keeps all processes waiting for some time to
 * make sure every process had time to finish.
 *
 * Used if there are to many nodes for the SignalledBarrier
 */
public class TimerBarrier implements Barrier {

  private int waitTime;

  TimerBarrier(int waitTime) {
    this.waitTime = waitTime;
  }

  @Override
  public void await() throws InterruptedException, IOException {
    Thread.sleep(waitTime);
  }
}
