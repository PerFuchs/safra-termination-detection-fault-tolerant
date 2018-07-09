package ibis.ipl.apps.safraExperiment.experiment;

import java.awt.*;
import java.util.*;
import java.util.List;

public class SafraStatistics {
  private int backupTokenSend;
  private int tokenSendAfterTermination;
  private int tokenSend;

  public SafraStatistics(int numberOfNodes, List<Event> events) {
    System.out.println(events.size());
    List<Event> sortedEvents = new ArrayList<Event>(events);
    Collections.sort(sortedEvents);
    System.out.println("Sorted event size " + sortedEvents.size());

    boolean terminated = false;
    tokenSend = 0;
    tokenSendAfterTermination = 0;

    List<List<Integer>> nodeSums = new ArrayList<>();
    List<Boolean> nodeActiveStatus = new ArrayList<>();
    for (int i = 0; i < numberOfNodes; i++) {
      nodeSums.add(new ArrayList<Integer>(Collections.nCopies(numberOfNodes, 0)));
      nodeActiveStatus.add(false);
    }

    List<Integer> crashedNodes = new LinkedList<>();


    for (Event e : sortedEvents) {
      if (e.isNodeCrashed()) {
        crashedNodes.add(e.getNode());
        terminated = hasTerminated(nodeSums, nodeActiveStatus, crashedNodes);
      }
      if (e.isActiveStatusChange()) {
        nodeActiveStatus.set(e.getNode(), e.getActiveStatus());
        terminated = hasTerminated(nodeSums, nodeActiveStatus, crashedNodes);
      }
      if (e.isSafraSums()) {
        nodeSums.set(e.getNode(), e.getSafraSum());
        terminated = hasTerminated(nodeSums, nodeActiveStatus, crashedNodes);
      }
      if (e.isTokenSend()) {
        tokenSend++;
        if (terminated) {
          tokenSendAfterTermination++;
        }
      }
      if (e.isBackupTokenSend()) {
        backupTokenSend++;
       }
    }
  }

  private boolean hasTerminated(List<List<Integer>> nodeSums, List<Boolean> nodeActiveStatus, List<Integer> crashedNodes) {
    int sum = 0;

    // nodeSums and nodeActiveStatus are of the same size
    for (int i = 0; i < nodeSums.size(); i++) {
      if (nodeActiveStatus.get(i)) {
        return false;
      }
      if (!crashedNodes.contains(i)) {
        List<Integer> sums = nodeSums.get(i);
        for (int j = 0; j < sums.size(); j++) {
          if (!crashedNodes.contains(j)) {
            sum += sums.get(j);
          }
        }
      }
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
