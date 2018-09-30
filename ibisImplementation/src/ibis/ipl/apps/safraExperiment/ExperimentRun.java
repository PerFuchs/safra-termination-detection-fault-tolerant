package ibis.ipl.apps.safraExperiment;

import ibis.ipl.*;
import ibis.ipl.apps.safraExperiment.afekKuttenYung.AfekKuttenYungMessageFactory;
import ibis.ipl.apps.safraExperiment.afekKuttenYung.AfekKuttenYungRunningState;
import ibis.ipl.apps.safraExperiment.afekKuttenYung.AfekKuttenYungStateMachine;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.SynchronizerMessageFactory;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.IbisDetectionService;
import ibis.ipl.apps.safraExperiment.communication.MessageFactory;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashException;
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
import ibis.ipl.apps.safraExperiment.utils.DeadlockDetector;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;
import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import ibis.ipl.apps.safraExperiment.utils.ThreadInteruptTimeout;
import ibis.ipl.apps.safraExperiment.utils.barrier.BarrierFactory;
import ibis.ipl.apps.safraExperiment.utils.barrier.MessageBarrier;
import ibis.ipl.apps.safraExperiment.utils.barrier.SignalledBarrier;
import org.apache.log4j.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;


class ExperimentRun {
  static Logger logger = Logger.getLogger(ExperimentRun.class);
  private final static Logger experimentLogger = Logger.getLogger(OnlineExperiment.experimentLoggerName);

  private final Ibis ibis;
  private Registry registry;
  private final int repetition_number;
  private Path outputFolder;
  private double faultPercentage;
  private boolean faultTolerant;
  private BasicAlgorithms basicAlgorithmChoice;
  private SignalPollerThread signalHandler;
  private CommunicationLayer communicationLayer;
  private SynchronizedRandom synchronizedRandom;
  private CrashSimulator crashSimulator;
  private Network network;
  private Safra safraNode;
  private CrashDetector crashDetector;
  private BasicAlgorithm basicAlgorithm;
  private OnlineExperiment experiment;
  private BarrierFactory barrierFactory;
  private IbisDetectionService detectionService;

  public ExperimentRun(int repetition_number, Path outputFolder, BasicAlgorithms basicAlgorithmChoice, boolean faultTolerant, double faultPercentage, Ibis ibis, IbisDetectionService detectionService, SignalPollerThread signalHandler, SynchronizedRandom synchronizedRandom) {
    this.repetition_number = repetition_number;
    this.outputFolder = outputFolder;
    this.faultTolerant = faultTolerant;
    this.faultPercentage = faultPercentage;
    this.ibis = ibis;
    this.registry = ibis.registry();
    this.detectionService = detectionService;
    this.basicAlgorithmChoice = basicAlgorithmChoice;
    this.signalHandler = signalHandler;
    this.synchronizedRandom = synchronizedRandom;
  }

  public void run(PortType portType) throws IOException, InterruptedException {
    long startTime = System.nanoTime();

    setupCommunicationLayer(portType);

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

      copy_log_file();
    }

