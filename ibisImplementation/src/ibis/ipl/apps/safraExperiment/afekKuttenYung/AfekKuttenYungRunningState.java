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
  private Map<Integer, AfekKuttenYungData> newNeighbourData;

  private CommunicationLayer communicationLayer;
  private final AlphaSynchronizer synchronizer;
  private Safra safra;
  private boolean active;
  private boolean terminated;
  private Thread loopThread;
  private int me;
  private boolean changed; // If the node's data changed during the step

  private boolean waitingForPulse = false;
  private boolean gotUpdatesBeforeStep = false;


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
      newNeighbourData.put(n, AfekKuttenYungData.getEmptyData());
    }
  }

  public void startAlgorithm() throws IOException, CrashException {
    logger.debug(String.format("%04d Starting algorihtm", me));
    synchronized (synchronizer) {
      synchronized (this) {
        try {
          safra.setActive(true, "Start AKY");
          sendDataToAllNeighbours(new OurTimer());
        } catch (CrashException e) {
          afekKuttenYungMachine.setState(new AfekKuttenYungCrashedState());
          throw e;
        }
      }
    }
    try {
      synchronizer.awaitPulse();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    logger.trace(String.format("%04d finished first pulse", me));

    loopThread = new Thread(this);
    loopThread.start();
  }

  @Override
  public void run() {
    try {
      stepLoop();
    } catch (CrashException e) {
      if (!terminated) {
        afekKuttenYungMachine.setState(new AfekKuttenYungCrashedState());
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      // This thread should be interupted when AKY terminates or crashes if so don't print the stack trace.
      if (!terminated && afekKuttenYungMachine.getState() == this) {
        e.printStackTrace();
      }
    } finally {
      if (terminated) {
        afekKuttenYungMachine.setState(new AfekKuttenYungTerminatedState(ownData.parent, ownData.distance, ownData.root));
      }
    }
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

            if (changed) {
              sendDataToAllNeighbours(timer);
            }
            timer.stopAndCreateBasicTimeSpentEvent();
            if (!gotUpdatesBeforeStep && (iAmRoot() || notRoot()) && maxRoot() && notHandling()) {
              setActive(false, "Step done");
              if (!changed && neighbourData.isEmpty()) {
                break;
              }
            } else {
              logger.trace(String.format("Updatebeforestep: %b && (%b (root) || %b (notroot) && %b (maxroot) && %b (nothandling)", gotUpdatesBeforeStep, iAmRoot(), notHandling(), maxRoot(), notHandling()));
            }
            changed = false;
            gotUpdatesBeforeStep = false;
            waitingForPulse = true;
          }
        }
        logger.trace(String.format("%04d waiting", me));
        synchronizer.awaitPulse();
        waitingForPulse = false;
      }
    } catch (NoNeighbourLeftException e) {
      becomeRoot();
      resetRequest();
      changed = false;
      gotUpdatesBeforeStep = false;
      waitingForPulse = false;
      setActive(false, "No neighbours left");
    }
  }

  private void setActive(boolean status, String reason) throws IOException, CrashException {
    active = status;
    safra.setActive(status, reason);
  }

  // TODO remove logging statements or put them in ifs
  private synchronized void step() {
    if (neighbourData.keySet().contains(ownData.parent) && granted(ownData.parent)) {
      logger.trace(String.format("%04d parents grants.", me));
    }
    if (!(notRoot() && maxRoot()) && !iAmRoot()) {
      logger.trace(String.format("%04d becomes root", me));
      becomeRoot();
    } else if (!maxRoot()) {
      boolean isAsking = false;
      for (int i : neighbourData.keySet()) {
        isAsking |= asking(i);
      }

      if (!isAsking) {
        logger.trace(String.format("%04d asks", me));

        ask();
      } else if (requesting() && granted(ownData.to)) {
        logger.trace(String.format("%04d joins", me));
        join();
      } else {
        logger.trace(String.format("%04d not max root, waiting for grant?", me));
      }
    } else {
      boolean isHandling = false;
      for (int i : neighbourData.keySet()) {
        isHandling |= handling(i) && request(i);
      }
      if (!isHandling) {
        if (!notHandling()) {
          logger.trace(String.format("%04d resets", me));
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
            logger.trace(String.format("%04d handles for %04d with state %s", me, requestBy, neighbourData.get(requestBy).toString()));
            handleFor(requestBy);
          } else {
            logger.trace(String.format("%04d empty handle", me));
          }
        }
      } else if (iAmRoot() && ownData.direction == AfekKuttenYungData.ASK) {
        logger.trace(String.format("%04d grants", me));
        grant();
      } else if (ownData.parent != -1 && granted(ownData.parent)) {
        logger.trace(String.format("%04d grants", me));
        grant();
      } else {
        logger.trace(String.format("%04d Parent state: %s", me, neighbourData.keySet().contains(ownData.parent) ? neighbourData.get(ownData.parent).toString() : ""));
      }
    }

    if (logger.isDebugEnabled()) {
      logger.debug(String.format("%04d: %s", me, ownData.toString()));
    }
  }

  private void handleFor(int requestBy) {
    changed = true;
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
    changed = true;
    ownData.req = AfekKuttenYungData.EMPTY_NODE;
    ownData.from = AfekKuttenYungData.EMPTY_NODE;
    ownData.to = AfekKuttenYungData.EMPTY_NODE;
    ownData.direction = AfekKuttenYungData.EMPTY_DIRECTION;
  }

  private void grant() {
    changed = true;
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
    changed = true;
    ownData.parent = ownData.to;
    ownData.root = neighbourData.get(ownData.to).root;
    ownData.distance = neighbourData.get(ownData.to).distance + 1;
    resetRequest();
  }

  private void ask() {
    changed = true;
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
    changed = true;
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
    neighbourData = newNeighbourData;
    newNeighbourData = new HashMap<>();
    for (int i : communicationLayer.getNeighbours()) {
      if (!safra.crashDetected(i)) {
        newNeighbourData.put(i, new AfekKuttenYungData(neighbourData.get(i)));
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

      newNeighbourData.get(source).update(message);
      // No call to safra.setActive(false). Message handling is not done before the next call to step.
    }
    timer.stopAndCreateBasicTimeSpentEvent();
  }

  public synchronized void terminate() throws TerminationDetectedTooEarly {
    if (active) {
      throw new TerminationDetectedTooEarly(String.format("%d was still active", communicationLayer.getID()));
    }
    logger.trace(String.format("AKY %04d terminating", me));
    terminated = true;
    loopThread.interrupt();
  }

  @Override
  public synchronized void handleCrash(int crashedNode) throws IOException, CrashException {
    OurTimer timer = new OurTimer();

    if (newNeighbourData.containsKey(crashedNode)) {
      newNeighbourData.remove(crashedNode);

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
