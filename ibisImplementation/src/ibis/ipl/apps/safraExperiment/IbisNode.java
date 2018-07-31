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
import ibis.ipl.apps.safraExperiment.network.Tree;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.faultSensitive.SafraFS;
import ibis.ipl.apps.safraExperiment.safra.faultTolerant.SafraFT;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;
import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import ibis.ipl.apps.safraExperiment.utils.barrier.BarrierFactory;
import ibis.ipl.apps.safraExperiment.utils.barrier.MessageBarrier;
import org.apache.log4j.*;

import java.io.IOException;
import java.nio.file.*;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;


class IbisNode {
  static Logger logger = Logger.getLogger(IbisNode.class);
  static Ibis ibis;
  static Registry registry;

  public static void main(String[] args) {
    try {
      System.setErr(System.out);  // Redirect because DAS4 does not show err.

      ConsoleAppender consoleAppender = new ConsoleAppender(new PatternLayout("[%t] - %m%n"));
      BasicConfigurator.configure(consoleAppender);

      Path outputFolder = Paths.get(args[0]);
      float crashPercentage = Float.valueOf(args[1]);
      boolean faultTolerant = args[2].equals("ft");

//      Logger.getLogger("ibis").setLevel(Level.INFO);
      Logger.getLogger(IbisNode.class).setLevel(Level.TRACE);
      Logger.getLogger(CommunicationLayer.class).setLevel(Level.INFO);
      Logger.getLogger(ChandyMisraNode.class).setLevel(Level.INFO);
      Logger.getLogger(SafraFT.class).setLevel(Level.INFO);
      Logger.getLogger(Experiment.class).setLevel(Level.INFO);
      Logger.getLogger(SafraStatistics.class).setLevel(Level.DEBUG);
      Logger.getLogger(CrashSimulator.class).setLevel(Level.INFO);
      Logger.getLogger(Network.class).setLevel(Level.INFO);
      Logger.getLogger(SynchronizedRandom.class).setLevel(Level.INFO);
      Logger.getLogger(MessageBarrier.class).setLevel(Level.INFO);
      Logger.getLogger(Tree.class).setLevel(Level.INFO);

      IbisCapabilities s = new IbisCapabilities(IbisCapabilities.TERMINATION, IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED, IbisCapabilities.CLOSED_WORLD, IbisCapabilities.ELECTIONS_STRICT, IbisCapabilities.SIGNALS);

      PortType porttype = new PortType(PortType.CONNECTION_MANY_TO_ONE, PortType.COMMUNICATION_RELIABLE, PortType.RECEIVE_AUTO_UPCALLS, PortType.SERIALIZATION_DATA, PortType.COMMUNICATION_FIFO);

      long startTime = System.nanoTime();

      ibis = IbisFactory.createIbis(s, null, porttype);
      registry = ibis.registry();
      logger.info(String.format("%s Created IBIS", ibis.identifier().toString()));

      SignalPollerThread signalHandler = new SignalPollerThread(registry);
      signalHandler.start();

      registry.waitUntilPoolClosed();
      logger.trace(String.format("%s Pool closed", ibis.identifier().toString()));

      SynchronizedRandom synchronizedRandom = new SynchronizedRandom(ibis.identifier(), registry);
      logger.debug(String.format("Pseudo random seed: %d", synchronizedRandom.getSeed()));  // To control all chose the same seed.

      CommunicationLayer communicationLayer = new CommunicationLayer(ibis, registry, porttype);

      BarrierFactory barrierFactory = new BarrierFactory(registry, signalHandler, communicationLayer);

      CrashDetector crashDetector = new CrashDetector();

      Set<CrashPoint> enabledCrashPoints = new HashSet<>();
      enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_TOKEN);
      enabledCrashPoints.add(CrashPoint.AFTER_SENDING_TOKEN);

      enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_BACKUP_TOKEN);
      enabledCrashPoints.add(CrashPoint.AFTER_SENDING_BACKUP_TOKEN);

