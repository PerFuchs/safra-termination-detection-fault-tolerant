package ibis.ipl.apps.safraExperiment.safra.api;

import ibis.ipl.ReadMessage;
import ibis.ipl.WriteMessage;

import java.io.IOException;

public interface Token {
  void writeToMessage(WriteMessage m) throws IOException;
}
