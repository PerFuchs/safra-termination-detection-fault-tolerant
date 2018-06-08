package ibis.ipl.apps.cell1d;

// File: $Id: IbisNode.java 6731 2007-11-05 22:38:04Z ndrost $

import ibis.ipl.*;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;
import ibis.ipl.apps.cell1d.algorithm.Network;

import java.io.IOException;


class IbisNode {
  static Ibis ibis;
  static Registry registry;
  static IbisIdentifier[] instances;

  public static void main(String[] args) throws IbisCreationFailedException, IOException, InterruptedException {
    IbisCapabilities s = new IbisCapabilities(
        IbisCapabilities.CLOSED_WORLD,
        IbisCapabilities.ELECTIONS_STRICT);

    PortType t = new PortType(
        PortType.CONNECTION_ONE_TO_ONE,
        PortType.COMMUNICATION_RELIABLE,
        PortType.RECEIVE_EXPLICIT,
        PortType.SERIALIZATION_DATA);

    ibis = IbisFactory.createIbis(s, null, t);

    registry = ibis.registry();

    long startTime = System.currentTimeMillis();

    CommunicationLayer communicationLayer = new CommunicationLayer(ibis, registry);
    Network network = new Network(communicationLayer.getIbises());
    ChandyMisraNode chandyMisraNode = new ChandyMisraNode(communicationLayer, network, ibis.identifier());

    communicationLayer.connectIbises(chandyMisraNode);
    chandyMisraNode.startAlgorithm();

    Thread.sleep(4000);

    if (communicationLayer.isRoot(ibis.identifier())) {
      long endTime = System.currentTimeMillis();
      double time = ((double) (endTime - startTime)) / 1000.0;

      System.out.println("ExecutionTime: " + time);
    }
    int root = 0;
    int me = communicationLayer.getNodeNumber(ibis.identifier());
    System.out.println("Node: " + me + " Parent: " + communicationLayer.getNodeNumber(chandyMisraNode.getParent()));

    ibis.end();
  }
}
