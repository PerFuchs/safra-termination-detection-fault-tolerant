package ibis.ipl.apps.safraExperiment.safra.faultSensitive;

public class Token {
  public boolean isBlack;
  public long messageCounter;

  public Token(long messageCounter, boolean isBlack) {
    this.messageCounter = messageCounter;
    this.isBlack = isBlack;
  }
}
