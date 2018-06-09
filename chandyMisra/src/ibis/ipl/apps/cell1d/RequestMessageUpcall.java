package ibis.ipl.apps.cell1d;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.MessageUpcall;
import ibis.ipl.ReadMessage;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;

import java.io.IOException;

public class RequestMessageUpcall implements MessageUpcall {
  private final ChandyMisraNode chandyMisraNode;
  private final IbisIdentifier origin;

  public RequestMessageUpcall(ChandyMisraNode chandyMisraNode, IbisIdentifier origin) {
    this.chandyMisraNode = chandyMisraNode;
    this.origin = origin;
  }


  @Override
  public void upcall(ReadMessage readMessage) throws IOException, ClassNotFoundException {
    readMessage.finish();
    chandyMisraNode.handleRequestMessage(origin);
  }
}
