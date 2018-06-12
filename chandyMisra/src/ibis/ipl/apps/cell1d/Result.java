package ibis.ipl.apps.cell1d;

public class Result {
  public final int node;
  public final int parent;
  public final int dist;

  public Result(int node, int parent, int dist) {
    this.node = node;
    this.parent = parent;
    this.dist = dist;
  }
}
