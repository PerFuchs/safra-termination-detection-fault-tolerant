package ibis.ipl.apps.safraExperiment.chandyMisra;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashHandler;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.faultSensitive.SafraFS;
import ibis.ipl.apps.safraExperiment.spanningTree.Network;
import org.apache.log4j.Logger;

import java.io.IOException;

public class ChandyMisraNode implements CrashHandler {
  private static Logger logger = Logger.getLogger(ChandyMisraNode.class);

  private CrashDetector crashDetector;
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
    this.crashDetector = crashDetector;
    this.safraNode = safraNode;
    crashDetector.addHandler(this);
  }

  public synchronized void startAlgorithm() throws IOException {
    safraNode.setActive(true);
    if (communicationLayer.isRoot()) {
      this.dist = 0;
      this.parent = -1;

      sendDistanceMessagesToAllNeighbours(0);
    }
    safraNode.setActive(false);
  }

  public synchronized void handleReceiveDistanceMessage(DistanceMessage dm, int origin) throws IOException {
    if (terminated) {
      logger.error(String.format("%d received distance message after termination.", communicationLayer.getID()));
    }
    if (!crashDetector.hasCrashed(origin)) {
      safraNode.setActive(true);
      int newDistance = dm.getDistance() + network.getWeight(origin, me);
      if ((dist == -1 || newDistance < dist) && newDistance > 0) {  // > 0 for overflows
        dist = newDistance;
        parent = origin;
        sendDistanceMessagesToAllNeighbours(dist);
      }
    }
    safraNode.setActive(false);
  }

  private void sendDistanceMessagesToAllNeighbours(int distance) throws IOException {
    for (int neighbour : network.getNeighbours(me)) {
      if (neighbour != parent) {
        sendDistanceMessage(distance, neighbour);
      }
    }
  }

  private void sendDistanceMessage(int distance, int receiver) throws IOException {
    communicationLayer.sendDistanceMessage(new DistanceMessage(distance), receiver);
  }

  public int getParent() {
    return parent;
  }

  public synchronized void handleCrash(int crashedNode) throws IOException {
    safraNode.setActive(true);
    if (terminated) {
      logger.error(String.format("%d notified crash after termination.", communicationLayer.getID()));
    }
    if (crashedNode == parent) {
      logger.trace(String.format("%d Detected parent %d", me, parent));
      handleRequestMessage(crashedNode);
    }
    safraNode.setActive(false);
  }

  public synchronized void receiveRequestMessage(int origin) throws IOException {
    safraNode.setActive(true);
    if (terminated) {
      logger.error(String.format("%d received request message after termination.", communicationLayer.getID()));
    }
    if (!crashDetector.hasCrashed(origin)) {
      handleRequestMessage(origin);
    }
    safraNode.setActive(false);
  }

  private void handleRequestMessage(int origin) throws IOException {
    if (origin == parent) {
      logger.trace(String.format("%d got request message from parent %d", communicationLayer.getID(), origin));
      int oldParent = parent;
      parent = -1;
      dist = -1;
      for (int neighbour : network.getNeighbours(me)) {
        if (neighbour != oldParent) {
          sendRequestMessage(neighbour);
        }
      }
    } else {
      if (dist != -1) {
        sendDistanceMessage(dist, origin);
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
