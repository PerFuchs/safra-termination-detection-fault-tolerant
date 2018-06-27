package ibis.ipl.apps.cell1d;


import ibis.ipl.Registry;

import java.util.*;

public class BarrierFactory implements Observer {
  private final Registry registry;
  private final CommunicationLayer communicationLayer;
  private Map<String, SignalledBarrier> barriers = new HashMap<>();

  public BarrierFactory(Registry registry, SignalPollerThread signalHandler, CommunicationLayer communicationLayer) {
    this.registry = registry;
    this.communicationLayer = communicationLayer;

    if (signalBarrierWorking()) {
      signalHandler.addObserver(this);
    }
  }

  public Barrier getBarrier(String name) {
    if (signalBarrierWorking()) {
      if (barriers.containsKey(name)) {
        return barriers.get(name);
      }
      SignalledBarrier barrier = new SignalledBarrier(name, communicationLayer.getIbises(), registry);
      barriers.put(name, barrier);

      return barrier;
    } else {
      return new TimerBarrier(10000);
    }
  }

  @Override
  public void update(Observable observable, Object o) {
    if (o instanceof IbisSignal && signalBarrierWorking()) {
      IbisSignal signal = (IbisSignal) o;
      SignalledBarrier barrier = (SignalledBarrier) getBarrier(signal.name);
      barrier.countDown();
    }
  }

  /**
   *  SignalBarriers are not scalable so use timers above a certain number of nodes.
   * @return
   */
  public boolean signalBarrierWorking() {
      return registry.getPoolSize() < 200;

  }
}
