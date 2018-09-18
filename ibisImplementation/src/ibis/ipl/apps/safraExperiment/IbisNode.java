package ibis.ipl.apps.safraExperiment;

import ibis.ipl.*;
import ibis.ipl.apps.safraExperiment.afekKuttenYung.AfekKuttenYungMessageFactory;
import ibis.ipl.apps.safraExperiment.afekKuttenYung.AfekKuttenYungRunningState;
import ibis.ipl.apps.safraExperiment.afekKuttenYung.AfekKuttenYungStateMachine;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.SynchronizerMessageFactory;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.MessageFactory;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashPoint;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.OnlineExperiment;
import ibis.ipl.apps.safraExperiment.experiment.SafraStatistics;
import ibis.ipl.apps.safraExperiment.experiment.afekKuttenYungVerification.AfekKuttenYungVerifier;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;
import ibis.ipl.apps.safraExperiment.network.Tree;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;
import ibis.ipl.apps.safraExperiment.safra.faultSensitive.SafraFS;
import ibis.ipl.apps.safraExperiment.safra.faultTolerant.SafraFT;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;
import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import ibis.ipl.apps.safraExperiment.utils.ThreadInteruptTimeout;
import ibis.ipl.apps.safraExperiment.utils.barrier.BarrierFactory;
import ibis.ipl.apps.safraExperiment.utils.barrier.MessageBarrier;
import org.apache.log4j.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;


class IbisNode {
  static Logger logger = Logger.getLogger(IbisNode.class);
  private final static Logger experimentLogger = Logger.getLogger(OnlineExperiment.experimentLoggerName);

  static Ibis ibis;
  static Registry registry;
  private static Path outputFolder;
  private static double faultPercentage;
  private static boolean faultTolerant;
  private static BasicAlgorithms basicAlgorithmChoice;
  private static SignalPollerThread signalHandler;
  private static CommunicationLayer communicationLayer;
  private static SynchronizedRandom synchronizedRandom;
  private static CrashSimulator crashSimulator;
  private static Network network;
  private static Safra safraNode;
  private static CrashDetector crashDetector;
  private static BasicAlgorithm basicAlgorithm;
  private static OnlineExperiment experiment;
  private static BarrierFactory barrierFactory;

