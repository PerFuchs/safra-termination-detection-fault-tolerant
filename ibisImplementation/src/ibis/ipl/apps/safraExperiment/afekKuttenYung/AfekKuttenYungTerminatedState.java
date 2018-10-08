package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.safra.api.CrashDetectionAfterTerminationException;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;

public class AfekKuttenYungTerminatedState extends AfekKuttenYungState {

  private final int parent;
  private final int dist;
  private final int root;

  AfekKuttenYungTerminatedState(int parent, int dist, int root) {
    this.parent = parent;
    this.dist = dist;
    this.root = root;
  }

  @Override
  public void handleMessage(int source, Message m) throws IOException, TerminationDetectedTooEarly {
    throw new TerminationDetectedTooEarly("Received basic message after termination");
  }

  @Override
  public void startAlgorithm() {
    throw new IllegalStateException("Algorithm not started before termination");
  }

  @Override
  public void terminate() throws TerminationDetectedTooEarly {
    // Ignore
  }

  @Override
  public void handleCrash(int crashedNode) throws CrashDetectionAfterTerminationException {
    throw new CrashDetectionAfterTerminationException();
  }

  @Override
  public void crash() {
    // Ignore
  }

  @Override
  public int getParent() {
    return parent;
  }

  @Override
  public int getRoot() {
    return root;
  }

  @Override
  public int getDistance() {
    return dist;
  }

}
