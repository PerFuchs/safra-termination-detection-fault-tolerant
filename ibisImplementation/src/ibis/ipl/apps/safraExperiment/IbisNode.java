package ibis.ipl.apps.safraExperiment;

// File: $Id: IbisNode.java 6731 2007-11-05 22:38:04Z ndrost $

import ibis.ipl.*;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashPoint;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.experiment.Experiment;
import ibis.ipl.apps.safraExperiment.experiment.SafraStatistics;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.faultTolerant.SafraFT;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import ibis.ipl.apps.safraExperiment.utils.barrier.BarrierFactory;
import org.apache.log4j.*;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;


class IbisNode {
  static Logger logger = Logger.getLogger(IbisNode.class);
  static Ibis ibis;
  static Registry registry;



  public static void main(String[] args) throws IbisCreationFailedException, IOException, InterruptedException, ParseException {
    System.setErr(System.out);  // Redirect because DAS4 does not show err.

    ConsoleAppender consoleAppender = new ConsoleAppender(new PatternLayout("[%t] - %m%n"));
    BasicConfigurator.configure(consoleAppender);

//      Logger.getLogger("ibis").setLevel(Level.INFO);
    Logger.getLogger(IbisNode.class).setLevel(Level.TRACE);
    Logger.getLogger(CommunicationLayer.class).setLevel(Level.TRACE);
    Logger.getLogger(ChandyMisraNode.class).setLevel(Level.INFO);
    Logger.getLogger(SafraFT.class).setLevel(Level.INFO);
    Logger.getLogger(Experiment.class).setLevel(Level.INFO);
    Logger.getLogger(SafraStatistics.class).setLevel(Level.INFO);
    Logger.getLogger(CrashSimulator.class).setLevel(Level.DEBUG);
    Logger.getLogger(Network.class).setLevel(Level.INFO);
    Logger.getLogger(SynchronizedRandom.class).setLevel(Level.INFO);

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

    SynchronizedRandom synchronizedRandom = new SynchronizedRandom(ibis.identifier(), registry);
    logger.info(String.format("Pseudo random seed: %d", synchronizedRandom.getSeed()));  // To control all chose the same seed.

    CommunicationLayer communicationLayer = new CommunicationLayer(ibis, registry, porttype);

    BarrierFactory barrierFactory = new BarrierFactory(registry, signalHandler, communicationLayer);

    CrashDetector crashDetector = new CrashDetector();

    Set<CrashPoint> enabledCrashPoints = new HashSet<>();
    enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_TOKEN);
    enabledCrashPoints.add(CrashPoint.AFTER_SENDING_TOKEN);

    enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_BACKUP_TOKEN);
    enabledCrashPoints.add(CrashPoint.AFTER_SENDING_BACKUP_TOKEN);
//
    enabledCrashPoints.add(CrashPoint.BEFORE_RECEIVING_TOKEN);
//
    enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_BASIC_MESSAGE);
    enabledCrashPoints.add(CrashPoint.AFTER_SENDING_BASIC_MESSAGE);

    CrashSimulator crashSimulator = new CrashSimulator(communicationLayer, synchronizedRandom,
        0.2,true, enabledCrashPoints);
    communicationLayer.setCrashSimulator(crashSimulator);

    Network network = Network.getRandomOutdegreeNetwork(communicationLayer, synchronizedRandom);
//    Network network = Network.getLineNetwork(communicationLayer);
    network = network.combineWith(Network.getUndirectedRing(communicationLayer), 100000);

    Safra safraNode = new SafraFT(registry, signalHandler, communicationLayer, crashSimulator, crashDetector, communicationLayer.isRoot());
//    Safra safraNode = new SafraFS(registry, signalHandler, communicationLayer);

    ChandyMisraNode chandyMisraNode = new ChandyMisraNode(communicationLayer, network, crashDetector, safraNode);

    Experiment experiment = new Experiment(communicationLayer, network, crashDetector);

    communicationLayer.connectIbises(network, chandyMisraNode, safraNode, crashDetector, barrierFactory);
    logger.debug(String.format("%04d connected communication layer", communicationLayer.getID()));

    barrierFactory.getBarrier("Connected").await();

    safraNode.startAlgorithm();
    chandyMisraNode.startAlgorithm();

    logger.debug(String.format("%04d started algorithm", communicationLayer.getID()));

    safraNode.await();
    chandyMisraNode.terminate();

    experiment.writeChandyMisraResults(chandyMisraNode);
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
