package ibis.ipl.apps.safraExperiment.communication;

import ibis.ipl.Ibis;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.PortType;
import ibis.ipl.Registry;
import org.apache.log4j.Logger;

import java.util.Arrays;

public class IbisDetectionService {
  private final static Logger logger = Logger.getLogger(IbisDetectionService.class);

  private IbisIdentifier[] ibises;
  private Registry registry;
  private int me;

  public IbisDetectionService(IbisIdentifier myself, Registry registry) {
    this.registry = registry;
    this.ibises = new IbisIdentifier[registry.getPoolSize()];
    findAllIbises(myself);
  }

  private void findAllIbises(IbisIdentifier myself) {
    ibises = registry.joinedIbises();
    if (ibises.length != registry.getPoolSize()) {
      logger.error("Not all ibises reported by joinedIbises");
    }
    Arrays.sort(ibises);
    me = Arrays.asList(ibises).indexOf(myself);
  }

  public int getMe() {
    return me;
  }

  public IbisIdentifier[] getIbises() {
    return ibises;
  }
}
