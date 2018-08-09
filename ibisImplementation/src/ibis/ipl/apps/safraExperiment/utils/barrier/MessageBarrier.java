package ibis.ipl.apps.safraExperiment.utils.barrier;

import ibis.ipl.Registry;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Message based barrier.
 *
 * Uses the ring based traversal algorithm to stop all instances until all instances reached the await statement.
 * When a nodes receives a BARRIER message for the first time it fowards it when it reaches the await call. When
 * a node receives a barrier message for the second time, it knows all nodes must have reached await and breaks the
 * barrier itself. Only the root node sends a BARRIER message when it arrives the await call.
 */
public class MessageBarrier implements Barrier {
  private static Logger logger = Logger.getLogger(MessageBarrier.class);

  private String name;
  private CountDownLatch barrier;
  private CommunicationLayer communicationLayer;

  private boolean waiting = false;
  private ReentrantLock lock = new ReentrantLock();

  MessageBarrier(String name, CommunicationLayer communicationLayer) {
    this.name = name;
    this.communicationLayer = communicationLayer;
    this.barrier = new CountDownLatch(getBarrierSize());
  }

  private int getBarrierSize() {
    return 2;
  }

  private int getNextNode() {
    return (communicationLayer.getID() + 1) % communicationLayer.getIbisCount();
  }

  public void await() throws InterruptedException, IOException {
    lock.lock();
    logger.trace(String.format("%04d waiting at %s", communicationLayer.getID(), name));
    try {
      waiting = true;
      if (communicationLayer.isRoot() || barrier.getCount() == 1) {
        communicationLayer.sendBarrierMessage(getNextNode(), name);
      }
    } finally {
      lock.unlock();
    }
    barrier.await();
    logger.trace(String.format("%04d passed %s", communicationLayer.getID(), name));
    barrier = new CountDownLatch(getBarrierSize());
    lock.lock();
    try {
      waiting = false;
    } finally {
      lock.unlock();
    }
  }

  void countDown() throws IOException {
    lock.lock();
    logger.trace(String.format("%04d counting down %s", communicationLayer.getID(), name));
    try {
      if (waiting) {
        try {
          logger.debug(String.format("Next node is: %d for barrier %s", getNextNode(), name));
          communicationLayer.sendBarrierMessage(getNextNode(), name);
        } catch (IOException e) {
          logger.error("Could not message next node.");
          throw e;
        }
      }
    } finally {
      lock.unlock();
    }
    barrier.countDown();
  }

}
