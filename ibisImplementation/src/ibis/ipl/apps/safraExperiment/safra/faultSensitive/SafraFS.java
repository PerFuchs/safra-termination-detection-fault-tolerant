package ibis.ipl.apps.safraExperiment.safra.faultSensitive;

import ibis.ipl.Registry;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.Experiment;
import ibis.ipl.apps.safraExperiment.ibisSignalling.IbisSignal;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.Token;
import ibis.ipl.apps.safraExperiment.safra.api.TokenFactory;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Semaphore;

public class SafraFS implements Observer, Safra {
  private final static Logger logger = Logger.getLogger(SafraFS.class);
  private final static Logger experimentLogger = Logger.getLogger(Experiment.experimentLoggerName);

  private Semaphore semaphore = new Semaphore(1, false);

  private boolean started = false;
  private boolean basicAlgorithmIsActive = false;
  private int isBlackUntil;
  private final boolean isInitiator;
  private long messageCounter = 0;
  private long sequenceNumber = 0;
  private TokenFS token;

  private CommunicationLayer communicationLayer;
  private final Registry registry;

  private boolean terminationDetected = false;

  public SafraFS(Registry registry, SignalPollerThread signalHandler, CommunicationLayer communicationLayer, boolean isInitiator) throws IOException {
    this.registry = registry;
    this.communicationLayer = communicationLayer;
    isBlackUntil = communicationLayer.getID();
    this.isInitiator = isInitiator;

    if (isInitiator) {
      token = new TokenFS(0, communicationLayer.getIbisCount() - 1);
      setActive(true, "Initiator");
    }

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

  public synchronized void setActive(boolean status, String reason) throws IOException {
    OurTimer timer = new OurTimer();
    if (terminationDetected) {
      experimentLogger.error(String.format("%d active status changed after termination.", communicationLayer.getID()));
    }
    if (status != basicAlgorithmIsActive) {
      experimentLogger.info(Event.getActiveStatusChangedEvent(status, reason));
    }

    basicAlgorithmIsActive = status;
    if (!basicAlgorithmIsActive) {
      handleToken(timer);
    }
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  public synchronized void startAlgorithm() throws InterruptedException, IOException {
    OurTimer timer = new OurTimer();
    semaphore.acquire();
    started = true;
    if (isInitiator) {
      handleToken(timer);
    }
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  public synchronized void handleSendingBasicMessage(int receiver) {
    OurTimer timer = new OurTimer();
    if (terminationDetected) {
      experimentLogger.error(String.format("%d sends basic message after termination.", communicationLayer.getID()));
    }

    messageCounter++;
    timer.pause();
    experimentLogger.info(Event.getSafraSumsEvent(receiver, 1));
    timer.start();
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  public synchronized void handleReceiveBasicMessage(int sender, long sequenceNumber) throws IOException {
    OurTimer timer = new OurTimer();
    if (terminationDetected) {
      experimentLogger.error(String.format("%d received basic message after termination.", communicationLayer.getID()));
    }
    setActive(true, "Received Basic message");
    messageCounter--;

    timer.pause();
    experimentLogger.info(Event.getSafraSumsEvent(sender, -1));
    timer.start();

    // Only color myself black if the message overtook the token. As defined in the paper
    if ((sender < communicationLayer.getID()
        && sequenceNumber > this.sequenceNumber)
        || (sender > communicationLayer.getID() && sequenceNumber == this.sequenceNumber)) {
      isBlackUntil = furthest(isBlackUntil, sender);
    }
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  public synchronized void receiveToken(Token token) throws IOException {
    OurTimer timer = new OurTimer();
    if (terminationDetected) {
      experimentLogger.error(String.format("%d received token after termination.", communicationLayer.getID()));
    }
    if (!(token instanceof TokenFS)) {
      throw new IllegalStateException("None FS token used with FS safra");
    }
    this.token = (TokenFS) token;
    handleToken(timer);
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  @Override
  public TokenFactory getTokenFactory() {
    return new TokenFactoryFS();
  }

  private synchronized void handleToken(OurTimer timer) throws IOException {
    if (!basicAlgorithmIsActive && this.token != null) {
      int me = communicationLayer.getID();
      isBlackUntil = furthest(token.isBlackUntil, isBlackUntil);
      token.messageCounter += messageCounter;

      if (token.messageCounter == 0 && isBlackUntil == me) {
        announce();
      } else {
        forwardToken(new TokenFS(token.messageCounter, furthest(isBlackUntil, (me + 1) % communicationLayer.getIbisCount())), timer);
        isBlackUntil = me;
        messageCounter = 0;
      }
    }
  }

  private synchronized void announce() throws IOException {
    experimentLogger.info(String.format("%s %d", Event.getAnnounceEvent(), communicationLayer.getID()));
    IbisSignal.signal(registry, communicationLayer.getIbises(), new IbisSignal("safra", "announce"));
  }

  public void await() throws InterruptedException {
    if (!started) {
      throw new IllegalStateException("SafraFS's algorithm has to be started before one can wait for termination.");
    }
    semaphore.acquire();
  }

  private synchronized void forwardToken(TokenFS token, OurTimer timer) throws IOException {
    timer.pause();
    experimentLogger.info(Event.getTokenSendEvent(token.getSize()));
    timer.start();

    this.token = null;
    sequenceNumber++;

    int nextNode = (communicationLayer.getID() + 1) % communicationLayer.getIbisCount();
    communicationLayer.sendToken(token, nextNode);
  }


  @Override
  public synchronized void update(Observable observable, Object o) {
    if (o instanceof IbisSignal) {
      IbisSignal signal = (IbisSignal) o;
      if (signal.module.equals("safra") && signal.name.equals("announce")) {
        terminationDetected = true;
        semaphore.release();
      }
    }
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }
}
