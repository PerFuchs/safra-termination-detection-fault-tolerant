package ibis.ipl.apps.safraExperiment.safra.faultTolerant;

import ibis.ipl.WriteMessage;
import ibis.ipl.apps.safraExperiment.safra.api.Token;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TokenFT extends Token {
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

  public int getSize() {
    // isBlackUntil + sequenceNumber + + messageCounters and crashed size + messageCounters  + crashed
    return INT_SIZE + LONG_SIZE + INT_SIZE * 2 + LONG_SIZE * messageCounters.size() + INT_SIZE * crashed.size();
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("< BlackUntil: %04d, SequenceNumber: %d Crashed: ", isBlackUntil, sequenceNumber));
    for (int c : crashed) {
      sb.append(c);
      sb.append(", ");
    }
    return sb.toString();
  }
}
