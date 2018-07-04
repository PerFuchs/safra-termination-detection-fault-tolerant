package ibis.ipl.apps.safraExperiment.safra.faultTolerant;

import ibis.ipl.WriteMessage;
import ibis.ipl.apps.safraExperiment.safra.api.Token;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TokenFT implements Token {
  public int isBlackUntil;
  public List<Long> messageCounters;
  public long sequenceNumber;
  public Set<Integer> crashed;

  public TokenFT(List<Long> messageCounters,
                 int isBlackUntil,
                 long sequenceNumber,
                 Set<Integer> crashed) {
    this.messageCounters = messageCounters;
    this.isBlackUntil = isBlackUntil;
    this.sequenceNumber = sequenceNumber;
    this.crashed = crashed;
  }

  @Override
  public void writeToMessage(WriteMessage m) throws IOException {
    m.writeInt(isBlackUntil);
    m.writeLong(sequenceNumber);

    m.writeInt(crashed.size());
    for (int i : crashed) {
      m.writeInt(i);
    }

    m.writeInt(messageCounters.size());
    for (long mc : messageCounters) {
      m.writeLong(mc);
    }
  }
}
