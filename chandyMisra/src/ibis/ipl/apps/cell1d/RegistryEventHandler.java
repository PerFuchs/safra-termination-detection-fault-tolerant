package ibis.ipl.apps.cell1d;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.Registry;

import java.util.LinkedList;
import java.util.List;

public class RegistryEventHandler implements ibis.ipl.RegistryEventHandler {
  private List<IbisIdentifier> joinedIbises = new LinkedList<>();
  private Registry registry;

  public void setRegistry(Registry registry) {
    this.registry = registry;
  }

  public List<IbisIdentifier> getAllIbises() {
    if (registry == null) {
      throw new IllegalStateException("Set registry before calling getAllIbises.");
    }
    registry.waitUntilPoolClosed();
    return joinedIbises;
  }

  @Override
  public void joined(IbisIdentifier ibisIdentifier) {
    joinedIbises.add(ibisIdentifier);
  }

  @Override
  public void left(IbisIdentifier ibisIdentifier) {

  }

  @Override
  public void died(IbisIdentifier ibisIdentifier) {

  }

  @Override
  public void gotSignal(String s, IbisIdentifier ibisIdentifier) {

  }

  @Override
  public void electionResult(String s, IbisIdentifier ibisIdentifier) {

  }

  @Override
  public void poolClosed() {

  }

  @Override
  public void poolTerminated(IbisIdentifier ibisIdentifier) {

  }
}
