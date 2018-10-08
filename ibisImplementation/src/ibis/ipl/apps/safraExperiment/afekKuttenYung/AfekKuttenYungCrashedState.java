package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashException;
import ibis.ipl.apps.safraExperiment.safra.api.CrashDetectionAfterTerminationException;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;

public class AfekKuttenYungCrashedState extends AfekKuttenYungState {
  @Override
  public void handleMessage(int source, Message m) throws IOException, TerminationDetectedTooEarly, CrashException {

  }

  @Override
  public void startAlgorithm() throws IOException, CrashException {
    throw new IllegalStateException("Algorithm crashed before being started");
  }

  @Override
  public void terminate() throws TerminationDetectedTooEarly {

  }

  @Override
  public void handleCrash(int crashedNode) throws CrashDetectionAfterTerminationException, IOException, CrashException {

  }

  @Override
  public void crash() {
    throw new IllegalStateException("Nodes cannot crash twice");
  }

  @Override
  public int getParent() {
    return 0;
  }

  @Override
  public int getRoot() {
    return 0;
  }

  @Override
  public int getDistance() {
    return 0;
  }
}
