package ibis.ipl.apps.safraExperiment.network;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.experiment.afekKuttenYungVerification.AfekKuttenYungResult;
import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Network {
  private final static Logger logger = Logger.getLogger(Network.class);

  // Adjacency map for an undirected graph. Keys: all vertices of the graph. Values: list of edges from each node.
  private final Map<Integer, Set<Channel>> adjancencyMap;

  public static Network fromChandyMisraResults(List<ChandyMisraResult> results) {
    Set<Channel> channels = new HashSet<>();
    for (ChandyMisraResult r : results) {
      if (r.parent != -1) {
        channels.add(new Channel(r.parent, r.node, r.parentEdgeWeight));
        channels.add(new Channel(r.node, r.parent, r.parentEdgeWeight));
      }
    }
    return new Network(channels);
  }

  public static Network fromAfekKuttenYungResults(List<AfekKuttenYungResult> results) {
    Set<Channel> channels = new HashSet<>();
    for (AfekKuttenYungResult r : results) {
      if (r.parent != -1) {
        channels.add(new Channel(r.parent, r.node, 1));
        channels.add(new Channel(r.node, r.parent, 1));
      }
    }
    return new Network(channels);
  }

  public static Network fromFile(Path filePath) throws IOException {
    Set<Channel> channels = new HashSet<>();

    for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
      channels.add(Channel.fromString(line));
    }
    return new Network(channels);
  }

  public void writeToFile(Path filePath) throws IOException {
    StringBuilder network = new StringBuilder();
    for (int node : adjancencyMap.keySet()) {
      for (Channel c : adjancencyMap.get(node)) {
        network.append(c.toString()).append('\n');
      }
    }

    Files.write(filePath, network.toString().getBytes());
  }

  public static Network getUndirectedRing(CommunicationLayer communicationLayer) {
    Set<Channel> channels = new HashSet<>();

    for (int i = 1; i < communicationLayer.getIbisCount(); i++) {
      channels.add(new Channel(i - 1, i, 1));
      channels.add(new Channel(i, i - 1, 1));
    }
    channels.add(new Channel(0, communicationLayer.getIbisCount() - 1, 1));
    channels.add(new Channel(communicationLayer.getIbisCount() - 1, 0, 1));
    return new Network(channels);
  }

  public static Network getLineNetwork(CommunicationLayer communicationLayer) {
    Set<Channel> channels = new HashSet<>();

    for (int i = 0; i < communicationLayer.getIbisCount() - 1; i++) {
      channels.add(new Channel(i + 1, i, 1));
      channels.add(new Channel(i, i + 1, 1));
    }

    return new Network(channels);
  }

  public static Network getRandomOutdegreeNetwork(int ibisCount, SynchronizedRandom synchronizedRandom) {
    Map<Integer, Set<Channel>> adjacenyMap = new HashMap<>();

    for (int i = 0; i < ibisCount; i++) {
      int outdeegree = synchronizedRandom.getInt(6);
      if (outdeegree == 0) {
        outdeegree = 1;
      }

      Set<Integer> connectedTo = getNeighbours(adjacenyMap, i);

      while (connectedTo.size() <= outdeegree) {
        int to = synchronizedRandom.getInt(ibisCount);
        while (connectedTo.contains(to) || to == i) {
          to = synchronizedRandom.getInt(ibisCount);
        }
        int weight = synchronizedRandom.getInt(50000);
        adjacenyMap.get(i).add(new Channel(i, to, weight));
        adjacenyMap.get(to).add(new Channel(to, i, weight));
        connectedTo.add(to);
      }
    }

    return new Network(adjacenyMap);
  }

  /**
   * Based on baseNetwork returns a network with edges so that all nodes not expected to crash stay connected with
   * expectedRoot.
   */
  public static Network getFailSafeNetwork(Network baseNetwork, Set<Integer> nodesExpectedToCrash, int expectedRoot, SynchronizedRandom synchronizedRandom) {
    Set<Channel> failSafeChannels = new HashSet<>();
    Network aliveNetwork = baseNetwork.getAliveNetwork(nodesExpectedToCrash);

    List<Integer> unreachableVertices = aliveNetwork.getUnconnectedNodes(expectedRoot);

    // All nodes should have the same list to pick a random element from.
    Collections.sort(unreachableVertices);

    while (!unreachableVertices.isEmpty()) {
      int nodesToConnect = unreachableVertices.size() / 2;
      if (nodesToConnect == 0) {
        nodesToConnect = 1;
      }

      for (int i = 0; i < nodesToConnect; i++) {
        int node = unreachableVertices.get(synchronizedRandom.getInt(unreachableVertices.size()));
        unreachableVertices.remove(new Integer(node));

        Channel c = new Channel(expectedRoot, node, 1);
        aliveNetwork.adjancencyMap.get(expectedRoot).add(c);
        failSafeChannels.add(c);

        c = new Channel(node, expectedRoot, 1);
        aliveNetwork.adjancencyMap.get(node).add(c);
        failSafeChannels.add(c);
        logger.trace(String.format("Connected %04d to 0", node));
      }

      unreachableVertices = aliveNetwork.getUnconnectedNodes(expectedRoot);
      Collections.sort(unreachableVertices);
    }

    logger.trace(String.format("Fail safe channels: %d", failSafeChannels.size()));

    return new Network(failSafeChannels);
  }


  private static Set<Integer> getNeighbours(Map<Integer, Set<Channel>> adjancencyMap, int node) {
    Set<Integer> neighbours = new HashSet<>();
    for (Channel c : adjancencyMap.get(node)) {
      neighbours.add(c.dest);
    }
    return neighbours;
  }

  private Network(Map<Integer, Set<Channel>> network) {
    adjancencyMap = network;
  }

  private Network(Set<Channel> channels) {
    adjancencyMap = new HashMap<>();
    for (Channel c : channels) {
      if (!adjancencyMap.containsKey(c.src)) {
        adjancencyMap.put(c.src, new HashSet<Channel>());
      }
      adjancencyMap.get(c.src).add(c);
    }
  }

  public Tree getSinkTree(int root) {
    return Tree.getSinkTree(this.adjancencyMap, root);
  }

  public Network combineWith(Network network, int weightMultiplier) {
    for (int node : network.adjancencyMap.keySet()) {
      if (!adjancencyMap.containsKey(node)) {
        adjancencyMap.put(node, new HashSet<Channel>());
      }
      for (Channel c : network.adjancencyMap.get(node)) {
        Channel weightedChannel = new Channel(c.src, c.dest, c.getWeight() * weightMultiplier);
        adjancencyMap.get(c.src).add(weightedChannel);
      }
    }
    return this;
  }

  public Set<Integer> getNeighbours(int id) {
    Set<Integer> neighbours = new HashSet<>();

    for (Channel c : adjancencyMap.get(id)) {
      neighbours.add(c.dest);
    }
    return neighbours;
  }

  public Channel getChannel(int source, int destination) {
    for (Channel c : adjancencyMap.get(source)) {
      if (c.dest == destination) {
        return c;
      }
    }
    throw new IllegalArgumentException("Requested noneexistent channel");
  }

  public int getWeight(int source, int destination) {
    return getChannel(source, destination).getWeight();
  }

  public boolean isSuperNetworkOf(Network other) {
    if (!this.adjancencyMap.keySet().containsAll(other.adjancencyMap.keySet())) {
      return false;
    }

    for (int node : other.adjancencyMap.keySet()) {
      if (!this.adjancencyMap.get(node).containsAll(other.adjancencyMap.get(node))) {
        return false;
      }
    }
    return true;
  }

  public Set<Integer> getVertices() {
    return adjancencyMap.keySet();
  }

  public Network getAliveNetwork(Set<Integer> crashedNodes) {
    Map<Integer, Set<Channel>> aliveNetwork = new HashMap<>();
    for (int node : adjancencyMap.keySet()) {
      aliveNetwork.put(node, new HashSet<Channel>());
      for (Channel c : adjancencyMap.get(node)) {
        aliveNetwork.get(node).add(new Channel(c.src, c.dest, c.getWeight()));
      }
    }

    for (int node : crashedNodes) {
      if (aliveNetwork.containsKey(node)) {
        for (Channel c : aliveNetwork.get(node)) {
          aliveNetwork.get(c.dest).remove(new Channel(c.dest, c.src, 1));
        }
      }
      aliveNetwork.remove(node);
    }
    return new Network(aliveNetwork);
  }

  public boolean hasCycle(int root) {
    return depthFirstSearch(root, new HashSet<Integer>(), new HashSet<Integer>(), -1);
  }

  /**
   * @param currentNode node to start search from
   * @param visited     output parameter, will contain all nodes visited
   * @param marked      for internal use, initialize with an empty set
   * @ret true if a cycle was detected, false otherwise
   */
  private boolean depthFirstSearch(int currentNode, Set<Integer> visited, Set<Integer> marked, int parent) {
    boolean ret = false;
    if (visited.contains(currentNode)) {
      return false;
    }
    if (marked.contains(currentNode)) {
      return true;
    }
    marked.add(currentNode);

    if (!adjancencyMap.containsKey(currentNode)) {
      logger.error(String.format("%04d not in network"));
    }
    for (Channel c : adjancencyMap.get(currentNode)) {
      if (c.dest != parent) {
        ret |= depthFirstSearch(c.dest, visited, marked, currentNode);
      }
    }
    visited.add(currentNode);
    return ret;
  }

  public List<Integer> getUnconnectedNodes(int root) {
    Set<Integer> connectedNodes = new HashSet<>();
    depthFirstSearch(root, connectedNodes, new HashSet<Integer>(), -1);
    List<Integer> unconnectedNodes = new LinkedList<>(adjancencyMap.keySet());
    unconnectedNodes.removeAll(connectedNodes);
    return unconnectedNodes;
  }

  public Network getConnectedSubnetwork(int root) {
    Set<Integer> reachableNodes = new HashSet<>();
    depthFirstSearch(root, reachableNodes, new HashSet<Integer>(), -1);

    Map<Integer, Set<Channel>> reachableNetwork = new HashMap<>();
    for (int node : adjancencyMap.keySet()) {
      if (reachableNodes.contains(node)) {
        reachableNetwork.put(node, new HashSet<Channel>(adjancencyMap.get(node)));
      }
    }

    return new Network(reachableNetwork);
  }

  public boolean hasEqualNodes(Network otherNetwork) {
    return adjancencyMap.keySet().equals(otherNetwork.adjancencyMap.keySet());
  }

  public boolean hasNode(int node) {
    return adjancencyMap.keySet().contains(node);
  }

  public String toString() {
    StringBuilder b = new StringBuilder();
    for (int node : adjancencyMap.keySet()) {
      b.append(node);
      b.append(": ");
      for (Channel c : adjancencyMap.get(node)) {
        b.append(c.dest);
        b.append(", ");
      }
      b.append("\n");
    }
    return b.toString();
  }
}


