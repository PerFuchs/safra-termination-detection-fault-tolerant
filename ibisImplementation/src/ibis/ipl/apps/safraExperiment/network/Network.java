package ibis.ipl.apps.safraExperiment.network;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Network {
  private final static Logger logger = Logger.getLogger(Network.class);

  private final int nodeCount;
  private List<Channel> channels;


  private Network(List<Channel> channels, int nodeCount) {
    this.nodeCount = nodeCount;
    this.channels = channels;
  }

  private static Set<Integer> getAliveNodes(int nodeCount, Set<Integer> crashedNodes) {
    Set<Integer> aliveNodes = new HashSet<>();
    for (int i = 0; i < nodeCount; i++) {
      aliveNodes.add(i);
    }
    aliveNodes.removeAll(crashedNodes);
    return aliveNodes;
  }

  private static List<Channel> getAliveChannel(List<Channel> channels, Set<Integer> crashedNodes) {
    List<Channel> aliveChannels = new LinkedList<>(channels);
    for (Channel c : channels) {
      if (crashedNodes.contains(c.src) || crashedNodes.contains(c.dest)) {
        aliveChannels.remove(c);
      }
    }
    return aliveChannels;
  }

  public Tree getMinimumSpanningTree(Set<Integer> crashedNodes) {
    Set<Integer> crashedNodeNumbers = new HashSet<>(crashedNodes);
    Set<Integer> aliveNodes = getAliveNodes(nodeCount, crashedNodeNumbers);
    List<Channel> aliveChannels = getAliveChannel(channels, crashedNodeNumbers);
    return Tree.getMinimumSpanningTree(aliveChannels, 0, aliveNodes);
  }

  public Tree getSinkTree(Set<Integer> crashedNodes) {
    Set<Integer> crashedNodeNumbers = new HashSet<>(crashedNodes);
    Set<Integer> aliveNodes = getAliveNodes(nodeCount, crashedNodeNumbers);
    List<Channel> aliveChannels = getAliveChannel(channels, crashedNodeNumbers);
    return Tree.getSinkTree(aliveChannels, 0, aliveNodes, new LinkedList<Integer>());
  }

  public Network combineWith(Network network, int weightMultiplier) {
    for (Channel c : network.channels) {
      if (!channels.contains(c)) {
        channels.add(new Channel(c.src, c.dest, c.getWeight() * weightMultiplier));
      }
    }
    return this;
  }

  public List<Integer> getNeighbours(int id) {
    List<Integer> neighbours = new LinkedList<>();

    for (Channel c : channels) {
      if (c.src == id) {
        neighbours.add(c.dest);
      }
    }
    return neighbours;
  }

  public int getWeight(int source, int destination) {
    return channels.get(
        channels.indexOf(new Channel(source, destination, 0)
        )).getWeight();
  }

  public static Network getUndirectedRing(CommunicationLayer communicationLayer) {
    List<Channel> channels = new LinkedList<>();

    for (int i = 1; i < communicationLayer.getIbisCount(); i++) {
      channels.add(new Channel(i-1, i, 1));
      channels.add(new Channel(i, i-1, 1));
    }
    channels.add(new Channel(0, communicationLayer.getIbisCount() - 1, 1));
    channels.add(new Channel(communicationLayer.getIbisCount() - 1, 0, 1));
    return new Network(channels, communicationLayer.getIbisCount());
  }

  public static Network getLineNetwork(CommunicationLayer communicationLayer) {
    List<Channel> channels = new LinkedList<>();

    for (int i = 0; i < communicationLayer.getIbisCount() - 1; i++) {
      channels.add(new Channel(i+1, i, 1));
      channels.add(new Channel(i, i+1, 1));
    }

    // Add heavyweight edges from the root to all nodes to simulate an fully connected network - because the root cannot
    // fail this guarantees the network stays connected with arbitrary failing nodes.
    for (int i = 2; i < communicationLayer.getIbisCount(); i++) {
      channels.add(new Channel(0, i, 1000*i));  // Carefull MAX_VALUE obviously leads to overflows later on
      channels.add(new Channel(i, 0, 1000*i));
    }

    return new Network(channels, communicationLayer.getIbisCount());
  }

  private static Set<Integer> connectedWith(List<Channel> channels, int node) {
    Set<Integer> neighbour = new HashSet<>();
    for (Channel c : channels) {
      if (c.src == node) {
        neighbour.add(c.dest);
      }
    }
    return neighbour;
  }

  public static Network getRandomOutdegreeNetwork(CommunicationLayer communicationLayer, SynchronizedRandom synchronizedRandom, Set<Integer> nodesExpectedToCrash) {
    List<Channel> channels = new LinkedList<>();

    for (int i = 0; i < communicationLayer.getIbisCount(); i++) {
      int outdeegree = synchronizedRandom.getInt(6);
      if (outdeegree == 0) {
        outdeegree = 1;
      }

      Set<Integer> connectedTo = connectedWith(channels, i);

      while (connectedTo.size() <= outdeegree) {
        int to = synchronizedRandom.getInt(communicationLayer.getIbisCount());
        while (connectedTo.contains(to) || to == i) {
          to = synchronizedRandom.getInt(communicationLayer.getIbisCount());
        }
        int weight = synchronizedRandom.getInt(50000);
        channels.add(new Channel(i, to, weight));
        channels.add(new Channel(to, i, weight));
        connectedTo.add(to);
      }
    }

    int root = communicationLayer.getRoot();
    // Add heavyweight edges from the root to nodes that are unreachable when other nodes crash - because the root cannot
    // fail this guarantees the network stays connected with arbitrary failing nodes.
    List<Integer> unreachableVertices = new LinkedList<>();  // Output parameter from getSinkTree
    Tree sinkTree = Tree.getSinkTree(getAliveChannel(channels, nodesExpectedToCrash),
        root,
        getAliveNodes(communicationLayer.getIbisCount(), nodesExpectedToCrash),
        unreachableVertices);

    // All nodes should have the same list to pick a random element from.
    Collections.sort(unreachableVertices);

    while (sinkTree == null) {
      unreachableVertices.removeAll(nodesExpectedToCrash);

      int nodesToConnect = unreachableVertices.size() / 2;
      if (nodesToConnect == 0) {
        nodesToConnect = 1;
      }
      for (int i = 0; i < nodesToConnect; i++) {
          int node = unreachableVertices.get(synchronizedRandom.getInt(unreachableVertices.size()));
          unreachableVertices.remove(new Integer(node));
          channels.add(new Channel(root, node, 400000));
          channels.add(new Channel(node, root, 400000));
      }

      unreachableVertices = new LinkedList<>();
      sinkTree = Tree.getSinkTree(getAliveChannel(channels, nodesExpectedToCrash),
          communicationLayer.getRoot(),
          getAliveNodes(communicationLayer.getIbisCount(), nodesExpectedToCrash),
          unreachableVertices);
      Collections.sort(unreachableVertices);
    }

    return new Network(channels, communicationLayer.getIbisCount());
  }

  public static Network fromFile(Path filePath) throws IOException {
    List<Channel> channels = new LinkedList<>();

    boolean networkSizeLine = true;
    int networkSize = -1;
    for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
      if (networkSizeLine) {
        networkSize = Integer.valueOf(line);
        networkSizeLine = false;
      } else {
        channels.add(Channel.fromString(line));
      }
    }
    return new Network(channels, networkSize);
  }

  public Set<Channel> getChannels() {
    return new HashSet<>(channels);
  }

  public void writeToFile(Path filePath) throws IOException {
    StringBuilder network = new StringBuilder();
    network.append(nodeCount).append('\n');
    for (Channel c : channels) {
      network.append(c.toString()).append('\n');
    }

    Files.write(filePath, network.toString().getBytes());
  }
}


