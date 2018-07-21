package ibis.ipl.apps.safraExperiment.crashSimulation;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class CrashDetector {
  private List<Integer> crashedNodes = new LinkedList<>();
  private List<CrashHandler> crashHandlers = new LinkedList<>();

  public void addHandler(CrashHandler ch) {
    crashHandlers.add(ch);
  }

  public synchronized void handleCrash(int crashedNode) throws IOException {
    crashedNodes.add(crashedNode);
    notifyCrashHandlers(crashedNode);
  }

  private void notifyCrashHandlers(int crashedNode) throws IOException {
    for (CrashHandler ch : crashHandlers) {
      ch.handleCrash(crashedNode);
    }
  }

  public List<Integer> getCrashedNodes() {
    return crashedNodes;
  }

  public boolean hasCrashed(int node) {
    return crashedNodes.contains(node);
  }
}
