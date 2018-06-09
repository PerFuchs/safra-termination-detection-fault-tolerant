package ibis.ipl.apps.cell1d;

// File: $Id: IbisNode.java 6731 2007-11-05 22:38:04Z ndrost $

import ibis.ipl.*;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;
import ibis.ipl.apps.cell1d.algorithm.Network;

import java.io.IOException;
import java.util.LinkedList;


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

    PortType crashedMessagePortType = new PortType(
        PortType.CONNECTION_ONE_TO_ONE,
        PortType.COMMUNICATION_RELIABLE,
        PortType.RECEIVE_AUTO_UPCALLS,
        PortType.SERIALIZATION_DATA);

    PortType requestMessagePortType = new PortType(
        PortType.CONNECTION_ONE_TO_ONE,
        PortType.COMMUNICATION_RELIABLE,
        PortType.RECEIVE_AUTO_UPCALLS,
        PortType.SERIALIZATION_DATA);


    ibis = IbisFactory.createIbis(s, null, distanceMessagePortType, requestMessagePortType, crashedMessagePortType);

    registry = ibis.registry();
    System.out.println("Created IBIS");
    long startTime = System.currentTimeMillis();

    CommunicationLayer communicationLayer = new CommunicationLayer(ibis, registry, distanceMessagePortType, crashedMessagePortType, requestMessagePortType);
    CrashSimulator crashSimulator = new CrashSimulator(communicationLayer);
    System.out.println("Created communication layer");
    Network network = Network.getLineNetwork(ibis.identifier(), communicationLayer.getIbises(), communicationLayer, crashSimulator);
    System.out.println("Created Network");
    ChandyMisraNode chandyMisraNode = new ChandyMisraNode(communicationLayer, network, ibis.identifier());
    System.out.println("Created Misra algorithm");

    CrashDetector crashDetector = new CrashDetector(chandyMisraNode);

    communicationLayer.connectIbises(chandyMisraNode, crashDetector);
    System.out.println("connected communication layer");
    chandyMisraNode.startAlgorithm();
    System.out.println("Started algorithm");

    Thread.sleep(2000);
    crashSimulator.triggerLateCrash();
    Thread.sleep(4000);

    if (communicationLayer.isRoot(ibis.identifier())) {
      long endTime = System.currentTimeMillis();
      double time = ((double) (endTime - startTime)) / 1000.0;

      System.out.println("ExecutionTime: " + time);
    }
    int root = 0;
    int me = communicationLayer.getNodeNumber(ibis.identifier());
    System.out.println("Node: " + me + " Parent: " + communicationLayer.getNodeNumber(chandyMisraNode.getParent()) + "Dist: " + chandyMisraNode.getDist());
    System.err.println(network.getSpanningTree(new LinkedList<IbisIdentifier>()));

    ibis.end();
  }
}
