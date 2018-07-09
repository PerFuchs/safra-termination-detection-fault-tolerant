package ibis.ipl.apps.safraExperiment.experiment;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Event implements Comparable<Event> {
  private final static Logger logger = Logger.getLogger(Event.class);

  private final int lineNumber;
  private final Date time;  // TODO does this support milliseconds?
  private final Level level;
  private final int node;
  private final String event;

  /**
   * Set of all nodes an event has been created for ever. To debug some early problems of logging.
   */
  public static Set<Integer> eventCreatedFor = new HashSet<>();

  private Event(int node, int lineNumber, String e, Date time, Level level) {
    this.node = node;
    this.lineNumber = lineNumber;
    this.event = e;
    this.time = time;
    this.level = level;
    eventCreatedFor.add(node);
  }

  public static Event createEventFromLogLine(int node, int lineNumber, String line) {
    String[] parts = line.split(" - ");

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    Date time;
    try {
      time = format.parse(parts[0].trim());
    } catch (ParseException e) {
      logger.debug(String.format("Could not parse event: %s", line));
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

  public boolean isCalledAnnounce() {
    return event.contains(getAnnounceEvent());
  }

  public boolean isTokenSend() {
    return event.contains(getTokenSendEvent());
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
    return event.contains(getNodeCrashedEvent());
  }

  public boolean isActiveStatusChange() {
    return event.contains("<ActiveStatus>");
  }

  public Boolean getActiveStatus() {
    if (!isActiveStatusChange()) {
      throw new IllegalStateException("Cannot get active status from this event");
    }
    Pattern pattern = Pattern.compile("<<ActiveStatus>(.*)>");
    Matcher m = pattern.matcher(event);
    m.find();
    String status = m.group(1);
    return status.equals("true");
  }

  public List<Integer> getSafraSum() {
    if (!isSafraSums()) {
      throw new IllegalStateException("Cannot get safra sum from this event");
    }
    Pattern pattern = Pattern.compile("<<SafraSums>(.*)>");
    Matcher matcher = pattern.matcher(event);
    matcher.find();

    String[] stringSums = matcher.group(1).split(",");
    List<Integer> sums = new LinkedList<>();

    for (String stringSum : stringSums) {
      sums.add(Integer.valueOf(stringSum));
    }
    return sums;
  }

  public boolean isSafraSums() {
    return event.contains("<SafraSums>");
  }

  public boolean isBackupTokenSend() {
    return event.contains(getBackupTokenSendEvent());
  }

  public static String getTokenSendEvent() {
    return "<<TokenSend>>";
  }

  public static String getBackupTokenSendEvent() {
    return "<<BackupToken>>";
  }

  public static String getSafraSumsEvent(List<Integer> sums) {
    StringBuilder sb = new StringBuilder("<<SafraSums>");
    for (int sum : sums) {
      sb.append(sum);
      sb.append(',');
    }
    sb.append(">");
    return sb.toString();
  }

  public static String getActiveStatusChangedEvent(boolean status) {
    return String.format("<<ActiveStatus>%b>", status);
  }

    public static String getAnnounceEvent() {
    return "<<Announce>>";
  }

  public static String getNodeCrashedEvent() {
    return "<<NodeCrashed>>";
  }

  public String getEvent() {
    return event;
  }
}
