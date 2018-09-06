package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.safra.api.CrashDetectionAfterTerminationException;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;

public abstract class AfekKuttenYungState {
  public abstract void handleMessage(Message m) throws IOException, TerminationDetectedTooEarly;

  public abstract void startAlgorithm() throws IOException;

  public abstract void terminate() throws TerminationDetectedTooEarly;

  public abstract void handleCrash(int crashedNode) throws CrashDetectionAfterTerminationException, IOException;

  public abstract int getParent();
  public abstract int getRoot();
  public abstract int getDistance();
}
