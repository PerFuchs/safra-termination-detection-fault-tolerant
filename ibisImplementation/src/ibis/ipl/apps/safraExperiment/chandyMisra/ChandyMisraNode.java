package ibis.ipl.apps.safraExperiment.chandyMisra;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashHandler;
import ibis.ipl.apps.safraExperiment.safra.faultSensitive.Safra;
import ibis.ipl.apps.safraExperiment.spanningTree.Network;

import java.io.IOException;

public class ChandyMisraNode implements CrashHandler {
  private CrashDetector crashDetector;
  private Safra safraNode;
  private CommunicationLayer communicationLayer;
  private Network network;

  private int me;

  private int dist = -1;
  private int parent = -1;

  public ChandyMisraNode(CommunicationLayer communicationLayer, Network network, CrashDetector crashDetector, Safra safraNode) {
    this.communicationLayer = communicationLayer;
    this.network = network;
    this.me = communicationLayer.getID();
    this.crashDetector = crashDetector;
    this.safraNode = safraNode;
    crashDetector.addHandler(this);
  }

  public void startAlgorithm() throws IOException {
    safraNode.setActive(true);
    if (communicationLayer.isRoot()) {
      this.dist = 0;
      this.parent = -1;

      sendDistanceMessagesToAllNeighbours(0);
    }
    safraNode.setActive(false);
  }

  public synchronized void handleReceiveDistanceMessage(DistanceMessage dm, int origin) throws IOException {
    safraNode.handleReceiveBasicMessage();
    if (!crashDetector.hasCrashed(origin)) {
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
    safraNode.handleSendingBasicMessage();
    communicationLayer.sendDistanceMessage(new DistanceMessage(distance), receiver);
  }

  public int getParent() {
    return parent;
  }

  public void handleCrash(int crashedNode) throws IOException {
    safraNode.setActive(true);
    if (crashedNode == parent) {
      System.out.println(String.format("Detected parent (%d) crash on %d",
          crashedNode,
          me));
      handleRequestMessage(crashedNode);
    }
    safraNode.setActive(false);
  }

  // TODO reunify naming scheme
  public synchronized void receiveRequestMessage(int origin) throws IOException {
    safraNode.handleReceiveBasicMessage();
    if (!crashDetector.hasCrashed(origin)) {
      handleRequestMessage(origin);
    }
    safraNode.setActive(false);
  }

  private void handleRequestMessage(int origin) throws IOException {
    if (origin == parent) {
      System.out.println("Got request message from parent or detected crash of parent at node: " + origin);
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
    safraNode.handleSendingBasicMessage();
    communicationLayer.sendRequestMessage(receiver);
  }

  public int getDist() {
    return dist;
  }
}
