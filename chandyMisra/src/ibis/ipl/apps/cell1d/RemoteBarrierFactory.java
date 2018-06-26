package ibis.ipl.apps.cell1d;


import ibis.ipl.Registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

public class RemoteBarrierFactory implements Observer {
  private final Registry registry;
  private final CommunicationLayer communicationLayer;
  private Map<String, RemoteBarrier> barriers = new HashMap<>();

  public RemoteBarrierFactory(Registry registry, SignalPollerThread signalHandler, CommunicationLayer communicationLayer) {
    this.registry = registry;
    this.communicationLayer = communicationLayer;

    signalHandler.addObserver(this);
  }

  public RemoteBarrier getBarrier(String name) {
    if (barriers.containsKey(name)) {
      return barriers.get(name);
    }
    RemoteBarrier barrier = new RemoteBarrier(name, communicationLayer.getIbises(), registry);
    barriers.put(name, barrier);

    return barrier;
  }

  @Override
  public void update(Observable observable, Object o) {
    if (o instanceof IbisSignal) {
      IbisSignal signal = (IbisSignal) o;
      RemoteBarrier barrier = getBarrier(signal.name);
      barrier.countDown();
    }
  }
}