    barrierFactory.getBarrier("End").await();
    barrierFactory.close();
    communicationLayer.close();
  }

  private void copy_log_file() throws IOException {
    BufferedReader br = Files.newBufferedReader(Paths.get("./out.log"), Charset.defaultCharset());
    BufferedWriter bw = Files.newBufferedWriter(outputFolder.resolve("out.log"), Charset.defaultCharset());
    String line;

    String starting_repetition_line = String.format("Starting repetition: %d", repetition_number);
    boolean part_of_this_repetition_log = false;

    while ((line = br.readLine()) != null) {
      if (part_of_this_repetition_log || line.endsWith(starting_repetition_line)) {
        part_of_this_repetition_log = true;
      }
      if (part_of_this_repetition_log) {
        bw.write(line);
        bw.newLine();
      }
    }
    br.close();
    bw.close();
  }

  private void setupCommunicationLayer(PortType porttype) {
    communicationLayer = new CommunicationLayer(detectionService.getIbises(), ibis, detectionService.getMe(), porttype);

    if (basicAlgorithmChoice == BasicAlgorithms.AFEK_KUTTEN_YUNG) {
      MessageFactory.registerFactory(new AfekKuttenYungMessageFactory());
      MessageFactory.registerFactory(new SynchronizerMessageFactory());
    }
  }

  private void setupCrashSimulator() {
    Set<CrashPoint> enabledCrashPoints = new HashSet<>();
    enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_TOKEN);
    enabledCrashPoints.add(CrashPoint.AFTER_SENDING_TOKEN);

    enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_BACKUP_TOKEN);
    enabledCrashPoints.add(CrashPoint.AFTER_SENDING_BACKUP_TOKEN);

    enabledCrashPoints.add(CrashPoint.BEFORE_RECEIVING_TOKEN);

    enabledCrashPoints.add(CrashPoint.BEFORE_SENDING_BASIC_MESSAGE);
    enabledCrashPoints.add(CrashPoint.AFTER_SENDING_BASIC_MESSAGE);

    // TODO test and use for CM as well
    boolean enableCrashException = basicAlgorithmChoice == BasicAlgorithms.AFEK_KUTTEN_YUNG;

    crashSimulator = new CrashSimulator(communicationLayer, synchronizedRandom, faultPercentage, faultTolerant, enabledCrashPoints, enableCrashException);
    communicationLayer.setCrashSimulator(crashSimulator);
  }

  private void setupNetwork() {
//    network = Network.getLineNetwork(communicationLayer);
    network = Network.getRandomOutdegreeNetwork(communicationLayer.getIbisCount(), synchronizedRandom);
    network = network.combineWith(Network.getUndirectedRing(communicationLayer), 100000);
    network = network.combineWith(Network.getFailSafeNetwork(network, crashSimulator.getCrashingNodes(), getExpectedRoot(), synchronizedRandom), 40000);

    communicationLayer.setNetwork(network);

    if (communicationLayer.isRoot()) {
      if (!network.getUnconnectedNodes(getExpectedRoot()).isEmpty()) {
        logger.warn("Fail safe did not work");
      } else {
        logger.trace("Fail safe did work");
      }
    }

    logger.trace("Constructed network");
  }

  private int getExpectedRoot() {
    if (basicAlgorithmChoice == BasicAlgorithms.CHANDY_MISRA) {
      return communicationLayer.getRoot();
    } else if (basicAlgorithmChoice == BasicAlgorithms.AFEK_KUTTEN_YUNG) {
      Set<Integer> survivingNodes = new HashSet<>();
      for (int i = 0; i < communicationLayer.getIbisCount(); i++) {
        if (!crashSimulator.getCrashingNodes().contains(i)) {
          survivingNodes.add(i);
        }
      }
      return Collections.max(survivingNodes);
    } else {
      throw new IllegalStateException("Unknown basic algorithm");
    }
  }

  private void setupSafra() throws IOException {
    boolean isBasicInitiator = communicationLayer.isRoot();
    if (basicAlgorithmChoice == BasicAlgorithms.AFEK_KUTTEN_YUNG) {
      isBasicInitiator = true;
    }

    if (faultTolerant) {
      safraNode = new SafraFT(communicationLayer, crashSimulator, crashDetector, isBasicInitiator);
    } else {
      safraNode = new SafraFS(communicationLayer, isBasicInitiator);
    }
  }

  private void setupBasicAlgorithm() {
    if (basicAlgorithmChoice == BasicAlgorithms.CHANDY_MISRA) {
      basicAlgorithm = new ChandyMisraNode(communicationLayer, network, crashDetector, safraNode);
    } else if (basicAlgorithmChoice == BasicAlgorithms.AFEK_KUTTEN_YUNG) {
      basicAlgorithm = new AfekKuttenYungStateMachine(communicationLayer, safraNode, crashDetector);
    } else {
      throw new IllegalArgumentException("Unknown basic algorithm");
    }
  }

  private void connectIbises() throws IOException, InterruptedException {
    if (basicAlgorithmChoice == BasicAlgorithms.CHANDY_MISRA) {
      communicationLayer.connectIbises(network, (ChandyMisraNode) basicAlgorithm, null, safraNode, crashDetector, barrierFactory, crashSimulator);
    } else {
      communicationLayer.connectIbises(network, null, ((AfekKuttenYungStateMachine) basicAlgorithm).getSynchronizer(), safraNode, crashDetector, barrierFactory, crashSimulator);
    }

    logger.debug(String.format("%04d connected communication layer", communicationLayer.getID()));
    barrierFactory.getBarrier("Connected").await();
  }

  private void runToTermination() throws IOException, InterruptedException {
    long maxExperimentTime = 600000;
    ThreadInteruptTimeout timeout = new ThreadInteruptTimeout(Thread.currentThread(), maxExperimentTime);
    Thread interuptThread = new Thread(timeout);
    interuptThread.start();

    try {
      OurTimer totalTime = new OurTimer();

      try {
        logger.trace(String.format("%04d Starting safra", communicationLayer.getID()));
        safraNode.startAlgorithm();
        logger.trace(String.format("%04d Starting basic algorithm", communicationLayer.getID()));
        basicAlgorithm.startAlgorithm();
      } catch (CrashException e) {
        // Pass
      }

      safraNode.await();
      timeout.clear();
      logger.debug(String.format("%04d Safra barrier broke", communicationLayer.getID()));

      totalTime.stopAndCreateTotalTimeSpentEvent();
    } catch (InterruptedException e) {
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

  private void writeResults() throws IOException, InterruptedException {
    experiment.writeBasicResults(basicAlgorithm);
    experiment.finalizeExperimentLogger();
    logger.debug(String.format("%04d Finished writing results", communicationLayer.getID()));
    barrierFactory.getBarrier("ResultsWritten").await();
  }

  private void rootNodeVerifyAndEvaluateResults() throws IOException {
    if (communicationLayer.isRoot()) {
      logger.debug("Starting verfication and output processing.");
      experiment.writeNetwork();
      experiment.verify();

      experiment.writeSafraStatitistics();
      SafraStatistics ss = experiment.getSafraStatistics();

      System.out.println(String.format("Tokens: %d Backuptokens: %d Tokens after: %d Total Time: %f Time Spent for Safra: %f Time Spent for Safra after termination: %f Basic Time Spent: %f Token size: %d", ss.getTokenSend(), ss.getBackupTokenSend(), ss.getTokenSendAfterTermination(), ss.getTotalTimeSpent(), ss.getSafraTimeSpent(), ss.getSafraTimeSpentAfterTermination(), ss.getBasicTimeSpent(), ss.getTokenBytes()));
      System.out.println(String.format("Crashed nodes (%d): %s", ss.getNumberOfNodesCrashed(), ss.getCrashNodeString()));
    }
  }
}
