package ibis.ipl.apps.safraExperiment.crashSimulation;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


// TODO observer pattern?
public class CrashDetector {
  private ChandyMisraNode chandyMisraNode;
  private CommunicationLayer communicationLayer;
  private List<Integer> crashedNodes = new LinkedList<>();

  public CrashDetector(ChandyMisraNode chandyMisraNode, CommunicationLayer communicationLayer) {
    this.chandyMisraNode = chandyMisraNode;
    this.communicationLayer = communicationLayer;
  }

  public synchronized void handleCrash(int crashedNode) throws IOException {
    System.out.println(String.format("Detected crash of %d at %d",
        crashedNode,
        communicationLayer.getID()));
    crashedNodes.add(crashedNode);
    chandyMisraNode.handleCrash(crashedNode);
  }

  public List<Integer> getCrashedNodes() {
    return crashedNodes;
  }

  public String getCrashedNodesString() {
    StringBuilder sb = new StringBuilder();
    for (int cn : crashedNodes) {
      sb.append(cn);
      sb.append(", ");
    }
    return sb.toString();
  }
}
