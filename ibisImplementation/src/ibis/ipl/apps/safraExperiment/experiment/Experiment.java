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
import java.util.LinkedList;
import java.util.List;

public class Experiment {
  private static Logger logger = Logger.getLogger(Experiment.class);

  private static String outputFolder = "/var/scratch/pfs250/safraExperiment/";
  private final CommunicationLayer communicationLayer;
  private final Network network;

  private int nodeID;
  private int nodeCount;
  private CrashDetector crashDetector;

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
    Path logFile = Paths.get(outputFolder, String.format("%d.log", nodeID));
    if (logFile.toFile().exists()) {
      Files.delete(logFile);
    }

    Logger root = Logger.getRootLogger();
    FileAppender fa = new FileAppender(new PatternLayout("%d{ISO8601} - %t - %p: %m%n"),
        Paths.get(outputFolder, String.format("%d.log", nodeID)).toString());
    fa.addFilter(new Filter() {
      @Override
      public int decide(LoggingEvent loggingEvent) {
        if (loggingEvent.getLevel().toInt() > Level.DEBUG_INT) {
          return ACCEPT;
        }
        return DENY;
      }
    });
    root.addAppender(fa);
  }

  private Path filePathForResults(int node) {
    return Paths.get(outputFolder, String.format("%04d.statistics", node));
  }

  public boolean verify() {
    boolean ret = true;
    List<ChandyMisraResult> results = readResults();

    ret &= verifyChandyMisraResult(results);


    // TODO verify logs
    return ret;
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

  private List<ChandyMisraResult> readResults() {
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
}
