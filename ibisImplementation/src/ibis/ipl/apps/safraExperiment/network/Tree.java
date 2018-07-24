package ibis.ipl.apps.safraExperiment.network;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import org.apache.log4j.Logger;

import java.util.*;

public class Tree {
  private final static Logger logger = Logger.getLogger(Tree.class);

  private LinkedList<Integer> badRoots = new LinkedList<>();
  private int root;
  private Set<Channel> channels = new TreeSet<>();
  private final Set<Integer> vertices;

  /**
   * Used to indicate that this tree was constructed from CM results with invalid calculated weights.
   *
   * This will not be visible in the weights of its channel as these are initialized according to the correct weights.
   * This is because the CM result does not allow to recover weights for individual channels.
   *
   * This field is not used in compareTo or equal. It is up to the user to check it accordingly.
   */
  private boolean invalidWeights = false;

  Tree(int root, Set<Channel> channels, Set<Integer> vertices) {
    this.root = root;
    this.channels = channels;
    this.vertices = vertices;
  }

  public Tree(CommunicationLayer communicationLayer, Network network, List<ChandyMisraResult> results, Set<Integer> crashedNodes) {
    // TODO does not detect unconnected vertices which are in a cycle. This does not influence detection of bad CM results but makes it harder to see why they are wrong.
    this.root = communicationLayer.getRoot();
    this.vertices = new HashSet<>();

    for (ChandyMisraResult r : results) {
      if (r.parent != -1 && !crashedNodes.contains(r.node)) {
        vertices.add(r.node);
        channels.add(new Channel(r.parent, r.node, network.getWeight(r.parent, r.node)));
        if (crashedNodes.contains(r.parent)) {
          badRoots.add(r.parent);
        }
      } else if (r.parent == -1 && r.node != 0 && !crashedNodes.contains(r.node)) {
        System.out.println(String.format("Node: %d has no parent.", r.node));
      }
    }

    // This takes a long time for big networks -> Skip it for them
    if (communicationLayer.getIbisCount() <= 500) {
      for (ChandyMisraResult r : results) {
        if (!crashedNodes.contains(r.node)) {
          int expectedDistance = getDistance(r.node);
          if (expectedDistance != r.dist) {
            invalidWeights = true;
            System.out.println(String.format("Node %d has incorrect distance %d should be %d", r.node, r.dist, expectedDistance));
          }
        }
      }
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
    return new Tree(root, treeChannels, vertices);
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

  public Set<Channel> getChannels() {
    return channels;
  }

  public boolean hasValidWeights() {
    return !invalidWeights;
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
     * <p>
     * This function is not consistent with the equals of the same class.
     */
    @Override
    public int compareTo(NodeDistancePair nodeDistancePair) {
      return priority - nodeDistancePair.priority;
    }

    /**
     * Equality defined by represented node.
     * <p>
     * This function is not consistent with compareTo of the same class.
     *
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
   * @param channels            All available channels
   * @param root                Root node of the sink tree
   * @param vertices            The node ID's. These do not have to be consecutive
   * @param unreachableVertices Output parameter if this methods returns null unreachableVertices will contain
   *                            all vertices that aren't reachable from root.
   * @return A shortest path sink tree to root or NULL if the graph is not connected.
   */
  public static Tree getSinkTree(List<Channel> channels, int root, Set<Integer> vertices, Set<Integer> unreachableVertices) {
    Map<Integer, List<Channel>> adjacencyGraph = new HashMap<>();
    for (int v : vertices) {
      adjacencyGraph.put(v, new LinkedList<Channel>());
    }
    for (Channel c : channels) {
      adjacencyGraph.get(c.src).add(c);
    }

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

      for (Channel c : adjacencyGraph.get(v.node)) {
        int alternativeDistance = distances.get(v.node) + c.getWeight();
        if (alternativeDistance < distances.get(c.dest) && alternativeDistance > 0) {  // Bigger 0 because of overflows
          distances.put(c.dest, alternativeDistance);
          unvisitedVertices.remove(new NodeDistancePair(-1, c.dest));  // Equality of PriorityQueue entries is defined by the node only.
          unvisitedVertices.offer(new NodeDistancePair(alternativeDistance, c.dest));  // Reenter the node with its new distance
          parents.put(c.dest, v.node);
        }
      }
    }

    if (distances.values().contains(Integer.MAX_VALUE)) {
      for (int node : distances.keySet()) {
        if (distances.get(node) == Integer.MAX_VALUE) {
          unreachableVertices.add(node);
        }
      }
      logger.debug("Could not construct sink tree because some nodes are not connected to root.");
      return null;
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

    return new Tree(root, treeChannels, vertices);
  }

  public Map<Integer, Set<Integer>> getLevels() {
    Map<Integer, Set<Integer>> levels = new HashMap<>();
    for (int v : vertices) {
      int level = getLevel(root, v, 0);
      if (!levels.containsKey(level)) {
        levels.put(level, new HashSet<Integer>());
      }
      levels.get(level).add(v);
    }
    return levels;
  }

  private int getLevel(int currentNode, int node, int level) {
    if (currentNode == node) {
      return level;
    }

    List<Channel> outChannels = channelsFrom(currentNode);
    for (Channel c : outChannels) {
      int l = getLevel(c.dest, node, level + 1);
      if (l != -1) {
        return l;
      }
    }
    return -1;
  }

  private int getDistance(int node) {
    return getDistance(root, node, 0);
  }

  private int getDistance(int currentNode, int node, int distance) {
    if (currentNode == node) {
      return distance;
    }

    List<Channel> outChannels = channelsFrom(currentNode);
    for (Channel c : outChannels) {
      int d = getDistance(c.dest, node, distance + c.getWeight());
      if (d != -1) {
        return d;
      }
    }
    return -1;
  }
}
