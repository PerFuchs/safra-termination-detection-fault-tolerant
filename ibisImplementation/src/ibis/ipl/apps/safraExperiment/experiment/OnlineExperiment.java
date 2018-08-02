package ibis.ipl.apps.safraExperiment.experiment;

import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.network.ChandyMisraResult;
import ibis.ipl.apps.safraExperiment.network.Channel;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.network.Tree;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class OnlineExperiment extends Experiment {
  private static Logger logger = Logger.getLogger(OnlineExperiment.class);

  public final static String experimentLoggerName = "safraExperimentLogger";

  private final static String experimentAppenderName = "experimentAppenderName";

  private Logger experimentLogger = Logger.getLogger(experimentLoggerName);

  private final Path outputFolder;

  private final CommunicationLayer communicationLayer;
  private final Network network;
  private CrashSimulator crashSimulator;

  private int nodeID;

  public OnlineExperiment(Path outputFolder, CommunicationLayer communicationLayer, Network network, CrashSimulator crashSimulator, boolean isFaultTolerant) throws IOException {
    super(outputFolder, outputFolder, communicationLayer.getIbisCount(), isFaultTolerant);
    this.outputFolder = outputFolder;
    this.communicationLayer = communicationLayer;
    this.network = network;
    this.nodeID = communicationLayer.getID();
    this.crashSimulator = crashSimulator;
    if (!outputFolder.toFile().exists()) {
      Files.createDirectories(outputFolder);
    }

    setupLogger();
  }

  private void setupLogger() throws IOException {
    experimentLogger.setLevel(Level.INFO);
    experimentLogger.setAdditivity(false);

    Path logFile = Paths.get(outputFolder.toString(), filePathForEvents(nodeID).toString());
    if (logFile.toFile().exists()) {
      Files.delete(logFile);
    }

    FileAppender fa = new FileAppender(new PatternLayout("%d{ISO8601} - %t - %p - %m%n"), filePathForEvents(nodeID).toString(), false);
    fa.setName(experimentAppenderName);

    experimentLogger.addAppender(fa);
  }

  public boolean verify() throws IOException {
    boolean ret = super.verify();
    SafraStatistics safraStatistics = getSafraStatistics();
    List<ChandyMisraResult> results = readChandyMisraResults();

    ret &= verifyChandyMisraResult(results, safraStatistics.getCrashedNodes(), crashSimulator.getCrashingNodes());
    return ret;
  }

  private boolean verifyChandyMisraResult(List<ChandyMisraResult> results, Set<Integer> crashedNodes, Set<Integer> nodesExpectedToCrash) throws IOException {
    Tree tree = new Tree(communicationLayer, network, results, crashedNodes);
    Tree expectedTree = network.getSinkTree(crashedNodes);

    if (expectedTree == null && !nodesExpectedToCrash.equals(crashedNodes)) {
      // Some nodes were expected to crash but did not. Now these have no connection to root
      tree = new Tree(communicationLayer, network, results, nodesExpectedToCrash);
      expectedTree = network.getSinkTree(nodesExpectedToCrash);
      logger.info("Some nodes were expected to crash but did not. Chandy Misra might constructs spuriously invalid results.");
      writeToWarnFile("Some nodes were expected to crash but did not. Chandy Misra might constructs spuriously invalid results.");
    }

    if (tree.equals(expectedTree) && tree.hasValidWeights()) {
      logger.info("Constructed and expected tree are equal.");
      return true;
    } else {
      logger.info(String.format("Weights are: %d %d", tree.getWeight(), expectedTree.getWeight()));
      if (!tree.hasValidWeights()) {
        logger.error("Chandy Misra calculated invalid weights.");
        writeToErrorFile("Chandy Misra calculated invalid weights.");
      }
      logger.error("Chandy Misra calculated incorrect result");
      logger.error(String.format("Constructed tree: %s", tree.toString()));
      logger.error(String.format("Expected tree: %s", expectedTree.toString()));

      writeToErrorFile(String.format("Weights are: %d %d", tree.getWeight(), expectedTree.getWeight()));
      writeToErrorFile("Chandy Misra calculated incorrect result");
      return false;
    }
  }

  public void writeChandyMisraResults(ChandyMisraNode chandyMisraNode) throws IOException {
    String str = String.format("%d %d %d\n", nodeID, chandyMisraNode.getParent(), chandyMisraNode.getDist());

    Path path = filePathForChandyMisraResults(nodeID);
    byte[] strToBytes = str.getBytes();

    Files.write(path, strToBytes);

    logger.trace(String.format("%04d output written", nodeID));
  }

  /**
   * Needs to be called before readEvents because otherwise file writes from other DAS4 nodes won't be readable - the
   * files appear to be empty.
   *
   * The logger should not be used after.
   */
  public void finalizeExperimentLogger() {
    FileAppender fa = (FileAppender) experimentLogger.getAppender(experimentAppenderName);
    fa.close();
    experimentLogger.removeAppender(experimentAppenderName);
    logger.trace(String.format("%04d finalized experiment logger.", communicationLayer.getID()));
  }

  public void writeNetworkStatistics(Network network) throws IOException {
    Tree sinkTree = network.getSinkTree(getSafraStatistics().getCrashedNodes());
    if (sinkTree != null) {
      Set<Channel> networkChannels = network.getChannels();
      Set<Channel> sinkTreeChannels = sinkTree.getChannels();

      Set<Channel> noneSinkTreeChannels = new HashSet<>(networkChannels);
      noneSinkTreeChannels.removeAll(sinkTreeChannels);

      StringBuilder networkStatistics = new StringBuilder();
      logger.info(String.format("Network Statistics: Channels: %d InTree: %d Other: %d", networkChannels.size(), sinkTreeChannels.size(), noneSinkTreeChannels.size()));
      networkStatistics.append(String.format("%d;%d;%d\n", networkChannels.size(), sinkTreeChannels.size(), noneSinkTreeChannels.size()));

      Map<Integer, Set<Integer>> levels = sinkTree.getLevels();
      logger.info(String.format("Tree Statistics: Has %d levels", levels.size()));
      networkStatistics.append(String.format("%d\n", levels.size()));
      for (int level : levels.keySet()) {
        logger.info(String.format("Level %d has %d nodes", level, levels.get(level).size()));
        networkStatistics.append(String.format("%d;%d\n", level, levels.get(level).size()));
      }

      Files.write(Paths.get(outputFolder.toString(), "network.csv"), networkStatistics.toString().getBytes());
    } else {
      if (!getSafraStatistics().getCrashedNodes().equals(crashSimulator.getCrashingNodes())) {
        logger.info("Could not construct sink tree because some nodes were expected to crash but did not.");
        writeToWarnFile("Could not construct sink tree because some nodes were expected to crash but did not. No networks statistics were written.");
      } else {
        throw new IllegalStateException("Could not construct sink tree");
      }
    }

  }
}
