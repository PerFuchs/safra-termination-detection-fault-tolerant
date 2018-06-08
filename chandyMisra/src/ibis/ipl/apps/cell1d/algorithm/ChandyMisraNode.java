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

  public SpanningTree getSpanningTree() {
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
    if (dist == -1 || newDistance < dist) {
      dist = newDistance;
      parent = origin;
      sendDistanceMessagesToAllNeighbours(dist);
    }
  }

  private void sendDistanceMessagesToAllNeighbours(int distance) throws IOException {
    for (IbisIdentifier neighbour : network.getNeighbours(me)) {
      sendDistanceMessage(distance, neighbour);
    }
  }

  private void sendDistanceMessage(int distance, IbisIdentifier receiver) throws IOException {
    communicationLayer.sendDistanceMessage(new DistanceMessage(distance), receiver);
  }

  public IbisIdentifier getParent() {
    return parent;
  }
}
