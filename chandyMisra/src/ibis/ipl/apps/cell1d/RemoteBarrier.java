package ibis.ipl.apps.cell1d;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.Registry;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class RemoteBarrier {
  private String name;
  private CountDownLatch barrier;
  private Registry registry;
  private List<IbisIdentifier> ibises;

  RemoteBarrier(String name, List<IbisIdentifier> ibises, Registry registry) {
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
