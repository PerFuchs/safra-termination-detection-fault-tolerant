package ibis.ipl.apps.cell1d.algorithm;

import ibis.ipl.apps.cell1d.CommunicationLayer;
import ibis.ipl.apps.cell1d.CrashSimulator;

import java.util.*;

public class Network {
  private final CommunicationLayer communicationLayer;
  private List<Channel> channels;

  private Network(List<Channel> channels, CommunicationLayer communicationLayer) {
    this.communicationLayer = communicationLayer;
    this.channels = channels;
  }

  public MinimumSpanningTree getSpanningTree(List<Integer> crashedNodes) {
    Set<Integer> crashedNodeNumbers = new HashSet<>(crashedNodes);

    Set<Integer> aliveNodes = new HashSet<>();
    for (int i = 0; i < communicationLayer.getIbisCount(); i++) {
      aliveNodes.add(i);
    }
    aliveNodes.removeAll(crashedNodeNumbers);

    List<Channel> aliveChannels = new LinkedList<>(channels);
    for (Channel c : channels) {
      if (crashedNodeNumbers.contains(c.src) || crashedNodeNumbers.contains(c.dest)) {
        aliveChannels.remove(c);
      }
    }
    return new MinimumSpanningTree(aliveChannels, communicationLayer.getRoot(), aliveNodes);
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

  // TODO not fault tolerant
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

    // Add heavyweight edges from the root to all nodes for more messages
    for (int i = 2; i < communicationLayer.getIbisCount(); i++) {
      channels.add(new Channel(0, i, 1000*i));  // Carefull MAX_VALUE obviously leads to overflows later on
      channels.add(new Channel(i, 0, 1000*i));
    }

    return new Network(channels, communicationLayer);
  }

}


