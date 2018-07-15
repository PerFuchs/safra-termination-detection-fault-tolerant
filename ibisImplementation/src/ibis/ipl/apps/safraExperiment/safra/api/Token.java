package ibis.ipl.apps.safraExperiment.safra.api;

import ibis.ipl.ReadMessage;
import ibis.ipl.WriteMessage;

import java.io.IOException;

public abstract class Token {
  public final static int INT_SIZE = 4;
  public final static int LONG_SIZE = 8;

  abstract public void writeToMessage(WriteMessage m) throws IOException;
  abstract public int getSize();
}