      enabledCrashPoints.add(CrashPoint.BEFORE_RECEIVING_TOKEN);

      enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_BASIC_MESSAGE);
      enabledCrashPoints.add(CrashPoint.AFTER_SENDING_BASIC_MESSAGE);

      CrashSimulator crashSimulator = new CrashSimulator(communicationLayer, synchronizedRandom, crashPercentage, faultTolerant, enabledCrashPoints);
      communicationLayer.setCrashSimulator(crashSimulator);

      Network network = Network.getRandomOutdegreeNetwork(communicationLayer, synchronizedRandom, crashSimulator.getCrashingNodes());
      network = network.combineWith(Network.getUndirectedRing(communicationLayer), 100000);
      logger.trace("Constructed network");

      Experiment experiment = new Experiment(outputFolder, communicationLayer, network, crashSimulator, faultTolerant);

      Safra safraNode;
      if (faultTolerant) {
        safraNode = new SafraFT(registry, signalHandler, communicationLayer, crashSimulator, crashDetector, communicationLayer.isRoot());
      } else {
        safraNode = new SafraFS(registry, signalHandler, communicationLayer, communicationLayer.isRoot());
      }

      ChandyMisraNode chandyMisraNode = new ChandyMisraNode(communicationLayer, network, crashDetector, safraNode);

      communicationLayer.connectIbises(network, chandyMisraNode, safraNode, crashDetector, barrierFactory);
      logger.debug(String.format("%04d connected communication layer", communicationLayer.getID()));

      barrierFactory.getBarrier("Connected").await();

      OurTimer totalTime = new OurTimer();
      safraNode.startAlgorithm();
      chandyMisraNode.startAlgorithm();

      safraNode.await();
      chandyMisraNode.terminate();
      totalTime.stopAndCreateTotalTimeSpentEvent();

      experiment.writeChandyMisraResults(chandyMisraNode);
      experiment.finalizeExperimentLogger();

      logger.debug(String.format("%04d Finished writting results", communicationLayer.getID()));
      barrierFactory.getBarrier("ResultsWritten").await();

      if (communicationLayer.isRoot()) {
        logger.debug("Starting verfication and output processing.");
        // Takes a long time for big networks skip it for them
        if (communicationLayer.getIbisCount() <= 500) {
          experiment.writeNetworkStatistics(network);
        }
        experiment.verify();

        experiment.writeSafraStatitistics();
        SafraStatistics ss = experiment.getSafraStatistics();

        System.out.println(String.format("Tokens: %d Backuptokens: %d Tokens after: %d Total Time: %f Time Spent for Safra: %f Time Spent for Safra after termination: %f Basic Time Spent: %f Token size: %d", ss.getTokenSend(), ss.getBackupTokenSend(), ss.getTokenSendAfterTermination(), ss.getTotalTimeSpent(), ss.getSafraTimeSpent(), ss.getSafraTimeSpentAfterTermination(), ss.getBasicTimeSpent(), ss.getTokenBytes()));
        System.out.println(String.format("Crashed nodes (%d): %s", ss.getNumberOfNodesCrashed(), ss.getCrashNodeString()));

        long endTime = System.nanoTime();
        double time = ((double) (endTime - startTime)) / 1000000000.0;
        System.out.println("ExecutionTime: " + time);

        System.out.println("End");

        // Copy the output log file
        Files.copy(Paths.get("./out.log"), Paths.get(outputFolder.toString(), "out.log"), StandardCopyOption.REPLACE_EXISTING);
      }

      barrierFactory.getBarrier("Done").await();
      registry.terminate();
      registry.waitUntilTerminated();

      signalHandler.stop();
      ibis.end();
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace();
      try {
        ibis.end();
      } catch (IOException io) {
        io.printStackTrace();
      }
      System.exit(1);
    }
  }

}
