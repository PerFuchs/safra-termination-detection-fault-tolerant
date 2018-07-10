package ibis.ipl.apps.safraExperiment.network;

import ibis.ipl.apps.safraExperiment.communication.CommunicationLayer;
import ibis.ipl.apps.safraExperiment.crashSimulation.CrashSimulator;
import ibis.ipl.apps.safraExperiment.utils.SynchronizedRandom;
import org.apache.log4j.Logger;

import java.util.*;

public class Network {
  private final static Logger logger = Logger.getLogger(Network.class);

  private final CommunicationLayer communicationLayer;
  private List<Channel> channels;

  private Network(List<Channel> channels, CommunicationLayer communicationLayer) {
    this.communicationLayer = communicationLayer;
    this.channels = channels;
  }

  private Set<Integer> getAliveNodes(Set<Integer> crashedNodes) {
    Set<Integer> aliveNodes = new HashSet<>();
    for (int i = 0; i < communicationLayer.getIbisCount(); i++) {
      aliveNodes.add(i);
    }
    aliveNodes.removeAll(crashedNodes);
    return aliveNodes;
  }

  private List<Channel> getAliveChannel(Set<Integer> crashedNodes) {
    List<Channel> aliveChannels = new LinkedList<>(channels);
    for (Channel c : channels) {
      if (crashedNodes.contains(c.src) || crashedNodes.contains(c.dest)) {
        aliveChannels.remove(c);
      }
    }
    return aliveChannels;
  }

  public Tree getMinimumSpanningTree(List<Integer> crashedNodes) {
    Set<Integer> crashedNodeNumbers = new HashSet<>(crashedNodes);
    Set<Integer> aliveNodes = getAliveNodes(crashedNodeNumbers);
    List<Channel> aliveChannels = getAliveChannel(crashedNodeNumbers);
    return Tree.getMinimumSpanningTree(aliveChannels, communicationLayer.getRoot(), aliveNodes);
  }

  public Tree getSinkTree(List<Integer> crashedNodes) {
    Set<Integer> crashedNodeNumbers = new HashSet<>(crashedNodes);
    Set<Integer> aliveNodes = getAliveNodes(crashedNodeNumbers);
    List<Channel> aliveChannels = getAliveChannel(crashedNodeNumbers);
    return Tree.getSinkTree(aliveChannels, communicationLayer.getRoot(), aliveNodes);
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

  public static Network getUndirectedRing(CommunicationLayer communicationLayer, CrashSimulator crashSimulator) {
    List<Channel> channels = new LinkedList<>();

    for (int i = 1; i < communicationLayer.getIbisCount(); i++) {
      channels.add(new Channel(i-1, i, 1));
      channels.add(new Channel(i, i-1, 1));
    }
    channels.add(new Channel(0, communicationLayer.getIbisCount() - 1, 1));
    channels.add(new Channel(communicationLayer.getIbisCount() - 1, 0, 1));
    crashSimulator.scheduleLateCrash(2);
    return new Network(channels, communicationLayer);
  }

  public static Network getLineNetwork(CommunicationLayer communicationLayer, CrashSimulator crashSimulator) {
    List<Channel> channels = new LinkedList<>();

    for (int i = 0; i < communicationLayer.getIbisCount() - 1; i++) {
      channels.add(new Channel(i+1, i, 1));
      channels.add(new Channel(i, i+1, 1));
    }

    crashSimulator.scheduleLateCrash(1);

    // TODO not working for 2000 nodes.

    // Add heavyweight edges from the root to all nodes to simulate an fully connected network - because the root cannot
    // fail this guarantees the network stays connected with arbitrary failing nodes.
    for (int i = 2; i < communicationLayer.getIbisCount(); i++) {
      channels.add(new Channel(0, i, 1000*i));  // Carefull MAX_VALUE obviously leads to overflows later on
      channels.add(new Channel(i, 0, 1000*i));
    }

    return new Network(channels, communicationLayer);
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

  public static Network getRandomOutdegreeNetwork(CommunicationLayer communicationLayer, SynchronizedRandom synchronizedRandom) {
    List<Channel> channels = new LinkedList<>();

    StringBuilder n = new StringBuilder();

    for (int i = 0; i < communicationLayer.getIbisCount(); i++) {
      int outdeegree = synchronizedRandom.getInt(6);
      if (outdeegree == 0) {
        outdeegree = 1;
      }

      Set<Integer> connectedTo = connectedWith(channels, i);
      connectedTo.add(i);

      Set<Integer> usedWeights = new HashSet<>();
      usedWeights.add(0);
      while (connectedTo.size() <= outdeegree) {
        int to = synchronizedRandom.getInt(communicationLayer.getIbisCount());
        while (connectedTo.contains(to)) {
          to = synchronizedRandom.getInt(communicationLayer.getIbisCount());
        }
        int weight = synchronizedRandom.getInt(50000);
        while (usedWeights.contains(weight)) {
          weight = synchronizedRandom.getInt(50000);
        }
        usedWeights.add(weight);
        n.append(String.format("%d -%d-> %d , ", i, weight, to));
        channels.add(new Channel(i, to, weight));
        channels.add(new Channel(to, i, weight));
        connectedTo.add(to);
      }
    }

    // Add heavyweight edges from the root to all nodes to simulate an fully connected network - because the root cannot
    // fail this guarantees the network stays connected with arbitrary failing nodes.
    for (int i = 1; i < communicationLayer.getIbisCount(); i++) {
      if (!connectedWith(channels, i).contains(0)) {
        channels.add(new Channel(0, i, 400000));  // Carefull MAX_VALUE obviously leads to overflows later on
        channels.add(new Channel(i, 0, 400000));
      }
    }

    logger.debug(String.format("Network on %d is: %s", communicationLayer.getID(), n.toString()));

    return new Network(channels, communicationLayer);
  }
}

