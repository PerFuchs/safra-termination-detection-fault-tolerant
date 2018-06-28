package ibis.ipl.apps.safraExperiment.ibisSignalling;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.Registry;

import java.io.IOException;
import java.util.List;

public class IbisSignal {

  public final String name;
  public final String module;

  public IbisSignal(String module, String name) {
    this.name = name;
    this.module = module;
  }

  public static void signal(Registry registry, List<IbisIdentifier> receivers,
                            IbisSignal signal) throws IOException {
    registry.signal(String.format("%s:%s", signal.module, signal.name),
        receivers.toArray(new IbisIdentifier[0]));
  }
}
