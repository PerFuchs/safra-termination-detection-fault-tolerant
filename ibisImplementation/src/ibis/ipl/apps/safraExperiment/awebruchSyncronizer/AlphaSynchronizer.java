package ibis.ipl.apps.safraExperiment.awebruchSyncronizer;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashException;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashHandler;
import ibis.ipl.apps.safraExperiment.safra.api.CrashDetectionAfterTerminationException;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * An Awebruch's Alpha Synchronizer as described in "Distributed Algorithms an intuitive approach" by Wan Fokkink.
 * <p>
 * Slightly changed to only release a process into the next pulse when its received safe messages from all neighbours
 * AND received all ACK messages for this pulse.
 */
public class AlphaSynchronizer implements CrashHandler {
  private final static Logger logger = Logger.getLogger(AlphaSynchronizer.class);

  private Map<Integer, Integer> ackMessages;
  private Map<Integer, Integer> messagesSent;

  private CommunicationLayer communicationLayer;
  private AwebruchClient client;
  private final Safra safra;

  private boolean pulseFinished;
  private Map<Integer, Integer> safeMessageReceived;

  private int pulses = 0;

  /**
   * Used to block the process until the next pulse starts. Needs to be released twice before it can be acquired and the pulse finished.
   * The releases happen when safe messages from all neighbours are received and when all ACK messages for this pulse have
   * been received.
   */
  private Semaphore semaphore;
  private boolean allSafeHandled;
  private boolean safeMessagesSent;

  public AlphaSynchronizer(CommunicationLayer communicationLayer, AwebruchClient client, CrashDetector crashDetector, Safra safra) {
    this.communicationLayer = communicationLayer;
    this.client = client;

    this.safra = safra;
    crashDetector.addHandler(this);

    safeMessageReceived = new HashMap<>();
    for (int neighbour : communicationLayer.getNeighbours()) {
      safeMessageReceived.put(neighbour, 0);
    }
    ackMessages = new HashMap<>();
    messagesSent = new HashMap<>();

    prepareNextPulse();
  }

  public synchronized void sendMessage(int destination, Message m, OurTimer timer) throws IOException, CrashException {
    if (pulseFinished) {
      throw new IllegalStateException("Client tried to send message after declaring pulse finished");
    }

    increaseMessageCounter(destination);
    communicationLayer.sendMessage(destination, m, timer);
  }

  public synchronized void receiveMessage(int source, Message m) throws IOException, TerminationDetectedTooEarly, CrashException {
    if (m instanceof AckMessage) {
      handleAckMessage(source, (AckMessage) m);
    } else if (m instanceof SafeMessage) {
      handleSafeMessage(source, (SafeMessage) m);
    } else {
      communicationLayer.sendMessage(source, new AckMessage(), new OurTimer());
      client.handleMessage(source, m);
    }
  }

  private void handleAckMessage(int source, AckMessage m) throws IOException, CrashException {

    increaseAckCounter(source);

    if (pulseFinished && allMessagesAcked()) {
      sendSafeMessageToAllNeighbours();
    }
  }

  private void increaseAckCounter(int index) {
    if (!safra.crashDetected(index)) {
      increaseMapCounter(ackMessages, index);
    }
  }

  private void increaseMessageCounter(int index) {
    if (!safra.crashDetected(index)) {
      increaseMapCounter(messagesSent, index);
    }
  }

  private void increaseMapCounter(Map<Integer, Integer> map, int index) {
    int counter = map.get(index);
    counter++;
    map.put(index, counter);
  }

  private boolean allMessagesAcked() {
    int messagesSentSum = 0;
    int messagesAckedSum = 0;
    for (int neighbour : messagesSent.keySet()) {
      messagesSentSum += messagesSent.get(neighbour);
      messagesAckedSum += ackMessages.get(neighbour);
    }
    return messagesSentSum == messagesAckedSum;
  }

  private void handleSafeMessage(int source, SafeMessage m) {
    if (safeMessageReceived.containsKey(source)) {
      int safeMessages = safeMessageReceived.get(source);
      if (safeMessages > 1) {
        logger.error("Too many safe messages");
      }
      safeMessageReceived.put(source, safeMessages + 1);

      if (allSafe()) {
        handleAllSafe();
      }
    }
  }

  private boolean allSafe() {
    boolean allSafe = true;
    for (int safe : safeMessageReceived.values()) {
      allSafe &= safe > 0;
    }
    return allSafe;
  }

  private void handleAllSafe() {
    if (!allSafeHandled) {
      for (int n : safeMessageReceived.keySet()) {
        int messages = safeMessageReceived.get(n);
        safeMessageReceived.put(n, messages - 1);
      }
      semaphore.release();
      allSafeHandled = true;
    }
  }

  private void sendSafeMessageToAllNeighbours() throws IOException, CrashException {
    if (!safeMessagesSent) {
      safeMessagesSent = true;

      for (int i : safeMessageReceived.keySet()) {
        communicationLayer.sendMessage(i, new SafeMessage(), new OurTimer());
      }

      semaphore.release();
    }
  }

  public synchronized void finishPulse() throws IOException, CrashException {
    pulseFinished = true;
    if (allMessagesAcked()) {
      sendSafeMessageToAllNeighbours();
    }
  }

  public void awaitPulse() throws InterruptedException, IOException, CrashException {
    if (!pulseFinished) {
      finishPulse();
    }

    // No neighbours. Hence all safe messages have been received.
    if (safeMessageReceived.isEmpty()) {
      semaphore.release();
    }

    semaphore.acquire();
    pulses++;
    prepareNextPulse();
  }


  private synchronized void prepareNextPulse() {
    pulseFinished = false;
    allSafeHandled = false;
    safeMessagesSent = false;
    semaphore = new Semaphore(-1);

    for (int neighbour : safeMessageReceived.keySet()) {
      ackMessages.put(neighbour, 0);
      messagesSent.put(neighbour, 0);
    }
  }


  @Override
  public synchronized void handleCrash(int crashedNode) throws IOException, CrashDetectionAfterTerminationException, CrashException {
    if (safeMessageReceived.containsKey(crashedNode)) {
      logger.debug(String.format("%04d Detected crash of neighbour %04d", communicationLayer.getID(), crashedNode));
      safeMessageReceived.remove(crashedNode);

      ackMessages.remove(crashedNode);
      messagesSent.remove(crashedNode);
    }

    if (pulseFinished && allMessagesAcked()) {
      sendSafeMessageToAllNeighbours();
    }
    if (allSafe()) {
      handleAllSafe();
    }
  }
}
