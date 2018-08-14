package ibis.ipl.apps.safraExperiment.experiment;

import ibis.ipl.apps.safraExperiment.BasicAlgorithms;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.experiment.chandyMisraVerification.IncorrectChannelUsedException;
import ibis.ipl.apps.safraExperiment.experiment.chandyMisraVerification.IncorrectTreeException;
import ibis.ipl.apps.safraExperiment.experiment.chandyMisraVerification.IncorrectWeightException;
import ibis.ipl.apps.safraExperiment.experiment.chandyMisraVerification.Verifier;
import ibis.ipl.apps.safraExperiment.network.ChandyMisraResult;
import ibis.ipl.apps.safraExperiment.network.Channel;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.network.Tree;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Drives result verification after each experiment run.
 * <p>
 * * Verifies no errors are in the event log
 * * Verifies Chandy Misra result
 * * Generates statistics about Safra and warns if announce has been called to early
 * * Generates and write statistic about the networks
 */
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
    super(outputFolder, outputFolder, communicationLayer.getIbisCount(), isFaultTolerant, TerminationDefinitions.EXTENDED);
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

  public void writeNetwork() throws IOException {
    network.writeToFile(Paths.get(outputFolder.toString(), "network.txt"));
  }

  public Network readNetwork() throws IOException {
    return Network.fromFile(Paths.get(outputFolder.toString(), "network.txt"));
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
    Set<ChandyMisraResult> validResults = new HashSet<>();
    for (ChandyMisraResult r : results) {
      if (!crashedNodes.contains(r.node)) {
        validResults.add(r);
      }
    }

    try {
      Verifier.check(validResults, network.getAliveNetwork(crashedNodes), communicationLayer.getRoot());
      logger.info("Chandy Misra result is correct.");
      return true;
    } catch (IncorrectChannelUsedException e) {
      logger.error("Chandy Misra uses non-existent channels");
      writeToErrorFile("Chandy Misra uses non-existent channels");
      return false;
    } catch (IncorrectTreeException e) {
      logger.error("Chandy Misra calculated an incorrect tree");
      writeToErrorFile("Chandy Misra calculated an incorrect tree");
      return false;
    } catch (IncorrectWeightException e) {
      logger.error("Chandy Misra calculated incorrect weights");
      writeToErrorFile("Chandy Misra calculated incorrect weights");
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
   * <p>
   * The logger should not be used after.
   */
  public void finalizeExperimentLogger() {
    FileAppender fa = (FileAppender) experimentLogger.getAppender(experimentAppenderName);
    fa.close();
    experimentLogger.removeAppender(experimentAppenderName);
    logger.trace(String.format("%04d finalized experiment logger.", communicationLayer.getID()));
  }

  public void printNetworkStatistics(Network network) throws IOException {
    Tree sinkTree = network.getSinkTree(getSafraStatistics().getCrashedNodes());
    Set<Channel> networkChannels = network.getChannels();

    logger.info(String.format("Network Statistics: Number of channels %d", networkChannels.size()));

    Map<Integer, Set<Integer>> levels = sinkTree.getLevels();
    logger.info(String.format("Tree Statistics: Has %d levels", levels.size()));
    for (int level : levels.keySet()) {
      logger.info(String.format("Level %d has %d nodes", level, levels.get(level).size()));
    }
  }

  public void writeBasicResults(BasicAlgorithms basicAlgorithm) {

  }
}
