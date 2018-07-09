package ibis.ipl.apps.safraExperiment.experiment;

import org.apache.log4j.Level;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Event implements Comparable<Event> {
  private final Date time;  // TODO does this support milliseconds?
  private Level level;
  private final int node;
  private final String event;

  public static Set<Integer> eventCreatedFor = new HashSet<>();

  public Event(int node, String e) throws ParseException {
    this.node = node;
    this.event = e;
    eventCreatedFor.add(node);

    String[] parts = e.split(" - ");
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    time = format.parse(parts[0].trim());
    String l = parts[2].trim();

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
          System.err.println("Cannot parse level: " + l);
    }

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
    Pattern pattern = Pattern.compile("<<ActiveStatus>(.*)");
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

    // The last comma creates an empty string at stringSums[last]
    for (int i = 0; i < stringSums.length; i++) {
        sums.add(Integer.valueOf(stringSums[i]));
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
