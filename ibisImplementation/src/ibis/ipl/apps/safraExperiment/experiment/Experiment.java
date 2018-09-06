package ibis.ipl.apps.safraExperiment.experiment;

import ibis.ipl.apps.safraExperiment.experiment.afekKuttenYungVerification.AfekKuttenYungResult;
import ibis.ipl.apps.safraExperiment.network.ChandyMisraResult;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;

/**
 * Collection of code to analyse the produced event logs.
 */
public abstract class Experiment {
  private static final Logger logger = Logger.getLogger(Experiment.class);

  private final Path analysisFolder;
  private final Path outputFolder;
  private final TerminationDefinitions terminationDefinition;
  private int nodeCount;
  private final boolean isFaultTolerant;

  private List<Event> events;
  private SafraStatistics safraStatistics;

  Experiment(Path analysisFolder, Path outputFolder, int nodeCount, boolean isFaultTolerant, TerminationDefinitions terminationDefinition) {
    this.analysisFolder = analysisFolder;
    this.outputFolder = outputFolder;
    this.nodeCount = nodeCount;
    this.isFaultTolerant = isFaultTolerant;
    this.terminationDefinition = terminationDefinition;
  }

  static void writeToFile(Path filePath, String line) throws IOException {
    if (!line.endsWith("\n")) {
      line += "\n";
    }
    Files.write(filePath, line.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
  }

  public boolean verify() throws IOException {
    boolean ret = true;
    List<Event> events = getEvents();
    for (Event e : events) {
      if (e.getLevel() == Level.ERROR || e.getLevel() == Level.WARN) {
        logger.error(String.format("Logs contain error or warning: %s on %d", e.getEvent(), e.getNode()));
        writeToErrorFile(String.format("Logs contain error or warning: %s on %d", e.getEvent(), e.getNode()));
        ret = false;
      }
    }
    return ret;
  }

  Path filePathForChandyMisraResult(int node) {
    return Paths.get(analysisFolder.toString(), String.format("%04d.chandyMisra", node));
  }

  Path filePathForAfekKuttenYungResult(int node) {
    return analysisFolder.resolve(String.format("%04d.afekKuttenYung", node));
  }

  List<ChandyMisraResult> readChandyMisraResults() throws IOException {
    List<ChandyMisraResult> results = new LinkedList<>();

    for (int i = 0; i < nodeCount; i++) {
      logger.trace(String.format("Reading result %04d", i));

      Path path = filePathForChandyMisraResult(i);

      String[] r = Files.readAllLines(path, StandardCharsets.UTF_8).get(0).split(" ");
      results.add(new ChandyMisraResult(Integer.valueOf(r[0]), Integer.valueOf(r[1]), Integer.valueOf(r[2]), Integer.valueOf(r[3])));
    }
    return results;
  }

  List<AfekKuttenYungResult> readAfekKuttenYungResults() throws IOException {
    List<AfekKuttenYungResult> results = new LinkedList<>();

    for (int i = 0; i < nodeCount; i++) {
      logger.trace(String.format("Reading result %04d", i));

      Path path = filePathForAfekKuttenYungResult(i);

      String[] r = Files.readAllLines(path, StandardCharsets.UTF_8).get(0).split(" ");
      results.add(new AfekKuttenYungResult(Integer.valueOf(r[0]), Integer.valueOf(r[1]), Integer.valueOf(r[2]), Integer.valueOf(r[3])));
    }
    return results;
  }

  void writeToWarnFile(String line) throws IOException {
    writeToFile(Paths.get(outputFolder.toString(), ".warn"), line);
  }

  public void writeToErrorFile(String line)  throws  IOException {
    writeToFile(Paths.get(outputFolder.toString(), ".error"), line);
  }

  boolean isFaultTolerant() {
    return isFaultTolerant;
  }

  List<Event> getEvents() throws IOException {
    if (events == null) {
      events = readEvents();
    }
    return events;
  }

  Path filePathForEvents(int node) {
    return Paths.get(analysisFolder.toString(), String.format("%04d.log", node));
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
        Event event = Event.createEventFromLogLine(this, i, lineNumber, e);
        if (event != null) {
          events.add(event);
        }
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

  public void writeSafraStatitistics() throws IOException {
    getSafraStatistics().writeToCSVFile(Paths.get(outputFolder.toString(), "safraStatistics.csv"));
  }

  public SafraStatistics getSafraStatistics() throws IOException {
    if (safraStatistics == null) {
      safraStatistics = new SafraStatistics(this, nodeCount, getEvents(), terminationDefinition);
    }
    return safraStatistics;
  }

}
