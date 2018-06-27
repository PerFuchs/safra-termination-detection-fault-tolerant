package ibis.ipl.apps.safraExperiment.utils.barrier;

import java.io.IOException;

public interface  Barrier {
  public void await() throws InterruptedException, IOException;
}
