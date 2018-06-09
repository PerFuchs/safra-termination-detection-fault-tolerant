package ibis.ipl.apps.cell1d;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;

import java.io.IOException;


// TODO observer pattern?
public class CrashDetector {


  private ChandyMisraNode chandyMisraNode;

  public CrashDetector(ChandyMisraNode chandyMisraNode) {
    this.chandyMisraNode = chandyMisraNode;
  }

  public synchronized void handleCrash(IbisIdentifier crashedNode) throws IOException {
    System.out.println("Detected crash of node: " + crashedNode);
    chandyMisraNode.handleCrash(crashedNode);
  }
}
