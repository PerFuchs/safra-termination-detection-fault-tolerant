package ibis.ipl.apps.safraExperiment.experiment;

import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Event implements Comparable<Event> {
  private final static Logger logger = Logger.getLogger(Event.class);

  private final static Pattern messageCounterUpdatePattern = Pattern.compile("<<MessageCounterUpdate>(.*)>");
  private final static Pattern activeStatusChangedPattern = Pattern.compile("<<ActiveStatus>(.*)>");
  private final static Pattern safraTimeSpentPattern = Pattern.compile("<<SafraTimeSpentEvent>(.*)>");
  private final static Pattern basicAlgorithmTimeSpentPattern = Pattern.compile("<<BasicTimeSpent>(.*)>");
  private final static Pattern totalTimeSpentPattern = Pattern.compile("<<TotalTimeSpentEvent>(.*)>");
  private final static Pattern tokenSentPattern = Pattern.compile("<<TokenSend>(.*)>");

  private final int lineNumber;
  private final Date time;
  private final Level level;
  private final int node;

  private final boolean isTokenSend;
  private final int tokenSize;
  private final boolean isBackupTokenSend;
  private final boolean isNodeCrashed;
  private final boolean isActiveStatusChange;
  private final boolean isMessageCounterUpdate;
  private final boolean isParentCrashDetected;
  private final boolean isSafraTimeSpentEvent;
  private final boolean isSafraReduceTime;    // TODO delete in the end
  private final boolean isBasicTimeSpent;
  private final boolean isTotalTimeSpentEvent;

  private final boolean activeStatus;
  private final int messageCounterUpdateIndex;
  private final int messageCounterUpdateValue;

  private long timeSpent;

  private final String event;

  /**
   * Set of all nodes an event has been created for ever. To debug some early problems of logging.
   */
  public static Set<Integer> eventCreatedFor = new HashSet<>();
  private boolean basicTimeSpentEvent;

  private Event(int node, int lineNumber, String e, Date time, Level level) {
    this.node = node;
    this.lineNumber = lineNumber;
    this.time = time;
    this.level = level;

    this.isBackupTokenSend = e.contains(getBackupTokenSendEvent());
    boolean typeFound = isBackupTokenSend;  // To avoid unnecessary string operations.
    this.isNodeCrashed = !typeFound && e.contains(getNodeCrashedEvent());
    typeFound |= isNodeCrashed;
    this.isParentCrashDetected = !typeFound && e.contains(getParentCrashEvent());
    typeFound |= isParentCrashDetected;

    if (!typeFound) {
      Matcher m = activeStatusChangedPattern.matcher(e);
      this.isActiveStatusChange = m.find();
      if (isActiveStatusChange) {
        activeStatus = m.group(1).equals("true");
      } else {
        activeStatus = false;
      }
    } else {
      isActiveStatusChange = false;
      activeStatus = false;
    }
    typeFound |= isActiveStatusChange;

    if (!typeFound) {
      Matcher m = tokenSentPattern.matcher(e);
      this.isTokenSend = m.find();
      if (isTokenSend) {
        tokenSize = Integer.valueOf(m.group(1));
      } else {
        tokenSize = 0;
      }
    } else {
      isTokenSend = false;
      tokenSize = 0;
    }
    typeFound |= isTokenSend;

    if (!typeFound) {
      Matcher m = messageCounterUpdatePattern.matcher(e);
      isMessageCounterUpdate = m.find();
      if (isMessageCounterUpdate) {
        String[] s = m.group(1).split(",");
        messageCounterUpdateIndex = Integer.valueOf(s[0]);
        messageCounterUpdateValue = Integer.valueOf(s[1]);
      } else {
        messageCounterUpdateIndex = -1;
        messageCounterUpdateValue = -1;
      }
    } else {
      isMessageCounterUpdate = false;

      messageCounterUpdateIndex = -1;
      messageCounterUpdateValue = -1;
    }
    typeFound |= isMessageCounterUpdate;

    if (!typeFound) {
      Matcher m = safraTimeSpentPattern.matcher(e);
      this.isSafraTimeSpentEvent = m.find();
      if (isSafraTimeSpentEvent) {
        timeSpent = Long.valueOf(m.group(1));
      }
    } else {
      isSafraTimeSpentEvent = false;
    }
    typeFound |= isSafraTimeSpentEvent;

    if (!typeFound) {
      Matcher m = basicAlgorithmTimeSpentPattern.matcher(e);
      this.isBasicTimeSpent= m.find();
      if (isBasicTimeSpent) {
        timeSpent = Long.valueOf(m.group(1));
      }
    } else {
      isBasicTimeSpent = false;
    }
    typeFound |= isBasicTimeSpent;

    if (!typeFound) {
      Matcher m = totalTimeSpentPattern.matcher(e);
      this.isTotalTimeSpentEvent = m.find();
      if (isTotalTimeSpentEvent) {
        timeSpent = Long.valueOf(m.group(1));
      }
    } else {
      isTotalTimeSpentEvent = false;
    }
    typeFound |= isTotalTimeSpentEvent;

    if (!typeFound) {
      Matcher m = Pattern.compile("<<TakeOff>(.*)>").matcher(e);
      this.isSafraReduceTime = m.find();
      if (isSafraReduceTime) {
        timeSpent = Integer.valueOf(m.group(1));
      } else {
        timeSpent = 0;
      }
    } else {
      isSafraReduceTime = false;
    }
    typeFound |= isSafraReduceTime;

    if (!typeFound) {
      event = e;
    } else {
      event = null;
    }

    eventCreatedFor.add(node);
  }

  public static Event createEventFromLogLine(Experiment experiment, int node, int lineNumber, String line) throws IOException {
    String[] parts = line.split(" - ");

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    Date time;
    try {
      time = format.parse(parts[0].trim());
    } catch (ParseException e) {
      logger.debug(String.format("Could not parse event: %s", line));
      experiment.writeToWarnFile(String.format("Could not parse event: %s", line));
      return null;
    }

    String l = parts[2].trim();

    Level level;
    switch (l) {
      case "INFO":
        level = Level.INFO;
        break;
      case "ERROR":
        level = Level.ERROR;
        break;
      case "WARN":
        level = Level.WARN;
        break;
      default:
        level = Level.FATAL;
        logger.error("Cannot parse level: " + l);
    }
    return new Event(node, lineNumber, line, time, level);
  }

  public static String getParentCrashEvent() {
    return "<<ParentCrashDetected>>";
  }

  public static String getBasicTimeSpentEvent(long duration) {
    return String.format("<<BasicTimeSpent>%d>", duration);
  }

  public boolean isTokenSend() {
    return isTokenSend;
  }

  public Level getLevel() {
    return level;
  }

  @Override
  public int compareTo(Event event) {
    if (event == null) {
      throw new NullPointerException();
    }

    /**
     * Order events from the same node by their linenumber as they can have the exact same time easily but
     * should not be reordered from their original occurence.
     */
    if (node == event.node) {
      return lineNumber - event.lineNumber;
    }
    int sort = time.compareTo(event.time);
    if (sort == 0) {
      sort = node - event.node;
    }
    return sort;
  }

  public int getNode() {
    return node;
  }

  public boolean isNodeCrashed() {
    return isNodeCrashed;
  }

  public boolean isActiveStatusChange() {
    return isActiveStatusChange;
  }

  public Boolean getActiveStatus() {
    if (!isActiveStatusChange) {
      throw new IllegalStateException("Cannot get active status from this event");
    }
    return activeStatus;
  }


  public int getSafraMessageCounterUpdateIndex() {
    if (!isMessageCounterUpdate) {
      throw new IllegalStateException("Cannot get safra sum from this event");
    }
    return messageCounterUpdateIndex;
  }

  public int getSafraMessageCounterUpdateValue() {
    if (!isMessageCounterUpdate) {
      throw new IllegalStateException("Cannot get safra sum from this event");
    }
    return messageCounterUpdateValue;
  }

  public boolean isMessageCounterUpdate() {
    return isMessageCounterUpdate;
  }

  public boolean isBackupTokenSend() {
    return isBackupTokenSend;
  }

  /**
   * @param tokenSize token size in bytes
   */
  public static String getTokenSendEvent(int tokenSize) {
    return String.format("<<TokenSend>%d>", tokenSize);
  }

  public static String getBackupTokenSendEvent() {
    return "<<BackupToken>>";
  }

  public static String getSafraSumsEvent(int index, long messageCount) {
    StringBuilder sb = new StringBuilder("<<MessageCounterUpdate>");
    sb.append(index);
    sb.append(",");
    sb.append(messageCount);
    sb.append(">");
    return sb.toString();
  }

  public static String getActiveStatusChangedEvent(boolean status, String reason) {
    return String.format("<<ActiveStatus>%b>%s", status, reason);
  }

  public static String getAnnounceEvent() {
    return "<<Announce>>";
  }

  public static String getNodeCrashedEvent() {
    return "<<NodeCrashed>>";
  }

  public String getEvent() {
    if (event != null) {
      return event;
    }
    StringBuilder sb = new StringBuilder();
    sb.append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(time));
    sb.append(": ");

    if (isActiveStatusChange) {
      sb.append("ActiveStatusChange ");
      sb.append(activeStatus);
    } else if (isTokenSend) {
      sb.append("TokenSend");
    } else if (isBackupTokenSend) {
      sb.append("BackupTokenSend");
    } else if (isNodeCrashed) {
      sb.append("NodeCrashed" + node);
    } else if (isMessageCounterUpdate) {
      sb.append(String.format("MessageCounterUpdate on %d for %d to %d", node, messageCounterUpdateIndex, messageCounterUpdateValue));
    } else if (isSafraTimeSpentEvent) {
      sb.append(String.format("Total time spent: %d", timeSpent));
    } else if (isTotalTimeSpentEvent) {
      sb.append(String.format("Total time spent: %d", timeSpent));
    }
    return sb.toString();
  }

  public boolean isParentCrashDetected() {
    return isParentCrashDetected;
  }

  public static String getSafraTimeSpentEvent(long elapsedTime) {
    return String.format("<<SafraTimeSpentEvent>%d>", elapsedTime);
  }

  public static String getTotalTimeSpentEvent(long elapsedTime) {
    return String.format("<<TotalTimeSpentEvent>%d>", elapsedTime);
  }

  public boolean isSafraTimeSpentEvent() {
    return isSafraTimeSpentEvent;
  }

  public boolean isTotalTimeSpentEvent() {
    return isTotalTimeSpentEvent;
  }

  public long getTimeSpent() {
    return timeSpent;
  }

  public int getTokenSize() {
    if (!isTokenSend) {
      throw new IllegalStateException("Cannot get token size of event: " + this.toString());
    }
    return tokenSize;
  }

  public boolean isReduceSafraTime() {
    return isSafraReduceTime;
  }

  /**
   * Not used during experiments but helpful to debug where Safra's time actually is spent.
   *
   * @param elapsedTime time by which safraSpentTime will be reduced.
   * @return
   */
  public String getReduceSafraTimeEvent(long elapsedTime) {
    return String.format("<<TakeOff>%d>", elapsedTime);
  }

  public boolean isBasicTimeSpentEvent() {
    return isBasicTimeSpent;
  }
}
