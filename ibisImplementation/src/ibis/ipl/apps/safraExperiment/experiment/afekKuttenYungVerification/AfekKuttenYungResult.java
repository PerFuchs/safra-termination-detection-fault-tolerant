package ibis.ipl.apps.safraExperiment.experiment.afekKuttenYungVerification;

public class AfekKuttenYungResult {
  public final int root;
  public final int parent;
  public final int node;
  public final int distance;

  public AfekKuttenYungResult(int node, int parent, int distance, int root) {
    this.root = root;
    this.parent = parent;
    this.node = node;
    this.distance = distance;
  }
}
