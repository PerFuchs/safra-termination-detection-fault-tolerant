package ibis.ipl.apps.safraExperiment.communication;

import ibis.ipl.ReadMessage;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.chandyMisra.DistanceMessage;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;

import java.io.IOException;

public class MessageUpcall implements ibis.ipl.MessageUpcall {

  private final ChandyMisraNode chandyMisraNode;
  private CrashDetector crashDetector;
  private final int origin;
  private boolean crashed = false;

  public MessageUpcall(ChandyMisraNode chandyMisraNode, CrashDetector crashDetector, int origin) {
    this.chandyMisraNode = chandyMisraNode;
    this.crashDetector = crashDetector;
    this.origin = origin;
  }


  @Override
  public synchronized void upcall(ReadMessage readMessage) throws IOException {
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
