package ibis.ipl.apps.safraExperiment.communication;

import ibis.ipl.*;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.chandyMisra.DistanceMessage;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.Token;
import ibis.ipl.apps.safraExperiment.spanningTree.Network;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.utils.barrier.BarrierFactory;

import java.io.IOException;
import java.util.*;

public class CommunicationLayer {
  private Ibis ibis;
  private Registry registry;
  private PortType portType;
  private IbisIdentifier[] ibises;
  private int me;
  private Map<Integer, SendPort> sendPorts = new HashMap<>();
  private Map<Integer, ReceivePort> receivePorts = new HashMap<>();
  private Map<Integer, MessageUpcall> messageUpcalls = new HashMap<>();

  private boolean crashed;
  private Network network;
  private Safra safraNode;

  public CommunicationLayer(Ibis ibis, Registry registry, PortType portType) throws IOException {
    this.ibis = ibis;
    this.registry = registry;
    this.portType = portType;
    this.ibises = new IbisIdentifier[registry.getPoolSize()];
    findAllIbises();
  }

  private void findAllIbises() {
    ibises = registry.joinedIbises();
    if (ibises.length != registry.getPoolSize()) {
      System.out.println("Not all ibises reported by joinedIbises");
    }
    Arrays.sort(ibises);
    me = Arrays.asList(ibises).indexOf(ibis.identifier());
    System.out.println("Found all ibises");
  }

  private String getGeneralReceivePortName() {
    return "Receive";
  }

  private String getReceivePortName(int i) {
    return "Receive" + i;
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
      IbisIdentifier id = ibises[i];
      String name = "Send" + i;
      SendPort p = ibis.createSendPort(portType, name);
      sendPorts.put(i, p);
      p.connect(id, getReceivePortName(getID()));
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

  public void sendDistanceMessage(DistanceMessage dm, int receiver) throws IOException {
    if (!crashed) {
      safraNode.handleSendingBasicMessage(receiver);
      SendPort sendPort = sendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.DISTANCE.ordinal());
      m.writeLong(safraNode.getSequenceNumber());
      m.writeInt(dm.getDistance());
      m.send();
      m.finish();
    }
  }

  public void crash() {
    this.crashed = true;
    for (MessageUpcall mu : messageUpcalls.values()) {
      mu.crashed();
    }
  }

  // TODO failure detector only works for local failures
  public void broadcastCrashMessage() throws IOException {
    for (int id : network.getNeighbours(getID())) {
      SendPort sendPort = sendPorts.get(id);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.CRASHED.ordinal());
      m.send();
      m.finish();
    }
  }

  public void sendRequestMessage(int receiver) throws IOException {
    if (!crashed) {
      safraNode.handleSendingBasicMessage(receiver);
      SendPort sendPort = sendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.REQUEST.ordinal());
      m.writeLong(safraNode.getSequenceNumber());
      m.send();
      m.finish();
    }
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
      SendPort sendPort = sendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.TOKEN.ordinal());
      token.writeToMessage(m);
      m.send();
      m.finish();
    }
  }
}
