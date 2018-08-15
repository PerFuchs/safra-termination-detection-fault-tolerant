package ibis.ipl.apps.safraExperiment.communication;

import ibis.ipl.ReadMessage;
import ibis.ipl.WriteMessage;

public abstract class Message {
  public abstract int getReceiver();
  public abstract int getSource();

  public abstract void writeToIPLMessage(WriteMessage writeMessage);

  public static Message fromIPLMessage(ReadMessage readMessage) {
    throw new UnsupportedOperationException();
  }

}
