package ibis.ipl.apps.safraExperiment.communication;

import ibis.ipl.ReadMessage;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.chandyMisra.DistanceMessage;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.safra.faultSensitive.Safra;
import ibis.ipl.apps.safraExperiment.safra.faultSensitive.Token;

import java.io.IOException;

public class MessageUpcall implements ibis.ipl.MessageUpcall {

  private final ChandyMisraNode chandyMisraNode;
  private final Safra safraNode;
  private CrashDetector crashDetector;
  private final int origin;
  private boolean crashed = false;

  public MessageUpcall(ChandyMisraNode chandyMisraNode, Safra safraNode, CrashDetector crashDetector, int origin) {
    this.chandyMisraNode = chandyMisraNode;
    this.safraNode = safraNode;
    this.crashDetector = crashDetector;
    this.origin = origin;
  }

  @Override
  public synchronized void upcall(ReadMessage readMessage) throws IOException {
    MessageTypes messageType = MessageTypes.values()[readMessage.readInt()];

    if (!crashed) {
      switch (messageType) {
        case DISTANCE:
          safraNode.handleReceiveBasicMessage(origin, readMessage.readLong());
          DistanceMessage dm = new DistanceMessage(readMessage.readInt());
          readMessage.finish();
          chandyMisraNode.handleReceiveDistanceMessage(dm, origin);
          break;
        case CRASHED:
          readMessage.finish();
          crashDetector.handleCrash(origin);
          break;
        case REQUEST:
          safraNode.handleReceiveBasicMessage(origin, readMessage.readLong());
          readMessage.finish();
          chandyMisraNode.receiveRequestMessage(origin);
          break;
        case TOKEN:
          long messageCount = readMessage.readLong();
          int blackUntil = readMessage.readInt();
          Token token = new Token(messageCount, blackUntil);
          readMessage.finish();
          safraNode.receiveToken(token);
          break;
        default:
          throw new IOException("Got message of unknown type.");
      }
    } else {
      readMessage.finish();
    }
  }

  protected void crashed() {
    this.crashed = true;
  }
}
