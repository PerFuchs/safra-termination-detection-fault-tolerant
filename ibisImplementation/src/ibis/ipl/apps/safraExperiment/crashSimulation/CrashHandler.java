package ibis.ipl.apps.safraExperiment.crashSimulation;

import java.io.IOException;

public interface CrashHandler {
  public void handleCrash(int crashedNode) throws IOException;
}
