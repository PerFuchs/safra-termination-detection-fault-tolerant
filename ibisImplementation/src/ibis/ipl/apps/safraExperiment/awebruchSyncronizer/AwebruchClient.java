package ibis.ipl.apps.safraExperiment.awebruchSyncronizer;

import ibis.ipl.apps.safraExperiment.communication.Message;

public interface AwebruchClient {
  public abstract void handleMessage(Message m);
}
