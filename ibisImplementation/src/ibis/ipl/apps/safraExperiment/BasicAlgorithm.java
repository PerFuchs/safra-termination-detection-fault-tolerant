package ibis.ipl.apps.safraExperiment;

import ibis.ipl.apps.safraExperiment.safra.api.TerminationDetectedTooEarly;

import java.io.IOException;

public interface BasicAlgorithm {
  public void terminate() throws TerminationDetectedTooEarly;
  public void startAlgorithm() throws IOException;
}
