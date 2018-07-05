package ibis.ipl.apps.safraExperiment.crashSimulation;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class CrashSimulator {
  private static Logger logger = Logger.getLogger(CrashSimulator.class);

  private boolean lateCrash;
  private CommunicationLayer communicationLayer;
  private boolean simulateCrashes;

  public CrashSimulator(CommunicationLayer communicationLayer, boolean simulateCrashes) {
    this.communicationLayer = communicationLayer;
    this.simulateCrashes = simulateCrashes;
//
//    double nodesToCrashPercentage = 0.2;
//    int networkSize = communicationLayer.getIbisCount();
//    long nodesToCrash = Math.round(networkSize * nodesToCrashPercentage);
//
//    Random r = new Random();
//    Set<Integer> toCrash = new HashSet<>();
//    for (int i = 0; i < nodesToCrash; i++) {
//      int crash;
//      do  {
//        crash = r.nextInt(networkSize);
//      } while ((toCrash.contains(crash)));
//      toCrash.add(crash);
//      if (!communicationLayer.isRoot(crash)) {
//        scheduleLateCrash(crash);
//      }
//    }
    Random r = new Random();

    if (r.nextInt(100) < 21 && !communicationLayer.isRoot()) {
      scheduleLateCrash(communicationLayer.getID());
    }

  }

  public void scheduleLateCrash(int node) {
    if (node == communicationLayer.getID()) {
      lateCrash = true;
    }
  }

  public void triggerLateCrash() throws IOException {
    if (lateCrash && simulateCrashes) {
      crash();
    }
  }

  private void crash() throws IOException {
    logger.info(String.format("Simulated crash for node %d", communicationLayer.getID()));
    communicationLayer.broadcastCrashMessage();
    communicationLayer.crash();
  }

}
