package ibis.ipl.apps.safraExperiment.safra.faultSensitive;

public class Token {
  public int isBlackUntil;
  public long messageCounter;

  public Token(long messageCounter, int isBlackUntil) {
    this.messageCounter = messageCounter;
    this.isBlackUntil = isBlackUntil;
  }
}
