package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AwebruchClient;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashException;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.OnlineExperiment;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

public class AfekKuttenYungRunningState extends AfekKuttenYungState implements Runnable, AwebruchClient {
  private final static Logger logger = Logger.getLogger(AfekKuttenYungRunningState.class);

  private final static Logger experimentLogger = Logger.getLogger(OnlineExperiment.experimentLoggerName);

  private AfekKuttenYungStateMachine afekKuttenYungMachine;

  private AfekKuttenYungData ownData;
  private Map<Integer, AfekKuttenYungData> neighbourData;
  private Map<Integer, LinkedList<AfekKuttenYungData>> newNeighbourData;

  private CommunicationLayer communicationLayer;
  private final AlphaSynchronizer synchronizer;
  private Safra safra;
  private boolean active;
  private boolean terminated;
  private Thread loopThread;
  private int me;
  private boolean ownStateChanged; // If the node's data ownStateChanged during the step

  private boolean waitingForPulse = false;
  private boolean gotUpdatesBeforeStep = false;
  private int noChangeCounter;


  AfekKuttenYungRunningState(CommunicationLayer communicationLayer, Safra safra, AfekKuttenYungStateMachine afekKuttenYungMachine, CrashDetector crashDetector) {
    me = communicationLayer.getID();
    synchronizer = new AlphaSynchronizer(communicationLayer, this, crashDetector);

    this.communicationLayer = communicationLayer;
    this.safra = safra;
    this.afekKuttenYungMachine = afekKuttenYungMachine;

    this.ownData = AfekKuttenYungData.getRandomData();
    neighbourData = new HashMap<>();
    newNeighbourData = new HashMap<>();
    for (int n : communicationLayer.getNeighbours()) {
      neighbourData.put(n, AfekKuttenYungData.getEmptyData());
      newNeighbourData.put(n, new LinkedList<AfekKuttenYungData>());
    }
  }

  public void startAlgorithm() {
    loopThread = new Thread(this);
    loopThread.start();
  }

  private void startup() throws IOException, CrashException, InterruptedException {
    logger.info(String.format("%04d Starting AKY", me));
    synchronized (synchronizer) {
      synchronized (this) {
        safra.setActive(true, "Start AKY");
        sendDataToAllNeighbours(new OurTimer());
      }
    }
    synchronizer.awaitPulse();
  }

