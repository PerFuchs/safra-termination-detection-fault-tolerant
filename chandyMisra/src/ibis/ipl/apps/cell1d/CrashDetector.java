package ibis.ipl.apps.cell1d;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


// TODO observer pattern?
public class CrashDetector {


  private ChandyMisraNode chandyMisraNode;
  private CommunicationLayer communicationLayer;
  private List<IbisIdentifier> crashedNodes = new LinkedList<>();

  public CrashDetector(ChandyMisraNode chandyMisraNode, CommunicationLayer communicationLayer) {
    this.chandyMisraNode = chandyMisraNode;
    this.communicationLayer = communicationLayer;
  }

  public synchronized void handleCrash(IbisIdentifier crashedNode) throws IOException {
    crashedNodes.add(crashedNode);
    chandyMisraNode.handleCrash(crashedNode);
  }

  public List<IbisIdentifier> getCrashedNodes() {
    return crashedNodes;
  }

  public String getCrashedNodesString() {
    StringBuilder sb = new StringBuilder();
    for (IbisIdentifier cn : crashedNodes) {
      sb.append(communicationLayer.getNodeNumber(cn));
      sb.append(", ");
    }
    return sb.toString();
  }
}
