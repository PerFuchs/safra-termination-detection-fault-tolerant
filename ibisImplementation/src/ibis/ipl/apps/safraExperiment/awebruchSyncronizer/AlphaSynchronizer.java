package ibis.ipl.apps.safraExperiment.awebruchSyncronizer;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class AlphaSynchronizer {

  private int ackMessages;
  private int messagesSent;

  private CommunicationLayer communicationLayer;
  private AwebruchClient client;

  private boolean pulseFinished;
  private CountDownLatch semaphore;

  public void sendMessage(Message m) throws IOException {
    if (pulseFinished) {
      throw new IllegalStateException("Client tried to send message after declaring pulse finished");
    }

    messagesSent++;
    communicationLayer.sendMessage(m);
  }

  public void finishPulse() {
    pulseFinished = true;
    if (ackMessages == messagesSent) {
      sendSafeMessageToAllNeighbours();
    }
  }

  public void awaitPulse() throws InterruptedException {
    if (!pulseFinished) {
      finishPulse();
    }

    semaphore.await();
    prepareNextPulse();
  }

  private void prepareNextPulse() {
    semaphore = new CountDownLatch(communicationLayer.getNeighbours().size());
    ackMessages = 0;
    messagesSent = 0;
  }

  public synchronized void receiveMessage(Message m) throws IOException {
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
    semaphore.countDown();
  }

  private void sendSafeMessageToAllNeighbours() {

  }
}
