package ibis.ipl.apps.cell1d.algorithm;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.apps.cell1d.CommunicationLayer;

import java.io.IOException;

public class ChandyMisraNode {
  private CommunicationLayer communicationLayer;
  private Network network;

  private IbisIdentifier me;

  private int dist = -1;
  private IbisIdentifier parent;

  public ChandyMisraNode(CommunicationLayer communicationLayer, Network network, IbisIdentifier ibisId) {
    this.communicationLayer = communicationLayer;
    this.network = network;
    this.me = ibisId;
  }

  public MinimumSpanningTree getSpanningTree() {
    return null;
  }

  public void startAlgorithm() throws IOException {
    if (communicationLayer.isRoot(me)) {
      this.dist = 0;
      this.parent = null;

      sendDistanceMessagesToAllNeighbours(0);
    }
  }

  // TODO is synchronized allowed and okay?
  public synchronized void handleReceiveDistanceMessage(DistanceMessage dm, IbisIdentifier origin) throws IOException {
    int newDistance = dm.getDistance() + network.getWeight(origin, me);
    if ((dist == -1 || newDistance < dist) && newDistance > 0) {  // > 0 for overflows
      dist = newDistance;
      parent = origin;
      sendDistanceMessagesToAllNeighbours(dist);
    }
  }

  private void sendDistanceMessagesToAllNeighbours(int distance) throws IOException {
    for (IbisIdentifier neighbour : network.getNeighbours(me)) {
      if (!neighbour.equals(parent)) {
        sendDistanceMessage(distance, neighbour);
      }
    }
  }

  private void sendDistanceMessage(int distance, IbisIdentifier receiver) throws IOException {
    communicationLayer.sendDistanceMessage(new DistanceMessage(distance), receiver);
  }

  public IbisIdentifier getParent() {
    return parent;
  }

  public void handleCrash(IbisIdentifier crashedNode) throws IOException {
    if (crashedNode.equals(parent)) {
      handleRequestMessage(crashedNode);
    }
  }

  public synchronized void handleRequestMessage(IbisIdentifier origin) throws IOException {
    if (origin.equals(parent)) {
      System.out.println("Got request message from parent or detected crash of parent at node: " + communicationLayer.getNodeNumber(communicationLayer.identifier()));
      IbisIdentifier oldParent = parent;
      parent = null;
      dist = -1;
      for (IbisIdentifier neighbour : network.getNeighbours(me)) {
        if (!neighbour.equals(oldParent)) {
          sendRequestMessage(neighbour);
        }
      }
    } else {
      if (dist != -1) {
        sendDistanceMessage(dist, origin);
      }
    }
  }

  public void sendRequestMessage(IbisIdentifier receiver) throws IOException {
    communicationLayer.sendRequestMessage(receiver);
  }

  public int getDist() {
    return dist;
  }
}
