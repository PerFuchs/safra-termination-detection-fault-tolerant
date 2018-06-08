package ibis.ipl.apps.cell1d.algorithm;

import ibis.ipl.IbisIdentifier;

public class Network {

  private IbisIdentifier[] ibises;

  public Network(IbisIdentifier[] ibises) {
    this.ibises = ibises;
  }

  public SpanningTree getExpectedSpanningTree() {
    return null;
  }

  public IbisIdentifier[] getNeighbours(IbisIdentifier id) {
    return ibises;
  }

  public int getWeight(IbisIdentifier source, IbisIdentifier destination) {
    return 1;
  }
}


