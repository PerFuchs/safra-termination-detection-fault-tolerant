package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.communication.Message;

import java.util.Random;

public class AfekKuttenYungData {
  public static final int EMPTY_DIRECTION = -1;
  public static final int ASK = 1;
  public static final int GRANT = 2;

  public static final int EMPTY_NODE = -1;
  public static final int EMPTY_PARENT = EMPTY_NODE;

  public int parent;
  public int root;
  public int distance;

  public int req;
  public int from;
  public int to;
  public int direction;

  public static AfekKuttenYungData getRandomData() {
    Random r = new Random();

    int parent = r.nextInt();
    int root = r.nextInt();
    int distance = r.nextInt();

    int req = r.nextInt();
    int from = r.nextInt();
    int to = r.nextInt();
    int direction = r.nextInt();

    return new AfekKuttenYungData(parent, root, distance, req, from, to, direction);
  }

  public static AfekKuttenYungData getEmptyData() {
    return new AfekKuttenYungData(EMPTY_NODE, EMPTY_NODE, -1, EMPTY_NODE, EMPTY_NODE, EMPTY_NODE, EMPTY_DIRECTION);
  }

  public AfekKuttenYungData(int parent, int root, int distance, int req, int from, int to, int direction) {
    this.parent = parent;
    this.root = root;
    this.distance = distance;
    this.req = req;
    this.from = from;
    this.to = to;
    this.direction = direction;
  }


  AfekKuttenYungData(AfekKuttenYungData afekKuttenYungData) {
    copyFrom(afekKuttenYungData);
  }
  
  private void copyFrom(AfekKuttenYungData other) {
    parent = other.parent;
    root = other.root;
    distance = other.distance;

    req = other.req;
    to = other.to;
    from = other.from;
    direction = other.direction;
  }

  public void update(AfekKuttenYungDataMessage m) {
    copyFrom(m.data);
  }

  public String treeVariablesAsString() {
    return String.format("Parent: %04d, Root: %04d, Distance: %d", parent, root, distance);
  }

  public String requestVariablesAsString() {
    String directionString = "N";
    if (direction == ASK) {
      directionString = "A";
    } else if (direction == GRANT) {
      directionString = "G";
    }
    return String.format("Direction: %s, Req: %04d, From: %04d, To: %04d", directionString, req, from, to);
  }

  public String toString(){
    return treeVariablesAsString() + requestVariablesAsString();
  }
}
