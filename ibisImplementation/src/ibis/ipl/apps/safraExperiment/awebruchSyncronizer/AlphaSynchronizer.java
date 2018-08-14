package ibis.ipl.apps.safraExperiment.awebruchSyncronizer;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashHandler;
import ibis.ipl.apps.safraExperiment.safra.api.CrashAfterTerminationException;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class AlphaSynchronizer implements CrashHandler {

  private int ackMessages;
  private int messagesSent;

  private CommunicationLayer communicationLayer;
  private AwebruchClient client;

  private boolean pulseFinished;
  private Map<Integer, Boolean> safeMessageReceived;
  private Semaphore semaphore;

  public AlphaSynchronizer(CommunicationLayer communicationLayer, AwebruchClient client) {
    this.communicationLayer = communicationLayer;
    this.client = client;

    safeMessageReceived = new HashMap<>();
    for (int neighbour : communicationLayer.getNeighbours()) {
      safeMessageReceived.put(neighbour, false);
    }

    prepareNextPulse();
  }

  public void sendMessage(Message m) throws IOException {
    if (pulseFinished) {
      throw new IllegalStateException("Client tried to send message after declaring pulse finished");
    }

    messagesSent++;
    communicationLayer.sendMessage(m);
  }

  public synchronized void receiveMessage(Message m) throws IOException, TerminationDetectedTooEarly {
    if (m instanceof AckMessage) {
      handleAckMessage((AckMessage) m);
    } else if (m instanceof SafeMessage) {
      handleSafeMessage((SafeMessage) m);
    } else {
      communicationLayer.sendMessage(new AckMessage());
      client.handleMessage(m);
    }
  }

  private void handleAckMessage(AckMessage m) {
    ackMessages++;
    if (pulseFinished && ackMessages == messagesSent) {
      sendSafeMessageToAllNeighbours();
    }
  }

  private void handleSafeMessage(SafeMessage m) {
    if (safeMessageReceived.containsKey(m.getSource())) {
      safeMessageReceived.put(m.getSource(), true);
      checkPulseComplete();
    }
  }

  private void checkPulseComplete() {
    boolean allSafe = true;
    for (boolean safe : safeMessageReceived.values()) {
      allSafe &= safe;
    }
    if (allSafe) {
      semaphore.release();
    }
  }

  private void sendSafeMessageToAllNeighbours() {

  }

  public synchronized void finishPulse() {
    pulseFinished = true;
    if (ackMessages == messagesSent) {
      sendSafeMessageToAllNeighbours();
    }
  }

  public void awaitPulse() throws InterruptedException {
    if (!pulseFinished) {
      finishPulse();
    }

    semaphore.acquire();
    prepareNextPulse();
  }

  private synchronized void prepareNextPulse() {
    pulseFinished = false;
    semaphore = new Semaphore(0);
    ackMessages = 0;
    messagesSent = 0;

    for (int neighbour : safeMessageReceived.keySet()) {
      safeMessageReceived.put(neighbour, false);
    }
  }


  @Override
  public void handleCrash(int crashedNode) throws IOException, CrashAfterTerminationException {
    if (safeMessageReceived.containsKey(crashedNode)) {
      safeMessageReceived.remove(crashedNode);
    }

    checkPulseComplete();
  }
}
