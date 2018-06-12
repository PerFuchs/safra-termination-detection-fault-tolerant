package ibis.ipl.apps.cell1d.algorithm;


import ibis.ipl.IbisIdentifier;
import ibis.ipl.apps.cell1d.CommunicationLayer;
import ibis.ipl.apps.cell1d.Result;

import java.util.*;

public class MinimumSpanningTree {
  private int root;
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

  public MinimumSpanningTree(List<Result> results, CommunicationLayer communicationLayer, Network network, List<IbisIdentifier> crashedNodes) {
    this.root = communicationLayer.getRoot();

    for (Result r : results) {
      if (r.parent != -1 && !crashedNodes.contains(communicationLayer.getIbises()[r.node])) {
        channels.add(new Channel(r.parent, r.node, network.getWeight(
            communicationLayer.getIbises()[r.parent], communicationLayer.getIbises()[r.node])));
      }
    }

    // TODO should I control weight calculation of Chandy misra?
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


  // TODO add warning or print of unreachable channels
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

  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof MinimumSpanningTree)) {
      return false;
    }
    final MinimumSpanningTree that = (MinimumSpanningTree) other;
    return root == that.root && channels.equals(that.channels);
  }
}
