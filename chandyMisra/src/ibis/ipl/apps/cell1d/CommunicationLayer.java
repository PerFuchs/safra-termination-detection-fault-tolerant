package ibis.ipl.apps.cell1d;

import ibis.ipl.*;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;
import ibis.ipl.apps.cell1d.algorithm.DistanceMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class CommunicationLayer {

  private Ibis ibis;
  private Registry registry;
  private PortType distanceMessagePortType;
  private IbisIdentifier[] ibises;
  private Map<IbisIdentifier, SendPort> distanceMessageSendPorts = new TreeMap<>();
  private Map<IbisIdentifier, ReceivePort> distanceMessageReceivePorts = new TreeMap<>();
  int me;
  private boolean crashed;
  private PortType crashedMessagePortType;
  private Map<IbisIdentifier, SendPort> crashedMessageSendPorts = new TreeMap<>();
  private Map<IbisIdentifier, ReceivePort> crashedMessageReceivePorts = new TreeMap<>();
  private Map<IbisIdentifier, ReceivePort> requestMessageReceivePorts = new TreeMap<>();
  private Map<IbisIdentifier, SendPort> requestMessageSendPorts = new TreeMap<>();

  public CommunicationLayer(Ibis ibis, Registry registry, PortType distanceMessagePortType, PortType crashedMessagePortType) throws IOException {
    this.ibis = ibis;
    this.registry = registry;
    this.distanceMessagePortType = distanceMessagePortType;
    this.crashedMessagePortType = crashedMessagePortType;
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

  private String getDistanceReceivePortName(int i) {
    return "ReceiveDistance" + i;
  }

  public void connectIbises(ChandyMisraNode chandyMisraNode, CrashDetector crashDetector) throws IOException {
    connectDistanceMessagePorts(chandyMisraNode);
    connectRequestMessagePorts(chandyMisraNode);
    connectCrashedMessagePorts(crashDetector);
  }

  private void connectRequestMessagePorts(ChandyMisraNode chandyMisraNode) throws IOException {
    for (int i = 0; i < ibises.length; i++) {
      IbisIdentifier id = ibises[i];
      if (!id.equals(ibis.identifier())) {
        String name = getRequestMessagePortName(i);
        ReceivePort p = ibis.createReceivePort(crashedMessagePortType, name, new RequestMessageUpcall(chandyMisraNode, id));
        requestMessageReceivePorts.put(id, p);
        p.enableConnections();
        p.enableMessageUpcalls();
      }
    }

    for (int i = 0; i < ibises.length; i++) {
      IbisIdentifier id = ibises[i];
      if (!id.equals(ibis.identifier())) {
        String name = "SendRequest" + i;
        SendPort p = ibis.createSendPort(crashedMessagePortType, name);
        requestMessageSendPorts.put(id, p);
        p.connect(id, getRequestMessagePortName(me));
      }
    }
  }

  private String getRequestMessagePortName(int i) {
    return "ReceiveRequest" + i;
  }

  // TODO refactor deduplication
  private void connectCrashedMessagePorts(CrashDetector crashDetector) throws IOException {
    for (int i = 0; i < ibises.length; i++) {
      IbisIdentifier id = ibises[i];
      if (!id.equals(ibis.identifier())) {
        String name = getCrashedReceivePortName(i);
        ReceivePort p = ibis.createReceivePort(crashedMessagePortType, name, new CrashedUpcall(crashDetector, id));
        crashedMessageReceivePorts.put(id, p);
        p.enableConnections();
        p.enableMessageUpcalls();
      }
    }

    for (int i = 0; i < ibises.length; i++) {
      IbisIdentifier id = ibises[i];
      if (!id.equals(ibis.identifier())) {
        String name = "SendCrashed" + i;
        SendPort p = ibis.createSendPort(crashedMessagePortType, name);
        crashedMessageSendPorts.put(id, p);
        p.connect(id, getCrashedReceivePortName(me));
      }
    }
  }

  private String getCrashedReceivePortName(int i) {
    return "ReceiveCrashed" + i;
  }

  private void connectDistanceMessagePorts(ChandyMisraNode chandyMisraNode) throws IOException {
    for (int i = 0; i < ibises.length; i++) {
      IbisIdentifier id = ibises[i];
      if (!id.equals(ibis.identifier())) {
        String name = getDistanceReceivePortName(i);
        ReceivePort p = ibis.createReceivePort(distanceMessagePortType, name, new DistanceUpCall(chandyMisraNode, id));
        distanceMessageReceivePorts.put(id, p);
        p.enableConnections();
        p.enableMessageUpcalls();
      }
    }

    for (int i = 0; i < ibises.length; i++) {
      IbisIdentifier id = ibises[i];
      if (!id.equals(ibis.identifier())) {
        String name = "SendDistance" + i;
        SendPort p = ibis.createSendPort(distanceMessagePortType, name);
        distanceMessageSendPorts.put(id, p);
        p.connect(id, getDistanceReceivePortName(me));
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
      SendPort sendPort = distanceMessageSendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
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


  public void crash() {
    this.crashed = true;
  }

  public void broadcastCrashMessage() throws IOException {
    for (IbisIdentifier id : ibises) {
      if (!id.equals(ibis.identifier())) {
        SendPort sendPort = crashedMessageSendPorts.get(id);
        WriteMessage m = sendPort.newMessage();
        m.send();
        m.finish();
      }
    }
  }

  public void sendRequestMessage(IbisIdentifier receiver) throws IOException {
    if (!crashed) {
      SendPort sendPort = requestMessageSendPorts.get(receiver);
      WriteMessage m = sendPort.newMessage();
      m.send();
      m.finish();
    }
  }

  public IbisIdentifier identifier() {
    return ibis.identifier();
  }
}
