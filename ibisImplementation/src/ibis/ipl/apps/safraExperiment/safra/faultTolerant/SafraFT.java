package ibis.ipl.apps.safraExperiment.safra.faultTolerant;

import ibis.ipl.Registry;
import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashHandler;
import ibis.ipl.apps.safraExperiment.ibisSignalling.IbisSignal;
import ibis.ipl.apps.safraExperiment.ibisSignalling.SignalPollerThread;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.Token;
import ibis.ipl.apps.safraExperiment.safra.api.TokenFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;

public class SafraFT implements Observer, Safra, CrashHandler {
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
  private TokenFT backupToken;  // TODO initialize

  private CommunicationLayer communicationLayer;
  private final Registry registry;

  public SafraFT(Registry registry, SignalPollerThread signalHandler, CommunicationLayer communicationLayer, CrashDetector crashDetector) {
    this.registry = registry;
    this.communicationLayer = communicationLayer;
    isBlackUntil = communicationLayer.getID();

    messageCounters = new ArrayList<>(Collections.nCopies(communicationLayer.getIbisCount(), 0));
    nextNode = (communicationLayer.getID() + 1) % communicationLayer.getIbisCount();

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
    basicAlgorithmIsActive = status;
    if (!basicAlgorithmIsActive) {
      handleToken();
    }
  }

  public synchronized void startAlgorithm() throws InterruptedException, IOException {
    semaphore.acquire();
    started = true;
    token = null;

    backupToken = new TokenFT(new ArrayList<Long>(
        Collections.nCopies(communicationLayer.getIbisCount(), 0L)),
        communicationLayer.getID(),
        0,
        new HashSet<Integer>());

    nextNode = (communicationLayer.getID() + 1) % communicationLayer.getIbisCount();
    if (communicationLayer.isRoot()) {
      token = new TokenFT(new ArrayList<Long>(
          Collections.nCopies(communicationLayer.getIbisCount(), 0L)),
          communicationLayer.getIbisCount() - 1,
          1,
          new HashSet<Integer>());
      backupToken = token;
      setActive(true);
    }
  }

  public synchronized void handleSendingBasicMessage(int receiver) {
    if (!crashed.contains(receiver) && !report.contains(receiver)) {
      int count = messageCounters.get(receiver);
      count++;
      messageCounters.set(receiver, count);
    }
  }

  public synchronized void handleReceiveBasicMessage(int sender, long sequenceNumber) {
    if (!crashed.contains(sender)) {
      if (!report.contains(sender)) {
        basicAlgorithmIsActive = true;
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
    }
  }

  public void handleCrash(int crashedNode) throws IOException {
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
          forwardToken(this.backupToken);
        }
      }
    }
  }

  private void newSuccessor() throws IOException {
    System.out.println("New Successor");
    nextNode = (nextNode + 1) % communicationLayer.getIbisCount();
    while (report.contains(nextNode) || crashed.contains(nextNode)) {
      nextNode = (nextNode + 1) % communicationLayer.getIbisCount();
    }
    if (nextNode == communicationLayer.getID()) {
      if (!basicAlgorithmIsActive) {
        announce();
      }
    }
    if (isBlackUntil != communicationLayer.getID()) {
      isBlackUntil = furthest(isBlackUntil, nextNode);
    }
  }

  public synchronized void receiveToken(Token token) throws IOException {
    if (!(token instanceof TokenFT)) {
      throw new IllegalStateException("None TokenFT used with SafraFT");
    }
    TokenFT t = (TokenFT) token;
    if (t.sequenceNumber == getSequenceNumber() + 1) {
      t.crashed.removeAll(crashed);
      crashed.addAll(t.crashed);
      this.token = t;
      handleToken();
    }
  }

  private synchronized void handleToken() throws IOException {
    if (!basicAlgorithmIsActive && this.token != null) {
      int me = communicationLayer.getID();
      isBlackUntil = furthest(token.isBlackUntil, isBlackUntil);
      report.removeAll(token.crashed);

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
          sum += token.messageCounters.get(i);
        }
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
      isBlackUntil = me;
    }
  }

  private synchronized void announce() throws IOException {
    IbisSignal.signal(registry, communicationLayer.getIbises(), new IbisSignal("safra", "announce"));
  }

  public void await() throws InterruptedException {
    if (!started) {
      throw new IllegalStateException("SafraFS's algorithm has to be started before one can wait for termination.");
    }
    semaphore.acquire();
    System.out.println("Safra passed");
  }

  private synchronized void forwardToken(TokenFT token) throws IOException {
    this.backupToken = token;
    this.token = null;
    sequenceNumber++;
    if (nextNode == communicationLayer.getID()) {
      if (!basicAlgorithmIsActive) {
        announce();
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
