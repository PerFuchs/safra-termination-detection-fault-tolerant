package ibis.ipl.apps.safraExperiment.experiment;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.*;

public class SafraStatistics {
  private final static Logger logger = Logger.getLogger(SafraStatistics.class);

  private long totalTime;
  private long safraTimeSpent;
  private long basicTimeSpent;
  private long safratTimeSpentAfterTermination;
  private int backupTokenSend;
  private int tokenSendAfterTermination;
  private int tokenSend;
  private long tokenBytes;
  private Set<Integer> crashedNodes = new HashSet<>();

  public SafraStatistics(Experiment experiment, int numberOfNodes, List<Event> events) throws IOException {
    Collections.sort(events);
    logger.trace("Events sorted");

    boolean terminated = false;
    tokenSend = 0;
    tokenSendAfterTermination = 0;
    tokenBytes = 0;

    Event lastParentCrashDetected = null;
    boolean lastParentCrashDetectedEncountered = false;

    totalTime = 0;
    safraTimeSpent = 0;
    basicTimeSpent = 0;
    safratTimeSpentAfterTermination = 0;

    // For the fault sensitive variant only the 0th inner index is used.
    int[][] nodeSums = new int[numberOfNodes][numberOfNodes];
    for (int i = 0; i < nodeSums.length; i++) {
      for (int j = 0; j < nodeSums[i].length; j++) {
        nodeSums[i][j] = 0;
      }
    }
    boolean[] nodeActiveStatus = new boolean[numberOfNodes];
    for (int i = 0; i < numberOfNodes; i++) {
      nodeActiveStatus[i] = false;
    }

    for (Event e : events) {
      if (e.isNodeCrashed()) {
        crashedNodes.add(e.getNode());
      }
      if (e.isParentCrashDetected()) {
        lastParentCrashDetected = e;
      }
      if (e.isTotalTimeSpentEvent()) {
        totalTime += e.getTimeSpent();
      }
      if (e.isBasicTimeSpentEvent()) {
        basicTimeSpent += e.getTimeSpent();
      }
    }

    Set<Integer> currentlyCrashedNodes = new HashSet<>();

    if (lastParentCrashDetected == null) {  // This can be the case if no nodes crashes
      lastParentCrashDetectedEncountered = true;
    }

    int i = 0;
    for (Event e : events) {
//      logger.trace(i++);
      logger.trace(String.format("Processing event %d %s", e.getNode(), e.getEvent()));
      if (terminated && (e.isActiveStatusChange() || e.isMessageCounterUpdate())) {
        logger.error(String.format("Basic event happened  on node %d after termination: %s",
            e.getNode(), e.getEvent()));
        experiment.writeToErrorFile(String.format("Basic event happened  on node %d after termination: %s",
            e.getNode(), e.getEvent()));
      }
      if (e == lastParentCrashDetected) {
        lastParentCrashDetectedEncountered = true;
        terminated |= hasTerminated(nodeSums, nodeActiveStatus, currentlyCrashedNodes, lastParentCrashDetectedEncountered);
      }
      if (e.isNodeCrashed()) {
        currentlyCrashedNodes.add(e.getNode());
        terminated |= hasTerminated(nodeSums, nodeActiveStatus, currentlyCrashedNodes, lastParentCrashDetectedEncountered);
      }
      if (e.isActiveStatusChange()) {
        nodeActiveStatus[e.getNode()] = e.getActiveStatus();
        terminated |= hasTerminated(nodeSums, nodeActiveStatus, currentlyCrashedNodes, lastParentCrashDetectedEncountered);
      }
      if (e.isMessageCounterUpdate()) {
        if (experiment.isFaultTolerant()) {
          nodeSums[e.getNode()][e.getSafraMessageCounterUpdateIndex()] = e.getSafraMessageCounterUpdateValue();
        } else {
          nodeSums[e.getNode()][0] += e.getSafraMessageCounterUpdateValue();
        }

        terminated |= hasTerminated(nodeSums, nodeActiveStatus, currentlyCrashedNodes, lastParentCrashDetectedEncountered);
      }
      if (e.isTokenSend()) {
        tokenSend++;
        tokenBytes += e.getTokenSize();
        if (terminated) {
          tokenSendAfterTermination++;
        }
      }
      if (e.isSafraTimeSpentEvent() && !currentlyCrashedNodes.contains(e.getNode())) {
        safraTimeSpent += e.getTimeSpent();
        if (terminated) {
          safratTimeSpentAfterTermination += e.getTimeSpent();
        }
      }

      if (e.isReduceSafraTime()) {
        logger.trace(String.format("Reduce Safra time: %d", e.getTimeSpent()));
        safraTimeSpent -= e.getTimeSpent();
      }
      if (e.isBackupTokenSend()) {
        backupTokenSend++;
        // Backup tokens can be issued after termination this behaviour is accounted for because every backupTokenSend
        // event has a corresponding tokenSend event.
       }
    }
  }

