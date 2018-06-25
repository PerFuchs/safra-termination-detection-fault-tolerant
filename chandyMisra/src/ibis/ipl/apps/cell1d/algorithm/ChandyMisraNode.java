package ibis.ipl.apps.cell1d.algorithm;

import ibis.ipl.apps.cell1d.CommunicationLayer;
import ibis.ipl.apps.cell1d.CrashDetector;

import java.io.IOException;

public class ChandyMisraNode {
  private CrashDetector crashDetector;
  private CommunicationLayer communicationLayer;
  private Network network;

  private int me;

  private int dist = -1;
  private int parent = -1;

  public ChandyMisraNode(CommunicationLayer communicationLayer, Network network, int id) {
    this.communicationLayer = communicationLayer;
    this.network = network;
    this.me = id;
  }


  // TODO refactor to observer pattern
  public void setCrashDetector(CrashDetector cd) {
    this.crashDetector = cd;
  }
  public MinimumSpanningTree getSpanningTree() {
    return null;
  }

  public void startAlgorithm() throws IOException {
    if (communicationLayer.isRoot(me)) {
      this.dist = 0;
      this.parent = -1;

      sendDistanceMessagesToAllNeighbours(0);
    }
  }

  // TODO is synchronized allowed and okay?
  public synchronized void handleReceiveDistanceMessage(DistanceMessage dm, int origin) throws IOException {
    if (crashDetector.getCrashedNodes().contains(communicationLayer.getIbises()[origin])) {
      System.out.println("Got message from crashed node");  // TODO this happens, why? Either fix or ignore these in the communication layer
    }
    int newDistance = dm.getDistance() + network.getWeight(origin, me);
    if ((dist == -1 || newDistance < dist) && newDistance > 0 && !crashDetector.getCrashedNodes().contains(communicationLayer.getIbises()[origin])) {  // > 0 for overflows
      dist = newDistance;
      parent = origin;
      sendDistanceMessagesToAllNeighbours(dist);
    }
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

  public void handleCrash(int crashedNode) throws IOException {
    if (crashedNode == parent) {
      System.out.println(String.format("Detected parent (%d) crash on %d",
          crashedNode,
          me));
      handleRequestMessage(crashedNode);
    }
  }

  public synchronized void handleRequestMessage(int origin) throws IOException {
    if (origin == parent) {
      System.out.println("Got request message from parent or detected crash of parent at node: " + communicationLayer.getNodeNumber(communicationLayer.identifier()));
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
}
