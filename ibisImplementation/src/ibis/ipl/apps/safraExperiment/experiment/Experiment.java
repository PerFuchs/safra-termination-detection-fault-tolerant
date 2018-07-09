package ibis.ipl.apps.safraExperiment.experiment;

import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.spanningTree.ChandyMisraResult;
import ibis.ipl.apps.safraExperiment.spanningTree.MinimumSpanningTree;
import ibis.ipl.apps.safraExperiment.spanningTree.Network;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;

public class Experiment {
  private static Logger logger = Logger.getLogger(Experiment.class);

  public final static String experimentLoggerName = "safraExperimentLogger";

  private final static String experimentAppenderName = "experimentAppenderName";
  private final static String outputFolder = "/var/scratch/pfs250/safraExperiment/";

  private Logger experimentLogger = Logger.getLogger(experimentLoggerName);

  private final CommunicationLayer communicationLayer;
  private final Network network;
  private CrashDetector crashDetector;

  private int nodeID;
  private int nodeCount;

  private List<Event> events;

  public Experiment(CommunicationLayer communicationLayer, Network network, CrashDetector crashDetector) throws IOException {
    this.communicationLayer = communicationLayer;
    this.network = network;
    this.nodeID = communicationLayer.getID();
    this.nodeCount = communicationLayer.getIbisCount();
    this.crashDetector = crashDetector;
    if (!new File(outputFolder).exists()) {
      Files.createDirectories(Paths.get(outputFolder));
    }

    setupLogger();
  }

  private void setupLogger() throws IOException {
    experimentLogger.setLevel(Level.INFO);
    experimentLogger.setAdditivity(false);

    Path logFile = Paths.get(outputFolder, filePathForEvents(nodeID).toString());
    if (logFile.toFile().exists()) {
      Files.delete(logFile);
    }

    FileAppender fa = new FileAppender(new PatternLayout("%d{ISO8601} - %t - %p - %m%n"), filePathForEvents(nodeID).toString(), false);
    fa.setName(experimentAppenderName);

    experimentLogger.addAppender(fa);
  }

  private Path filePathForChandyMisraResults(int node) {
    return Paths.get(outputFolder, String.format("%04d.chandyMisra", node));
  }

  public boolean verify() throws IOException {
    boolean ret = true;
    List<ChandyMisraResult> results = readChandyMisraResults();

    ret &= verifyChandyMisraResult(results);


    List<Event> events = getEvents();
    for (Event e : events) {
      if (e.getLevel() == Level.ERROR || e.getLevel() == Level.WARN) {
        logger.error(String.format("Logs contain error or warning: %s on %d", e.getEvent(), e.getNode()));
        ret = false;
        break;
      }
    }
    return ret;
  }

  public SafraStatistics getSafraStatistics() throws IOException {
    return new SafraStatistics(nodeCount, getEvents());
  }

  private boolean verifyChandyMisraResult(List<ChandyMisraResult> results) {
    MinimumSpanningTree tree = new MinimumSpanningTree(communicationLayer, network, results, crashDetector.getCrashedNodes());
    MinimumSpanningTree expectedTree = network.getSpanningTree(crashDetector.getCrashedNodes());
    if (tree.equals(expectedTree)) {
      logger.info("Constructed and expected tree are equal.");
      return true;
    } else {
      logger.error("Chandy Misra calculated incorrect result");
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
        logger.warn(String.format("No events logged on node %d", i));
      }
      int lineNumber = 0;
      for (String e : lines) {
        events.add(Event.createEventFromLogLine(i, lineNumber, e));
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
    return Paths.get(outputFolder, String.format("%04d.log", node));
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
}