  @Override
  public void run() {
    try {
      startup();
      stepLoop();
    } catch (CrashException e) {
      if (!terminated) {
        afekKuttenYungMachine.setState(new AfekKuttenYungCrashedState());
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      // This thread should be interrupted when AKY terminates or crashes if so don't print the stack trace.
      if (!terminated && afekKuttenYungMachine.getState() == this) {
        e.printStackTrace();
      }
    } finally {
      if (terminated) {
        afekKuttenYungMachine.setState(new AfekKuttenYungTerminatedState(ownData.parent, ownData.distance, ownData.root));
      }
    }
    logger.info(String.format("%04d Finished AKY: terminated: %b, crashed: %b",
        me, terminated, afekKuttenYungMachine.getState() instanceof AfekKuttenYungCrashedState));
  }

  private void stepLoop() throws IOException, InterruptedException, CrashException {
    OurTimer timer = new OurTimer();
    try {
      while (true) {
        synchronized (synchronizer) {
          synchronized (this) {
            if (neighbourData.isEmpty()) {
              throw new NoNeighbourLeftException();
            }

            timer.start();
            copyNeighbourStates();

            step();

            if (ownStateChanged) {
              sendDataToAllNeighbours(timer);
              noChangeCounter = 0;
            } else {
              noChangeCounter++;
            }
            timer.stopAndCreateBasicTimeSpentEvent();

            if (active && !gotUpdatesBeforeStep && ((iAmRoot() || notRoot()) && maxRoot() && notHandling())) {
              setActive(false, "Step done");
            } else {
              if (active) {
                logger.trace(String.format("%04d AKY not becoming passive: Updatebeforestep: %b && (%b (root) || %b (notroot) && %b (maxroot) && %b (nothandling)", me, gotUpdatesBeforeStep, iAmRoot(), notHandling(), maxRoot(), notHandling()));
              }
            }

            ownStateChanged = false;
            gotUpdatesBeforeStep = false;
            waitingForPulse = true;

            if (!notHandling() && noChangeCounter > communicationLayer.getIbisCount() + 2) {
              logger.error(String.format("%04d waits for a request to be fulfilled. Request: %d. State: %s", communicationLayer.getID(), ownData.req, ownData.toString()));
            }
          }
        }
        logger.trace(String.format("%04d Waiting for pulse", me));
        synchronizer.awaitPulse();
        waitingForPulse = false;
      }
    } catch (NoNeighbourLeftException e) {
      becomeRoot();
      resetRequest();
      ownStateChanged = false;
      gotUpdatesBeforeStep = false;
      waitingForPulse = false;
      setActive(false, "No neighbours left");
      logger.info(String.format("%04d Finished, no neighbours left"));
    }
  }

  private void setActive(boolean status, String reason) throws IOException, CrashException {
    active = status;
    safra.setActive(status, reason);
  }

  // TODO remove logging statements or put them in ifs
  private synchronized void step() {
    logger.debug(String.format("%04d: %s", me, ownData.toString()));

    if (!(notRoot() && maxRoot()) && !iAmRoot()) {
      logger.trace(String.format("%04d becomes root", me));
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
      } else {
        logger.trace(String.format("%04d waits 1", me));
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
            if (request(i)) {
              isRequested = true;
              requestBy = i;
              break;
            }
          }
          if ((iAmRoot() || getParentData().from != me) && isRequested) {
            handleFor(requestBy);
          } else {
            logger.trace(String.format("%04d waits 2", me));
          }
        }
      } else if (iAmRoot() && ownData.direction == AfekKuttenYungData.ASK) {
        grant();
      } else if (ownData.parent != -1 && granted(ownData.parent)) {
        grant();
      } else {
        logger.trace(String.format("%04d waits 3", me));
      }
    }
  }

  private void handleFor(int requestBy) {
    logger.trace(String.format("%04d handles for %04d", me, requestBy));

    ownStateChanged = true;
    AfekKuttenYungData otherData = neighbourData.get(requestBy);
    ownData.req = otherData.req;
    ownData.from = requestBy;
    ownData.to = ownData.parent;
    ownData.direction = AfekKuttenYungData.ASK;
  }

  private boolean notHandling() {
    return ownData.req == AfekKuttenYungData.EMPTY_NODE && ownData.from == AfekKuttenYungData.EMPTY_NODE && ownData.to == AfekKuttenYungData.EMPTY_NODE && ownData.direction == AfekKuttenYungData.EMPTY_DIRECTION;
  }

  private boolean handling(int i) {
    AfekKuttenYungData data = neighbourData.get(i);
    return data.req == ownData.req && ownData.from == i && data.to == me && ownData.to == ownData.parent && data.direction == AfekKuttenYungData.ASK;
  }

  private boolean request(int i) {
    AfekKuttenYungData data = neighbourData.get(i);
    return ((isRoot(i) && data.req == data.from && data.from == i) || (data.parent == me && data.req != i && data.req != AfekKuttenYungData.EMPTY_NODE)) && data.to == me;
  }

  private boolean isRoot(int i) {
    AfekKuttenYungData data = i == me ? ownData : neighbourData.get(i);
    return data.parent == AfekKuttenYungData.EMPTY_PARENT && data.root == i && data.distance == 0;
  }

  private void resetRequest() {
    logger.trace(String.format("%04d resets request", me));
    ownStateChanged = true;
    ownData.req = AfekKuttenYungData.EMPTY_NODE;
    ownData.from = AfekKuttenYungData.EMPTY_NODE;
    ownData.to = AfekKuttenYungData.EMPTY_NODE;
    ownData.direction = AfekKuttenYungData.EMPTY_DIRECTION;
  }

  private void grant() {
    logger.trace(String.format("%04d grants", me));
    ownStateChanged = true;
    ownData.direction = AfekKuttenYungData.GRANT;
  }

  private boolean granted(int grantingNeighbour) {
    AfekKuttenYungData data = neighbourData.get(grantingNeighbour);
    if (data == null) {
      throw new IllegalStateException(String.format("No data for %04d on %04d", grantingNeighbour, me));
    }
    return ownData.req == data.req && me == data.from && data.direction == AfekKuttenYungData.GRANT && ownData.direction == AfekKuttenYungData.ASK;
  }

  private boolean requesting() {
    return neighbourData.keySet().contains(ownData.to) && neighbourData.get(ownData.to).root > me && ownData.req == me && ownData.from == me;
  }

  private void join() {
    logger.trace(String.format("%04d joins", me));
    ownStateChanged = true;
    ownData.parent = ownData.to;
    ownData.root = neighbourData.get(ownData.to).root;
    ownData.distance = neighbourData.get(ownData.to).distance + 1;
    resetRequest();
  }

  private void ask() {
    logger.trace(String.format("%04d asks", me));
    ownStateChanged = true;
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
    return isMaxRoot(neighbourData.get(i).root) && ownData.req == me && ownData.from == me && ownData.to == i && ownData.direction == AfekKuttenYungData.ASK;
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
    ownStateChanged = true;
    ownData.parent = AfekKuttenYungData.EMPTY_PARENT;
    ownData.root = me;
    ownData.distance = 0;
  }

  private boolean iAmRoot() {
    return isRoot(me);
  }

  private boolean notRoot() {
    return neighbourData.containsKey(ownData.parent) && ownData.root > me && ownData.root == getParentData().root && ownData.distance == getParentData().distance + 1;
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
    for (int i : communicationLayer.getNeighbours()) {
      if (!safra.crashDetected(i)) {
        if (!newNeighbourData.get(i).isEmpty()) {
          neighbourData.put(i, new AfekKuttenYungData(newNeighbourData.get(i).poll()));
        }
      }
    }
  }

  private void sendDataToAllNeighbours(OurTimer timer) throws IOException, CrashException {
    for (int n : newNeighbourData.keySet()) {
      synchronizer.sendMessage(n, new AfekKuttenYungDataMessage(safra.getSequenceNumber(), ownData), timer);
    }
  }

  @Override
  public synchronized void handleMessage(int source, Message m) throws IOException, TerminationDetectedTooEarly, CrashException {
    OurTimer timer = new OurTimer();
    if (terminated) {
      throw new TerminationDetectedTooEarly(String.format("%d received basic message", communicationLayer.getID()));

    }

    if (!(m instanceof AfekKuttenYungDataMessage)) {
      throw new IllegalStateException("Afek Kutten Yung received illegal message type");
    }

    AfekKuttenYungDataMessage message = (AfekKuttenYungDataMessage) m;

    if (!safra.crashDetected(source)) {
      timer.pause();
      try {
        setActive(true, "Got state update");
      } catch (CrashException e) {
        afekKuttenYungMachine.setState(new AfekKuttenYungCrashedState());
        loopThread.interrupt();
        throw e;
      }
      timer.start();

      if (!waitingForPulse) {
        gotUpdatesBeforeStep = true;
      }

      AfekKuttenYungData data = AfekKuttenYungData.getEmptyData();
      data.update(message);
      newNeighbourData.get(source).offer(data);
      // No call to safra.setActive(false). Message handling is not done before the next call to step.
    }
    timer.stopAndCreateBasicTimeSpentEvent();
  }

  public synchronized void terminate() throws TerminationDetectedTooEarly {
    if (active) {
      throw new TerminationDetectedTooEarly(String.format("%d was still active", communicationLayer.getID()));
    }
    logger.debug(String.format("%04d AKY terminating", me));
    terminated = true;
    loopThread.interrupt();
  }

  @Override
  public synchronized void handleCrash(int crashedNode) throws IOException, CrashException {
    OurTimer timer = new OurTimer();

    if (newNeighbourData.containsKey(crashedNode)) {
      newNeighbourData.remove(crashedNode);
      neighbourData.remove(crashedNode);

      timer.pause();
      try {
        setActive(true, "Crash detected");
      } catch (CrashException e) {
        afekKuttenYungMachine.setState(new AfekKuttenYungCrashedState());
        loopThread.interrupt();
        throw e;
      }

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

  private class NoNeighbourLeftException extends Exception {
  }
}
