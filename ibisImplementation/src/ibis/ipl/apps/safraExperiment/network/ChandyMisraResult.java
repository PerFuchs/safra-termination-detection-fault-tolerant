package ibis.ipl.apps.safraExperiment.network;

public class ChandyMisraResult {
  public final int node;
  public final int parent;
  public final int dist;

  public ChandyMisraResult(int node, int parent, int dist) {
    this.node = node;
    this.parent = parent;
    this.dist = dist;
  }
}
