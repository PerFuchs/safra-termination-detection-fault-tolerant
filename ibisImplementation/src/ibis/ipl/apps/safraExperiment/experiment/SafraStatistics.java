package ibis.ipl.apps.safraExperiment.experiment;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SafraStatistics {
  private final static Logger logger = Logger.getLogger(SafraStatistics.class);

  private long totalTime;
  private long safraTimeSpent;
  private long basicTimeSpent;
  private long safratTimeSpentAfterTermination;
  private long totalTimeAfterTermination;
  private int backupTokenSend;
  private int tokenSendAfterTermination;
  private int tokenSend;
  private long tokenBytes;
  private Set<Integer> crashedNodes = new HashSet<>();
  private final TerminationDefinitions terminationDefinition;

  public SafraStatistics(Experiment experiment, int numberOfNodes, List<Event> events, TerminationDefinitions terminationDefinition) throws IOException {
    this.terminationDefinition = terminationDefinition;
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
    totalTimeAfterTermination = 0;

    long actualTerminationTime = 0;

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
      if (terminated && terminationDefinition == TerminationDefinitions.NORMAL&& (e.isActiveStatusChange() || e.isMessageCounterUpdate())) {

        // This is not done for the extended definition because these warnings can be spurious in this case e.g.
        // node X crashes, node Y get's a message from X and its fault detector did not detect the crash yet. Node Y
        // will become active. This is hard to verify because it needs to follow causal relations.
        // However, it is a good estimation to when the normal termination detection falls short.
        logger.info(String.format("Basic event happened  on node %d after termination detection would be legal by normal definition: %s", e.getNode(), e.getEvent()));
        experiment.writeToWarnFile(String.format("Basic event happened  on node %d after termination detection would be legal by normal definition: %s", e.getNode(), e.getEvent()));
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
      }

      if (terminated && actualTerminationTime == 0) {
        logger.debug("Actual time after termination: " + e.getTime());
        actualTerminationTime = e.getTime();
      }

      if (e.isAnnounce()) {
        if (!terminated) {
          logger.error("Announce was called before actual termination");
          experiment.writeToErrorFile("Announce was called before actual termination.");

          provideEarlyAnnounceInformation(experiment, events);
        }
        totalTimeAfterTermination = e.getTime() - actualTerminationTime;
        logger.debug("Total time after termination " + totalTimeAfterTermination);
      }

    }
  }

  private void provideEarlyAnnounceInformation(Experiment experiment, List<Event> events) throws IOException {
    Event lastBasicEvent = null;
    Event announce = null;
    List<Event> crashes = new LinkedList<>();
    List<Event> parentCrashEvents = new LinkedList<>();
    for (Event e : events) {
      if (e.isBasic() && announce == null) {
        lastBasicEvent = e;
      }
      if (e.isParentCrashDetected()) {
        parentCrashEvents.add(e);
      }
      if (e.isNodeCrashed()) {
        crashes.add(e);
      }
      if (e.isAnnounce()) {
        announce = e;
      }
    }

    List<Event> closeParentCrashEvents = new LinkedList<>();
    int announceCrashDetectedDelta = 500;
    long lowerParentCrashLimit = announce.getTime() - announceCrashDetectedDelta;
    for (Event parentCrash : parentCrashEvents) {
      if (parentCrash.getTime() >= lowerParentCrashLimit) {
        closeParentCrashEvents.add(parentCrash);
      }
    }

    if (closeParentCrashEvents.isEmpty()) {
      experiment.writeToErrorFile("Announce was called early; did not find related parent crash event!");
    }

    String message = buildEarlyAnnounceInformationMessage(announce, parentCrashEvents, closeParentCrashEvents, crashes, lastBasicEvent);

    logger.warn(message);
    experiment.writeToWarnFile(message);
  }

  private String buildEarlyAnnounceInformationMessage(Event announce, List<Event> parentCrashEvents,
                                                      List<Event> closeParentCrashEvents, List<Event> crashes,
                                                      Event lastBasicEvent) {
    StringBuilder message = new StringBuilder();
    message.append("Announce event is: " ).append(announce.toString()).append('\n');

    message.append("Found parent crash detected close or after announce: \n");
    for (Event e : closeParentCrashEvents) {
      message.append(e.getEvent());
      message.append('\n');
    }

    message.append('\n');

    message.append("Found the following parent crash events (use grep on `out.log` for crash reason): \n");
    for (Event e : parentCrashEvents) {
      if (!closeParentCrashEvents.contains(e)) {
        message.append(e.getEvent());
        message.append('\n');
      }
    }
    message.append('\n');

    message.append("Found the following crash events: ");
    for (Event e : crashes) {
      message.append(e.getEvent());
      message.append('\n');
    }
    message.append('\n');

    message.append("Last basic event before announce was: ");
    message.append(lastBasicEvent);
    message.append('\n');

    return message.toString();
  }

  /**
   * Determines if the basic algorithm has terminated.
   *
   * Checks if all nodes are passive and the sum of all send and received messages in the system is zero ignoring
   * messages from and to crashed nodes.
   *
   * Depending on the value of terminationDefinition also checks if the last parent crash occurred.
   */
  private boolean hasTerminated(int[][] nodeSums, boolean[] nodeActiveStatus, Set<Integer> currentlyCrashedNodes, boolean lastParentCrashEventEncountered) {
    if (!lastParentCrashEventEncountered && terminationDefinition == TerminationDefinitions.EXTENDED) {
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
    String headerLine = "tokens;tokenAfterTermination;backupToken;tokenSize (bytes);safraTime (seconds);safraTimeAfterTermination;totalTime;numberOfNodesCrashed;basicTime;totalTimeAfterTermination\n";

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
    contentLine.append(";");
    contentLine.append(getTotalTimeAfterTermination());
    contentLine.append("\n");

    Files.write(filePath, (headerLine + contentLine.toString()).getBytes());
  }

  private double getTotalTimeAfterTermination() {
    return totalTimeAfterTermination / 1000.0;
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
