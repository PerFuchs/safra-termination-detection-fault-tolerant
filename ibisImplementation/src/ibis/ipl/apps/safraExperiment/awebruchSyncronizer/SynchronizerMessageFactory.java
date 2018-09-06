package ibis.ipl.apps.safraExperiment.awebruchSyncronizer;

import ibis.ipl.ReadMessage;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.communication.MessageClassTypes;
import ibis.ipl.apps.safraExperiment.communication.MessageFactory;
import ibis.ipl.apps.safraExperiment.communication.UnknownMessageTypeException;

import java.io.IOException;

public class SynchronizerMessageFactory extends MessageFactory {

  @Override
  public Message constructMessage(int messageType, ReadMessage message) throws UnknownMessageTypeException, IOException {
    if (messageType == MessageClassTypes.SYNCHRONIZER_ACK_MESSAGE.ordinal()) {
      return new AckMessage();
    } else if (messageType == MessageClassTypes.SYNCHRONIZER_SAFE_MESSAGE.ordinal()) {
      return new SafeMessage();
    } else {
      throw new UnknownMessageTypeException();
    }
  }
}
