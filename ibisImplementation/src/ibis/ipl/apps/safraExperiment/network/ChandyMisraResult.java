package ibis.ipl.apps.safraExperiment.network;

public class ChandyMisraResult {
  public final int node;
  public final int parent;
  public final int dist;
  public final int parentEdgeWeight;

  public ChandyMisraResult(int node, int parent, int dist, int parentEdgeWeight) {
    this.node = node;
    this.parent = parent;
    this.dist = dist;
    this.parentEdgeWeight = parentEdgeWeight;
  }
}
