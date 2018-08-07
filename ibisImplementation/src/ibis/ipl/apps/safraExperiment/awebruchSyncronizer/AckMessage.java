package ibis.ipl.apps.safraExperiment.awebruchSyncronizer;

import ibis.ipl.WriteMessage;
import ibis.ipl.apps.safraExperiment.communication.Message;

public class AckMessage extends Message {
  @Override
  public int getReceiver() {
    return 0;
  }

  @Override
  public int getSource() {
    return 0;
  }

  @Override
  public void writeToIPLMessage(WriteMessage writeMessage) {

  }
}
