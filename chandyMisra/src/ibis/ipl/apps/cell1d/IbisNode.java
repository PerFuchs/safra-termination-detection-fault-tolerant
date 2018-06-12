package ibis.ipl.apps.cell1d;

// File: $Id: IbisNode.java 6731 2007-11-05 22:38:04Z ndrost $

import ibis.ipl.*;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;
import ibis.ipl.apps.cell1d.algorithm.MinimumSpanningTree;
import ibis.ipl.apps.cell1d.algorithm.Network;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;


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
    CrashSimulator crashSimulator = new CrashSimulator(communicationLayer, true);
    System.out.println("Created communication layer");
    Network network = Network.getLineNetwork(ibis.identifier(), communicationLayer.getIbises(), communicationLayer, crashSimulator);
    System.out.println("Created Network");
    ChandyMisraNode chandyMisraNode = new ChandyMisraNode(communicationLayer, network, ibis.identifier());
    System.out.println("Created Misra algorithm");

    CrashDetector crashDetector = new CrashDetector(chandyMisraNode, communicationLayer);

    communicationLayer.connectIbises(chandyMisraNode, crashDetector);
    System.out.println("connected communication layer");
    chandyMisraNode.startAlgorithm();
    System.out.println("Started algorithm");

    Thread.sleep(2000);
    crashSimulator.triggerLateCrash();
    Thread.sleep(2000);
    writeResults(communicationLayer.getNodeNumber(ibis.identifier()), chandyMisraNode, communicationLayer);
    Thread.sleep(200);

    System.out.println("After sleeping");
    int me = communicationLayer.getNodeNumber(ibis.identifier());
    System.out.println("Node: " + me + " Parent: " + communicationLayer.getNodeNumber(chandyMisraNode.getParent()) + "Dist: " + chandyMisraNode.getDist());
    Thread.sleep(200);

    if (communicationLayer.isRoot(ibis.identifier())) {
      long endTime = System.currentTimeMillis();
      double time = ((double) (endTime - startTime)) / 1000.0;

      System.out.println("ExecutionTime: " + time);
      List<Result> results = readResults(communicationLayer);
      MinimumSpanningTree tree = new MinimumSpanningTree(results, communicationLayer, network, crashDetector.getCrashedNodes());
      System.out.println("Constructed tree:");
      System.out.println(tree);
      System.out.println("Expected tree:");
      MinimumSpanningTree expectedTree =network.getSpanningTree(crashDetector.getCrashedNodes());
      System.out.println(expectedTree);
      System.out.println(String.format("Weight equal: %b Trees equal: %b",
          tree.getWeight() == expectedTree.getWeight(), tree.equals(expectedTree)));
      System.out.println(String.format("Crashed nodes: %s", crashDetector.getCrashedNodesString()));
    }



    ibis.end();
  }

  private static String filePathForResults(int node) {
    return String.format("/var/scratch/pfs250/%d.output", node);
  }

  private static void writeResults(int node, ChandyMisraNode chandyMisraNode, CommunicationLayer communicationLayer) throws IOException {
    String str = String.format("%d %d %d\n", node, communicationLayer.getNodeNumber(chandyMisraNode.getParent()), chandyMisraNode.getDist());

    Path path = Paths.get(filePathForResults(node));
    byte[] strToBytes = str.getBytes();

    Files.write(path, strToBytes);
    System.out.println("Output files written.");
  }

  private static List<Result> readResults(CommunicationLayer communicationLayer) throws IOException {
    List<Result> results = new LinkedList<>();

    for (int i = 0; i < communicationLayer.getIbises().length; i++) {
      Path path = Paths.get(filePathForResults(i));
      String[] r = Files.readAllLines(path, StandardCharsets.UTF_8).get(0).split(" ");
      results.add(new Result(Integer.valueOf(r[0]), Integer.valueOf(r[1]), Integer.valueOf(r[2])));
    }
    return results;
  }
}
