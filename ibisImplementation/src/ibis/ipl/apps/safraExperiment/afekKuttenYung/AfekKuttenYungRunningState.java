package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AwebruchClient;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.OnlineExperiment;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class AfekKuttenYungRunningState extends AfekKuttenYungState implements Runnable, AwebruchClient {
  private final static Logger experimentLogger = Logger.getLogger(OnlineExperiment.experimentLoggerName);

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

  public void startAlgorithm() throws IOException {
    safra.setActive(true, "Start AKY");
    sendStateToAllNeighbours(new OurTimer());
    try {
      synchronizer.awaitPulse();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
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
    OurTimer timer = new OurTimer();
    while (true) {
      step();

      timer.stopAndCreateBasicTimeSpentEvent();
      setActive(false, "Step done");
      synchronizer.awaitPulse();

      timer.start();
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

  private void updateOwnState(Message m, OurTimer timer) {
    ownData.update(m);
    sendStateUpdateToAllNeighbours(m, timer);
  }

  private synchronized void copyNeighbourStates() {
    neighbourData = newNeighbourData;
    newNeighbourData = new HashMap<>();
    for (int i : communicationLayer.getNeighbours()) {
      newNeighbourData.put(i, AfekKuttenYungData.getEmptyData());
    }
  }

  private void sendStateToAllNeighbours(OurTimer timer) {

  }

  private void sendStateUpdateToAllNeighbours(Message m, OurTimer timer) {

  }

  @Override
  public synchronized void handleMessage(Message m) throws IOException, TerminationDetectedTooEarly {
    OurTimer timer = new OurTimer();

    if (!safra.crashDetected(m.getSource())) {
      timer.pause();
      setActive(true, "Got state update");
      timer.start();

      newNeighbourData.get(m.getSource()).update(m);
      // No call to safra.setActive(false). Message handling is not done before the next call to step.
    }
    timer.stopAndCreateBasicTimeSpentEvent();
  }

  public synchronized void terminate() throws TerminationDetectedTooEarly {
    if (active) {
      throw new TerminationDetectedTooEarly(String.format("%d was still active", communicationLayer.getID()));
    }
    terminated = true;
    loopThread.interrupt();
  }

  @Override
  public void handleCrash(int crashedNode) throws IOException {
    OurTimer timer = new OurTimer();

    if (newNeighbourData.containsKey(crashedNode)) {
      newNeighbourData.remove(crashedNode);

      timer.pause();
      setActive(true, "Crash detected");

      experimentLogger.info(Event.getParentCrashEvent());
    }
    timer.stopAndCreateBasicTimeSpentEvent();
  }

  public AlphaSynchronizer getSynchronizer() {
    return synchronizer;
  }
}
