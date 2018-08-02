package ibis.ipl.apps.safraExperiment.utils;

import ibis.ipl.apps.safraExperiment.experiment.Event;
import ibis.ipl.apps.safraExperiment.experiment.OnlineExperiment;
import org.apache.log4j.Logger;

public class OurTimer {
  private final static Logger experimentLogger = Logger.getLogger(OnlineExperiment.experimentLoggerName);

  private long duration;
  private long start;

  public OurTimer() {
    start();
  }

  public void start() {
    start = System.nanoTime();
  }

  private void updateDuration() {
    long end = System.nanoTime();
    duration += end - start;
  }

  public long pause() {
    updateDuration();
    return duration;
  }

  public void stopAndCreateSafraTimeSpentEvent() {
    long duration = pause();
    experimentLogger.info(Event.getSafraTimeSpentEvent(duration));
  }

  public void stopAndCreateTotalTimeSpentEvent() {
    long duration = pause();
    experimentLogger.info(Event.getTotalTimeSpentEvent(duration));
  }

  public void stopAndCreateBasicTimeSpentEvent() {
    long duration = pause();
    experimentLogger.info(Event.getBasicTimeSpentEvent(duration));
  }
}
