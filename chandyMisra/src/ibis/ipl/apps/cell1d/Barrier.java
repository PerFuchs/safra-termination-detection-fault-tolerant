package ibis.ipl.apps.cell1d;

import java.io.IOException;

public interface  Barrier {
  public void await() throws InterruptedException, IOException;
}
