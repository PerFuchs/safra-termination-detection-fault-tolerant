package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AwebruchClient;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashHandler;
import ibis.ipl.apps.safraExperiment.safra.api.CrashAfterTerminationException;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;

public class AfekKuttenYungStateMachine implements AwebruchClient, CrashHandler {
  private AfekKuttenYungState state;

  public AfekKuttenYungStateMachine(CommunicationLayer communicationLayer, Safra safra, CrashDetector crashDetector) {
    state = new AfekKuttenYungRunningState(communicationLayer, safra, this);
    crashDetector.addHandler(this);
  }

  @Override
  public synchronized void handleMessage(Message m) throws IOException, TerminationDetectedTooEarly {
    state.handleMessage(m);
  }

  public void startAlgorithm() throws Exception {
    state.startAlgorithm();
  }

  public synchronized void terminate() throws TerminationDetectedTooEarly {
    state.terminate();
  }

  @Override
  public void handleCrash(int crashedNode) throws IOException, CrashAfterTerminationException {
    state.handleCrash(crashedNode);
  }

  void setState(AfekKuttenYungState state) {
    this.state = state;
  }


}
