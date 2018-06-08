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

    PortType distanceMessagePortType = new PortType(
        PortType.CONNECTION_ONE_TO_ONE,
        PortType.COMMUNICATION_RELIABLE,
        PortType.RECEIVE_AUTO_UPCALLS,
        PortType.SERIALIZATION_DATA);

    ibis = IbisFactory.createIbis(s, null, distanceMessagePortType);

    registry = ibis.registry();
    System.out.println("Created IBIS");
    long startTime = System.currentTimeMillis();

    CommunicationLayer communicationLayer = new CommunicationLayer(ibis, registry, distanceMessagePortType);
    System.out.println("Created communication layer");
    Network network = new Network(ibis.identifier(), communicationLayer.getIbises());
    System.out.println("Created Network");
    ChandyMisraNode chandyMisraNode = new ChandyMisraNode(communicationLayer, network, ibis.identifier());
    System.out.println("Created Misra algorithm");

    communicationLayer.connectIbises(chandyMisraNode);
    System.out.println("connected communication layer");
    chandyMisraNode.startAlgorithm();
    System.out.println("Started algorithm");

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
