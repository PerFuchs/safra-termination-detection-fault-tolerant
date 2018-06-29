package ibis.ipl.apps.safraExperiment.safra.faultSensitive;

import ibis.ipl.Registry;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.ibisSignalling.IbisSignal;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Semaphore;

public class Safra implements Observer {
  private Semaphore semaphore = new Semaphore(1, false);

  private boolean started = false;
  private boolean basicAlgorithmIsActive = false;
  private int isBlackUntil;
  private long messageCounter = 0;
  private long sequenceNumber = 0;
  private Token token;


  private CommunicationLayer communicationLayer;
  private final Registry registry;

  public Safra(Registry registry, SignalPollerThread signalHandler, CommunicationLayer communicationLayer) {
    this.registry = registry;
    this.communicationLayer = communicationLayer;
    isBlackUntil = communicationLayer.getID();

    signalHandler.addObserver(this);
  }

  /**
   *
   * @param j id of node in ring
   * @param k id or node in ring
   * @return The id that is further away from this node.
   */
  private int furthest(int j, int k) {
    int me = communicationLayer.getID();
    if ((me <= j && j <= k)
        || (k < me && me <= j)
        || (j <= k && k < me)) {
      return k;
    }
    return j;
  }

  public void setActive(boolean status) throws IOException {
    basicAlgorithmIsActive = status;
    if (!basicAlgorithmIsActive) {
      handleToken();
    }
  }

  public void startAlgorithm() throws InterruptedException {
    semaphore.acquire();
    started = true;
    token = null;
    if (communicationLayer.isRoot()) {
      token = new Token(0, communicationLayer.getIbisCount() - 1);
    }
  }

  public synchronized void handleSendingBasicMessage() {
    messageCounter++;
  }

  public synchronized void handleReceiveBasicMessage(int sender, long sequenceNumber) {
    basicAlgorithmIsActive = true;
    messageCounter--;

    // Only color myself black if the message overtook the token. As defined in the paper
    if ((sender < communicationLayer.getID()
        && sequenceNumber > this.sequenceNumber)
        || (sender > communicationLayer.getID() && sequenceNumber == this.sequenceNumber)) {
      isBlackUntil = furthest(isBlackUntil, sender);
    }
  }

  public synchronized void receiveToken(Token token) throws IOException {
    this.token = token;
    handleToken();
  }

  private synchronized void handleToken() throws IOException {
    if (!basicAlgorithmIsActive && this.token != null) {
      int me = communicationLayer.getID();
      isBlackUntil = furthest(token.isBlackUntil, isBlackUntil);
      token.messageCounter += messageCounter;

      System.out.println("Message Count: " + messageCounter);
      System.out.println(String.format("Handle token: %d %d", isBlackUntil, token.messageCounter));
      if (token.messageCounter == 0 && isBlackUntil == me) {
        System.out.println("Calling announce");
        announce();
      } else {
        forwardToken(new Token(token.messageCounter, furthest(isBlackUntil, (me + 1) % communicationLayer.getIbisCount())));
        isBlackUntil = me;
        messageCounter = 0;
      }
    }
  }

  private void announce() throws IOException {
    IbisSignal.signal(registry, communicationLayer.getIbises(), new IbisSignal("safra", "announce"));
  }

  public void await() throws InterruptedException {
    if (!started) {
      throw new IllegalStateException("Safra's algorithm has to be started before one can wait for termination.");
    }
    semaphore.acquire();
  }

  private void forwardToken(Token token) throws IOException {
    this.token = null;
    sequenceNumber++;

    int nextNode = (communicationLayer.getID() + 1) % communicationLayer.getIbisCount();
    System.out.println(String.format("Forwarding token to %d Token: %d %d", nextNode, token.isBlackUntil, token.messageCounter));
    communicationLayer.sendToken(token, nextNode);
  }


  @Override
  public void update(Observable observable, Object o) {
    if (o instanceof IbisSignal) {
      IbisSignal signal = (IbisSignal) o;
      if (signal.module.equals("safra") && signal.name.equals("announce")) {
        semaphore.release();
      }
    }
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }
}
