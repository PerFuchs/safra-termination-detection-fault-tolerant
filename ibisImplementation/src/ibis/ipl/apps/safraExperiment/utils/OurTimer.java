package ibis.ipl.apps.safraExperiment.utils;

import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.Experiment;
import org.apache.log4j.Logger;

public class OurTimer {
  private final static Logger experimentLogger = Logger.getLogger(Experiment.experimentLoggerName);

  private final long start;
  private long end;

  public OurTimer() {
    start = System.nanoTime();
  }
  
  public long stop() {
    end = System.nanoTime();
    return end - start;
  }
  
  public void stopAndCreateSafraTimeSpentEvent() {
    long duration = stop();
    experimentLogger.info(Event.getSafraTimeSpentEvent(duration));
  }
}
