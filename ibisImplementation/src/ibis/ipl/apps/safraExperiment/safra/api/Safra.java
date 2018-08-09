package ibis.ipl.apps.safraExperiment.safra.api;

import java.io.IOException;

public interface Safra {
  void startAlgorithm() throws InterruptedException, IOException;

  void await() throws InterruptedException;

  void setActive(boolean status, String reason) throws IOException;
  void handleReceiveBasicMessage(int origin, long sequenceNumber) throws IOException;
  void handleSendingBasicMessage(int receiver);

  long getSequenceNumber();

  void receiveToken(Token token) throws IOException;

  TokenFactory getTokenFactory();

  boolean crashDetected(int origin);

  void handleAnnounce() throws IOException;
}
