package ibis.ipl.apps.safraExperiment.crashSimulation;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class CrashSimulator {

  private boolean lateCrash;
  private CommunicationLayer communicationLayer;
  private boolean simulateCrashes;

  public CrashSimulator(CommunicationLayer communicationLayer, boolean simulateCrashes) {
    this.communicationLayer = communicationLayer;
    this.simulateCrashes = simulateCrashes;

    double nodesToCrashPercentage = 0.2;
    int networkSize = communicationLayer.getIbisCount();
    long nodesToCrash = Math.round(networkSize * nodesToCrashPercentage);

    Random r = new Random();
    Set<Integer> toCrash = new HashSet<>();
    for (int i = 0; i < nodesToCrash; i++) {
      int crash;
      do  {
        crash = r.nextInt(networkSize);
      } while ((toCrash.contains(crash)));
      toCrash.add(crash);
      if (!communicationLayer.isRoot(crash)) {
        scheduleLateCrash(crash);
      }
    }

  }

  public void scheduleLateCrash(int node) {
    if (node == communicationLayer.getID()) {
      System.out.println("Scheduled late crash for node: " + communicationLayer.getID());
      lateCrash = true;
    }
  }

  public void triggerLateCrash() throws IOException {
    if (lateCrash && simulateCrashes) {
      crash();
    }
  }

  private void crash() throws IOException {
    System.out.println("Simulated crash for node: " + communicationLayer.getID());
    communicationLayer.broadcastCrashMessage();
    communicationLayer.crash();
  }

}
