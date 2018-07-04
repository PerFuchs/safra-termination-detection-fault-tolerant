package ibis.ipl.apps.safraExperiment.ibisSignalling;

import ibis.ipl.Registry;
import org.apache.log4j.Logger;

import java.util.Observable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SignalPollerThread extends Observable implements Runnable {
  private static Logger logger = Logger.getLogger(SignalPollerThread.class);

  private Registry registry;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private ScheduledFuture<?> signalPollerHandle;

  public SignalPollerThread(Registry registry) {
    this.registry = registry;
  }

  public void start() {
     signalPollerHandle = scheduler.scheduleAtFixedRate(this, 0, 300, TimeUnit.MILLISECONDS);
  }

  public void stop() {
    signalPollerHandle.cancel(false);  // Do not interrupt but allow to complete
  }

  @Override
  public void run() {
    String[] signals = registry.receivedSignals();
    for (String signal : signals) {
      handleSignal(signal);
    }
  }

  private void handleSignal(String signal) {
    if (signal.contains(":")) {
      String[] parts = signal.split(":");
      IbisSignal ibisSignal = new IbisSignal(parts[0], parts[1]);
      setChanged();
      notifyObservers(ibisSignal);
    } else {
      logger.error("Received unknown signal: " + signal);
    }
  }
}
