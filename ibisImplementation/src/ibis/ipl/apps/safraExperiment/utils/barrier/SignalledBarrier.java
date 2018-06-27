package ibis.ipl.apps.safraExperiment.utils.barrier;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.Registry;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Barrier based on IBIS signals works up to at least 200 instances.
 *
 * Does not scale up to 2000 instances.
 *
 * Used to speed up small test runs compared to if TimerBarrier were used.
 */
public class SignalledBarrier implements Barrier {
  private String name;
  private CountDownLatch barrier;
  private Registry registry;
  private List<IbisIdentifier> ibises;

  SignalledBarrier(String name, List<IbisIdentifier> ibises, Registry registry) {
    this.name = name;
    this.registry = registry;
    this.ibises = ibises;
    this.barrier = new CountDownLatch(ibises.size());
  }

  public void await() throws InterruptedException, IOException {
    registry.signal(name, ibises.toArray(new IbisIdentifier[0]));
    barrier.await();
    barrier = new CountDownLatch(ibises.size());
  }

  void countDown() {
    barrier.countDown();
  }

}
