package ibis.ipl.apps.safraExperiment.afekKuttenYung;

import ibis.ipl.ReadMessage;
import ibis.ipl.WriteMessage;
import ibis.ipl.apps.safraExperiment.communication.BasicMessage;

import java.io.IOException;

public class AfekKuttenYungDataMessage extends BasicMessage {
  private final long sequenceNumber;
  final AfekKuttenYungData data;

  public AfekKuttenYungDataMessage(long sequenceNumber, AfekKuttenYungData data) {
    this.sequenceNumber = sequenceNumber;
    this.data = data;
  }

  @Override
  public long getSequenceNumber() {
    return sequenceNumber;
  }

  @Override
  public void writeToIPLMessage(WriteMessage writeMessage) throws IOException {
    writeMessage.writeInt(AfekKuttenYungMessageFactory.DATA_MESSAGE_TYPE);

    writeMessage.writeLong(sequenceNumber);

    writeMessage.writeInt(data.root);
    writeMessage.writeInt(data.parent);
    writeMessage.writeInt(data.distance);

    writeMessage.writeInt(data.req);
    writeMessage.writeInt(data.to);
    writeMessage.writeInt(data.from);
    writeMessage.writeInt(data.direction);
  }

  public static AfekKuttenYungDataMessage fromIPLMessage(ReadMessage readMessage) throws IOException {
    long sequenceNumber = readMessage.readLong();

    AfekKuttenYungData data = AfekKuttenYungData.getEmptyData();

    data.root = readMessage.readInt();
    data.parent = readMessage.readInt();
    data.distance = readMessage.readInt();

    data.req = readMessage.readInt();
    data.to = readMessage.readInt();
    data.from = readMessage.readInt();
    data.direction = readMessage.readInt();

    return new AfekKuttenYungDataMessage(sequenceNumber, data);
  }
}
