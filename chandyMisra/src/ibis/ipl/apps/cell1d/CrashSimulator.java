package ibis.ipl.apps.cell1d;

import ibis.ipl.IbisIdentifier;

import java.io.IOException;

public class CrashSimulator {

  private boolean lateCrash;
  private CommunicationLayer communicationLayer;

  public CrashSimulator(CommunicationLayer communicationLayer) {
    this.communicationLayer = communicationLayer;
  }

  public void scheduleLateCrash(IbisIdentifier node) {
    // TODO use that everywhere instead of the ibis instance
    if (node.equals(communicationLayer.identifier())) {
      System.out.println("Scheduled late crash for node: " + communicationLayer.getNodeNumber(communicationLayer.identifier()));
      lateCrash = true;
    }
  }

  public void triggerLateCrash() throws IOException {
    if (lateCrash) {
      crash();
    }
  }

  private void crash() throws IOException {
    System.out.println("Simulated crash for node: " + communicationLayer.getNodeNumber(communicationLayer.identifier()));
    communicationLayer.crash();
    communicationLayer.broadcastCrashMessage();
  }

}
