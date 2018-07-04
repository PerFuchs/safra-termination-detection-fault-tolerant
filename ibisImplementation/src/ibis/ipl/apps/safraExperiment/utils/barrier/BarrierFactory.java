package ibis.ipl.apps.safraExperiment.utils.barrier;


import ibis.ipl.Registry;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.ibisSignalling.IbisSignal;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;

import java.util.*;

public class BarrierFactory implements Observer {
  private final Registry registry;
  private final CommunicationLayer communicationLayer;
  private Map<String, Barrier> barriers = new HashMap<>();

  public BarrierFactory(Registry registry, SignalPollerThread signalHandler, CommunicationLayer communicationLayer) {
    this.registry = registry;
    this.communicationLayer = communicationLayer;

    if (signalBarrierWorking()) {
      signalHandler.addObserver(this);
    }
  }

  public Barrier getBarrier(String name) {
    if (!barriers.containsKey(name)) {
      if (signalBarrierWorking()) {
        barriers.put(name, new SignalledBarrier(name, communicationLayer, registry));
      } else {
        barriers.put(name, new MessageBarrier(name, communicationLayer));
      }
    }
    return barriers.get(name);
  }

  public synchronized void handleBarrierMessage(String name) {
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
    return registry.getPoolSize() < 200;
  }
}
