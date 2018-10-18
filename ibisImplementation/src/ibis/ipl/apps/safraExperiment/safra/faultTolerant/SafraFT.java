package ibis.ipl.apps.safraExperiment.safra.faultTolerant;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.*;
import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.OnlineExperiment;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.Token;
import ibis.ipl.apps.safraExperiment.safra.api.TokenFactory;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;


public class SafraFT implements Safra, CrashHandler {
  private final static Logger logger = Logger.getLogger(SafraFT.class);
  private final static Logger experimentLogger = Logger.getLogger(OnlineExperiment.experimentLoggerName);

  private Semaphore semaphore = new Semaphore(1, false);

  private boolean started = false;
  private boolean basicAlgorithmIsActive = false;
  private int isBlackUntil;
  private long[] messageCounters;
  private Set<Integer> crashed = new HashSet<>();
  private Set<Integer> report = new HashSet<>();

  private int nextNode;

  private long sequenceNumber = 0;
  private TokenFT token;
  private TokenFT backupToken;
  private final CrashSimulator crashSimulator;

  private CommunicationLayer communicationLayer;

  private boolean terminationDetected = false;

  public SafraFT(CommunicationLayer communicationLayer,
                 CrashSimulator crashSimulator,
                 CrashDetector crashDetector,
                 boolean isBasicInitiator) throws IOException {
    this.communicationLayer = communicationLayer;
    isBlackUntil = communicationLayer.getID();

    messageCounters = new long[communicationLayer.getIbisCount()];
    nextNode = (communicationLayer.getID() + 1) % communicationLayer.getIbisCount();

    backupToken = new TokenFT(new ArrayList<Integer>(
        Collections.nCopies(communicationLayer.getIbisCount(), 0)),
        communicationLayer.getID(),
        0,
        new HashSet<Integer>());
    this.crashSimulator = crashSimulator;

    token = null;

    if (communicationLayer.isRoot()) {
      token = new TokenFT(new ArrayList<Integer>(
          Collections.nCopies(communicationLayer.getIbisCount(), 0)),
          communicationLayer.getIbisCount() - 1,
          1,
          new HashSet<Integer>());
      backupToken = token;
    }

    if (isBasicInitiator) {
      try {
        setActive(true, "Initiator");
      } catch (CrashException e) {
        // Pass, is never actually thrown.
      }
    }

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

  public synchronized void setActive(boolean status, String reason) throws IOException, CrashException {
    OurTimer timer = new OurTimer();

    if (status != basicAlgorithmIsActive) {
      timer.pause();
      if (terminationDetected) {
        experimentLogger.error(String.format("%d active status changed after termination.", communicationLayer.getID()));
      }
      experimentLogger.info(Event.getActiveStatusChangedEvent(status, reason));
      timer.start();
    }

    basicAlgorithmIsActive = status;
    if (!basicAlgorithmIsActive) {
      handleToken(timer);
    }
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  @SuppressWarnings("Duplicates")
  public synchronized void startAlgorithm() throws InterruptedException, IOException, CrashException {
    OurTimer timer = new OurTimer();
    semaphore.acquire();
    started = true;
    handleToken(timer);
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  public synchronized void handleSendingBasicMessage(int receiver) {
    OurTimer timer = new OurTimer();
    if (terminationDetected) {
      experimentLogger.error(String.format("%d sends basic message after termination.", communicationLayer.getID()));
    }
    if (!basicAlgorithmIsActive) {
      logger.error(String.format("%04d Send message while being passive", communicationLayer.getID()));
    }
    if (!crashed.contains(receiver) && !report.contains(receiver)) {
      messageCounters[receiver]++;

      timer.pause();
      experimentLogger.info(Event.getSafraSumsEvent(receiver, messageCounters[receiver]));
      timer.start();
    }
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  public synchronized void handleReceiveBasicMessage(int sender, long sequenceNumber) throws IOException, CrashException {
    OurTimer timer = new OurTimer();
    if (terminationDetected) {
      experimentLogger.error(String.format("%d received basic message after termination.", communicationLayer.getID()));
    }
    if (!crashed.contains(sender)) {
      if (!report.contains(sender)) {
        timer.pause();
        setActive(true, "Received Basic Message");
        timer.start();
      }
      messageCounters[sender]--;

      timer.pause();
      experimentLogger.info(Event.getSafraSumsEvent(sender, messageCounters[sender]));
      timer.start();

      // Only color myself black if the message overtook the token. As defined in the paper
      if ((sender < communicationLayer.getID()
          && sequenceNumber > this.sequenceNumber)
          || (sender > communicationLayer.getID() && sequenceNumber == this.sequenceNumber)) {
        isBlackUntil = furthest(isBlackUntil, sender);
      }
    }
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  public synchronized void handleCrash(int crashedNode) throws IOException, CrashException {
    OurTimer timer = new OurTimer();
    if (!crashed.contains(crashedNode) && !report.contains(crashedNode) && !terminationDetected) {
      report.add(crashedNode);
      if (crashedNode == nextNode) {
        newSuccessor();
        if (sequenceNumber > 0 || nextNode < communicationLayer.getID()) {
          backupToken.isBlackUntil = communicationLayer.getID();
          backupToken.crashed.addAll(report);
          if (nextNode < communicationLayer.getID()) {
            backupToken.sequenceNumber = getSequenceNumber() + 1;
          }

          timer.pause();
          experimentLogger.info(Event.getBackupTokenSendEvent());
          timer.start();

          crashSimulator.reachedCrashPoint(CrashPoint.BEFORE_SENDING_BACKUP_TOKEN);
          forwardToken(this.backupToken, timer);
          crashSimulator.reachedCrashPoint(CrashPoint.AFTER_SENDING_BACKUP_TOKEN);
        }
      }
    }
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  private void newSuccessor() throws IOException {
    nextNode = (nextNode + 1) % communicationLayer.getIbisCount();
    while (report.contains(nextNode) || crashed.contains(nextNode)) {
      nextNode = (nextNode + 1) % communicationLayer.getIbisCount();
    }
    if (nextNode == communicationLayer.getID()) {
      if (!basicAlgorithmIsActive) {
        announce();
      }
      return;
    }
    if (isBlackUntil != communicationLayer.getID()) {
      isBlackUntil = furthest(isBlackUntil, nextNode);
    }
  }

  public synchronized void receiveToken(Token token) throws IOException, CrashException {
    OurTimer timer = new OurTimer();
    if (!(token instanceof TokenFT)) {
      throw new IllegalStateException("None TokenFT used with SafraFT");
    }
    crashSimulator.reachedCrashPoint(CrashPoint.BEFORE_RECEIVING_TOKEN);

    TokenFT t = (TokenFT) token;
    logger.debug(String.format("%04d received token %s", communicationLayer.getID(), token.toString()));
    if (t.sequenceNumber == getSequenceNumber() + 1) {
      logger.debug(String.format("%04d accepts token", communicationLayer.getID()));
      if (terminationDetected) {
        experimentLogger.warn(String.format("%d received token after termination.", communicationLayer.getID()));
      }
      this.token = t;
      handleToken(timer);
    } else {
      logger.debug(String.format("%04d disregards token", communicationLayer.getID()));
    }
    timer.stopAndCreateSafraTimeSpentEvent();
  }

  private synchronized void handleToken(OurTimer timer) throws IOException, CrashException {
    if (!basicAlgorithmIsActive && this.token != null) {
      int me = communicationLayer.getID();
      if (logger.isDebugEnabled()) {
        StringBuilder crashedString = new StringBuilder();
        List<Integer> crashedSorted = new LinkedList<>(crashed);
        Collections.sort(crashedSorted);
        for (int c : crashedSorted) {
          crashedString.append(c);
          crashedString.append(", ");
        }
        logger.debug(String.format("%04d crashed: %s", me, crashedString.toString()));
      }

      token.crashed.removeAll(crashed);
      crashed.addAll(token.crashed);

      isBlackUntil = furthest(token.isBlackUntil, isBlackUntil);
      logger.debug(String.format("%04d is black until %04d", me, isBlackUntil));
      report.removeAll(token.crashed);

      if (!report.isEmpty()) {
        logger.debug(String.format("%04d report is not empty", me));
      }
      if (isBlackUntil == me || report.isEmpty()) {
        int mySum = 0;
        for (int i = 0; i < messageCounters.length; i++) {
          if (i != me && !crashed.contains(i)) {
            mySum += messageCounters[i];
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

        if (sum == 0) {
          announce();
          this.token = null;
          return;
        } else {
          logger.debug(String.format("%04d sum %d", me, sum));
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

      crashSimulator.reachedCrashPoint(CrashPoint.BEFORE_SENDING_TOKEN);
      forwardToken(token, timer);
      this.token = null;
      crashSimulator.reachedCrashPoint(CrashPoint.AFTER_SENDING_TOKEN);
      sequenceNumber++;
      isBlackUntil = me;
    }
  }

  private synchronized void announce() throws IOException {
    experimentLogger.info(String.format("%s %d", Event.getAnnounceEvent(), communicationLayer.getID()));
    handleAnnounce();
  }

  public void await() throws InterruptedException {
    if (!started) {
      throw new IllegalStateException("SafraFS's algorithm has to be started before one can wait for termination.");
    }
    semaphore.acquire();
  }

  private synchronized void forwardToken(TokenFT token, OurTimer timer) throws IOException {
    timer.pause();
    experimentLogger.info(Event.getTokenSendEvent(token.getSize()));
    timer.start();

    this.backupToken = token;
    if (nextNode == communicationLayer.getID()) {
      if (!basicAlgorithmIsActive) {
        announce();
        return;
      }
    } else {
      logger.debug(String.format("%04d to %04d sends token: %s", communicationLayer.getID(), nextNode, token.toString()));
      communicationLayer.sendToken(token, nextNode);
    }
  }

  public synchronized void handleAnnounce() throws IOException {
    if (!terminationDetected) {
      logger.debug(String.format("%d got announce signal", communicationLayer.getID()));
        communicationLayer.sendAnnounce((communicationLayer.getID() + 1) % communicationLayer.getIbisCount());
        terminationDetected = true;
        semaphore.release();
    }
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }


  @Override
  public TokenFactory getTokenFactory() {
    return new TokenFactoryFT();
  }

  @Override
  public boolean crashDetected(int origin) {
    return crashed.contains(origin) || report.contains(origin) || (token != null && token.crashed.contains(origin));
  }
}