  public static void main(String[] args) {
    try {
      long startTime = System.nanoTime();

      setupOutput();
      parseArgs(args);

      PortType porttype = new PortType(PortType.CONNECTION_MANY_TO_ONE, PortType.COMMUNICATION_RELIABLE, PortType.RECEIVE_AUTO_UPCALLS, PortType.SERIALIZATION_DATA, PortType.COMMUNICATION_FIFO);
      setupIBISAndWaitForPoolClosed(porttype);

      synchronizedRandom = new SynchronizedRandom(ibis.identifier(), registry);
      logger.debug(String.format("Pseudo random seed: %d", synchronizedRandom.getSeed()));

      setupCommunicationLayer(porttype);

      barrierFactory = new BarrierFactory(registry, signalHandler, communicationLayer);

      crashDetector = new CrashDetector();

      setupCrashSimulator();

      setupNetwork();

      experiment = new OnlineExperiment(outputFolder, communicationLayer, network, crashSimulator, faultTolerant, basicAlgorithmChoice);

      setupSafra();

      setupBasicAlgorithm();

      connectIbises();

      runToTermination();

      writeResults();

      rootNodeVerifyAndEvaluateResults();

      if (communicationLayer.isRoot()) {
        long endTime = System.nanoTime();
        double time = ((double) (endTime - startTime)) / 1000000000.0;
        System.out.println("ExecutionTime: " + time);
        System.out.println("End");

        // Copy the output log file
        Files.copy(Paths.get("./out.log"), Paths.get(outputFolder.toString(), "out.log"), StandardCopyOption.REPLACE_EXISTING);
      }

      barrierFactory.getBarrier("End").await();

      shutdownIbis();

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

  private static void parseArgs(String[] args) {
    validateArgs(args);
    outputFolder = Paths.get(args[0]);
    faultPercentage = Double.valueOf(args[1]);
    faultTolerant = args[2].equals("ft");
    basicAlgorithmChoice = args[3].equals("cm") ? BasicAlgorithms.CHANDY_MISRA : BasicAlgorithms.AFEK_KUTTEN_YUNG;
  }

  private static void validateArgs(String[] args) {
    if (args.length < 4) {
      System.err.println("Too less arguments. Use: <outputFolder> <faultPercentage> <fs|ft> <cm|aky");
      System.exit(1);
    }
    try {
      Double.valueOf(args[1]);
    } catch (NumberFormatException e) {
      System.err.println("The second argument should be a float");
      System.exit(1);
    }
    if (!(args[2].equals("ft") || args[2].equals("fs"))) {
      System.err.println("Use either 'fs' or 'ft' as third argument");
      System.exit(1);
    }
    if (!(args[3].equals("cm") || args[3].equals("aky"))) {
      System.err.println("Use either 'cm' or 'aky' as fourth argument");
      System.exit(1);
    }
  }

  private static void setupOutput() {
    System.setErr(System.out);  // Redirect because DAS4 does not show err.

    ConsoleAppender consoleAppender = new ConsoleAppender(new PatternLayout("[%t] - %m%n"));
    BasicConfigurator.configure(consoleAppender);

//      Logger.getLogger("ibis").setLevel(Level.INFO);
    Logger.getLogger(IbisNode.class).setLevel(Level.TRACE);
    Logger.getLogger(CommunicationLayer.class).setLevel(Level.TRACE);
    Logger.getLogger(ChandyMisraNode.class).setLevel(Level.INFO);
    Logger.getLogger(AfekKuttenYungRunningState.class).setLevel(Level.TRACE);
    Logger.getLogger(AlphaSynchronizer.class).setLevel(Level.TRACE);
    Logger.getLogger(AfekKuttenYungVerifier.class).setLevel(Level.TRACE);
    Logger.getLogger(SafraFT.class).setLevel(Level.TRACE);
    Logger.getLogger(SafraFS.class).setLevel(Level.TRACE);
    Logger.getLogger(OnlineExperiment.class).setLevel(Level.INFO);
    Logger.getLogger(SafraStatistics.class).setLevel(Level.DEBUG);
    Logger.getLogger(CrashSimulator.class).setLevel(Level.INFO);
    Logger.getLogger(Network.class).setLevel(Level.TRACE);
    Logger.getLogger(SynchronizedRandom.class).setLevel(Level.INFO);
    Logger.getLogger(MessageBarrier.class).setLevel(Level.INFO);
    Logger.getLogger(Tree.class).setLevel(Level.TRACE);
  }

  private static void setupIBISAndWaitForPoolClosed(PortType porttype) throws IbisCreationFailedException {
    IbisCapabilities s = new IbisCapabilities(IbisCapabilities.TERMINATION, IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED, IbisCapabilities.CLOSED_WORLD, IbisCapabilities.ELECTIONS_STRICT, IbisCapabilities.SIGNALS);

    ibis = IbisFactory.createIbis(s, null, porttype);
    logger.info(String.format("%s Created IBIS", ibis.identifier().toString()));

    registry = ibis.registry();

    signalHandler = new SignalPollerThread(registry);
    signalHandler.start();

    registry.waitUntilPoolClosed();
    logger.trace(String.format("%s Pool closed", ibis.identifier().toString()));
  }

  private static void setupCommunicationLayer(PortType porttype) {
    communicationLayer = new CommunicationLayer(ibis, registry, porttype);

    if (basicAlgorithmChoice == BasicAlgorithms.AFEK_KUTTEN_YUNG) {
      MessageFactory.registerFactory(new AfekKuttenYungMessageFactory());
      MessageFactory.registerFactory(new SynchronizerMessageFactory());
    }
  }

  private static void setupCrashSimulator() {
    Set<CrashPoint> enabledCrashPoints = new HashSet<>();
    enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_TOKEN);
    enabledCrashPoints.add(CrashPoint.AFTER_SENDING_TOKEN);

    enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_BACKUP_TOKEN);
    enabledCrashPoints.add(CrashPoint.AFTER_SENDING_BACKUP_TOKEN);

    enabledCrashPoints.add(CrashPoint.BEFORE_RECEIVING_TOKEN);

    enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_BASIC_MESSAGE);
    enabledCrashPoints.add(CrashPoint.AFTER_SENDING_BASIC_MESSAGE);

