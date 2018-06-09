package ibis.ipl.apps.cell1d.algorithm;

import java.util.Objects;

public class Channel implements Comparable<Channel> {

  public final int src;
  public final int dest;
  private int weight;

  public Channel(int src, int dest, int weigh) {
    this.src = src;
    this.dest = dest;
    this.weight = weigh;
  }

  public int getWeight() {
    return weight;
  }

  public void setWeight(int weight) {
    this.weight = weight;
  }

  @Override
  public int hashCode() {
    return Objects.hash(src, dest);
  }

  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Channel)) {
      return false;
    }
    final Channel that = (Channel) other;
    return src == that.src && dest == that.dest;
  }

  @Override
  public int compareTo(Channel channel) {
    if (channel == null) {
      throw new NullPointerException();
    }
    int sort = weight - channel.weight;
    if (sort == 0) {
      sort = src - channel.src;
      if (sort == 0) {
        sort = dest - channel.dest;
      }
    }
    return sort;
  }
}
