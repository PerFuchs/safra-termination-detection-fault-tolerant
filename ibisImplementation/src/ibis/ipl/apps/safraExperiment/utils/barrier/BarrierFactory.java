package ibis.ipl.apps.safraExperiment.utils.barrier;


import ibis.ipl.Registry;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.ibisSignalling.IbisSignal;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;

import java.io.IOException;
import java.util.*;


/**
 * Builds a signal barrier if these are working for the current network size (signal become unreliable after a certain
 * network size), otherwise it builds an message based barrier which is slower but more reliable.
 *
 * Also forwards signals and messages to the correct barrier.
 */
public class BarrierFactory implements Observer {
  private final Registry registry;
  private SignalPollerThread signalHandler;
  private final CommunicationLayer communicationLayer;
  private Map<String, Barrier> barriers = new HashMap<>();

  public BarrierFactory(Registry registry, SignalPollerThread signalHandler, CommunicationLayer communicationLayer) {
    this.registry = registry;
    this.signalHandler = signalHandler;
    this.communicationLayer = communicationLayer;

    if (signalBarrierWorking()) {
      signalHandler.addObserver(this);
    }
  }

  public synchronized Barrier getBarrier(String name) {
    if (!barriers.containsKey(name)) {
      if (signalBarrierWorking()) {
        barriers.put(name, new SignalledBarrier(name, communicationLayer, registry));
      } else {
        barriers.put(name, new MessageBarrier(name, communicationLayer));
      }
    }
    return barriers.get(name);
  }

  public synchronized void handleBarrierMessage(String name) throws IOException {
    MessageBarrier barrier = (MessageBarrier) getBarrier(name);
    barrier.countDown();
  }

  @Override
  public void update(Observable observable, Object o) {
    if (o instanceof IbisSignal && signalBarrierWorking()) {
      IbisSignal signal = (IbisSignal) o;
      if (signal.module.equals("barrier")) {
        SignalledBarrier barrier = (SignalledBarrier) getBarrier(signal.name);
        barrier.countDown();
      }
    }
  }

  /**
   * SignalBarriers are not scalable so use different barrier instead.
   */
  public boolean signalBarrierWorking() {
    return registry.getPoolSize() < 0;
  }

  public void close() {
    signalHandler.deleteObserver(this);
  }
}
