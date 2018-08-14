package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.safra.api.CrashAfterTerminationException;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;

public class AfekKuttenYungTerminatedState extends AfekKuttenYungState {

  @Override
  public void handleMessage(Message m) throws IOException, TerminationDetectedTooEarly {
    throw new TerminationDetectedTooEarly("Received basic message after termination");
  }

  @Override
  public void startAlgorithm() throws Exception {
    throw new Exception("Algorithm not started before termination");
  }

  @Override
  public void terminate() throws TerminationDetectedTooEarly {
    // Ignore
  }

  @Override
  public void handleCrash(int crashedNode) throws CrashAfterTerminationException {
    throw new CrashAfterTerminationException();
  }
}
