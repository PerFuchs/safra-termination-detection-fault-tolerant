package ibis.ipl.apps.safraExperiment.safra.faultTolerant;

import ibis.ipl.ReadMessage;
import ibis.ipl.apps.safraExperiment.safra.api.Token;
import ibis.ipl.apps.safraExperiment.safra.api.TokenFactory;

import java.io.IOException;
import java.util.*;

public class TokenFactoryFT implements TokenFactory {
  @Override
  public Token readTokenFromMessage(ReadMessage m) throws IOException {
    int isBlackUntil = m.readInt();
    long sequenceNumber = m.readLong();

    int crashedNodesLength = m.readInt();
    Set<Integer> crashed = new HashSet<>();
    for (int i=0; i < crashedNodesLength; i++) {
      int c = m.readInt();
      crashed.add(c);
    }

    int mcLength = m.readInt();
    List<Long> mcs = new ArrayList<>();
    for (int i=0; i < mcLength; i++) {
      mcs.add(m.readLong());
    }

    return new TokenFT(mcs, isBlackUntil, sequenceNumber, crashed);
  }
}
