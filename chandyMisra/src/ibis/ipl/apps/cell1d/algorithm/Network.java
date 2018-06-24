package ibis.ipl.apps.cell1d.algorithm;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.apps.cell1d.CommunicationLayer;
import ibis.ipl.apps.cell1d.CrashSimulator;

import java.util.*;

public class Network {

  private IbisIdentifier me;
  private final CommunicationLayer communicationLayer;
  private Set<IbisIdentifier> otherIbises;

  private List<Channel> channels;

  private Network(IbisIdentifier me, IbisIdentifier[] ibises, List<Channel> channels, CommunicationLayer communicationLayer) {
    this.me = me;
    this.communicationLayer = communicationLayer;
    this.otherIbises = new TreeSet<>();
    for (IbisIdentifier id : ibises) {
      if (!id.equals(me)) {
        this.otherIbises.add(id);
      }
    }
    this.channels = channels;
  }

  public MinimumSpanningTree getSpanningTree(List<IbisIdentifier> crashedNodes) {
    Set<Integer> crashedNodeNumbers = new HashSet<>();
    for (IbisIdentifier id : crashedNodes) {
      crashedNodeNumbers.add(communicationLayer.getNodeNumber(id));
    }

    Set<Integer> aliveNodes = new HashSet<>();
    for (int i = 0; i < communicationLayer.getIbises().length; i++) {
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

  // TODO refactor all to use integers
  // TODO refactor all to use collections
  public IbisIdentifier[] getNeighbours(IbisIdentifier id) {
    List<IbisIdentifier> neighbours = new LinkedList<>();

    for (Channel c : channels) {
      if (c.src == communicationLayer.getNodeNumber(id)) {
        neighbours.add(communicationLayer.getIbises()[c.dest]);
      }
    }
    return neighbours.toArray(new IbisIdentifier[neighbours.size()]);
  }

  public int getWeight(IbisIdentifier source, IbisIdentifier destination) {
    return channels.get(
        channels.indexOf(new Channel(
            communicationLayer.getNodeNumber(source), communicationLayer.getNodeNumber(destination), 0)
        )).getWeight();
  }

  public static Network getCompleteNetwork(IbisIdentifier me, IbisIdentifier[] ibises, CommunicationLayer communicationLayer) {
    List<Channel> channels = new LinkedList<>();
    for (IbisIdentifier id1 : ibises) {
      for (IbisIdentifier id2 : ibises) {
        if (!id1.equals(id2)) {
          channels.add(new Channel(communicationLayer.getNodeNumber(id1), communicationLayer.getNodeNumber(id2), 1));
        }
      }
    }

    return new Network(me, ibises, channels, communicationLayer);
  }

  public static Network getUndirectedRing(IbisIdentifier me, IbisIdentifier[] ibises, CommunicationLayer communicationLayer, CrashSimulator crashSimulator) {
    List<Channel> channels = new LinkedList<>();

    int root = communicationLayer.getRoot();
    for (int i = 1; i < communicationLayer.getIbises().length; i++) {
      channels.add(new Channel(i-1, i, 1));
      channels.add(new Channel(i, i-1, 1));
    }
    channels.add(new Channel(0, communicationLayer.getIbises().length - 1, 1));
    channels.add(new Channel(communicationLayer.getIbises().length - 1, 0, 1));
    crashSimulator.scheduleLateCrash(communicationLayer.getIbises()[2]);
    return new Network(me, ibises, channels, communicationLayer);
  }

  public static Network getLineNetwork(IbisIdentifier me, IbisIdentifier[] ibises, CommunicationLayer communicationLayer, CrashSimulator crashSimulator) {
    List<Channel> channels = new LinkedList<>();

    int root = communicationLayer.getRoot();
    for (int i = 0; i < communicationLayer.getIbises().length - 1; i++) {
      channels.add(new Channel(i+1, i, 1));
      channels.add(new Channel(i, i+1, 1));
      if (i == 1) {
        crashSimulator.scheduleLateCrash(communicationLayer.getIbises()[i]);
      }
    }

    // Add heavyweight edges from the root to all nodes for more messages
//    for (int i = 2; i < communicationLayer.getIbises().length; i++) {
//      channels.add(new Channel(0, i, 1000*i));  // Carefull MAX_VALUE obviously leads to overflows later on
//      channels.add(new Channel(i, 0, 1000*i));
//    }

    return new Network(me, ibises, channels, communicationLayer);
  }

}


