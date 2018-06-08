package ibis.ipl.apps.cell1d.algorithm;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.apps.cell1d.CommunicationLayer;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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

  public SpanningTree getExpectedSpanningTree() {
    return null;
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

  public static Network getUndirectedRing(IbisIdentifier me, IbisIdentifier[] ibises, CommunicationLayer communicationLayer) {
    List<Channel> channels = new LinkedList<>();

    int root = communicationLayer.getRoot();
    for (int i = 1; i < communicationLayer.getIbises().length; i++) {
      channels.add(new Channel(i-1, i, 1));
      channels.add(new Channel(i, i-1, 1));
    }
    channels.add(new Channel(0, communicationLayer.getIbises().length - 1, 1));
    channels.add(new Channel(communicationLayer.getIbises().length - 1, 0, 1));

    return new Network(me, ibises, channels, communicationLayer);
  }

}


