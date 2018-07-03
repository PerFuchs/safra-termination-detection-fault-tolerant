package ibis.ipl.apps.safraExperiment.safra.faultSensitive;

import ibis.ipl.ReadMessage;
import ibis.ipl.apps.safraExperiment.safra.api.Token;
import ibis.ipl.apps.safraExperiment.safra.api.TokenFactory;

import java.io.IOException;

public class TokenFactoryFS implements TokenFactory {
  @Override
  public Token readTokenFromMessage(ReadMessage m) throws IOException {
    long mc = m.readLong();
    int isBlackUntil = m.readInt();
    return new TokenFS(mc, isBlackUntil);
  }
}