    crashSimulator = new CrashSimulator(communicationLayer, synchronizedRandom, faultPercentage, faultTolerant, enabledCrashPoints);
    communicationLayer.setCrashSimulator(crashSimulator);
  }

  private static void setupNetwork() {
    network = Network.getLineNetwork(communicationLayer);
//    network = Network.getRandomOutdegreeNetwork(communicationLayer, synchronizedRandom, crashSimulator.getCrashingNodes());
    network = network.combineWith(Network.getUndirectedRing(communicationLayer), 100000);

    communicationLayer.setNetwork(network);

    logger.trace("Constructed network");
  }

  private static void setupSafra() throws IOException {
    if (faultTolerant) {
      safraNode = new SafraFT(communicationLayer, crashSimulator, crashDetector, communicationLayer.isRoot());
    } else {
      safraNode = new SafraFS(communicationLayer, communicationLayer.isRoot());
    }
  }

  private static void setupBasicAlgorithm() {
    if (basicAlgorithmChoice == BasicAlgorithms.CHANDY_MISRA) {
      basicAlgorithm = new ChandyMisraNode(communicationLayer, network, crashDetector, safraNode);
    } else if (basicAlgorithmChoice == BasicAlgorithms.AFEK_KUTTEN_YUNG) {
      basicAlgorithm = new AfekKuttenYungStateMachine(communicationLayer, safraNode, crashDetector);
    } else {
      throw new IllegalArgumentException("Unknown basic algorithm");
    }
  }

  private static void connectIbises() throws IOException, InterruptedException {
    if (basicAlgorithmChoice == BasicAlgorithms.CHANDY_MISRA) {
      communicationLayer.connectIbises(network, (ChandyMisraNode) basicAlgorithm, null, safraNode, crashDetector, barrierFactory, crashSimulator);
    } else {
      communicationLayer.connectIbises(network, null, ((AfekKuttenYungStateMachine) basicAlgorithm).getSynchronizer(), safraNode, crashDetector, barrierFactory, crashSimulator);
    }

    logger.debug(String.format("%04d connected communication layer", communicationLayer.getID()));
    barrierFactory.getBarrier("Connected").await();
  }

  private static void runToTermination() throws IOException, InterruptedException {
    long maxExperimentTime = 90000;
    ThreadInteruptTimeout timeout = new ThreadInteruptTimeout(Thread.currentThread(), maxExperimentTime);
    Thread interuptThread = new Thread(timeout);
    interuptThread.start();

    try {
      OurTimer totalTime = new OurTimer();

      safraNode.startAlgorithm();
      basicAlgorithm.startAlgorithm();

      safraNode.await();
      timeout.clear();
      logger.debug(String.format("%04d Safra barrier broke", communicationLayer.getID()));

      totalTime.stopAndCreateTotalTimeSpentEvent();
    } catch (InterruptedException e){
      logger.error("Termination wasn't detected in 1:30 minutes.");
      experiment.writeToErrorFile("Termination wasn't detected in 1:30 minutes.");
    }
    try {
      basicAlgorithm.terminate();
    } catch (TerminationDetectedTooEarly e) {
      logger.error("Termination was detected to early");
      experimentLogger.error(Event.getTerminationDetectedToEarlyEvent());
    }
    Thread.sleep(5000);  // Give events after termination a chance to be logged
  }

  private static void writeResults() throws IOException, InterruptedException {
    experiment.writeBasicResults(basicAlgorithm);
    experiment.finalizeExperimentLogger();
    logger.debug(String.format("%04d Finished writing results", communicationLayer.getID()));
    barrierFactory.getBarrier("ResultsWritten").await();
  }

  private static void rootNodeVerifyAndEvaluateResults() throws IOException {
    if (communicationLayer.isRoot()) {
      logger.debug("Starting verfication and output processing.");
      experiment.writeNetwork();
      // Takes a long time for big networks skip it for them
//      if (communicationLayer.getIbisCount() <= 500) {
//        experiment.printNetworkStatistics(network);
//      }
      experiment.verify();

      experiment.writeSafraStatitistics();
      SafraStatistics ss = experiment.getSafraStatistics();

      System.out.println(String.format("Tokens: %d Backuptokens: %d Tokens after: %d Total Time: %f Time Spent for Safra: %f Time Spent for Safra after termination: %f Basic Time Spent: %f Token size: %d", ss.getTokenSend(), ss.getBackupTokenSend(), ss.getTokenSendAfterTermination(), ss.getTotalTimeSpent(), ss.getSafraTimeSpent(), ss.getSafraTimeSpentAfterTermination(), ss.getBasicTimeSpent(), ss.getTokenBytes()));
      System.out.println(String.format("Crashed nodes (%d): %s", ss.getNumberOfNodesCrashed(), ss.getCrashNodeString()));
    }
  }

  private static void shutdownIbis() throws IOException {
    registry.terminate();
    registry.waitUntilTerminated();

    signalHandler.stop();
    ibis.end();
  }
 }
