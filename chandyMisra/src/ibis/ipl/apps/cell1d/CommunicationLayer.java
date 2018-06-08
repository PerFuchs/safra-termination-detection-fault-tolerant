package ibis.ipl.apps.cell1d;

import ibis.ipl.*;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;
import ibis.ipl.apps.cell1d.algorithm.DistanceMessage;

import java.io.IOException;
import java.util.Map;

public class CommunicationLayer {

  private Ibis ibis;
  private Registry registry;
  private IbisIdentifier[] ibises;
  private Map<IbisIdentifier, SendPort> distanceMessageSendPorts;
  private Map<IbisIdentifier, ReceivePort> distanceMessageReceivePorts;
  int me;

  public CommunicationLayer(Ibis ibis, Registry registry) throws IOException {
    this.ibis = ibis;
    this.registry = registry;
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

  public void connectIbises(ChandyMisraNode chandyMisraNode) throws IOException {
    PortType distanceMessagePortType = new PortType(
        PortType.CONNECTION_ONE_TO_ONE,
        PortType.COMMUNICATION_RELIABLE,
        PortType.RECEIVE_AUTO_UPCALLS,
        PortType.SERIALIZATION_DATA);

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
        p.connect(id, getDistanceReceivePortName(i));
      }
    }
  }

  public boolean isRoot(IbisIdentifier id) {
    return ibises[0].equals(id);
  }

  public void sendDistanceMessage(DistanceMessage dm, IbisIdentifier receiver) throws IOException {
    SendPort sendPort = distanceMessageSendPorts.get(receiver);
    WriteMessage m = sendPort.newMessage();
    m.writeInt(dm.getDistance());
    m.send();
    m.finish();
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

}
