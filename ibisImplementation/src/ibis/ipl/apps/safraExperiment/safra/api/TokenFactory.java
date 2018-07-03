package ibis.ipl.apps.safraExperiment.safra.api;

import ibis.ipl.ReadMessage;

import java.io.IOException;

public interface TokenFactory {
  Token readTokenFromMessage(ReadMessage m) throws IOException;
}
