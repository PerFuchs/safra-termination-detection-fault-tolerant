package ibis.ipl.apps.cell1d.algorithm;


import java.util.*;

public class MinimumSpanningTree {

  private class Node {
    private final int parent;
    private final int dist;
    private final List<Node> children = new LinkedList<>();

    private Node(int parent, int dist) {
      this.parent = parent;
      this.dist = dist;
    }
  }

  private int root;
  private Node rootNode;
  private List<Channel> channels = new LinkedList<>();

  /**
   * Prims algorithm is used
   * @param channels
   * @param root
   * @param vertices
   */
  public MinimumSpanningTree(List<Channel> channels, int root, Set<Integer> vertices) {
    this.root = root;
    Set<Integer> visited = new HashSet<>();
    visited.add(root);

    List<Channel> sortedChannels = new ArrayList<>(channels);
    Collections.sort(sortedChannels);

    while (!vertices.equals(visited)) {
      Channel lowestOutgoing = null;

      for (int i = 0; i < sortedChannels.size(); i++) {
        lowestOutgoing = sortedChannels.get(i);
        if (visited.contains(lowestOutgoing.src) && !visited.contains(lowestOutgoing.dest)) {
          break;
        }
      }

      this.channels.add(lowestOutgoing);
      visited.add(lowestOutgoing.dest);
    }
  }

  public int getWeight() {
    int weight = 0;
    for (Channel c : channels) {
      weight += c.getWeight();
    }
    return weight;
  }

  private List<Channel> channelsFrom(int src) {
    List<Channel> ret = new LinkedList<>();
    for (Channel c : channels) {
      if (c.src == src) {
        ret.add(c);
      }
    }
    return ret;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    Stack<Channel> work = new Stack<>();

    work.addAll(channelsFrom(root));
    while (!work.empty()) {
      Channel c = work.pop();
      b.append(String.format("Node: %d Parent: %d\n", c.dest, c.src));
      work.addAll(channelsFrom(c.dest));
    }
    return b.toString();
  }
}
