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
  private PortType messagePortType;
  private IbisIdentifier[] ibises;
  private Map<IbisIdentifier, SendPort> sendPorts = new TreeMap<>();
  private Map<IbisIdentifier, ReceivePort> receivePorts = new TreeMap<>();

  int me;
  private boolean crashed;
  private List<IbisIdentifier> neighbours;

  public CommunicationLayer(Ibis ibis, Registry registry, PortType messagePortType) throws IOException {
    this.ibis = ibis;
    this.registry = registry;
    this.messagePortType = messagePortType;
    findAllIbises();
  }

  private void findAllIbises() throws IOException {
    final int processCount = registry.getPoolSize();
    ibises = new IbisIdentifier[processCount];

    for (int i = 0; i < processCount; i++) {
      IbisIdentifier id = registry.elect("" + i);
      ibises[i] = id;
      if (id.equals(ibis.identifier())) {
        me = i;
        break;
      }
    }

    for (int i = me + 1; i < processCount; i++) {
      ibises[i] = registry.getElectionResult("" + i);
    }
  }

  private String getReceivePortName(int i) {
    return "Receive" + i;
  }

  public void connectIbises(Network network, ChandyMisraNode chandyMisraNode, CrashDetector crashDetector) throws IOException {
    neighbours = Arrays.asList(network.getNeighbours(ibis.identifier()));
    for (int i = 0; i < ibises.length; i++) {
      IbisIdentifier id = ibises[i];
      if (neighbours.contains(id)) {
        String name = getReceivePortName(i);
        ReceivePort p = ibis.createReceivePort(messagePortType, name, new MessageUpcall(chandyMisraNode, crashDetector, id));
        receivePorts.put(id, p);
        p.enableConnections();
        p.enableMessageUpcalls();
      }
    }

    for (int i = 0; i < ibises.length; i++) {
      IbisIdentifier id = ibises[i];
      if (neighbours.contains(id)) {
        String name = "SendDistance" + i;
        SendPort p = ibis.createSendPort(messagePortType, name);
        sendPorts.put(id, p);
        p.connect(id, getReceivePortName(me));
      }
    }

  }

  public int getRoot() {
    return 0;
  }

  public boolean isRoot(IbisIdentifier id) {
    return ibises[0].equals(id);
  }

  public void sendDistanceMessage(DistanceMessage dm, IbisIdentifier receiver) throws IOException {
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
    for (IbisIdentifier id : neighbours) {
        SendPort sendPort = sendPorts.get(id);
        WriteMessage m = sendPort.newMessage();
        m.writeInt(MessageTypes.CRASHED.ordinal());
        m.send();
        m.finish();
    }
  }

  public void sendRequestMessage(IbisIdentifier receiver) throws IOException {
    if (!crashed) {
      SendPort sendPort = sendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.writeInt(MessageTypes.REQUEST.ordinal());
      m.send();
      m.finish();
    }
  }

  public IbisIdentifier identifier() {
    return ibis.identifier();
  }
}
