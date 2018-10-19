package ibis.ipl.apps.safraExperiment;

import ibis.ipl.*;
import ibis.ipl.apps.safraExperiment.afekKuttenYung.AfekKuttenYungRunningState;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.IbisDetectionService;
import ibis.ipl.apps.safraExperiment.communication.MessageUpcall;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.experiment.OnlineExperiment;
import ibis.ipl.apps.safraExperiment.experiment.SafraStatistics;
import ibis.ipl.apps.safraExperiment.experiment.afekKuttenYungVerification.AfekKuttenYungVerifier;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;
import ibis.ipl.apps.safraExperiment.network.Tree;
import ibis.ipl.apps.safraExperiment.safra.faultSensitive.SafraFS;
import ibis.ipl.apps.safraExperiment.safra.faultTolerant.SafraFT;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import ibis.ipl.apps.safraExperiment.utils.barrier.FileBasedBarrier;
import ibis.ipl.apps.safraExperiment.utils.barrier.MessageBarrier;
import ibis.ipl.apps.safraExperiment.utils.barrier.SignalledBarrier;
import org.apache.log4j.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;


class IbisNode {
  static Logger logger = Logger.getLogger(IbisNode.class);
  private final static Logger experimentLogger = Logger.getLogger(OnlineExperiment.experimentLoggerName);

  public static final Path generalOutputFile = Paths.get(String.format("/var/scratch/%s/safraExperiment.log", System.getProperty("user.name")));

  static Ibis ibis;
  static Registry registry;
  private static Path outputFolder;
  private static double faultPercentage;
  private static boolean faultTolerant;
  private static BasicAlgorithms basicAlgorithmChoice;
  private static SignalPollerThread signalHandler;
  private static SynchronizedRandom synchronizedRandom;
  private static IbisDetectionService detectionService;
  private static int repetitions;

  public static void main(String[] args) {
    try {
      System.setErr(System.out);  // Redirect because DAS4 does not show err.

      ConsoleAppender consoleAppender = new ConsoleAppender(new PatternLayout("%p - [%t] - %m%n"));
      BasicConfigurator.configure(consoleAppender);

      setupLoggingLevel();

      parseArgs(args);

      PortType porttype = new PortType(PortType.CONNECTION_MANY_TO_ONE, PortType.COMMUNICATION_RELIABLE, PortType.RECEIVE_AUTO_UPCALLS, PortType.SERIALIZATION_DATA, PortType.COMMUNICATION_FIFO);
      setupIBISAndWaitForPoolClosed(porttype);

      detectionService = new IbisDetectionService(ibis.identifier(), registry);

      synchronizedRandom = new SynchronizedRandom(ibis.identifier(), registry);
      logger.debug(String.format("Pseudo random seed: %d", synchronizedRandom.getSeed()));

      for (int i = 0; i < repetitions; i++) {
        logger.info(String.format("Starting repetition: %d", i));
        Path outputFolderForRun = outputFolder.resolve(String.format("%04d", i));
        ExperimentRun run = new ExperimentRun(i, outputFolderForRun, basicAlgorithmChoice, faultTolerant, faultPercentage, ibis, detectionService, signalHandler, synchronizedRandom);
        run.run(porttype);
        logger.info(String.format("Finishing repetition: %d", i));

        FileBasedBarrier endBarrier = new FileBasedBarrier("end", outputFolderForRun, detectionService.getMe(), detectionService.getIbises().length);
        endBarrier.await();
      }

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

  private static void deleteDirectory(Path directory) throws IOException {
    File d = directory.toFile();
    if (d.isDirectory()) {
      File[] fs = d.listFiles();
      if (fs != null) {
        for (File f : fs) {
          if (f.isDirectory()) {
            deleteDirectory(f.toPath());
          } else {
            Files.delete(f.toPath());
          }
        }
      }
      Files.delete(directory);
    }
    throw new IllegalArgumentException("Argument needs to be a directory");
  }

  private static void setupLoggingLevel() {
    Logger.getLogger(IbisNode.class).setLevel(Level.TRACE);
    Logger.getLogger(ExperimentRun.class).setLevel(Level.TRACE);
    Logger.getLogger(CommunicationLayer.class).setLevel(Level.INFO);
    Logger.getLogger(MessageUpcall.class).setLevel(Level.INFO);
    Logger.getLogger(ChandyMisraNode.class).setLevel(Level.TRACE);
    Logger.getLogger(AfekKuttenYungRunningState.class).setLevel(Level.TRACE);
    Logger.getLogger(AlphaSynchronizer.class).setLevel(Level.TRACE);
    Logger.getLogger(AfekKuttenYungVerifier.class).setLevel(Level.INFO);
    Logger.getLogger(SafraFT.class).setLevel(Level.TRACE);
    Logger.getLogger(SafraFS.class).setLevel(Level.INFO);
    Logger.getLogger(OnlineExperiment.class).setLevel(Level.INFO);
    Logger.getLogger(SafraStatistics.class).setLevel(Level.INFO);
    Logger.getLogger(CrashSimulator.class).setLevel(Level.INFO);
    Logger.getLogger(Network.class).setLevel(Level.INFO);
    Logger.getLogger(SynchronizedRandom.class).setLevel(Level.INFO);
    Logger.getLogger(MessageBarrier.class).setLevel(Level.INFO);
    Logger.getLogger(SignalledBarrier.class).setLevel(Level.INFO);
    Logger.getLogger(Tree.class).setLevel(Level.INFO);
    Logger.getLogger(CrashDetector.class).setLevel(Level.TRACE);
  }

  private static void parseArgs(String[] args) {
    validateArgs(args);
    outputFolder = Paths.get(args[0]);
    faultPercentage = Double.valueOf(args[1]);
    faultTolerant = args[2].equals("ft");
    basicAlgorithmChoice = args[3].equals("cm") ? BasicAlgorithms.CHANDY_MISRA : BasicAlgorithms.AFEK_KUTTEN_YUNG;
    repetitions = Integer.valueOf(args[4]);
  }

  private static void validateArgs(String[] args) {
    if (args.length < 5) {
      System.err.println("Too less arguments. Use: <outputFolder> <faultPercentage> <fs|ft> <cm|aky> <repetitions>");
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
    try {
      Integer.valueOf(args[4]);
    } catch (NumberFormatException e) {
      System.err.println("The repetition argument should be an integer");
      System.exit(1);
    }
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


  private static void shutdownIbis() throws IOException {
    registry.terminate();
    registry.waitUntilTerminated();

    signalHandler.stop();
    ibis.end();
  }
}
