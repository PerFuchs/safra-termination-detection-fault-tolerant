package ibis.ipl.apps.safraExperiment.awebruchSyncronizer;

import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;

public interface AwebruchClient {
  void handleMessage(int source, Message m) throws IOException, TerminationDetectedTooEarly;
}
