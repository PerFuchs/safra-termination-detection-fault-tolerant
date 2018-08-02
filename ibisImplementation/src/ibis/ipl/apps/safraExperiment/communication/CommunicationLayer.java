package ibis.ipl.apps.safraExperiment.communication;

import ibis.ipl.*;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.chandyMisra.DistanceMessage;
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

public class CommunicationLayer {
  private static Logger logger = Logger.getLogger(CommunicationLayer.class);

  private Ibis ibis;
  private Registry registry;
  private PortType portType;
  private IbisIdentifier[] ibises;
  private CrashSimulator crashSimulator;
  private int me;
  private Map<Integer, SendPort> sendPorts = new HashMap<>();
  private Map<Integer, ReceivePort> receivePorts = new HashMap<>();
  private Map<Integer, MessageUpcall> messageUpcalls = new HashMap<>();

  private boolean crashed;
  private Network network;
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

  private void createdSendPort(int receiver, String receiverPortName) throws IOException {
    IbisIdentifier id = ibises[receiver];
    String name = "Send" + receiver;
    SendPort p = ibis.createSendPort(portType, name);
    sendPorts.put(receiver, p);
    p.connect(id, receiverPortName);
  }

  public void connectIbises(Network network,
                            ChandyMisraNode chandyMisraNode,
                            Safra safraNode,
                            CrashDetector crashDetector,
                            BarrierFactory barrierFactory) throws IOException {
    this.network = network;
    this.safraNode = safraNode;
    List<Integer> neighbours = network.getNeighbours(getID());

    MessageUpcall generalUpcall = new MessageUpcall(
        this,
        chandyMisraNode,
        safraNode,
        crashDetector,
        barrierFactory);
    messageUpcalls.put(-1, generalUpcall);

    ReceivePort generalReceivePort = ibis.createReceivePort(portType, getGeneralReceivePortName(), generalUpcall);
    receivePorts.put(-1, generalReceivePort);
    generalReceivePort.enableConnections();
    generalReceivePort.enableMessageUpcalls();

    for (int i : neighbours) {
      String name = getReceivePortName(i);

      MessageUpcall mu = new MessageUpcall(this, chandyMisraNode, safraNode, crashDetector, barrierFactory);
      messageUpcalls.put(i, mu);

      ReceivePort p = ibis.createReceivePort(portType, name, mu);
      receivePorts.put(i, p);
      p.enableConnections();
      p.enableMessageUpcalls();
    }

    for (int i : neighbours) {
      createdSendPort(i, getReceivePortName(getID()));
    }
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
   *
   * @param dm
   * @param receiver
   * @param basicTimer used to time the basic algorithm. Is stopped while Safra processes the send event.
   * * @throws IOException
   */
  public void sendDistanceMessage(DistanceMessage dm, int receiver, OurTimer basicTimer) throws IOException {
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

  public void crash() {
    this.crashed = true;
    for (MessageUpcall mu : messageUpcalls.values()) {
      mu.crashed();
    }
  }

  // TODO failure detector only works for local failures
  public void broadcastCrashMessage() throws IOException {
    for (ReceivePort rp : receivePorts.values()) {
      SendPortIdentifier[] sids = rp.connectedTo();
      for (SendPortIdentifier sid : sids) {
        int id = Arrays.binarySearch(ibises, sid.ibisIdentifier());
        if (!sendPorts.containsKey(id)) {
          logger.debug(String.format("%d Creating new send port to %d.", getID(), id));
          createdSendPort(id, getGeneralReceivePortName());
        }
        sendCrashMessage(id);
      }
    }
  }

  public void sendCrashMessage(int receiver) throws IOException {
    logger.trace(String.format("%d sending crash message to %d", getID(), receiver));
    if (!sendPorts.containsKey(receiver)) {
      logger.debug(String.format("%d Creating new send port to %d.", getID(), receiver));
      createdSendPort(receiver, getGeneralReceivePortName());
    }
    SendPort sendPort = sendPorts.get(receiver);
    WriteMessage m = sendPort.newMessage();
    m.writeInt(MessageTypes.CRASHED.ordinal());
    m.send();
    m.finish();
  }

  // TODO exclude safrat time

  public void sendRequestMessage(int receiver) throws IOException {
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
        createdSendPort(receiver, getGeneralReceivePortName());
      }
      SendPort sendPort = sendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.TOKEN.ordinal());
      token.writeToMessage(m);
      m.send();
      m.finish();
    }
  }

  public void setCrashSimulator(CrashSimulator crashSimulator) {
    this.crashSimulator = crashSimulator;
  }
}
