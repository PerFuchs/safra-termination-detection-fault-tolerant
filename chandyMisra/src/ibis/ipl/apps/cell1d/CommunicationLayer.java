package ibis.ipl.apps.cell1d;

import ibis.ipl.*;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;
import ibis.ipl.apps.cell1d.algorithm.DistanceMessage;
import ibis.ipl.apps.cell1d.algorithm.Network;

import java.io.IOException;
import java.util.*;

public class CommunicationLayer {
  private Ibis ibis;
  private Registry registry;
  private PortType portType;
  private IbisIdentifier[] ibises;
  private Map<Integer, SendPort> sendPorts = new HashMap<>();
  private Map<Integer, ReceivePort> receivePorts = new HashMap<>();

  private boolean crashed;
  private Network network;

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
    System.out.println("Found all ibises");
  }

  private String getReceivePortName(int i) {
    return "Receive" + i;
  }

  public void connectIbises(Network network, ChandyMisraNode chandyMisraNode, CrashDetector crashDetector) throws IOException {
    this.network = network;
    List<Integer> neighbours = network.getNeighbours(getID());

    for (int i : neighbours) {
      String name = getReceivePortName(i);
      ReceivePort p = ibis.createReceivePort(portType, name, new MessageUpcall(chandyMisraNode, crashDetector, i));
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
    return id == 0;
  }

  public void sendDistanceMessage(DistanceMessage dm, int receiver) throws IOException {
    if (!crashed) {
      SendPort sendPort = sendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.DISTANCE.ordinal());
      m.writeInt(dm.getDistance());
      m.send();
      m.finish();
    }
  }

  public IbisIdentifier[] getIbises() {
    return ibises;
  }

  public int getNodeNumber(IbisIdentifier identifier) {
    for (int i = 0; i < ibises.length; i++) {
      if (ibises[i].equals(identifier)) {
        return i;
      }
    }
    // TODO throw exception
    return -1;
  }

  // TODO notify the upcalls
  public void crash() {
    this.crashed = true;
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
      SendPort sendPort = sendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.REQUEST.ordinal());
      m.send();
      m.finish();
    }
  }

  public int getID() {
    return getNodeNumber(ibis.identifier());
  }

  public IbisIdentifier identifier() {
    return ibis.identifier();
  }

  public int getIbisCount() {
    return ibises.length;
  }
}
