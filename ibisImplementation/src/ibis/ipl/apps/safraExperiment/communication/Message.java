package ibis.ipl.apps.safraExperiment.communication;

import ibis.ipl.ReadMessage;
import ibis.ipl.WriteMessage;

import java.io.IOException;

public abstract class Message {
  public abstract void writeToIPLMessage(WriteMessage writeMessage) throws IOException;
}
