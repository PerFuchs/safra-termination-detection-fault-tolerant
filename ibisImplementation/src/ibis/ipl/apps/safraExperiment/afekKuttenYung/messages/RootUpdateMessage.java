package ibis.ipl.apps.safraExperiment.afekKuttenYung.messages;

import ibis.ipl.WriteMessage;
import ibis.ipl.apps.safraExperiment.communication.BasicMessage;

public class RootUpdateMessage extends BasicMessage {
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
