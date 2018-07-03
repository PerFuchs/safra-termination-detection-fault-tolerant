package ibis.ipl.apps.safraExperiment;

// File: $Id: IbisNode.java 6731 2007-11-05 22:38:04Z ndrost $

import ibis.ipl.*;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.faultSensitive.SafraFS;
import ibis.ipl.apps.safraExperiment.spanningTree.MinimumSpanningTree;
import ibis.ipl.apps.safraExperiment.spanningTree.Network;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;
import ibis.ipl.apps.safraExperiment.spanningTree.Result;
import ibis.ipl.apps.safraExperiment.utils.barrier.BarrierFactory;
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
//      Logger.getLogger("ibis").setLevel(Level.INFO);

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

    long startTime = System.currentTimeMillis();

    ibis = IbisFactory.createIbis(s, null, porttype);
    registry = ibis.registry();
    System.out.println("Created IBIS");

    SignalPollerThread signalHandler = new SignalPollerThread(registry);
    signalHandler.start();

    registry.waitUntilPoolClosed();
    System.out.println("Pool closed");

    CommunicationLayer communicationLayer = new CommunicationLayer(ibis, registry, porttype);
    System.out.println("Created communication layer");

    BarrierFactory barrierFactory = new BarrierFactory(registry, signalHandler, communicationLayer);

    CrashDetector crashDetector = new CrashDetector();
    CrashSimulator crashSimulator = new CrashSimulator(communicationLayer, false);

    Network network = Network.getLineNetwork(communicationLayer, crashSimulator);
    network = network.combineWith(Network.getUndirectedRing(communicationLayer, crashSimulator), 3000);
    System.out.println("Created Network");

    Safra safraNode = new SafraFS(registry, signalHandler, communicationLayer);

    ChandyMisraNode chandyMisraNode = new ChandyMisraNode(communicationLayer, network, crashDetector, safraNode);
    System.out.println("Created Misra algorithm");

    communicationLayer.connectIbises(network, chandyMisraNode, safraNode, crashDetector, barrierFactory);
    System.out.println("Connected communication layer");

    if (!barrierFactory.signalBarrierWorking()) {
      // Barrier relies on messages. Lets wait until all nodes have there channels setup.
      Thread.sleep(20000);
    }

    barrierFactory.getBarrier("Connected").await();

    safraNode.startAlgorithm();
    chandyMisraNode.startAlgorithm();
    System.out.println("Started algorithm");

//    Thread.sleep(5000);
//    crashSimulator.triggerLateCrash();
//    Thread.sleep(10000);
    safraNode.await();

    writeResults(communicationLayer.getID(), chandyMisraNode);
    barrierFactory.getBarrier("ResultsWritten").await();

    if (communicationLayer.isRoot()) {
      long endTime = System.currentTimeMillis();
      double time = ((double) (endTime - startTime)) / 1000.0;
      System.out.println("ExecutionTime: " + time);

      List<Result> results = readResults(communicationLayer.getIbisCount());

      MinimumSpanningTree tree = new MinimumSpanningTree(communicationLayer, network, results, crashDetector.getCrashedNodes());
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

  private static void writeResults(int node, ChandyMisraNode chandyMisraNode) {
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

  private static List<Result> readResults(int ibisCount) {
    List<Result> results = new LinkedList<>();

    for (int i = 0; i < ibisCount; i++) {
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
