package ibis.ipl.apps.safraExperiment.crashSimulation;

import java.util.HashMap;
import java.util.Map;


// TODO do I want crash points in the basic algorithm
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
    maxRepitions.put(BEFORE_SENDING_TOKEN, 3);
    maxRepitions.put(AFTER_SENDING_TOKEN, 3);

    maxRepitions.put(BEFORE_SENDING_BACKUP_TOKEN, 2);
    maxRepitions.put(AFTER_SENDING_BACKUP_TOKEN, 2);

    maxRepitions.put(BEFORE_RECEIVING_TOKEN, 3);

    maxRepitions.put(BEFORE_SENDING_BASIC_MESSAGE, 5);
    maxRepitions.put(AFTER_SENDING_BASIC_MESSAGE, 5);
  }

  public static int getMaxRepitions(CrashPoint crashPoint) {
    return maxRepitions.get(crashPoint);
  }
}
