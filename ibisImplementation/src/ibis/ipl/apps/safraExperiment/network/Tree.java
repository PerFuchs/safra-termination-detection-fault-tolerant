package ibis.ipl.apps.safraExperiment.network;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import org.apache.log4j.Logger;

import java.util.*;

public class Tree {
  private final static Logger logger = Logger.getLogger(Tree.class);

  private int root;
  private Map<Integer, Integer> nodesToParent;
  private Map<Integer, Integer> distancesToParent;

  public static Tree getBFSTree(Network network, int root) {
    LinkedList<Channel> work = new LinkedList<>();
    Set<Integer> visited = new HashSet<>();

    Set<Channel> allChannels = new HashSet<>(network.getChannels());
    for (Channel c : channelsFrom(allChannels, root)) {
      work.offerFirst(c);
    }
    allChannels.removeAll(work);
    visited.add(root);

    Map<Integer, Integer> parents = new HashMap<>();
    Map<Integer, Integer> distancesToParent = new HashMap<>();
    while (!work.isEmpty()) {
      Channel current = work.pollLast();
      if (!visited.contains(current.dest)) {
        logger.debug(String.format("Current: %04d", current.dest));

        parents.put(current.dest, current.src);
        distancesToParent.put(current.dest, current.getWeight());

        for (Channel c : channelsFrom(allChannels, current.dest)) {
          if (!visited.contains(c.dest)) {
            work.offerFirst(c);
          }
        }
        allChannels.removeAll(work);
        visited.add(current.dest);
      }
  }

    logger.debug(String.format("Root %04d", root));
    for (int n : parents.keySet()) {
      logger.debug(String.format("%04d --> %04d", n, parents.get(n)));
    }

    return new Tree(root, parents, distancesToParent);
  }

  private static Set<Channel> channelsFrom(Set<Channel> channels, int node) {
    Set<Channel> ret = new HashSet<>();
    for (Channel c : channels) {
      if (c.src == node) {
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
  public static Tree getSinkTree(List<Channel> channels, int root, Set<Integer> vertices, List<Integer> unreachableVertices) {
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
    Map<Integer, Integer> distancesToParent = new HashMap<>();

    while (!unvisitedVertices.isEmpty()) {
      NodeDistancePair v = unvisitedVertices.poll();

      for (Channel c : adjacencyGraph.get(v.node)) {
        int alternativeDistance = distances.get(v.node) + c.getWeight();
        if (alternativeDistance < distances.get(c.dest) && alternativeDistance > 0) {  // Bigger 0 because of overflows
          distances.put(c.dest, alternativeDistance);
          unvisitedVertices.remove(new NodeDistancePair(-1, c.dest));  // Equality of PriorityQueue entries is defined by the node only.
          unvisitedVertices.offer(new NodeDistancePair(alternativeDistance, c.dest));  // Reenter the node with its new distance
          parents.put(c.dest, v.node);
          distancesToParent.put(c.dest, c.getWeight());
        }
      }
    }

    if (distances.values().contains(Integer.MAX_VALUE)) {
      for (int node : distances.keySet()) {
        if (distances.get(node) == Integer.MAX_VALUE) {
          unreachableVertices.add(node);
        }
      }
      logger.debug("Some vertices cannot be reached via this sink tree");
    }

    return new Tree(root, parents, distancesToParent);
  }

  private Tree(int root, Map<Integer, Integer> parents, Map<Integer, Integer> distancesToParent) {
    this.root = root;
    this.nodesToParent = parents;
    this.distancesToParent = distancesToParent;
  }

  public boolean hasEqualVerticesWith(Tree other) {
    return nodesToParent.keySet().equals(other.nodesToParent.keySet()) && root == other.root;
  }

  public Map<Integer, Set<Integer>> getLevels() {
    Map<Integer, Set<Integer>> levels = new HashMap<>();
    levels.put(0, new HashSet<Integer>());
    levels.get(0).add(root);

    for (int v : nodesToParent.keySet()) {
      int level = getLevel(v);
      if (!levels.containsKey(level)) {
        levels.put(level, new HashSet<Integer>());
      }
      levels.get(level).add(v);
    }
    return levels;
  }

  public int getLevel(int node) {
    if (node == root) {
      return 0;
    }
    int distance = 1;
    int parent = nodesToParent.get(node);
    while (parent != root) {
      distance += 1;
      parent = nodesToParent.get(parent);
    }
    return distance;
  }

  public boolean hasEqualLevels(Tree other) {
    return getLevels().equals(other.getLevels());
  }

  public int getDistance(int node) {
    if (node == root) {
      return 0;
    }
    int distance = distancesToParent.get(node);
    int parent = nodesToParent.get(node);
    while (parent != root) {
      distance += distancesToParent.get(parent);
      parent = nodesToParent.get(parent);
    }
    return distance;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    Stack<Integer> work = new Stack<>();

    work.addAll(getChildren(root));
    while (!work.empty()) {
      Integer node = work.pop();

      b.append(String.format("Node: %d Parent: %d\n", node, nodesToParent.get(node)));
      work.addAll(getChildren(node));
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
    return root == that.root && nodesToParent.equals(that.nodesToParent) && distancesToParent.equals(that.distancesToParent);
  }

  private Set<Integer> getChildren(int node) {
    Set<Integer> children = new HashSet<>();

    for (int potentialChild : nodesToParent.keySet()) {
      if (nodesToParent.get(potentialChild) == node) {
        children.add(potentialChild);
      }
    }
    return children;
  }

}