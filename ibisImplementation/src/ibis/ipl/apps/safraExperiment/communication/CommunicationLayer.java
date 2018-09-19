package ibis.ipl.apps.safraExperiment.communication;

import ibis.ipl.*;
import ibis.ipl.apps.safraExperiment.awebruchSyncronizer.AlphaSynchronizer;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.chandyMisra.DistanceMessage;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashException;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashPoint;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.Token;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.utils.OurTimer;
import ibis.ipl.apps.safraExperiment.utils.barrier.BarrierFactory;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

/**
 * This is the sending part of the IPL wrapper, it exposes an interface where Ibises are referred to as numbers between
 * 0 to <networkSize> - 1 to send messages to them. It hides the complexity of finding all ibises and assigning these
 * numbers, as well as, the complexity of setting up channels between the ibises.
 * <p>
 * The channels between Ibises are mostly setup statically, each ibis sets up channels as specified by a `Network` instance
 * for the whole run. If an Ibises is expected to simulate a crash it also sets up channels to send crash notification
 * to all neighbours. This is done to avoid transferring these messages in band and FIFO with all other messages.
 * Channels to keep the undirected ring used by Safra intact are setup dynamically when they are needed.
 */
public class CommunicationLayer {
  private static Logger logger = Logger.getLogger(CommunicationLayer.class);

  private Ibis ibis;
  private Registry registry;
  private PortType portType;
  private IbisIdentifier[] ibises;
  private CrashSimulator crashSimulator;
  private int me;
  private Map<Integer, SendPort> sendPorts = new HashMap<>();       // Used for other messages
  private Map<Integer, SendPort> crashSendPorts = new HashMap<>();  // Used to send crash notifications
  private Map<Integer, ReceivePort> receivePorts = new HashMap<>();
  private Map<Integer, MessageUpcall> messageUpcalls = new HashMap<>();

  private boolean crashed;
  private Network network;
  private AlphaSynchronizer synchronizer;
  private Safra safraNode;

  public CommunicationLayer(Ibis ibis, Registry registry, PortType portType) {
    this.ibis = ibis;
    this.registry = registry;
    this.portType = portType;
    this.ibises = new IbisIdentifier[registry.getPoolSize()];
    findAllIbises();
  }

  private void findAllIbises() {
    ibises = registry.joinedIbises();
    if (ibises.length != registry.getPoolSize()) {
      logger.error("Not all ibises reported by joinedIbises");
    }
    Arrays.sort(ibises);
    me = Arrays.asList(ibises).indexOf(ibis.identifier());
  }

  private String getGeneralReceivePortName() {
    return "Receive";
  }

  private String getReceivePortName(int i) {
    return "Receive" + i;
  }

  private String getCrashPortName() {
    return "CrashPort";
  }

  public void connectIbises(Network network, ChandyMisraNode chandyMisraNode, AlphaSynchronizer synchronizer, Safra safraNode, CrashDetector crashDetector, BarrierFactory barrierFactory, CrashSimulator crashSimulator) throws IOException {
    this.network = network;
    this.synchronizer = synchronizer;
    this.safraNode = safraNode;

    setupGeneralReceivePort(chandyMisraNode, safraNode, crashDetector, barrierFactory);
    setupCrashReceivePort(chandyMisraNode, safraNode, crashDetector, barrierFactory);

    Set<Integer> neighbours = network.getNeighbours(getID());
    for (int i : neighbours) {
      String name = getReceivePortName(i);

      MessageUpcall mu = new MessageUpcall(this, chandyMisraNode, synchronizer, safraNode, crashDetector, barrierFactory);
      messageUpcalls.put(i, mu);

      ReceivePort p = setupReceivePort(name, mu);
      receivePorts.put(i, p);
    }

    for (int i : neighbours) {
      createSendPort(i, getReceivePortName(getID()));
      if (crashSimulator.couldCrash()) {
        createCrashSendPort(i);
      }
    }
  }

