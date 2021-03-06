package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.BasicAlgorithm;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AwebruchClient;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashException;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashHandler;
import ibis.ipl.apps.safraExperiment.safra.api.CrashDetectionAfterTerminationException;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;

public class AfekKuttenYungStateMachine implements BasicAlgorithm, AwebruchClient, CrashHandler {
  private AfekKuttenYungState state;

  public AfekKuttenYungStateMachine(CommunicationLayer communicationLayer, Safra safra, CrashDetector crashDetector) {
    state = new AfekKuttenYungRunningState(communicationLayer, safra, this, crashDetector);
    crashDetector.addHandler(this);
  }

  @Override
  public synchronized void handleMessage(int source, Message m) throws IOException, TerminationDetectedTooEarly, CrashException {
    state.handleMessage(source, m);
  }

  public void startAlgorithm() throws IOException, CrashException {
    state.startAlgorithm();
  }

  public synchronized void terminate() throws TerminationDetectedTooEarly {
    state.terminate();
  }

  @Override
  public void crash() {
    state.crash();
  }

  @Override
  public void handleCrash(int crashedNode) throws IOException, CrashDetectionAfterTerminationException, CrashException {
    state.handleCrash(crashedNode);
  }

  void setState(AfekKuttenYungState state) {
    this.state = state;
  }


  public AlphaSynchronizer getSynchronizer() {
    if (!(state instanceof AfekKuttenYungRunningState)) {
      throw new IllegalStateException("Can only get synchronizer in running state");
    }
    return ((AfekKuttenYungRunningState) state).getSynchronizer();
  }

  public int getParent() {
    return state.getParent();
  }

  public int getDistance() {
    return state.getDistance();
  }

  public int getRoot() {
    return state.getRoot();
  }

  public AfekKuttenYungState getState() {
    return state;
  }
}
