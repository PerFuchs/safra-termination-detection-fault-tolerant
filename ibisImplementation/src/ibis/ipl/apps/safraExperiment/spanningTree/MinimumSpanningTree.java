package ibis.ipl.apps.safraExperiment.spanningTree;


import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;

import java.util.*;

public class MinimumSpanningTree {
  private LinkedList<Integer> badRoots;
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
    this.badRoots = new LinkedList<>();
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

  public MinimumSpanningTree(List<Result> results, CommunicationLayer communicationLayer, Network network, List<Integer> crashedNodes) {
    this.root = communicationLayer.getRoot();
    this.badRoots = new LinkedList<Integer>();

    for (Result r : results) {
      if (r.parent != -1 && !crashedNodes.contains(r.node)) {
        channels.add(new Channel(r.parent, r.node, network.getWeight(r.parent, r.node)));
        if (crashedNodes.contains(r.parent)) {
          badRoots.add(r.parent);
        }
      } else if ((r.parent == -1) && r.node != 0 && !crashedNodes.contains(r.node)) {
        System.out.println(String.format("Node: %d has no parent.", r.node));
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
    b.append("Actual tree:\n");
    while (!work.empty()) {
      Channel c = work.pop();
      b.append(String.format("Node: %d Parent: %d\n", c.dest, c.src));
      work.addAll(channelsFrom(c.dest));
    }

    b.append("Bad Trees:\n");

    for (int badRoot : badRoots) {
      LinkedList<Integer> visited = new LinkedList<>();
      work.addAll(channelsFrom(badRoot));
      while (!work.empty()) {
        Channel c = work.pop();
        if (!visited.contains(c.dest)) {
          b.append(String.format("Node: %d Parent: %d\n", c.dest, c.src));
          work.addAll(channelsFrom(c.dest));
          visited.add(c.dest);
        } else {
          b.append(String.format("Detected cycle at: %d", c.dest));
        }
      }
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
