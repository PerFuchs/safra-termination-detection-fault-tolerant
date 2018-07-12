package ibis.ipl.apps.safraExperiment.crashSimulation;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.Experiment;
import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class CrashSimulator {
  private final static Logger logger = Logger.getLogger(CrashSimulator.class);
  private final static Logger experimentLogger = Logger.getLogger(Experiment.experimentLoggerName);

  private final CrashPoint crashPoint;
  private final int crashPointRepition;
  private int crashPointCounter = 0;

  private CommunicationLayer communicationLayer;
  private boolean simulateCrashes;
  private final Set<CrashPoint> enabledCrashPoints;

  public CrashSimulator(CommunicationLayer communicationLayer, SynchronizedRandom synchronizedRandom, double crashPercentage, boolean simulateCrashes,
                        Set<CrashPoint> enabledCrashPoints) {
    this.communicationLayer = communicationLayer;
    this.simulateCrashes = simulateCrashes;
    this.enabledCrashPoints = enabledCrashPoints;

    int me = communicationLayer.getID();
    int numberOfNodes = communicationLayer.getIbisCount();

    long numberOfNodesToCrash = Math.round(numberOfNodes * crashPercentage);
    Set<Integer> nodesToCrash = new HashSet<>();
    StringBuilder nodesToCrashString = new StringBuilder();

    // All nodes agree on which nodes to crash.
    for (int i = 0; i < numberOfNodesToCrash; i++) {
      int crash = synchronizedRandom.getInt(numberOfNodes);
      while (nodesToCrash.contains(crash) || crash == communicationLayer.getRoot()) {  // Do not crash the root node. Otherwise Chandy-Misra is senseless
        crash = synchronizedRandom.getInt(numberOfNodes);
      }
      nodesToCrash.add(crash);
      nodesToCrashString.append(String.format("%d ,", crash));
    }
    logger.trace(String.format("Crashing nodes: %s", nodesToCrashString.toString()));

    // Nodes choose their crash point and crash point repetition to crash locally at random.
    Random r = new Random();
    CrashPoint[] crashPoints = CrashPoint.values();
    if (nodesToCrash.contains(me)) {
      crashPoint = crashPoints[r.nextInt(crashPoints.length)];
    } else {
      crashPoint = null;
    }

    if (crashPoint != null) {
      int temp = r.nextInt(CrashPoint.getMaxRepitions(crashPoint) + 1);
      crashPointRepition = temp == 0 ? 1 : temp;
    } else {
      crashPointRepition = -1;
    }

    if (crashPoint != null) {
      logger.debug(String.format("%d crash point %s, repitions %d", me, crashPoint.toString(), crashPointRepition));
    }
  }

  public void reachedCrashPoint(CrashPoint crashPoint) throws IOException {
    if (crashPoint == this.crashPoint && simulateCrashes && enabledCrashPoints.contains(crashPoint)) {
      logger.debug(String.format("%d Reached crash point its crash point %s", communicationLayer.getID(), crashPoint.toString()));
      crashPointCounter++;
      if (crashPointCounter == crashPointRepition) {
        crash();
      }
    }
  }

  private void crash() throws IOException {
    experimentLogger.info(String.format("%s %d", Event.getNodeCrashedEvent(), communicationLayer.getID()));
    logger.info(String.format("Node %d crashed on %s with %d repititions", communicationLayer.getID(), crashPoint.toString(), crashPointRepition));
    communicationLayer.broadcastCrashMessage();
    communicationLayer.crash();
  }

}
