package ibis.ipl.apps.safraExperiment;

// File: $Id: IbisNode.java 6731 2007-11-05 22:38:04Z ndrost $

import ibis.ipl.*;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.experiment.Experiment;
import ibis.ipl.apps.safraExperiment.experiment.SafraStatistics;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.faultTolerant.SafraFT;
import ibis.ipl.apps.safraExperiment.spanningTree.MinimumSpanningTree;
import ibis.ipl.apps.safraExperiment.spanningTree.Network;
import ibis.ipl.apps.safraExperiment.spanningTree.ChandyMisraResult;
import ibis.ipl.apps.safraExperiment.utils.barrier.BarrierFactory;
import org.apache.log4j.*;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;


class IbisNode {
  static Logger logger = Logger.getLogger(IbisNode.class);
  static Ibis ibis;
  static Registry registry;



  public static void main(String[] args) throws IbisCreationFailedException, IOException, InterruptedException, ParseException {
    System.setErr(System.out);  // Redirect because DAS4 does not show err.

    ConsoleAppender consoleAppender = new ConsoleAppender(new PatternLayout("[%t] - %m%n"));
    BasicConfigurator.configure(consoleAppender);

//      Logger.getLogger("ibis").setLevel(Level.INFO);
    Logger.getLogger(IbisNode.class).setLevel(Level.INFO);
    Logger.getLogger(CommunicationLayer.class).setLevel(Level.INFO);
    Logger.getLogger(SafraFT.class).setLevel(Level.INFO);
    Logger.getLogger(Experiment.class).setLevel(Level.INFO);
    Logger.getLogger(CrashSimulator.class).setLevel(Level.INFO);

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

    Experiment experiment = new Experiment(communicationLayer, network, crashDetector);

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
    chandyMisraNode.terminate();

    experiment.writeResults(chandyMisraNode);
    experiment.finalizeExperimentLogger();
    barrierFactory.getBarrier("ResultsWritten").await();

    if (communicationLayer.isRoot()) {
      long endTime = System.currentTimeMillis();
      double time = ((double) (endTime - startTime)) / 1000.0;
      System.out.println("ExecutionTime: " + time);

      experiment.verify();

      SafraStatistics ss = experiment.getSafraStatistics();
      System.out.println(String.format("Tokens: %d Backuptokens: %d Tokens after: %d", ss.getTokenSend(), ss.getBackupTokenSend(), ss.getTokenSendAfterTermination()));
      System.out.println(String.format("Crashed nodes: %s", crashDetector.getCrashedNodesString()));
      System.out.println("End");
    }

    barrierFactory.getBarrier("Done").await();

    signalHandler.stop();
    ibis.end();
  }

}
