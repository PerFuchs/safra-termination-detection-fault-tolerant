package ibis.ipl.apps.safraExperiment.awebruchSyncronizer;

import ibis.ipl.WriteMessage;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.communication.MessageClassTypes;

import java.io.IOException;

public class SafeMessage extends Message {
  @Override
  public void writeToIPLMessage(WriteMessage writeMessage) throws IOException {
    writeMessage.writeInt(MessageClassTypes.SYNCHRONIZER_SAFE_MESSAGE.ordinal());
  }
}
