package ibis.ipl.apps.safraExperiment.awebruchSyncronizer;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashHandler;
import ibis.ipl.apps.safraExperiment.safra.api.CrashDetectionAfterTerminationException;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class AlphaSynchronizer implements CrashHandler {
  private final static Logger logger = Logger.getLogger(AlphaSynchronizer.class);

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

  public void sendMessage(int destination, Message m, OurTimer timer) throws IOException {
    if (pulseFinished) {
      throw new IllegalStateException("Client tried to send message after declaring pulse finished");
    }

    if (logger.isTraceEnabled()) {
      logger.trace(String.format("%04d sending message %d", communicationLayer.getID(), messagesSent));
    }

    messagesSent++;
    communicationLayer.sendMessage(destination, m, timer);
  }

  public synchronized void receiveMessage(int source, Message m) throws IOException, TerminationDetectedTooEarly {
    if (m instanceof AckMessage) {
      handleAckMessage((AckMessage) m);
    } else if (m instanceof SafeMessage) {
      handleSafeMessage(source, (SafeMessage) m);
    } else {
      communicationLayer.sendMessage(source, new AckMessage(), new OurTimer());
      client.handleMessage(source, m);
    }
  }

  private void handleAckMessage(AckMessage m) throws IOException {
    ackMessages++;
    logger.trace(String.format("%04d received ack message. Messages left %d, pulse finished %b", communicationLayer.getID(), messagesSent - ackMessages, pulseFinished));
    if (pulseFinished && ackMessages == messagesSent) {
      logger.trace(String.format("%04d pulse safe by ack message", communicationLayer.getID()));
      sendSafeMessageToAllNeighbours();
    }
  }

  private void handleSafeMessage(int source, SafeMessage m) {
    if (safeMessageReceived.containsKey(source)) {
      logger.trace(String.format("%04d got safe message from %04d", communicationLayer.getID(), source));
      safeMessageReceived.put(source, true);
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

  private void sendSafeMessageToAllNeighbours() throws IOException {
    for (int i : safeMessageReceived.keySet()) {
      communicationLayer.sendMessage(i, new SafeMessage(), new OurTimer());
    }
  }

  public synchronized void finishPulse() throws IOException {
    pulseFinished = true;
    if (ackMessages == messagesSent) {
      logger.trace(String.format("%04d pulse safe by await pulse", communicationLayer.getID()));
      sendSafeMessageToAllNeighbours();
    }
  }

  public void awaitPulse() throws InterruptedException, IOException {
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
  public void handleCrash(int crashedNode) throws IOException, CrashDetectionAfterTerminationException {
    if (safeMessageReceived.containsKey(crashedNode)) {
      safeMessageReceived.remove(crashedNode);
    }

    checkPulseComplete();
  }
}
