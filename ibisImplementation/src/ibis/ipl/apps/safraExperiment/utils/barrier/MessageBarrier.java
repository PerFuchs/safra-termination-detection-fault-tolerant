package ibis.ipl.apps.safraExperiment.utils.barrier;

import ibis.ipl.Registry;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class MessageBarrier implements Barrier {
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
    try {
      waiting = true;
      if (communicationLayer.isRoot() || barrier.getCount() == 1) {
        communicationLayer.sendBarrierMessage(getNextNode(), name);
      }
    } finally {
      lock.unlock();
    }
    barrier.await();
    barrier = new CountDownLatch(getBarrierSize());
  }

  void countDown() {
    lock.lock();
    try {
      if (waiting) {
        try {
          communicationLayer.sendBarrierMessage(getNextNode(), name);
        } catch (IOException e) {
          System.err.println("Could not message next node.");
        }
      }
    } finally {
      lock.unlock();
    }
    barrier.countDown();
  }

}
