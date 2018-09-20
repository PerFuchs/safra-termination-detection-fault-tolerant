package ibis.ipl.apps.safraExperiment.utils.barrier;

import ibis.ipl.Registry;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.ibisSignalling.IbisSignal;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

/**
 * Barrier based on IBIS signals works up to at least 200 instances is faster than a message based barrier.
 *
 * All node but the root send a "BARRIER" signal to the root node, when the root node received a signal from all nodes
 * it breaks the barrier and sends a "BARRIER" signal to all other nodes. When the other nodes receive the signal they
 * break the barrier.
 */
public class SignalledBarrier implements Barrier {
  private final static Logger logger = Logger.getLogger(SignalledBarrier.class);

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
      logger.debug(String.format("%04d sends signal for %s", communicationLayer.getID(), name));
      IbisSignal.signal(registry,
          Collections.singletonList(communicationLayer.getIbisIdentifier(communicationLayer.getRoot())),
          new IbisSignal("barrier", name));
    }
    barrier.await();
    if (communicationLayer.isRoot()) {
      logger.debug(String.format("%04d sends signal for %s", communicationLayer.getID(), name));
      IbisSignal.signal(registry,
          communicationLayer.getOtherIbises(),
          new IbisSignal("barrier", name));

    }
    logger.info(String.format("%04d broke barrier %s", communicationLayer.getID(), name));
    barrier = new CountDownLatch(getBarrierSize());
  }

  void countDown() {
    barrier.countDown();
  }

}
