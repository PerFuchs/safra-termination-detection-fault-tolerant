package ibis.ipl.apps.safraExperiment.utils;

import org.apache.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DeadlockDetector from https://korhner.github.io/java/multithreading/detect-java-deadlocks-programmatically/
 * <p>
 * TODO remove towards the end
 */
public class DeadlockDetector {
  private final static Logger logger = Logger.getLogger(DeadlockDetector.class);

  private final long period;
  private final TimeUnit unit;
  private final ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  final Runnable deadlockCheck = new Runnable() {
    @Override
    public void run() {
      long[] deadlockedThreadIds = DeadlockDetector.this.mbean.findDeadlockedThreads();

      if (deadlockedThreadIds != null) {
        ThreadInfo[] threadInfos = DeadlockDetector.this.mbean.getThreadInfo(deadlockedThreadIds);
        if (threadInfos != null) {
          logger.error("Deadlock detected!");

          Map<Thread, StackTraceElement[]> stackTraceMap = Thread.getAllStackTraces();
          for (ThreadInfo threadInfo : threadInfos) {

            if (threadInfo != null) {

              for (Thread thread : Thread.getAllStackTraces().keySet()) {

                if (thread.getId() == threadInfo.getThreadId()) {
                  logger.error(threadInfo.toString().trim());

                  for (StackTraceElement ste : thread.getStackTrace()) {
                    logger.error("\t" + ste.toString().trim());
                  }
                }
              }
            }
          }
        }
      }
    }
  };

  public DeadlockDetector(final long period, final TimeUnit unit) {
    this.period = period;
    this.unit = unit;
  }

  public void start() {
    this.scheduler.scheduleAtFixedRate(this.deadlockCheck, this.period, this.period, this.unit);
  }
}