  private void createSendPort(int receiver, String receiverPortName) throws IOException {
    IbisIdentifier id = ibises[receiver];
    String name = "Send" + receiver;
    SendPort p = ibis.createSendPort(portType, name);
    sendPorts.put(receiver, p);
    p.connect(id, receiverPortName);
  }

  private void createCrashSendPort(int receiver) throws IOException {
    IbisIdentifier id = ibises[receiver];
    String name = "CrashSend" + receiver;
    SendPort p = ibis.createSendPort(portType, name);
    crashSendPorts.put(receiver, p);
    p.connect(id, getCrashPortName());
  }

  private void setupCrashReceivePort(ChandyMisraNode chandyMisraNode, Safra safraNode, CrashDetector crashDetector, BarrierFactory barrierFactory) throws IOException {
    MessageUpcall upcall = new MessageUpcall(this, chandyMisraNode, synchronizer, safraNode, crashDetector, barrierFactory);
    messageUpcalls.put(-2, upcall);

    ReceivePort crashReceivePort = setupReceivePort(getCrashPortName(), upcall);
    receivePorts.put(-2, crashReceivePort);
  }

  private void setupGeneralReceivePort(ChandyMisraNode chandyMisraNode, Safra safraNode, CrashDetector crashDetector, BarrierFactory barrierFactory) throws IOException {
    MessageUpcall generalUpcall = new MessageUpcall(this, chandyMisraNode, synchronizer, safraNode, crashDetector, barrierFactory);
    messageUpcalls.put(-1, generalUpcall);

    ReceivePort generalReceivePort = setupReceivePort(getGeneralReceivePortName(), generalUpcall);
    receivePorts.put(-1, generalReceivePort);
  }

  private ReceivePort setupReceivePort(String name, MessageUpcall upcall) throws IOException {
    ReceivePort receivePort = ibis.createReceivePort(portType, name, upcall);
    receivePort.enableConnections();
    receivePort.enableMessageUpcalls();
    return receivePort;
  }

  public int getRoot() {
    return 0;
  }

  public boolean isRoot(int id) {
    return getID() == id;
  }

  public boolean isRoot() {
    return getID() == getRoot();
  }

