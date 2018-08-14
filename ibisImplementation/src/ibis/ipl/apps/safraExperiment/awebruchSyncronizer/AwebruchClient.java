package ibis.ipl.apps.safraExperiment.awebruchSyncronizer;

import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;

public interface AwebruchClient {
  public abstract void handleMessage(Message m) throws IOException, TerminationDetectedTooEarly;
}
