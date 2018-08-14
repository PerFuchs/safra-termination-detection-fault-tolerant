package ibis.ipl.apps.safraExperiment.crashSimulation;

import ibis.ipl.apps.safraExperiment.safra.api.CrashAfterTerminationException;

import java.io.IOException;

public interface CrashHandler {
  public void handleCrash(int crashedNode) throws IOException, CrashAfterTerminationException;
}
