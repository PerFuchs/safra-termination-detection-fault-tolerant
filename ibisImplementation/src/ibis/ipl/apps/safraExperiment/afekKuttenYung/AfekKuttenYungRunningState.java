package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AwebruchClient;
import ibis.ipl.apps.safraExperiment.chandyMisra.DistanceMessage;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.OnlineExperiment;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;

import java.io.IOException;
import java.util.*;
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
  private int me;

  AfekKuttenYungRunningState(CommunicationLayer communicationLayer, Safra safra, AfekKuttenYungStateMachine afekKuttenYungMachine) {
    me = communicationLayer.getID();
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
    sendDataToAllNeighbours(new OurTimer());
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
        afekKuttenYungMachine.setState(new AfekKuttenYungTerminatedState(ownData.parent, ownData.distance, ownData.root));
      }
    }
  }

  private void stepLoop() throws IOException, InterruptedException {
    OurTimer timer = new OurTimer();
    while (true) {
      step();

      sendDataToAllNeighbours(timer);
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
    if (!(notRoot() && maxRoot()) && !iAmRoot()) {
      becomeRoot();
    } else if (!maxRoot()) {

      boolean isAsking = false;
      for (int i : neighbourData.keySet()) {
        isAsking |= asking(i);
      }

      if (!isAsking) {
        ask();
      } else if (requesting() && granted(ownData.to)) {
        join();
      }
    } else {
      boolean isHandling = false;
      for (int i : neighbourData.keySet()) {
        isHandling |= handling(i) && request(i);
      }
      if (!isHandling) {
        if (!notHandling()) {
          resetRequest();
        } else {
          boolean isRequested = false;
          int requestBy = AfekKuttenYungData.EMPTY_NODE;
          for (int i : neighbourData.keySet()) {
            isRequested |= request(i);
            requestBy = i;
          }
          if (getParentData().from != me && isRequested) {
            handleFor(requestBy);
          }
        }
      } else if (iAmRoot() && ownData.direction == AfekKuttenYungData.ASK) {
        grant();
      } else if (granted(ownData.parent)) {
        grant();
      }
    }


    // Implement algorithm from the book operating on own state and neighbour state
  }

  private void handleFor(int requestBy) {
    AfekKuttenYungData otherData = neighbourData.get(requestBy);
    ownData.req = otherData.req;
    ownData.from = requestBy;
    ownData.to = ownData.parent;
    ownData.direction = AfekKuttenYungData.ASK;
  }

  private boolean notHandling() {
    return ownData.req == AfekKuttenYungData.EMPTY_NODE
        && ownData.from == AfekKuttenYungData.EMPTY_NODE
        && ownData.to == AfekKuttenYungData.EMPTY_NODE
        && ownData.direction == AfekKuttenYungData.EMPTY_DIRECTION;
  }

  private boolean handling(int i) {
    AfekKuttenYungData data = neighbourData.get(i);
    return data.req == ownData.req
        && ownData.from == i
        && data.to == me
        && ownData.to == ownData.parent
        && data.direction == AfekKuttenYungData.ASK;
  }

  private boolean request(int i) {
    AfekKuttenYungData data = neighbourData.get(i);
    return (isRoot(i)
          && data.req == data.from
          && data.from == i)
        || (data.parent == me
          && data.req != i
          && data.req != AfekKuttenYungData.EMPTY_NODE);
  }

  private boolean isRoot(int i) {
    AfekKuttenYungData data = i == me ? ownData : neighbourData.get(i);
    return data.parent == AfekKuttenYungData.EMPTY_PARENT
        && data.root == i
        && data.distance == 0;
  }

  private void resetRequest() {
    ownData.req = AfekKuttenYungData.EMPTY_NODE;
    ownData.from = AfekKuttenYungData.EMPTY_NODE;
    ownData.to = AfekKuttenYungData.EMPTY_NODE;
    ownData.direction = AfekKuttenYungData.EMPTY_DIRECTION;
  }

  private void grant() {
    ownData.direction = AfekKuttenYungData.GRANT;
  }

  private boolean granted(int grantingNeighbour) {
    AfekKuttenYungData data = neighbourData.get(grantingNeighbour);
    return ownData.req == data.req
        && ownData.from == data.from
        && data.direction == AfekKuttenYungData.GRANT
        && ownData.direction == AfekKuttenYungData.ASK;
  }

  private boolean requesting() {
    return neighbourData.keySet().contains(ownData.to)
        && neighbourData.get(ownData.to).root > me
        && ownData.req == me
        && ownData.from == me;
  }

  private void join() {
    ownData.parent = ownData.to;
    ownData.root = neighbourData.get(ownData.to).root;
    ownData.distance = neighbourData.get(ownData.to).distance + 1;
    resetRequest();
  }

  private void ask() {
    ownData.req = me;
    ownData.from = me;
    ownData.direction = AfekKuttenYungData.ASK;
    ownData.to = getMaxRoot();
  }

  private int getMaxRoot() {
    int maxRootValue = -1;
    Set<Integer> maxRoots = new HashSet<>();

    for (int n : neighbourData.keySet()) {
      int rootValue = neighbourData.get(n).root;
      if (rootValue > maxRootValue) {
        maxRoots.clear();
        maxRootValue = rootValue;
      }
      if (rootValue == maxRootValue) {
        maxRoots.add(n);
      }
    }
    return Collections.max(maxRoots);
  }

  private boolean asking(int i) {
    return isMaxRoot(neighbourData.get(i).root)
        && ownData.req == me
        && ownData.from == me
        && ownData.to == i
        && ownData.direction == AfekKuttenYungData.ASK;
  }

  private boolean isMaxRoot(int root) {
    for (AfekKuttenYungData data : neighbourData.values()) {
      if (data.root > root) {
        return false;
      }
    }
    return true;
  }

  private void becomeRoot() {
    ownData.parent = AfekKuttenYungData.EMPTY_PARENT;
    ownData.root = me;
    ownData.distance = 0;
  }

  private boolean iAmRoot() {
    return isRoot(me);
  }

  private boolean notRoot() {
    return neighbourData.containsKey(ownData.parent)
        && ownData.root > me
        && ownData.root == getParentData().root
        && ownData.distance == getParentData().distance + 1;
  }

  private boolean maxRoot() {
    for (AfekKuttenYungData neighbour : neighbourData.values()) {
      if (ownData.root < neighbour.root) {
        return false;
      }
    }
    return true;
  }

  private AfekKuttenYungData getParentData() {
    return neighbourData.get(ownData.parent);
  }

  private synchronized void copyNeighbourStates() {
    neighbourData = newNeighbourData;
    newNeighbourData = new HashMap<>();
    for (int i : communicationLayer.getNeighbours()) {
      if (!safra.crashDetected(i)) {
        newNeighbourData.put(i, new AfekKuttenYungData(neighbourData.get(i)));
      }
    }
  }

  private void sendDataToAllNeighbours(OurTimer timer) {

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

  @Override
  public int getParent() {
    return ownData.parent;
  }

  @Override
  public int getRoot() {
    return ownData.root;
  }

  @Override
  public int getDistance() {
    return ownData.distance;
  }

  public AlphaSynchronizer getSynchronizer() {
    return synchronizer;
  }
}
