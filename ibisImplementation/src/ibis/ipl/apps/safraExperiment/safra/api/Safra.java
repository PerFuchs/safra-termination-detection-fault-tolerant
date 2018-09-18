package ibis.ipl.apps.safraExperiment.safra.api;

import ibis.ipl.apps.safraExperiment.crashSimulation.CrashException;

import java.io.IOException;

public interface Safra {
  void startAlgorithm() throws InterruptedException, IOException, CrashException;

  void await() throws InterruptedException;

  void setActive(boolean status, String reason) throws IOException, CrashException;
  void handleReceiveBasicMessage(int origin, long sequenceNumber) throws IOException, CrashException;
  void handleSendingBasicMessage(int receiver);

  long getSequenceNumber();

  void receiveToken(Token token) throws IOException, CrashException;

  TokenFactory getTokenFactory();

  boolean crashDetected(int origin);

  void handleAnnounce() throws IOException;
}
