package ibis.ipl.apps.safraExperiment;

// File: $Id: IbisNode.java 6731 2007-11-05 22:38:04Z ndrost $

import ibis.ipl.*;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.faultTolerant.SafraFT;
import ibis.ipl.apps.safraExperiment.spanningTree.MinimumSpanningTree;
import ibis.ipl.apps.safraExperiment.spanningTree.Network;
import ibis.ipl.apps.safraExperiment.spanningTree.Result;
import ibis.ipl.apps.safraExperiment.utils.barrier.BarrierFactory;
import org.apache.log4j.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;


class IbisNode {
  static Logger logger = Logger.getLogger(IbisNode.class);
  static Ibis ibis;
  static Registry registry;



  public static void main(String[] args) throws IbisCreationFailedException, IOException, InterruptedException {
    BasicConfigurator.configure(new ConsoleAppender(new PatternLayout("[%t] - %m%n")));

//      Logger.getLogger("ibis").setLevel(Level.INFO);

    System.setErr(System.out);  // Redirect because DAS4 does not show err.

    Logger.getLogger(IbisNode.class).setLevel(Level.DEBUG);
    Logger.getLogger(CommunicationLayer.class).setLevel(Level.TRACE);
    Logger.getLogger(SafraFT.class).setLevel(Level.TRACE);

    IbisCapabilities s = new IbisCapabilities(
        IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED,
        IbisCapabilities.CLOSED_WORLD,
        IbisCapabilities.ELECTIONS_STRICT,
        IbisCapabilities.SIGNALS);

    PortType porttype = new PortType(
        PortType.CONNECTION_MANY_TO_ONE,
        PortType.COMMUNICATION_RELIABLE,
        PortType.RECEIVE_AUTO_UPCALLS,
        PortType.SERIALIZATION_DATA,
        PortType.COMMUNICATION_FIFO);

    long startTime = System.currentTimeMillis();

    ibis = IbisFactory.createIbis(s, null, porttype);
    registry = ibis.registry();
    logger.info(String.format("%s Created IBIS", ibis.identifier().toString()));

    SignalPollerThread signalHandler = new SignalPollerThread(registry);
    signalHandler.start();

    registry.waitUntilPoolClosed();
    logger.trace(String.format("%s Pool closed", ibis.identifier().toString()));

    CommunicationLayer communicationLayer = new CommunicationLayer(ibis, registry, porttype);

    BarrierFactory barrierFactory = new BarrierFactory(registry, signalHandler, communicationLayer);

    CrashDetector crashDetector = new CrashDetector();
    CrashSimulator crashSimulator = new CrashSimulator(communicationLayer, true);

    Network network = Network.getLineNetwork(communicationLayer, crashSimulator);
    network = network.combineWith(Network.getUndirectedRing(communicationLayer, crashSimulator), 3000);

    Safra safraNode = new SafraFT(registry, signalHandler, communicationLayer, crashDetector, communicationLayer.isRoot());
//    Safra safraNode = new SafraFS(registry, signalHandler, communicationLayer);

    ChandyMisraNode chandyMisraNode = new ChandyMisraNode(communicationLayer, network, crashDetector, safraNode);

    communicationLayer.connectIbises(network, chandyMisraNode, safraNode, crashDetector, barrierFactory);
    logger.debug(String.format("%04d connected communication layer", communicationLayer.getID()));

    if (!barrierFactory.signalBarrierWorking()) {
      // Barrier relies on messages. Lets wait until all nodes have there channels setup.
      Thread.sleep(10000);
    }

    barrierFactory.getBarrier("Connected").await();

    safraNode.startAlgorithm();
    chandyMisraNode.startAlgorithm();

    logger.debug(String.format("%04d started algorithm", communicationLayer.getID()));

    if (communicationLayer.getIbisCount() > 300) {
      Thread.sleep(2000);
    } else {
      Thread.sleep(100);
    }

    crashSimulator.triggerLateCrash();
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
      logger.error(String.format("Could not write output file: %d", node));
    }
    logger.trace(String.format("%04d output written", node));
  }

  private static List<Result> readResults(int ibisCount) {
    List<Result> results = new LinkedList<>();

    for (int i = 0; i < ibisCount; i++) {
      logger.trace(String.format("Reading result %04d", i));

      Path path = Paths.get(filePathForResults(i));

      try {
        String[] r = Files.readAllLines(path, StandardCharsets.UTF_8).get(0).split(" ");
        results.add(new Result(Integer.valueOf(r[0]), Integer.valueOf(r[1]), Integer.valueOf(r[2])));
      } catch (IOException e) {
        logger.error(String.format("Could not read output file from: %d", i));
      }

    }
    return results;
  }
}
