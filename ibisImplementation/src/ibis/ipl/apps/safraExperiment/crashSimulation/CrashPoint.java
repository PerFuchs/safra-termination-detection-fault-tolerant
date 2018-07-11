package ibis.ipl.apps.safraExperiment.crashSimulation;

import java.util.HashMap;
import java.util.Map;

public enum CrashPoint {
  BEFORE_SENDING_TOKEN,
  AFTER_SENDING_TOKEN,

  BEFORE_SENDING_BACKUP_TOKEN,
  AFTER_SENDING_BACKUP_TOKEN,

  BEFORE_RECEIVING_TOKEN,

  BEFORE_SENDING_BASIC_MESSAGE,
  AFTER_SENDING_BASIC_MESSAGE;

  private static Map<CrashPoint, Integer> maxRepitions = new HashMap<>();

  static {
    maxRepitions.put(BEFORE_SENDING_TOKEN, 1);
    maxRepitions.put(AFTER_SENDING_TOKEN, 1);

    maxRepitions.put(BEFORE_SENDING_BACKUP_TOKEN, 1);
    maxRepitions.put(AFTER_SENDING_BACKUP_TOKEN, 1);

    maxRepitions.put(BEFORE_RECEIVING_TOKEN, 1);

    maxRepitions.put(BEFORE_SENDING_BASIC_MESSAGE, 1);
    maxRepitions.put(AFTER_SENDING_BASIC_MESSAGE, 1);
  }

  public static int getMaxRepitions(CrashPoint crashPoint) {
    return maxRepitions.get(crashPoint);
  }
}
