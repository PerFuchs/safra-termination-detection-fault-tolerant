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

  private boolean basicAlgorithmIsActive = false;
  private boolean nodeIsBlack = false;
  private boolean isInitiator = false;
  private long messageCounter = 0;
  private Token token;

  private CommunicationLayer communicationLayer;
  private final Registry registry;

  public Safra(Registry registry, SignalPollerThread signalHandler, CommunicationLayer communicationLayer) {
    this.registry = registry;
    this.communicationLayer = communicationLayer;

    signalHandler.addObserver(this);
  }

  public void setActive(boolean status) throws IOException {
    basicAlgorithmIsActive = status;
    if (!basicAlgorithmIsActive) {
//      handleToken(token);
    }
  }

  public void startAlgorithm() throws InterruptedException {
    semaphore.acquire();
    if (communicationLayer.isRoot(communicationLayer.getID())) {
      token = new Token(0, false);
      isInitiator = true;
      nodeIsBlack = true;
    }
  }

  public void handleSendingBasicMessage() {
    messageCounter++;
  }

  public void handleReceiveBasicMessage() {
    basicAlgorithmIsActive = true;
    messageCounter--;
    nodeIsBlack = true;
  }

  public void receiveToken(Token token) throws IOException {
    this.token = token;
//    handleToken(token);
  }

  private void handleToken(Token token) throws IOException {
    if (basicAlgorithmIsActive == false && token != null) {
      if (!nodeIsBlack && !token.isBlack) {
        messageCounter += token.messageCounter;
      }
      if (!isInitiator) {
        forwardToken(new Token(messageCounter, nodeIsBlack || token.isBlack));
        nodeIsBlack = false;
      } else if (nodeIsBlack || messageCounter > 0) {
        forwardToken(new Token(0, false));
        nodeIsBlack = false;
      } else {
        announce();
      }
    }
  }

  private void announce() throws IOException {
    IbisSignal.signal(registry, communicationLayer.getIbises(), new IbisSignal("safra", "announce"));
  }

  public void await() throws InterruptedException {
    if (semaphore.availablePermits() > 0) {
      throw new IllegalStateException("Safra's algorithm has to be started before one can wait for termination.");
    }
    semaphore.acquire();
  }

  private void forwardToken(Token token) throws IOException {
    this.token = null;

    int nextNode = (communicationLayer.getID() + 1) % communicationLayer.getIbisCount();
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
}
