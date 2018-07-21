package ibis.ipl.apps.safraExperiment.safra.faultSensitive;

import ibis.ipl.ReadMessage;
import ibis.ipl.WriteMessage;
import ibis.ipl.apps.safraExperiment.safra.api.Token;

import java.io.IOException;

public class TokenFS extends Token {
  public int isBlackUntil;
  public long messageCounter;

  public TokenFS(long messageCounter, int isBlackUntil) {
    this.messageCounter = messageCounter;
    this.isBlackUntil = isBlackUntil;
  }

  @Override
  public void writeToMessage(WriteMessage m) throws IOException {
    m.writeLong(messageCounter);
    m.writeInt(isBlackUntil);
  }

  public int getSize() {
    return INT_SIZE + LONG_SIZE;
  }
}
