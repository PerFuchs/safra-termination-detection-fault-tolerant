package ibis.ipl.apps.safraExperiment.experiment;

import ibis.ipl.apps.safraExperiment.BasicAlgorithm;
import ibis.ipl.apps.safraExperiment.BasicAlgorithms;
import ibis.ipl.apps.safraExperiment.afekKuttenYung.AfekKuttenYungStateMachine;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.experiment.afekKuttenYungVerification.AfekKuttenYungResult;
import ibis.ipl.apps.safraExperiment.experiment.afekKuttenYungVerification.AfekKuttenYungVerifier;
import ibis.ipl.apps.safraExperiment.experiment.afekKuttenYungVerification.IncorrectRootException;
import ibis.ipl.apps.safraExperiment.experiment.chandyMisraVerification.IncorrectDistanceException;
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
  
  private final BasicAlgorithms basicAlgorithmChoice;

  private int nodeID;

  public OnlineExperiment(Path outputFolder, CommunicationLayer communicationLayer, Network network, CrashSimulator crashSimulator, boolean isFaultTolerant, BasicAlgorithms basicAlgorithmChoice) throws IOException {
    super(outputFolder, outputFolder, communicationLayer.getIbisCount(), isFaultTolerant, TerminationDefinitions.EXTENDED);
    this.outputFolder = outputFolder;
    this.communicationLayer = communicationLayer;
    this.network = network;
    this.nodeID = communicationLayer.getID();
    this.crashSimulator = crashSimulator;
    this.basicAlgorithmChoice = basicAlgorithmChoice;
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

    if (basicAlgorithmChoice == BasicAlgorithms.CHANDY_MISRA) {
      List<ChandyMisraResult> results = readChandyMisraResults();
      ret &= verifyChandyMisraResult(results, safraStatistics.getCrashedNodes());
    } else {
      List<AfekKuttenYungResult> results = readAfekKuttenYungResults();
      ret &= verifyAfekKuttenYungResult(results, safraStatistics.getCrashedNodes());
    }
    return ret;
  }



  private boolean verifyAfekKuttenYungResult(List<AfekKuttenYungResult> results, Set<Integer> crashedNodes) throws IOException {
    List<AfekKuttenYungResult> validResults = new LinkedList<>();
    for (AfekKuttenYungResult r : results) {
      if (!crashedNodes.contains(r.node)) {
        validResults.add(r);
      }
    }

    try {
      AfekKuttenYungVerifier.check(validResults, network.getAliveNetwork(crashedNodes));
      logger.info("Afek Kutten Yung result is correct.");
      return true;
    } catch (IncorrectChannelUsedException e) {
      logger.error("Afek Kutten Yung uses non-existent channels");
      writeToErrorFile("Afek Kutten Yung uses non-existent channels");
      return false;
    } catch (IncorrectTreeException e) {
      logger.error("Afek Kutten Yung calculated an incorrect tree");
      writeToErrorFile("Afek Kutten Yung calculated an incorrect tree");
      return false;
    } catch (IncorrectDistanceException e) {
      logger.error("Afek Kutten Yung calculated incorrect weights");
      writeToErrorFile("Afek Kutten Yung calculated incorrect weights");
      return false;
    } catch (IncorrectRootException e) {
      logger.error("Afek Kutten Yung calculated incorrect root");
      writeToErrorFile("Afek Kutten Yung calculated incorrect root");
      return false;
    }
  }

  private boolean verifyChandyMisraResult(List<ChandyMisraResult> results, Set<Integer> crashedNodes) throws IOException {
    List<ChandyMisraResult> validResults = new LinkedList<>();
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
    } catch (IncorrectDistanceException e) {
      logger.error("Chandy Misra calculated incorrect weights");
      writeToErrorFile("Chandy Misra calculated incorrect weights");
      return false;
    }
  }

  public void writeBasicResults(BasicAlgorithm basicAlgorithm) throws IOException {
    if (basicAlgorithm instanceof ChandyMisraNode) {
      writeChandyMisraResults((ChandyMisraNode) basicAlgorithm);
    } else {
      writeAfekKuttenYungResult((AfekKuttenYungStateMachine) basicAlgorithm);
    }
  }

  private void writeChandyMisraResults(ChandyMisraNode chandyMisraNode) throws IOException {
    String str = String.format("%d %d %d %d\n", nodeID, chandyMisraNode.getParent(), chandyMisraNode.getDist(), chandyMisraNode.getParentEdgeWeight());

    Path path = filePathForChandyMisraResult(nodeID);
    byte[] strToBytes = str.getBytes();

    Files.write(path, strToBytes);

    logger.trace(String.format("%04d output written", nodeID));
  }


  private void writeAfekKuttenYungResult(AfekKuttenYungStateMachine afekKuttenYungStateMachine) throws IOException {
    String str = String.format("%d %d %d %d\n", nodeID, afekKuttenYungStateMachine.getParent(), afekKuttenYungStateMachine.getDistance(), afekKuttenYungStateMachine.getRoot());

    Path path = filePathForAfekKuttenYungResult(nodeID);
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
}
