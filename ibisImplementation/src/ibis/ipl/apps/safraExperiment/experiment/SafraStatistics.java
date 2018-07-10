package ibis.ipl.apps.safraExperiment.experiment;

import org.apache.log4j.Logger;

import java.util.*;

public class SafraStatistics {
  private final static Logger logger = Logger.getLogger(SafraStatistics.class);

  private int backupTokenSend;
  private int tokenSendAfterTermination;
  private int tokenSend;

  public SafraStatistics(int numberOfNodes, List<Event> events) {
    Collections.sort(events);
    logger.trace("Events sorted");

    boolean terminated = false;
    tokenSend = 0;
    tokenSendAfterTermination = 0;

    int numberOfNodesCrashed = 0;
    for (Event e : events) {
      if (e.isNodeCrashed()) {
        numberOfNodesCrashed++;
      }
    }
    logger.trace("All crashed nodes found.");

    int[][] nodeSums = new int[numberOfNodes][numberOfNodes];
    boolean[] nodeActiveStatus = new boolean[numberOfNodes];
    for (int i = 0; i < numberOfNodes; i++) {
      nodeActiveStatus[i] = false;
    }

    Set<Integer> crashedNodes = new HashSet<>();

    int i = 0;
    for (Event e : events) {
      logger.trace(i++);
//      logger.trace(String.format("Processing event %d %s", e.getNode(), e.getEvent()));
      if (terminated && (e.isNodeCrashed() || e.isActiveStatusChange() || e.isMessageCounterUpdate())) {
        logger.error(String.format("Basic event happened  on node %d after termination: %s",
            e.getNode(), e.getEvent()));
      }
      if (e.isNodeCrashed()) {
        crashedNodes.add(e.getNode());
        terminated |= hasTerminated(nodeSums, nodeActiveStatus, crashedNodes, numberOfNodes);
      }
      if (e.isActiveStatusChange()) {
        nodeActiveStatus[e.getNode()] = e.getActiveStatus();
        terminated |= hasTerminated(nodeSums, nodeActiveStatus, crashedNodes, numberOfNodes);
      }
      if (e.isMessageCounterUpdate()) {
        nodeSums[e.getNode()][e.getSafraMessageCounterUpdateIndex()] = e.getSafraMessageCounterUpdateValue();
        terminated |= hasTerminated(nodeSums, nodeActiveStatus, crashedNodes, numberOfNodes);
      }
      if (e.isTokenSend()) {
        tokenSend++;
        if (terminated) {
          tokenSendAfterTermination++;
        }
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
   * messages from and to crashed nodes. Also termination cannot be reached before final fault.
   *
   * @return if the system has terminated.
   */
  private boolean hasTerminated(int[][] nodeSums,
                                boolean[] nodeActiveStatus,
                                Set<Integer> currentlyCrashedNodes,
                                int numberOfNodesCrashed) {
    if (currentlyCrashedNodes.size() != numberOfNodesCrashed) {
      return false;
    }
    for (boolean as : nodeActiveStatus) {
      if (as) {
        return false;
      }
    }
    int sum = 0;

    // nodeSums and nodeActiveStatus are of the same size
    for (int i = 0; i < nodeSums.length; i++) {
      if (!currentlyCrashedNodes.contains(i)) {
        int[] sums = nodeSums[i];
        for (int j = 0; j < sums.length; j++) {
          if (!currentlyCrashedNodes.contains(j)) {
            sum += sums[j];
          }
        }
      }
    }
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

}
