package ibis.ipl.apps.safraExperiment.communication;

import ibis.ipl.ReadMessage;
import ibis.ipl.apps.safraExperiment.chandyMisra.ChandyMisraNode;
import ibis.ipl.apps.safraExperiment.chandyMisra.DistanceMessage;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashDetector;
import ibis.ipl.apps.safraExperiment.safra.api.Safra;
import ibis.ipl.apps.safraExperiment.safra.api.Token;
import ibis.ipl.apps.safraExperiment.safra.faultSensitive.TokenFS;
import ibis.ipl.apps.safraExperiment.utils.barrier.BarrierFactory;

import java.io.IOException;

import static ibis.ipl.apps.safraExperiment.communication.MessageTypes.DISTANCE;

public class MessageUpcall implements ibis.ipl.MessageUpcall {

  private CommunicationLayer communicationLayer;
  private final ChandyMisraNode chandyMisraNode;
  private final Safra safraNode;
  private CrashDetector crashDetector;
  private BarrierFactory barrierFactory;
  private boolean crashed = false;

  public MessageUpcall(CommunicationLayer communicationLayer,
                       ChandyMisraNode chandyMisraNode,
                       Safra safraNode,
                       CrashDetector crashDetector,
                       BarrierFactory barrierFactory) {
    this.communicationLayer = communicationLayer;
    this.chandyMisraNode = chandyMisraNode;
    this.safraNode = safraNode;
    this.crashDetector = crashDetector;
    this.barrierFactory = barrierFactory;
  }

  // TODO clean up synchronization.

  @Override
  public synchronized void upcall(ReadMessage readMessage) throws IOException {
    int origin = communicationLayer.getIbises().indexOf(readMessage.origin().ibisIdentifier());
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
          Token token = safraNode.getTokenFactory().readTokenFromMessage(readMessage);
          readMessage.finish();
          safraNode.receiveToken(token);
          break;
        case BARRIER:
          String name = readMessage.readString();
          readMessage.finish();
          barrierFactory.handleBarrierMessage(name);
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