  /**
   * Determines if the basic algorithm has terminated.
   *
   * Checks if all nodes are passive and the sum of all send and received messages in the system is zero ignoring
   * messages from and to crashed nodes.
   *
   * Also termination cannot be reached before final fault. Furthermore, termination cannot be reached before the
   * last event of a node detecting it's parent crashing (and repairing this)
   *
   * @return if the system has terminated.
   */
  private boolean hasTerminated(int[][] nodeSums,
                                boolean[] nodeActiveStatus,
                                Set<Integer> currentlyCrashedNodes,
                                boolean lastParentCrashEventEncountered) {
    if (!lastParentCrashEventEncountered) {
      return false;
    }
    logger.trace("Trying active");
    for (int i = 0; i < nodeActiveStatus.length; i++) {
      if (nodeActiveStatus[i] && !currentlyCrashedNodes.contains(i)) {
        return false;
      }
    }
    logger.trace("Trying sum");

    int sum = 0;

    // nodeSums and nodeActiveStatus are of the same size
    for (int i = 0; i < nodeSums.length; i++) {
      if (!currentlyCrashedNodes.contains(i)) {
        int[] sums = nodeSums[i];
        for (int j = 0; j < sums.length; j++) {  // The fault sensitive version uses only the 0th index but that's fine as all the others are initialized to 0
          if (!currentlyCrashedNodes.contains(j)) {
            sum += sums[j];
          }
        }
      }
    }
    logger.trace("Sum: " + sum);
    if (sum == 0) {
      logger.debug("Termination detected");
    }
    return sum == 0;
  }

  public int getTokenSend() {
    return tokenSend;
  }

  public int getBackupTokenSend() {
    return backupTokenSend;
  }

  public int getTokenSendAfterTermination() {
    return tokenSendAfterTermination;
  }

  /**
   * @return processing time spent on Safra's calculation in milliseconds.
   */
  public double getSafraTimeSpent() {
    return safraTimeSpent / 1000000000.0;
  }

  public double getSafraTimeSpentAfterTermination() {
    return safratTimeSpentAfterTermination / 1000000000.0;
  }

  public double getTotalTimeSpent() {
    return totalTime / 1000000000.0;
  }

  public long getTokenBytes() {
    return tokenBytes;
  }

  public void writeToCSVFile(Path filePath) throws IOException {
    String headerLine = "tokens;tokenAfterTermination;backupToken;tokenSize (bytes);safraTime (seconds);safraTimeAfterTermination;totalTime;numberOfNodesCrashed;basicTime\n";

    StringBuilder contentLine = new StringBuilder();
    contentLine.append(getTokenSend());
    contentLine.append(";");
    contentLine.append(getTokenSendAfterTermination());
    contentLine.append(";");
    contentLine.append(getBackupTokenSend());
    contentLine.append(";");
    contentLine.append(getTokenBytes());
    contentLine.append(";");
    contentLine.append(getSafraTimeSpent());
    contentLine.append(";");
    contentLine.append(getSafraTimeSpentAfterTermination());
    contentLine.append(";");
    contentLine.append(getTotalTimeSpent());
    contentLine.append(";");
    contentLine.append(getNumberOfNodesCrashed());
    contentLine.append(";");
    contentLine.append(getBasicTimeSpent());
    contentLine.append("\n");


    Files.write(filePath, (headerLine + contentLine.toString()).getBytes());
  }

  public int getNumberOfNodesCrashed() {
    return crashedNodes.size();
  }

  public String getCrashNodeString() {
    StringBuilder sb = new StringBuilder();
    for (int cn : crashedNodes) {
      sb.append(cn);
      sb.append(", ");
    }
    return sb.toString();
  }

  public Set<Integer> getCrashedNodes() {
    return crashedNodes;
  }

  public double getBasicTimeSpent() {
    return basicTimeSpent / 1000000000.0;
  }
}
