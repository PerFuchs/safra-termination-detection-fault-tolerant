package ibis.ipl.apps.safraExperiment.experiment;

import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
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

public class Experiment {
  private static Logger logger = Logger.getLogger(Experiment.class);

  public final static String experimentLoggerName = "safraExperimentLogger";

  private final static String experimentAppenderName = "experimentAppenderName";

  private Logger experimentLogger = Logger.getLogger(experimentLoggerName);

  private final Path outputFolder;

  private final CommunicationLayer communicationLayer;
  private final Network network;
  private boolean isFaultTolerant;

  private int nodeID;
  private int nodeCount;

  private List<Event> events;
  private SafraStatistics safraStatistics;

  public Experiment(Path outputFolder, CommunicationLayer communicationLayer, Network network, CrashDetector crashDetector, boolean isFaultTolerant) throws IOException {
    this.outputFolder = outputFolder;
    this.communicationLayer = communicationLayer;
    this.network = network;
    this.nodeID = communicationLayer.getID();
    this.nodeCount = communicationLayer.getIbisCount();
    this.isFaultTolerant = isFaultTolerant;
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

  private Path filePathForChandyMisraResults(int node) {
    return Paths.get(outputFolder.toString(), String.format("%04d.chandyMisra", node));
  }

  public boolean verify() throws IOException {
    boolean ret = true;
    SafraStatistics safraStatistics = getSafraStatistics();
    List<ChandyMisraResult> results = readChandyMisraResults();

    ret &= verifyChandyMisraResult(results, safraStatistics.getCrashedNodes());

    List<Event> events = getEvents();
    for (Event e : events) {
      if (e.getLevel() == Level.ERROR || e.getLevel() == Level.WARN) {
        logger.error(String.format("Logs contain error or warning: %s on %d", e.getEvent(), e.getNode()));
        writeToErrorFile(String.format("Logs contain error or warning: %s on %d", e.getEvent(), e.getNode()));
        ret = false;
        break;
      }
    }
    return ret;
  }

  public SafraStatistics getSafraStatistics() throws IOException {
    if (safraStatistics == null) {
      safraStatistics = new SafraStatistics(this, nodeCount, getEvents());
    }
    return safraStatistics;
  }

  static void writeToFile(Path filePath, String line) throws IOException {
    if (!line.endsWith("\n")) {
      line += "\n";
    }
    Files.write(filePath, line.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
  }

  void writeToWarnFile(String line) throws IOException {
    writeToFile(Paths.get(outputFolder.toString(), ".warn"), line);
  }

  void writeToErrorFile(String line)  throws  IOException {
    writeToFile(Paths.get(outputFolder.toString(), ".error"), line);
  }


  private boolean verifyChandyMisraResult(List<ChandyMisraResult> results, Set<Integer> crashedNodes) throws IOException {
    Tree tree = new Tree(communicationLayer, network, results, crashedNodes);
    Tree expectedTree = network.getSinkTree(crashedNodes);
    if (tree.equals(expectedTree)) {
      logger.info("Constructed and expected tree are equal.");
      return true;
    } else {
      logger.info(String.format("Weights are: %d %d", tree.getWeight(), expectedTree.getWeight()));
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

  private List<ChandyMisraResult> readChandyMisraResults() throws IOException {
    List<ChandyMisraResult> results = new LinkedList<>();

    for (int i = 0; i < nodeCount; i++) {
      logger.trace(String.format("Reading result %04d", i));

      Path path = filePathForChandyMisraResults(i);

      String[] r = Files.readAllLines(path, StandardCharsets.UTF_8).get(0).split(" ");
      results.add(new ChandyMisraResult(Integer.valueOf(r[0]), Integer.valueOf(r[1]), Integer.valueOf(r[2])));
    }
    return results;
  }

  public List<Event> getEvents() throws IOException {
    if (events == null) {
      events = readEvents();
    }
    return events;
  }

  private List<Event> readEvents() throws IOException {
    List<Event> events = new LinkedList<>();

    for (int i = 0; i < nodeCount; i++) {
      Path path = filePathForEvents(i);
      logger.trace("Reading events from file path: " + path.toString());

      List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      if (lines.isEmpty()) {
        logger.error(String.format("No events logged on node %d", i));
        writeToErrorFile(String.format("No events logged on node %d", i));
      }
      int lineNumber = 0;
      for (String e : lines) {
        events.add(Event.createEventFromLogLine(this, i, lineNumber, e));
        lineNumber++;
      }
    }

    StringBuilder sb = new StringBuilder();
    for (int i : Event.eventCreatedFor) {
      sb.append(i);
      sb.append(", ");
    }
    logger.trace(String.format("Event created for: %s", sb.toString()));

    return events;
  }

  private Path filePathForEvents(int node) {
    return Paths.get(outputFolder.toString(), String.format("%04d.log", node));
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
  }

  public void writeSafraStatitistics() throws IOException {
    getSafraStatistics().writeToCSVFile(Paths.get(outputFolder.toString(), "safraStatistics.csv"));
  }

  public void writeNetworkStatistics(Network network) throws IOException {
    Tree sinkTree = network.getSinkTree(getSafraStatistics().getCrashedNodes());

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
  }

  public boolean isFaultTolerant() {
    return isFaultTolerant;
  }
}
