package ibis.ipl.apps.safraExperiment.network;

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

  /**
   * Channels are equal if there src and dest fields are equal.
   *
   * That is not consistent with {@link Channel#compareTo(Channel)} which takes weight into account. To ease finding
   * of channels between nodes without knowing the weight equals does not take weight into account.
   *
   * @param other
   * @return
   */
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

  /**
   * Channels are sorted by weight, then by src and then by dest.
   *
   * That is not consistent to their equal implementation which does not take weight into account.
   *
   * @param channel
   * @return
   */
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
