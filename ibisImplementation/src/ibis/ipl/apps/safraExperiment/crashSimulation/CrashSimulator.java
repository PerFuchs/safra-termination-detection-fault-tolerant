package ibis.ipl.apps.safraExperiment.crashSimulation;

import ibis.ipl.apps.safraExperiment.BasicAlgorithm;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.OnlineExperiment;
import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

public class CrashSimulator {
  private final static Logger logger = Logger.getLogger(CrashSimulator.class);
  private final static Logger experimentLogger = Logger.getLogger(OnlineExperiment.experimentLoggerName);

  /**
   * Map from points to crash at mapped to the amount of repetitions when to crash after
   */
  private final Map<CrashPoint, Integer> crashPoints;

  /**
   * Counters for encounters of crash points that this node might crash at.
   */
  private final Map<CrashPoint, Integer> crashPointCounters = new HashMap<>();

  private CommunicationLayer communicationLayer;
  private BasicAlgorithm basicAlgorithm;
  private boolean simulateCrashes;
  private final Set<CrashPoint> enabledCrashPoints;
  private final boolean throwCrashException;
  private Set<Integer> crashingNodes = new HashSet<>();

  public CrashSimulator(CommunicationLayer communicationLayer, SynchronizedRandom synchronizedRandom, double crashPercentage, boolean simulateCrashes,
                        Set<CrashPoint> enabledCrashPoints, boolean throwCrashException) {
    this.communicationLayer = communicationLayer;
    this.simulateCrashes = simulateCrashes;
    this.enabledCrashPoints = enabledCrashPoints;
    this.throwCrashException = throwCrashException;

    int me = communicationLayer.getID();
    int numberOfNodes = communicationLayer.getIbisCount();

    long numberOfNodesToCrash = Math.round(numberOfNodes * crashPercentage);
    StringBuilder nodesToCrashString = new StringBuilder();

    // All nodes agree on which nodes to crash.
    LinkedList<Integer> nodesAvailableToCrash = new LinkedList<>();
    for (int i = 1; i < numberOfNodes; i++) {
      nodesAvailableToCrash.add(i);
    }
    for (int i = 0; i < numberOfNodesToCrash; i++) {
      int crash = nodesAvailableToCrash.get(synchronizedRandom.getInt(nodesAvailableToCrash.size()));
      nodesAvailableToCrash.remove(new Integer(crash));
      crashingNodes.add(crash);
      nodesToCrashString.append(String.format("%d ,", crash));
    }
    logger.trace(String.format("Crashing nodes (%d): %s", crashingNodes.size(), nodesToCrashString.toString()));

    // Nodes choose their crash point and crash point repetition to crash locally at random.
    Random r = new Random();
    CrashPoint[] crashPoints = CrashPoint.values();
    this.crashPoints = new HashMap<>();
    if (crashingNodes.contains(me)) {
      CrashPoint crashPoint = crashPoints[r.nextInt(crashPoints.length)];

      // Do not choose backup token related crash points as single crash point as the happened to seldom.
      while (crashPoint == CrashPoint.BEFORE_SENDING_BACKUP_TOKEN || crashPoint == CrashPoint.AFTER_SENDING_BACKUP_TOKEN ) {
        crashPoint = crashPoints[r.nextInt(crashPoints.length)];
      }
      int repetitions = r.nextInt(CrashPoint.getMaxRepitions(crashPoint)) + 1;
      this.crashPoints.put(crashPoint, repetitions);
      logger.trace(String.format("Node %d chose %s after %d repetitions as crash point", communicationLayer.getID(), crashPoint.toString(), repetitions));
    }

    // Let each node that is scheduled to crash also crash on backup token related points as these happen to seldom
    // to get sufficient coverage otherwise
    if (crashingNodes.contains(me)) {
      // 50% on BEFORE_SENDING_BACKUP_TOKEN and on AFTER_SENDING_BACKUP_TOKEN
      if (r.nextInt(1) == 1) {
        this.crashPoints.put(CrashPoint.BEFORE_SENDING_BACKUP_TOKEN, r.nextInt(CrashPoint.getMaxRepitions(CrashPoint.BEFORE_SENDING_BACKUP_TOKEN) + 1));
      } else {
        this.crashPoints.put(CrashPoint.AFTER_SENDING_BACKUP_TOKEN, r.nextInt(CrashPoint.getMaxRepitions(CrashPoint.AFTER_SENDING_BACKUP_TOKEN) + 1));
      }
    }

    for (CrashPoint cp : this.crashPoints.keySet()) {
      crashPointCounters.put(cp, 0);
    }
  }

  public void reachedCrashPoint(CrashPoint crashPoint) throws IOException, CrashException {
    if (crashPoints.keySet().contains(crashPoint) && simulateCrashes && enabledCrashPoints.contains(crashPoint)) {
      logger.debug(String.format("%d Reached crash point its crash point %s", communicationLayer.getID(), crashPoint.toString()));
      int counter = crashPointCounters.get(crashPoint);
      counter++;
      crashPointCounters.put(crashPoint, counter);
      if (counter == crashPoints.get(crashPoint)) {
        logger.info(String.format("Node %04d crashed on %s with %d repetitions", communicationLayer.getID(), crashPoint.toString(), counter));
        crash();
      }
    }
  }

  private void crash() throws IOException, CrashException {
    experimentLogger.info(String.format("%s %d", Event.getNodeCrashedEvent(), communicationLayer.getID()));
    communicationLayer.broadcastCrashMessage();
    communicationLayer.crash();

    basicAlgorithm.crash();

    if (throwCrashException) {
      throw new CrashException();
    }
  }

  public boolean couldCrash() {
    return crashingNodes.contains(communicationLayer.getID());
  }

  public Set<Integer> getCrashingNodes() {
    return crashingNodes;
  }

  public void setBasicAlgorithm(BasicAlgorithm basicAlgorithm) {
    this.basicAlgorithm = basicAlgorithm;
  }
}
