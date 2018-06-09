package ibis.ipl.apps.cell1d;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.MessageUpcall;
import ibis.ipl.ReadMessage;

import java.io.IOException;

public class CrashedUpcall implements MessageUpcall {

  private final IbisIdentifier origin;
  private final CrashDetector crashDetector;

  protected CrashedUpcall(CrashDetector crashDetector, IbisIdentifier origin) {
    this.origin = origin;
    this.crashDetector = crashDetector;
  }

  @Override
  public void upcall(ReadMessage readMessage) throws IOException, ClassNotFoundException {
    readMessage.finish();
    crashDetector.handleCrash(origin);
  }
}
