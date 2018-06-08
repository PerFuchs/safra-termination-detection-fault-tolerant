package ibis.ipl.apps.cell1d;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.MessageUpcall;
import ibis.ipl.ReadMessage;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;
import ibis.ipl.apps.cell1d.algorithm.DistanceMessage;

import java.io.IOException;

public class DistanceUpCall implements MessageUpcall {
  private ChandyMisraNode node;
  private IbisIdentifier origin;

  public DistanceUpCall(ChandyMisraNode node, IbisIdentifier origin) {
    this.node = node;
    this.origin = origin;
  }

  @Override
  public void upcall(ReadMessage readMessage) throws IOException, ClassNotFoundException {
    DistanceMessage dm = new DistanceMessage(readMessage.readInt());
    readMessage.finish();
    node.handleReceiveDistanceMessage(dm, origin);
  }
}
