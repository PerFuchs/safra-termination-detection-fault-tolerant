package ibis.ipl.apps.safraExperiment.safra.api;

public class TerminationDetectedTooEarly extends Exception {
  public final String reason;

  public TerminationDetectedTooEarly(String reason) {
    this.reason = reason;
  }
}
