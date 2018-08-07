package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AwebruchClient;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class AfekKuttenYungAlgorithm implements AwebruchClient {
  private AfekKuttenYungState ownState;
  private Map<Integer, AfekKuttenYungState> neighbourStates;
  private Map<Integer, AfekKuttenYungState> newNeighbourStates;

  private AlphaSynchronizer synchronizer;
  private Safra safra;
  private boolean terminated;

  @Override
  public void handleMessage(Message m) {
    newNeighbourStates.get(m.getSource()).update(m);
    // No call to safra.setActive(false). Message handling is not done before the next call to step.
  }

  public void startAlgorithm() throws InterruptedException, IOException {
    safra.setActive(true, "Start AKY");
    sendStateToAllNeighbours();
    synchronizer.awaitPulse();
    stepLoop();
  }

  private void stepLoop() throws IOException, InterruptedException {
    while (true) {
      if (!neighbourStates.equals(newNeighbourStates)) {
        step();
      }

      safra.setActive(false, "Step done");
      if (terminated) {
        break;
      }

      synchronizer.awaitPulse();
      neighbourStates = newNeighbourStates;
    }
  }

  private void step() throws IOException {
    // Implement algorithm from the book operating on own state and neighbour state
  }

  private void updateOwnState(Message m) {
    ownState.update(m);
    sendStateUpdateToAllNeighbours(m);
  }

  private void sendStateToAllNeighbours() {

  }

  private void sendStateUpdateToAllNeighbours(Message m) {

  }

  public void terminate() {
    terminated = true;
  }
}