  /**
   * @param basicTimer used to time the basic algorithm. Is stopped while Safra processes the send event.
   *                   * @throws IOException
   */
  public void sendDistanceMessage(DistanceMessage dm, int receiver, OurTimer basicTimer) throws IOException, CrashException {
    crashSimulator.reachedCrashPoint(CrashPoint.BEFORE_SENDING_BASIC_MESSAGE);
    if (!crashed) {
      logger.trace(String.format("%d sending distance message to %d", getID(), receiver));

      basicTimer.pause();
      safraNode.handleSendingBasicMessage(receiver);
      basicTimer.start();

      SendPort sendPort = sendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.DISTANCE.ordinal());
      m.writeLong(safraNode.getSequenceNumber());
      m.writeInt(dm.getDistance());
      m.send();
      m.finish();
    }
    crashSimulator.reachedCrashPoint(CrashPoint.AFTER_SENDING_BASIC_MESSAGE);
  }

  /**
   * Informs the communication layer that this node has crashed and should not send or receive any messages anymore.
   */
  public void crash() {
    this.crashed = true;
    for (MessageUpcall mu : messageUpcalls.values()) {
      mu.crashed();
    }
  }

  /**
   * This is no actual broadcast. It only sends these messages to all neighbours
   *
   * @throws IOException
   */
  public void broadcastCrashMessage() throws IOException {
    for (ReceivePort rp : receivePorts.values()) {
      SendPortIdentifier[] sids = rp.connectedTo();
      for (SendPortIdentifier sid : sids) {
        int id = Arrays.binarySearch(ibises, sid.ibisIdentifier());
        sendCrashMessage(id);
      }
    }
  }

  public void sendCrashMessage(int receiver) throws IOException {
    logger.trace(String.format("%d sending crash message to %d", getID(), receiver));
    if (!crashSendPorts.containsKey(receiver)) {
      logger.debug(String.format("%d Creating new send port to %d.", getID(), receiver));
      createCrashSendPort(receiver);
    }
    SendPort sendPort = crashSendPorts.get(receiver);
    WriteMessage m = sendPort.newMessage();
    m.writeInt(MessageTypes.CRASHED.ordinal());
    m.send();
    m.finish();
  }

  public void sendRequestMessage(int receiver) throws IOException, CrashException {
    crashSimulator.reachedCrashPoint(CrashPoint.BEFORE_SENDING_BASIC_MESSAGE);
    if (!crashed) {
      logger.trace(String.format("%d sending request message to %d", getID(), receiver));
      safraNode.handleSendingBasicMessage(receiver);
      SendPort sendPort = sendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.REQUEST.ordinal());
      m.writeLong(safraNode.getSequenceNumber());
      m.send();
      m.finish();
    }
    crashSimulator.reachedCrashPoint(CrashPoint.AFTER_SENDING_BASIC_MESSAGE);
  }

  public void sendBarrierMessage(int receiver, String name) throws IOException {
    SendPort sendPort = sendPorts.get(receiver);
    WriteMessage m = sendPort.newMessage();
    m.writeInt(MessageTypes.BARRIER.ordinal());
    m.writeString(name);
    m.send();
    m.finish();
  }

  public int getID() {
    return me;
  }

  public int getIbisCount() {
    return ibises.length;
  }

  public List<IbisIdentifier> getIbises() {
    return Arrays.asList(ibises);
  }

  public IbisIdentifier getIbisIdentifier() {
    return getIbisIdentifier(getID());
  }

  public List<IbisIdentifier> getOtherIbises() {
    List<IbisIdentifier> otherIbises = new LinkedList<IbisIdentifier>(Arrays.asList(ibises));
    otherIbises.remove(getID());
    return otherIbises;
  }

  public IbisIdentifier getIbisIdentifier(int id) {
    return ibises[id];
  }

  public void sendToken(Token token, int receiver) throws IOException {
    if (!crashed) {
      logger.debug(String.format("%d sending token to %d", getID(), receiver));
      if (!sendPorts.containsKey(receiver)) {
        logger.debug(String.format("%d Creating new send port to %d.", getID(), receiver));
        createSendPort(receiver, getGeneralReceivePortName());
      }
      SendPort sendPort = sendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.TOKEN.ordinal());
      token.writeToMessage(m);
      m.send();
      m.finish();
    }
  }

  public void sendAnnounce(int receiver) throws IOException {
    SendPort sendPort = sendPorts.get(receiver);
    WriteMessage m = sendPort.newMessage();
    m.writeInt(MessageTypes.ANNOUNCE.ordinal());
    m.send();
    m.finish();
  }

  public void setCrashSimulator(CrashSimulator crashSimulator) {
    this.crashSimulator = crashSimulator;
  }

  public void sendMessage(int receiver, Message message, OurTimer basicTimer) throws IOException, CrashException {
    if (message instanceof BasicMessage) {
      crashSimulator.reachedCrashPoint(CrashPoint.BEFORE_SENDING_BASIC_MESSAGE);
      if (!crashed) {
        basicTimer.pause();
        safraNode.handleSendingBasicMessage(receiver);
        basicTimer.start();
      }
    }
    if (!crashed) {
      WriteMessage m = sendPorts.get(receiver).newMessage();
      m.writeInt(MessageTypes.MESSAGECLASS.ordinal());
      message.writeToIPLMessage(m);
      m.send();
      m.finish();
    }
    if (message instanceof BasicMessage) {
      crashSimulator.reachedCrashPoint(CrashPoint.AFTER_SENDING_BASIC_MESSAGE);
    }
  }

  public Set<Integer> getNeighbours() {
    return network.getNeighbours(this.getID());
  }

  public void setNetwork(Network network) {
    this.network = network;
  }
}
