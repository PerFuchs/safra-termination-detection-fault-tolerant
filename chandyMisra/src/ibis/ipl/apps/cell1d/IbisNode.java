package ibis.ipl.apps.cell1d;

// File: $Id: IbisNode.java 6731 2007-11-05 22:38:04Z ndrost $

import ibis.ipl.*;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;
import ibis.ipl.apps.cell1d.algorithm.MinimumSpanningTree;
import ibis.ipl.apps.cell1d.algorithm.Network;
import org.apache.log4j.BasicConfigurator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;


class IbisNode {
  static Ibis ibis;
  static Registry registry;



  public static void main(String[] args) throws IbisCreationFailedException, IOException, InterruptedException {
    BasicConfigurator.configure();

//    if (args.length >= 2 && args[1].equals("root")) {
//      Logger.getLogger("ibis").setLevel(Level.INFO);
//    }
    System.setErr(System.out);  // Redirect because DAS4 does not show err.
    IbisCapabilities s = new IbisCapabilities(
        IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED,
        IbisCapabilities.CLOSED_WORLD,
        IbisCapabilities.ELECTIONS_STRICT,
        IbisCapabilities.SIGNALS);

    PortType porttype = new PortType(
        PortType.CONNECTION_ONE_TO_ONE,
        PortType.COMMUNICATION_RELIABLE,
        PortType.RECEIVE_AUTO_UPCALLS,
        PortType.SERIALIZATION_DATA,
        PortType.COMMUNICATION_FIFO);

    ibis = IbisFactory.createIbis(s, null, porttype);

    registry = ibis.registry();

    SignalPollerThread signalHandler = new SignalPollerThread(registry);
    signalHandler.start();

    System.out.println("Created IBIS");
    registry.waitUntilPoolClosed();
    System.out.println("Pool closed");

    long startTime = System.currentTimeMillis();

    CommunicationLayer communicationLayer = new CommunicationLayer(ibis, registry, porttype);

    BarrierFactory barrierFactory = new BarrierFactory(registry, signalHandler, communicationLayer);

    CrashSimulator crashSimulator = new CrashSimulator(communicationLayer, true);
    System.out.println("Created communication layer");
    Network network = Network.getLineNetwork(communicationLayer, crashSimulator);
    System.out.println("Created Network");
    ChandyMisraNode chandyMisraNode = new ChandyMisraNode(communicationLayer, network);
    System.out.println("Created Misra algorithm");

    CrashDetector crashDetector = new CrashDetector(chandyMisraNode, communicationLayer);
    chandyMisraNode.setCrashDetector(crashDetector);

    communicationLayer.connectIbises(network, chandyMisraNode, crashDetector);
    System.out.println("Connected communication layer");


    barrierFactory.getBarrier("Connected").await();

    chandyMisraNode.startAlgorithm();
    System.out.println("Started algorithm");

    Thread.sleep(5000);
    crashSimulator.triggerLateCrash();
    Thread.sleep(30000);
    writeResults(communicationLayer.getID(), chandyMisraNode, communicationLayer);


    barrierFactory.getBarrier("ResultsWritten").await();


    if (communicationLayer.isRoot(communicationLayer.getID())) {
      long endTime = System.currentTimeMillis();
      double time = ((double) (endTime - startTime)) / 1000.0;

      System.out.println("ExecutionTime: " + time);
      List<Result> results = readResults(communicationLayer);
      MinimumSpanningTree tree = new MinimumSpanningTree(results, communicationLayer, network, crashDetector.getCrashedNodes());
      System.out.println("Constructed tree:");
      System.out.println(tree);
      System.out.println("Expected tree:");
      MinimumSpanningTree expectedTree = network.getSpanningTree(crashDetector.getCrashedNodes());
      System.out.println(expectedTree);
      System.out.println(String.format("Weight equal: %b Trees equal: %b",
          tree.getWeight() == expectedTree.getWeight(), tree.equals(expectedTree)));
      System.out.println(String.format("Crashed nodes: %s", crashDetector.getCrashedNodesString()));
      System.out.println("End");
    }

    barrierFactory.getBarrier("Done").await();

    signalHandler.stop();
    ibis.end();
  }

  private static String filePathForResults(int node) {
    return String.format("/var/scratch/pfs250/%04d.output", node);
  }

  private static void writeResults(int node, ChandyMisraNode chandyMisraNode, CommunicationLayer communicationLayer) {
    String str = String.format("%d %d %d\n", node, chandyMisraNode.getParent(), chandyMisraNode.getDist());

    Path path = Paths.get(filePathForResults(node));
    byte[] strToBytes = str.getBytes();

    try {
      Files.write(path, strToBytes);
    } catch (IOException e) {
      System.out.println(String.format("Could not write output file: %d", node));
    }
    System.out.println("Output files written.");
  }

  private static List<Result> readResults(CommunicationLayer communicationLayer) {
    List<Result> results = new LinkedList<>();

    for (int i = 0; i < communicationLayer.getIbisCount(); i++) {
      System.out.println("Reading results " + i);

      Path path = Paths.get(filePathForResults(i));

      try {
        String[] r = Files.readAllLines(path, StandardCharsets.UTF_8).get(0).split(" ");
        results.add(new Result(Integer.valueOf(r[0]), Integer.valueOf(r[1]), Integer.valueOf(r[2])));
      } catch (IOException e) {
        System.out.println(String.format("Could not read output file from: %d", i));
      }

    }
    return results;
  }
}
