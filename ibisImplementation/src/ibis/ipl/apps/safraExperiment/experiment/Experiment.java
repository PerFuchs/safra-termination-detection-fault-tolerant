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
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

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

    FileAppender fa = new FileAppender(new PatternLayout("%d{ISO8601} - %t - %p - %m%n"),
        filePathForEvents(nodeID).toString(), false);
    fa.setName(experimentAppenderName);
    fa.addFilter(new Filter() {
      @Override
      public int decide(LoggingEvent loggingEvent) {
        if (loggingEvent.getLevel().toInt() > Level.DEBUG_INT) {
          return ACCEPT;
        }
        return DENY;
      }
    });
    experimentLogger.addAppender(fa);
  }

  private Path filePathForResults(int node) {
    return Paths.get(outputFolder, String.format("%04d.statistics", node));
  }

  public boolean verify() throws ParseException {
    boolean ret = true;
    List<ChandyMisraResult> results = readStatistics();

    ret &= verifyChandyMisraResult(results);


    List<Event> logs = getEvents();
    for (Event e : logs) {
      if (e.getLevel() == Level.ERROR || e.getLevel() == Level.WARN) {
        System.err.println(String.format("Logs contain error or warning: %s on %d", e.getEvent(), e.getNode()));
        ret = false;
        break;
      }
    }
    return ret;
  }

  public SafraStatistics getSafraStatistics() throws ParseException {
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

  public void writeResults(ChandyMisraNode chandyMisraNode) {
    String str = String.format("%d %d %d\n", nodeID, chandyMisraNode.getParent(), chandyMisraNode.getDist());

    Path path = filePathForResults(nodeID);
    byte[] strToBytes = str.getBytes();

    try {
      Files.write(path, strToBytes);
    } catch (IOException e) {
      logger.error(String.format("Could not write output file: %d", nodeID));
    }
    logger.trace(String.format("%04d output written", nodeID));
  }

  private List<ChandyMisraResult> readStatistics() {
    List<ChandyMisraResult> results = new LinkedList<>();

    for (int i = 0; i < nodeCount; i++) {
      logger.trace(String.format("Reading result %04d", i));

      Path path = filePathForResults(i);

      try {
        String[] r = Files.readAllLines(path, StandardCharsets.UTF_8).get(0).split(" ");
        results.add(new ChandyMisraResult(Integer.valueOf(r[0]), Integer.valueOf(r[1]), Integer.valueOf(r[2])));
      } catch (IOException e) {
        logger.error(String.format("Could not read output file from: %d", i));
      }

    }
    return results;
  }

  public List<Event> getEvents() throws ParseException {
    if (events == null) {
      events = readEvents();
    }
    return events;
  }

  private List<Event> readEvents() throws ParseException {
    List<Event> events = new LinkedList<>();

    for (int i = 0; i < nodeCount; i++) {
      Path path = filePathForEvents(i);
      logger.trace("Reading events from file path: " + path.toString());

      try {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

        for (String e : lines) {
          // TODO bad way of reconizing a good event ;)

          if (e.startsWith("2018-")) {
            events.add(new Event(i, e));
          }
        }
      } catch (IOException e) {
        logger.error(String.format("Could not read output file from: %d", i));
      }

    }
    System.out.println("Event created for");
    for (int i : Event.eventCreatedFor) {
      System.out.print(i + ",");
    }

    return events;
  }

  private Path filePathForEvents(int node) {
    return Paths.get(outputFolder, String.format("%04d.log", node));
  }

  public void finalizeExperimentLogger() {
    FileAppender fa = (FileAppender) experimentLogger.getAppender(experimentAppenderName);
    fa.close();
    experimentLogger.removeAppender(experimentAppenderName);
  }
}
