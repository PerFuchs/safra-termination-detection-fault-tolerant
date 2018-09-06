package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.apps.safraExperiment.communication.Message;

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
    return null;
  }

  public static AfekKuttenYungData getEmptyData() {
    return null;
  }

  AfekKuttenYungData(AfekKuttenYungData afekKuttenYungData) {

  }

  void update(Message message) {

  }


}
