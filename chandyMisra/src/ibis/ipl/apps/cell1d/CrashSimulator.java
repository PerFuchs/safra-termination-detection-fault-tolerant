package ibis.ipl.apps.cell1d;

import ibis.ipl.IbisIdentifier;

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
    int networkSize = communicationLayer.getIbises().length;
    long nodesToCrash = Math.round(networkSize * nodesToCrashPercentage);

    Random r = new Random();
    Set<Integer> toCrash = new HashSet<>();
    for (int i = 0; i < nodesToCrash; i++) {
      int crashNodeNumber;
      do  {
        crashNodeNumber = r.nextInt(networkSize);
      } while ((toCrash.contains(crashNodeNumber)));
      toCrash.add(crashNodeNumber);
      IbisIdentifier crash = communicationLayer.getIbises()[crashNodeNumber];
      if (!communicationLayer.isRoot(crash)) {
        scheduleLateCrash(crash);
      }
    }

  }

  public void scheduleLateCrash(IbisIdentifier node) {
    // TODO use that everywhere instead of the ibis instance
    if (node.equals(communicationLayer.identifier())) {
      System.out.println("Scheduled late crash for node: " + communicationLayer.getNodeNumber(communicationLayer.identifier()));
      lateCrash = true;
    }
  }

  public void triggerLateCrash() throws IOException {
    if (lateCrash && simulateCrashes) {
      crash();
    }
  }

  private void crash() throws IOException {
    System.out.println("Simulated crash for node: " + communicationLayer.getNodeNumber(communicationLayer.identifier()));
    communicationLayer.broadcastCrashMessage();
    communicationLayer.crash();
  }

}
