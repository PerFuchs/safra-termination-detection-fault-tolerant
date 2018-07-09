package ibis.ipl.apps.safraExperiment.experiment;

import org.apache.log4j.Logger;

import java.util.*;

public class SafraStatistics {
  private final static Logger logger = Logger.getLogger(SafraStatistics.class);

  private int backupTokenSend;
  private int tokenSendAfterTermination;
  private int tokenSend;

  public SafraStatistics(int numberOfNodes, List<Event> events) {
    List<Event> sortedEvents = new ArrayList<Event>(events);
    Collections.sort(sortedEvents);

    boolean terminated = false;
    tokenSend = 0;
    tokenSendAfterTermination = 0;

    Set<Integer> allCrashedNodes = new HashSet<>();
    for (Event e : sortedEvents) {
      if (e.isNodeCrashed()) {
        allCrashedNodes.add(e.getNode());
      }
    }

    List<List<Integer>> nodeSums = new ArrayList<>();
    List<Boolean> nodeActiveStatus = new ArrayList<>();
    for (int i = 0; i < numberOfNodes; i++) {
      nodeSums.add(new ArrayList<Integer>(Collections.nCopies(numberOfNodes, 0)));
      nodeActiveStatus.add(false);
    }

    Set<Integer> crashedNodes = new HashSet<>();

    for (Event e : sortedEvents) {
      logger.trace(String.format("Processing event %d %s", e.getNode(), e.getEvent()));
      if (terminated && (e.isNodeCrashed() || e.isActiveStatusChange() || e.isSafraSums())) {
        logger.error(String.format("Basic event happened  on node %d after termination: %s",
            e.getNode(), e.getEvent()));
      }
      if (e.isNodeCrashed()) {
        crashedNodes.add(e.getNode());
        terminated |= hasTerminated(nodeSums, nodeActiveStatus, crashedNodes, allCrashedNodes);
      }
      if (e.isActiveStatusChange()) {
        logger.trace("Set status to " + e.getActiveStatus());
        nodeActiveStatus.set(e.getNode(), e.getActiveStatus());
        terminated |= hasTerminated(nodeSums, nodeActiveStatus, crashedNodes, allCrashedNodes);
      }
      if (e.isSafraSums()) {
        nodeSums.set(e.getNode(), e.getSafraSum());
        terminated |= hasTerminated(nodeSums, nodeActiveStatus, crashedNodes, allCrashedNodes);
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
  private boolean hasTerminated(List<List<Integer>> nodeSums,
                                List<Boolean> nodeActiveStatus,
                                Set<Integer> currentlyCrashedNodes,
                                Set<Integer> allCrashedNodes) {
    if (!currentlyCrashedNodes.equals(allCrashedNodes)) {
      return false;
    }
    int sum = 0;

    // nodeSums and nodeActiveStatus are of the same size
    for (int i = 0; i < nodeSums.size(); i++) {
      if (nodeActiveStatus.get(i)) {
        return false;
      }
      if (!currentlyCrashedNodes.contains(i)) {
        List<Integer> sums = nodeSums.get(i);
        for (int j = 0; j < sums.size(); j++) {
          if (!currentlyCrashedNodes.contains(j)) {
            sum += sums.get(j);
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
