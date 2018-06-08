package ibis.ipl.apps.cell1d.algorithm;

import ibis.ipl.IbisIdentifier;

import java.util.Set;
import java.util.TreeSet;

public class Network {

  private IbisIdentifier me;
  private Set<IbisIdentifier> otherIbises;

  public Network(IbisIdentifier me, IbisIdentifier[] ibises) {
    this.me = me;
    this.otherIbises = new TreeSet<>();
    for (IbisIdentifier id : ibises) {
      if (!id.equals(me)) {
        this.otherIbises.add(id);
      }
    }
  }

  public SpanningTree getExpectedSpanningTree() {
    return null;
  }

  public IbisIdentifier[] getNeighbours(IbisIdentifier id) {
    return otherIbises.toArray(new IbisIdentifier[otherIbises.size()]);
  }

  public int getWeight(IbisIdentifier source, IbisIdentifier destination) {
    return 1;
  }
}


