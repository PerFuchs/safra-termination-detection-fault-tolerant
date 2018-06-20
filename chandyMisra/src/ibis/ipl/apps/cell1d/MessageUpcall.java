package ibis.ipl.apps.cell1d;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReadMessage;
import ibis.ipl.apps.cell1d.algorithm.ChandyMisraNode;
import ibis.ipl.apps.cell1d.algorithm.DistanceMessage;

import java.awt.*;
import java.io.IOException;

import static ibis.ipl.apps.cell1d.MessageTypes.DISTANCE;

public class MessageUpcall implements ibis.ipl.MessageUpcall {

  private final ChandyMisraNode chandyMisraNode;
  private CrashDetector crashDetector;
  private final IbisIdentifier origin;
  private boolean crashed = false;

  public MessageUpcall(ChandyMisraNode chandyMisraNode, CrashDetector crashDetector, IbisIdentifier origin) {
    this.chandyMisraNode = chandyMisraNode;
    this.crashDetector = crashDetector;
    this.origin = origin;
  }


  @Override
  public synchronized void upcall(ReadMessage readMessage) throws IOException, ClassNotFoundException {
    MessageTypes messageType = MessageTypes.values()[readMessage.readInt()];

    if (!crashed) {
      switch (messageType) {
        case DISTANCE:
          DistanceMessage dm = new DistanceMessage(readMessage.readInt());
          readMessage.finish();
          chandyMisraNode.handleReceiveDistanceMessage(dm, origin);
          break;
        case CRASHED:
          readMessage.finish();
          crashDetector.handleCrash(origin);
          break;
        case REQUEST:
          readMessage.finish();
          chandyMisraNode.handleRequestMessage(origin);
          break;

      }
    } else {
      readMessage.finish();
    }
  }

  protected void crashed() {
    this.crashed = true;
  }
}
