package ibis.ipl.apps.safraExperiment.network;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;

import java.util.*;

public class Tree {
  private LinkedList<Integer> badRoots = new LinkedList<>();
  private int root;
  private Set<Channel> channels = new TreeSet<>();

  Tree(int root, Set<Channel> channels) {
    this.root = root;
    this.channels = channels;
  }

  public Tree(CommunicationLayer communicationLayer, Network network, List<ChandyMisraResult> results, List<Integer> crashedNodes) {
    this.root = communicationLayer.getRoot();

    for (ChandyMisraResult r : results) {
      if (r.parent != -1 && !crashedNodes.contains(r.node)) {
        channels.add(new Channel(r.parent, r.node, network.getWeight(r.parent, r.node)));
        if (crashedNodes.contains(r.parent)) {
          badRoots.add(r.parent);
        }
      } else if (r.parent == -1 && r.node != 0 && !crashedNodes.contains(r.node)) {
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
    if (!(other instanceof Tree)) {
      return false;
    }
    final Tree that = (Tree) other;
    return root == that.root && channels.equals(that.channels);
  }

  /**
   * Prims algorithm is used to build the expected spanning tree from given channels and vertices.
   * <p>
   * Channels that have a src or dest outside of vertices are ignored. That is useful to simulated crashed channels.
   *
   * @param channels The channels/edges defining the graph to construct the spanning tree for. Can include "illegal"
   *                 channels which have src or dest set to a value not contained in vertices. These are ignored.
   * @param root     The root of the spanning tree to build.
   * @param vertices The vertices of the graph; includes root
   */
  public static Tree getMinimumSpanningTree(List<Channel> channels, int root, Set<Integer> vertices) {
    Set<Channel> treeChannels = new TreeSet<>();
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

      treeChannels.add(lowestOutgoing);
      visited.add(lowestOutgoing.dest);
    }
    return new Tree(root, treeChannels);
  }

  private static List<Channel> channelsFrom(List<Channel> channels, int src) {
    List<Channel> ret = new LinkedList<>();
    for (Channel c : channels) {
      if (c.src == src) {
        ret.add(c);
      }
    }
    return ret;
  }

  /**
   * Used as items for the PriorityQueue of a Dijkstra shortest path implementation.
   */
  static class NodeDistancePair implements Comparable<NodeDistancePair> {
    private final int priority;
    private final int node;

    NodeDistancePair(int priority, int node) {
      this.priority = priority;
      this.node = node;
    }

    /**
     * Sort by priority.
     *
     * This function is not consistent with the equals of the same class.
     */
    @Override
    public int compareTo(NodeDistancePair nodeDistancePair) {
      return priority - nodeDistancePair.priority;
    }

    /**
     * Equality defined by represented node.
     *
     * This function is not consistent with compareTo of the same class.
     * @param o
     * @return
     */
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof NodeDistancePair)) {
        return false;
      }
      NodeDistancePair that = (NodeDistancePair) o;
      return node == that.node;
    }
  }

  /**
   * Uses Dijkstra shortest path algorithm to compute a sink tree.
   *
   * @param channels All available channels
   * @param root Root node of the sink tree
   * @param vertices The node ID's. These do not have to be consecutive
   *
   * @return A shortest path sink tree to root.
   */
  public static Tree getSinkTree(List<Channel> channels, int root, Set<Integer> vertices) {
    PriorityQueue<NodeDistancePair> unvisitedVertices = new PriorityQueue<>();  // Used to find the node with lowest distance to root.
    Map<Integer, Integer> distances = new HashMap<>();  // Used to find distance of a node
    for (int v : vertices) {
      int distance = Integer.MAX_VALUE;
      if (root == v) {
        distance = 0;
      }
      unvisitedVertices.offer(new NodeDistancePair(distance, v));
      distances.put(v, distance);
    }
    Map<Integer, Integer> parents = new HashMap<>();

    while (!unvisitedVertices.isEmpty()) {
      NodeDistancePair v = unvisitedVertices.poll();

      for (Channel c: channelsFrom(channels, v.node)) {
        int alternativeDistance = distances.get(v.node) + c.getWeight();
        if (alternativeDistance < distances.get(c.dest) && alternativeDistance > 0) {  // Bigger 0 because of overflows
          distances.put(c.dest, alternativeDistance);
          unvisitedVertices.remove(new NodeDistancePair(-1, c.dest));  // Equality of PriorityQueue entries is defined by the node only.
          unvisitedVertices.offer(new NodeDistancePair(alternativeDistance, c.dest));  // Reenter the node with its new distance
          parents.put(c.dest, v.node);
        }
      }
    }

    Set<Channel> treeChannels = new TreeSet<>();
    // Select the channels based on parent relationship calculated by Dijkstra.
    for (int v : vertices) {
      if (v == root) {
        continue;
      }
      int parent = parents.get(v);
      treeChannels.add(channels.get(channels.indexOf(new Channel(parent, v, -1))));
    }

    return new Tree(root, treeChannels);
  }
}