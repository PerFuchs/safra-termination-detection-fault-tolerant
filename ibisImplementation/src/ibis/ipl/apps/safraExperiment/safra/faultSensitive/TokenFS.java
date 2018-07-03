package ibis.ipl.apps.safraExperiment.safra.faultSensitive;

import ibis.ipl.apps.safraExperiment.safra.api.Token;

public class TokenFS implements Token {
  public int isBlackUntil;
  public long messageCounter;

  public TokenFS(long messageCounter, int isBlackUntil) {
    this.messageCounter = messageCounter;
    this.isBlackUntil = isBlackUntil;
  }
}
