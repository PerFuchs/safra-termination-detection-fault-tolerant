package ibis.ipl.apps.safraExperiment.safra.faultTolerant;

import ibis.ipl.Registry;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashHandler;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.Experiment;
import ibis.ipl.apps.safraExperiment.ibisSignalling.IbisSignal;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.Token;
import ibis.ipl.apps.safraExperiment.safra.api.TokenFactory;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;


// TODO add timer metrics
public class SafraFT implements Observer, Safra, CrashHandler {
  private final static Logger logger = Logger.getLogger(SafraFT.class);
  private final static Logger experimentLogger = Logger.getLogger(Experiment.experimentLoggerName);

  private Semaphore semaphore = new Semaphore(1, false);

  private boolean started = false;
  private boolean basicAlgorithmIsActive = false;
  private int isBlackUntil;
  private List<Integer> messageCounters;
  private Set<Integer> crashed = new HashSet<>();
  private Set<Integer> report = new HashSet<>();

  private int nextNode;

  private long sequenceNumber = 0;
  private TokenFT token;
  private TokenFT backupToken;

  private CommunicationLayer communicationLayer;
  private final Registry registry;

  private boolean terminationDetected = false;

  public SafraFT(Registry registry,
                 SignalPollerThread signalHandler,
                 CommunicationLayer communicationLayer,
                 CrashDetector crashDetector,
                 boolean isBasicInitiator) throws IOException {
    this.registry = registry;
    this.communicationLayer = communicationLayer;
    isBlackUntil = communicationLayer.getID();

    messageCounters = new ArrayList<>(Collections.nCopies(communicationLayer.getIbisCount(), 0));
    nextNode = (communicationLayer.getID() + 1) % communicationLayer.getIbisCount();

    backupToken = new TokenFT(new ArrayList<Long>(
        Collections.nCopies(communicationLayer.getIbisCount(), 0L)),
        communicationLayer.getID(),
        0,
        new HashSet<Integer>());

    token = null;

    if (communicationLayer.isRoot()) {
      token = new TokenFT(new ArrayList<Long>(
          Collections.nCopies(communicationLayer.getIbisCount(), 0L)),
          communicationLayer.getIbisCount() - 1,
          1,
          new HashSet<Integer>());
      backupToken = token;
    }

    if (isBasicInitiator) {
      setActive(true);
    }

    signalHandler.addObserver(this);
    crashDetector.addHandler(this);
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

  public synchronized void setActive(boolean status) throws IOException {
    if (terminationDetected) {
      experimentLogger.error(String.format("%d active status changed after termination.", communicationLayer.getID()));
    }
    if (status != basicAlgorithmIsActive) {
      experimentLogger.info(Event.getActiveStatusChangedEvent(status));
    }

    basicAlgorithmIsActive = status;
    if (!basicAlgorithmIsActive) {
      handleToken();
    }
  }

  public synchronized void startAlgorithm() throws InterruptedException, IOException {
    semaphore.acquire();
    started = true;
    handleToken();
  }

  public synchronized void handleSendingBasicMessage(int receiver) {
    if (terminationDetected) {
      experimentLogger.error(String.format("%d sends basic message after termination.", communicationLayer.getID()));
    }
    if (!basicAlgorithmIsActive) {
      logger.error(String.format("Send message while being passive %d", communicationLayer.getID()));
    }
    if (!crashed.contains(receiver) && !report.contains(receiver)) {
      int count = messageCounters.get(receiver);
      count++;
      messageCounters.set(receiver, count);
      experimentLogger.info(Event.getSafraSumsEvent(messageCounters));
    }
  }

  public synchronized void handleReceiveBasicMessage(int sender, long sequenceNumber) throws IOException {
    if (terminationDetected) {
      experimentLogger.error(String.format("%d received basic message after termination.", communicationLayer.getID()));
    }
    if (!crashed.contains(sender)) {
      if (!report.contains(sender)) {
        setActive(true);
      }
      int counter = messageCounters.get(sender);
      counter--;
      messageCounters.set(sender, counter);

      // Only color myself black if the message overtook the token. As defined in the paper
      if ((sender < communicationLayer.getID()
          && sequenceNumber > this.sequenceNumber)
          || (sender > communicationLayer.getID() && sequenceNumber == this.sequenceNumber)) {
        isBlackUntil = furthest(isBlackUntil, sender);
      }
      experimentLogger.info(Event.getSafraSumsEvent(messageCounters));
    }
  }

  public synchronized void handleCrash(int crashedNode) throws IOException {
    if (terminationDetected) {
      experimentLogger.error(String.format("%d notfified crash after termination.", communicationLayer.getID()));
    }
    if (!crashed.contains(crashedNode) && !report.contains(crashedNode)) {
      report.add(crashedNode);
      if (crashedNode == nextNode) {
        newSuccessor();
        if (sequenceNumber > 0 || nextNode < communicationLayer.getID()) {
          backupToken.isBlackUntil = communicationLayer.getID();
          backupToken.crashed.addAll(report);
          if (nextNode < communicationLayer.getID()) {
            backupToken.sequenceNumber++;
          }
          experimentLogger.info(Event.getBackupTokenSendEvent());
          forwardToken(this.backupToken);
        }
      }
    }
  }

  private void newSuccessor() throws IOException {
    nextNode = (nextNode + 1) % communicationLayer.getIbisCount();
    while (report.contains(nextNode) || crashed.contains(nextNode)) {
      nextNode = (nextNode + 1) % communicationLayer.getIbisCount();
    }
    if (nextNode == communicationLayer.getID()) {
      logger.debug(String.format("%d next successor is myself", communicationLayer.getID()));
      if (!basicAlgorithmIsActive) {
        announce();
      }
      return;
    }
    logger.debug(String.format("%d choosing new successor %d", communicationLayer.getID(), nextNode));
    if (isBlackUntil != communicationLayer.getID()) {
      isBlackUntil = furthest(isBlackUntil, nextNode);
    }
  }

  public synchronized void receiveToken(Token token) throws IOException {
    if (terminationDetected) {
      experimentLogger.error(String.format("%d received token after termination.", communicationLayer.getID()));
    }
    if (!(token instanceof TokenFT)) {
      throw new IllegalStateException("None TokenFT used with SafraFT");
    }
    TokenFT t = (TokenFT) token;
    logger.debug(String.format("%d received token.", communicationLayer.getID()));

    StringBuilder crashedNodes = new StringBuilder();
    for (int c : ((TokenFT) token).crashed) {
      crashedNodes.append(c);
      crashedNodes.append(", ");
    }

    logger.debug(String.format("%d Token crashed nodes: %s", communicationLayer.getID(), crashedNodes.toString()));
    if (t.sequenceNumber == getSequenceNumber() + 1) {
      t.crashed.removeAll(crashed);
      crashed.addAll(t.crashed);
      this.token = t;
      handleToken();
    } else {
      logger.debug(String.format("%d ignored because of sequence number.", communicationLayer.getID()));
    }
  }

  private synchronized void handleToken() throws IOException {
    if (!basicAlgorithmIsActive && this.token != null) {
      int me = communicationLayer.getID();
      isBlackUntil = furthest(token.isBlackUntil, isBlackUntil);
      report.removeAll(token.crashed);

      logger.debug(String.format("%d isBlackUntil %d", me, isBlackUntil));

      StringBuilder crashedNodes = new StringBuilder();
      for (int c : crashed) {
        crashedNodes.append(c);
        crashedNodes.append(", ");
      }
      crashedNodes.append("Report: ");
      for (int r : report) {
        crashedNodes.append(r);
        crashedNodes.append(", ");
      }

      logger.debug(String.format("%d Crashed: %s", communicationLayer.getID(), crashedNodes.toString()));
      if (isBlackUntil == me || report.isEmpty()) {
        long mySum = 0;
        for (int i = 0; i < messageCounters.size(); i++) {
          if (i != me && !crashed.contains(i)) {
            mySum += messageCounters.get(i);
          }
        }
        token.messageCounters.set(me, mySum);
      }

      if (isBlackUntil == me) {
        long sum = 0;
        for (int i = 0; i < token.messageCounters.size(); i++) {
          if (!crashed.contains(i)) {
            sum += token.messageCounters.get(i);
          }
        }

        logger.debug(String.format("%d calculated sum %d", me, sum));
        if (sum == 0) {
          announce();
          return;
        }
      }

      if (crashed.contains(nextNode)) {
        newSuccessor();
      }

      if (nextNode < me) {
        token.sequenceNumber++;
      }

      if (!report.isEmpty()) {
        token.crashed.addAll(report);
        crashed.addAll(report);
        report.clear();
        token.isBlackUntil = me;
      } else {
        token.isBlackUntil = furthest(isBlackUntil, nextNode);
      }

      forwardToken(token);
      sequenceNumber++;
      isBlackUntil = me;
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

  private synchronized void forwardToken(TokenFT token) throws IOException {
    experimentLogger.info(Event.getTokenSendEvent());

    logger.debug(String.format("%d Forwarding token to %d", communicationLayer.getID(), nextNode));
    logger.debug(String.format("%d Token has %d crash reports", communicationLayer.getID(), token.crashed.size()));

    this.backupToken = token;
    this.token = null;
    if (nextNode == communicationLayer.getID()) {
      logger.debug(String.format("%d considering myself next", communicationLayer.getID()));
      if (!basicAlgorithmIsActive) {
        announce();
        return;
      }
    } else {
      communicationLayer.sendToken(token, nextNode);
    }
  }


  @Override
  public synchronized void update(Observable observable, Object o) {
    if (o instanceof IbisSignal) {
      IbisSignal signal = (IbisSignal) o;
      if (signal.module.equals("safra") && signal.name.equals("announce")) {
        logger.debug(String.format("%d got announce signal", communicationLayer.getID()));
        terminationDetected = true;
        semaphore.release();
      }
    }
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }


  @Override
  public TokenFactory getTokenFactory() {
    return new TokenFactoryFT();
  }
}
