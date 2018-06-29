package ibis.ipl.apps.safraExperiment.utils.barrier;

import ibis.ipl.Registry;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.ibisSignalling.IbisSignal;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

/**
 * Barrier based on IBIS signals works up to at least 200 instances.
 * <p>
 * Does not scale up to 2000 instances.
 * <p>
 * Used to speed up small test runs compared to if TimerBarrier were used.
 */
public class SignalledBarrier implements Barrier {
  private String name;
  private CountDownLatch barrier;
  private CommunicationLayer communicationLayer;
  private Registry registry;

  SignalledBarrier(String name, CommunicationLayer communicationLayer, Registry registry) {
    this.name = name;
    this.communicationLayer = communicationLayer;
    this.registry = registry;
    this.barrier = new CountDownLatch(getBarrierSize());
  }

  private int getBarrierSize() {
    if (communicationLayer.isRoot()) {
      return communicationLayer.getIbisCount() - 1;
    } else {
      return 1;
    }
  }

  public void await() throws InterruptedException, IOException {
    if (!communicationLayer.isRoot()) {
      IbisSignal.signal(registry,
          Collections.singletonList(communicationLayer.getIbisIdentifier(communicationLayer.getRoot())),
          new IbisSignal("barrier", name));
    }
    barrier.await();
    if (communicationLayer.isRoot()) {
      IbisSignal.signal(registry,
          communicationLayer.getOtherIbises(),
          new IbisSignal("barrier", name));

    }
    barrier = new CountDownLatch(getBarrierSize());
  }

  void countDown() {
    barrier.countDown();
  }

}
