package ibis.ipl.apps.safraExperiment.communication;

import ibis.ipl.ReadMessage;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public abstract class MessageFactory {
  private static List<MessageFactory> factoryImplementations = new LinkedList<>();

  static Message buildMessage(ReadMessage message) throws IOException {
    int messageType = message.readInt();

    for (MessageFactory f : factoryImplementations) {
      try {
        return f.constructMessage(messageType, message);
      } catch (UnknownMessageTypeException e) {

      }
    }
    throw new IllegalStateException(String.format("Could not construct message for type %d. Did you forget to register a factory?", messageType));
  }

  static void registerFactory(MessageFactory factory) {
    factoryImplementations.add(factory);
  }


  public abstract Message constructMessage(int messageType, ReadMessage message) throws UnknownMessageTypeException, IOException;
}
