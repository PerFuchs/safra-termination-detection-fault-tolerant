package ibis.ipl.apps.safraExperiment.chandyMisra;

import ibis.ipl.apps.safraExperiment.BasicAlgorithm;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashHandler;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.OnlineExperiment;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;
import org.apache.log4j.Logger;

import java.io.IOException;

public class ChandyMisraNode implements CrashHandler, BasicAlgorithm {
  private final static Logger logger = Logger.getLogger(ChandyMisraNode.class);
  private final static Logger experimentLogger = Logger.getLogger(OnlineExperiment.experimentLoggerName);

  private Safra safraNode;
  private CommunicationLayer communicationLayer;
  private Network network;

  private int me;

  private int dist = -1;
  private int parent = -1;
  private boolean terminated = false;

  public ChandyMisraNode(CommunicationLayer communicationLayer, Network network, CrashDetector crashDetector, Safra safraNode) {
    this.communicationLayer = communicationLayer;
    this.network = network;
    this.me = communicationLayer.getID();
    this.safraNode = safraNode;
    crashDetector.addHandler(this);
  }

  public synchronized void startAlgorithm() throws IOException {
    if (communicationLayer.isRoot()) {
      safraNode.setActive(true, "Start basic");
      OurTimer timer = new OurTimer();
      this.dist = 0;
      this.parent = -1;

      sendDistanceMessagesToAllNeighbours(0, timer);
      timer.stopAndCreateBasicTimeSpentEvent();
      safraNode.setActive(false, "End basic");
    }
  }

  public synchronized void handleReceiveDistanceMessage(DistanceMessage dm, int origin) throws IOException {
    if (terminated) {
      logger.error(String.format("%d received distance message after termination.", communicationLayer.getID()));
    }
    if (!safraNode.crashDetected(origin)) {
      safraNode.setActive(true, "Processing Distance Message");

      OurTimer timer = new OurTimer();
      int newDistance = dm.getDistance() + network.getWeight(origin, me);
      if ((dist == -1 || newDistance < dist) && newDistance > 0) {  // > 0 for overflows
        dist = newDistance;
        parent = origin;
        sendDistanceMessagesToAllNeighbours(dist, timer);
      }
      timer.stopAndCreateBasicTimeSpentEvent();

      safraNode.setActive(false, "End Processing Distance Message");
    }
  }

  private void sendDistanceMessagesToAllNeighbours(int distance, OurTimer timer) throws IOException {
    for (int neighbour : network.getNeighbours(me)) {
      if (neighbour != parent) {
        sendDistanceMessage(distance, neighbour, timer);
      }
    }
  }

  private void sendDistanceMessage(int distance, int receiver, OurTimer timer) throws IOException {
    communicationLayer.sendDistanceMessage(new DistanceMessage(distance), receiver, timer);
  }

  public int getParent() {
    return parent;
  }

  public synchronized void handleCrash(int crashedNode) throws IOException {
    if (crashedNode == parent) {
      if (terminated) {
        experimentLogger.warn(String.format("%d notfified crash after termination.", communicationLayer.getID()));
      }

      safraNode.setActive(true, "Processing crash");
      OurTimer timer = new OurTimer();
      if (terminated) {  // No node triggering activity can fail after termination has been detected
        logger.error(String.format("%d notified crash after termination.", communicationLayer.getID()));
      }

      logger.trace(String.format("%d Detected parent %d", me, parent));
      handleRequestMessage(crashedNode, timer);

      // Do not move this timer down the events below should not be timed
      timer.stopAndCreateBasicTimeSpentEvent();
      safraNode.setActive(false, "End processing crash");

      // Do not move this event up; it should happen after necessary handling events.
      experimentLogger.info(Event.getParentCrashEvent());
    }
  }

  public synchronized void receiveRequestMessage(int origin) throws IOException {
    safraNode.setActive(true, "Processing Request Message");
    OurTimer timer = new OurTimer();
    if (terminated) {
      logger.error(String.format("%d received request message after termination.", communicationLayer.getID()));
    }
    if (!safraNode.crashDetected(origin)) {
      handleRequestMessage(origin, timer);
    }
    timer.stopAndCreateBasicTimeSpentEvent();
    safraNode.setActive(false, "End processing Request Message");
  }

  private void handleRequestMessage(int origin, OurTimer timer) throws IOException {
    if (origin == parent) {
      logger.trace(String.format("%d got request message from parent %d", communicationLayer.getID(), origin));
      parent = -1;
      dist = -1;
      for (int neighbour : network.getNeighbours(me)) {
          sendRequestMessage(neighbour);
      }
    } else {
      if (dist != -1) {
        sendDistanceMessage(dist, origin, timer);
      }
    }
  }

  private void sendRequestMessage(int receiver) throws IOException {
    communicationLayer.sendRequestMessage(receiver);
  }

  public int getDist() {
    return dist;
  }

  public void terminate() {
    terminated = true;
  }
}
