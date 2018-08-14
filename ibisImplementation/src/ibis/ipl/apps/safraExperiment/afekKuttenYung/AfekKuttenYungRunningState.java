package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AwebruchClient;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AfekKuttenYungRunningState extends AfekKuttenYungState implements Runnable, AwebruchClient {
  private AfekKuttenYungStateMachine afekKuttenYungMachine;

  private AfekKuttenYungData ownData;
  private Map<Integer, AfekKuttenYungData> neighbourData;
  private Map<Integer, AfekKuttenYungData> newNeighbourData;

  private CommunicationLayer communicationLayer;
  private AlphaSynchronizer synchronizer;
  private Safra safra;
  private boolean active;
  private boolean terminated;
  private Thread loopThread;

  AfekKuttenYungRunningState(CommunicationLayer communicationLayer, Safra safra, AfekKuttenYungStateMachine afekKuttenYungMachine) {
    synchronizer = new AlphaSynchronizer(communicationLayer, this);

    this.communicationLayer = communicationLayer;
    this.safra = safra;
    this.afekKuttenYungMachine = afekKuttenYungMachine;

    this.ownData = AfekKuttenYungData.getRandomData();
    neighbourData = new HashMap<>();
    newNeighbourData = new HashMap<>();
    for (int n : communicationLayer.getNeighbours()) {
      neighbourData.put(n, AfekKuttenYungData.getEmptyData());
      newNeighbourData.put(n, AfekKuttenYungData.getEmptyData());
    }

  }

  public void startAlgorithm() throws InterruptedException, IOException {
    safra.setActive(true, "Start AKY");
    sendStateToAllNeighbours();
    synchronizer.awaitPulse();
    copyNeighbourStates();

    loopThread = new Thread(this);
    loopThread.run();
  }

  @Override
  public void run() {
    try {
      stepLoop();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      if (!terminated) {
        e.printStackTrace();
      }
    } finally {
      if (terminated) {
        afekKuttenYungMachine.setState(new AfekKuttenYungTerminatedState());
      }
    }
  }

  private void stepLoop() throws IOException, InterruptedException {
    while (true) {
      step();

      setActive(false, "Step done");
      synchronizer.awaitPulse();
      copyNeighbourStates();
    }
  }

  private void setActive(boolean status, String reason) throws IOException {
    active = status;
    safra.setActive(status, reason);
  }

  private synchronized void step() throws IOException {
    // Implement algorithm from the book operating on own state and neighbour state
  }

  private void updateOwnState(Message m) {
    ownData.update(m);
    sendStateUpdateToAllNeighbours(m);
  }

  private synchronized void copyNeighbourStates() {
    neighbourData = newNeighbourData;
    newNeighbourData = new HashMap<>();
    for (int i : communicationLayer.getNeighbours()) {
      newNeighbourData.put(i, AfekKuttenYungData.getEmptyData());
    }
  }

  private void sendStateToAllNeighbours() {

  }

  private void sendStateUpdateToAllNeighbours(Message m) {

  }

  @Override
  public synchronized void handleMessage(Message m) throws IOException, TerminationDetectedTooEarly {
    if (!safra.crashDetected(m.getSource())) {
      setActive(true, "Got state update");
      newNeighbourData.get(m.getSource()).update(m);
      // No call to safra.setActive(false). Message handling is not done before the next call to step.
    }
  }

  public synchronized void terminate() throws TerminationDetectedTooEarly {
    if (active) {
      throw new TerminationDetectedTooEarly(String.format("%d was still active", communicationLayer.getID()));
    }
    terminated = true;
    loopThread.interrupt();
  }

  @Override
  public void handleCrash(int crashedNode) {
    newNeighbourData.remove(crashedNode);
  }

}
