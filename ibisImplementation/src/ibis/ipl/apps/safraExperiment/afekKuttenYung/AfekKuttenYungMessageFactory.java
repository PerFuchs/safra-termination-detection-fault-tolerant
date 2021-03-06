package ibis.ipl.apps.safraExperiment.afekKuttenYung;


import ibis.ipl.ReadMessage;
import ibis.ipl.apps.safraExperiment.communication.Message;
import ibis.ipl.apps.safraExperiment.communication.MessageClassTypes;
import ibis.ipl.apps.safraExperiment.communication.MessageFactory;
import ibis.ipl.apps.safraExperiment.communication.UnknownMessageTypeException;

import java.io.IOException;

public class AfekKuttenYungMessageFactory extends MessageFactory {

  public Message constructMessage(int messageType, ReadMessage message) throws UnknownMessageTypeException, IOException {
    if (messageType == MessageClassTypes.AKY_MESSAGE.ordinal()) {
      return AfekKuttenYungDataMessage.fromIPLMessage(message);
    }
    throw new UnknownMessageTypeException();
  }
}
