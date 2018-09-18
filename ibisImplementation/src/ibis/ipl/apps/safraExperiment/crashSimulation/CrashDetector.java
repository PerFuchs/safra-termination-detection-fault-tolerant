package ibis.ipl.apps.safraExperiment.crashSimulation;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.OnlineExperiment;
import ibis.ipl.apps.safraExperiment.safra.api.CrashDetectionAfterTerminationException;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class CrashDetector {
  private static final Logger experimentLogger = Logger.getLogger(OnlineExperiment.experimentLoggerName);

  private List<Integer> crashedNodes = new LinkedList<>();
  private List<CrashHandler> crashHandlers = new LinkedList<>();

  public void addHandler(CrashHandler ch) {
    crashHandlers.add(ch);
  }

  public synchronized void handleCrash(int crashedNode) throws IOException, CrashException {
    if (!crashedNodes.contains(crashedNode)) {
      crashedNodes.add(crashedNode);
      notifyCrashHandlers(crashedNode);
    }
  }

  private void notifyCrashHandlers(int crashedNode) throws IOException, CrashException {
    for (CrashHandler ch : crashHandlers) {
      try {
        ch.handleCrash(crashedNode);
      } catch (CrashDetectionAfterTerminationException e) {
        experimentLogger.error(Event.getDetectedCrashAfterTerminationEvent());
      }
    }
  }

  public List<Integer> getCrashedNodes() {
    return crashedNodes;
  }

  public boolean hasCrashed(int node) {
    return crashedNodes.contains(node);
  }
}
