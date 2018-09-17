package ibis.ipl.apps.safraExperiment.experiment.afekKuttenYungVerification;

import ibis.ipl.apps.safraExperiment.experiment.IncorrectChannelUsedException;
import ibis.ipl.apps.safraExperiment.experiment.IncorrectTreeException;
import ibis.ipl.apps.safraExperiment.experiment.chandyMisraVerification.IncorrectDistanceException;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.network.Tree;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.List;

public class AfekKuttenYungVerifier {
  private static final Logger logger = Logger.getLogger(AfekKuttenYungVerifier.class);

  private AfekKuttenYungVerifier() {

  }

  public static void check(List<AfekKuttenYungResult> results, Network usedNetworkTopology) throws IncorrectChannelUsedException, IncorrectTreeException, IncorrectRootException, IncorrectDistanceException {
    logger.trace("Starting verification");
    Network constructedNetwork = Network.fromAfekKuttenYungResults(results);

    if (!usedNetworkTopology.isSuperNetworkOf(constructedNetwork)) {
      throw new IncorrectChannelUsedException();
    }
    logger.trace("Correct channels used");

    int expectedRoot = getExpectedRoot(usedNetworkTopology);
    for (AfekKuttenYungResult r : results) {
      if (r.root != expectedRoot) {
        throw new IncorrectRootException();
      }
    }
    logger.trace("Correct root computed");

    checkIsTree(constructedNetwork, usedNetworkTopology, expectedRoot);
    logger.trace("Correct tree computed");

    checkDistanceCalculation(results, usedNetworkTopology, expectedRoot);
    logger.trace("Correct distance calculated");
  }

  private static int getExpectedRoot(Network expectedNetwork) {
    return Collections.max(expectedNetwork.getVertices());
  }

  private static void checkIsTree(Network constructedNetwork, Network expectedNetwork, int root) throws IncorrectTreeException {
    // Check connectedness. All nodes reachable from root in the expected network have to be connected to root in the constructed network.
    Network constructedConnectedNetwork = constructedNetwork.filterUnconnectedNodes(root);
    Network expectedConnectedNetwork = expectedNetwork.filterUnconnectedNodes(root);

    if (!constructedConnectedNetwork.hasEqualNodes(expectedConnectedNetwork)) {
      throw new IncorrectTreeException();
    }
    logger.trace("Vertices are equal");

    if (constructedConnectedNetwork.hasCycle(root)) {
      throw new IncorrectTreeException();
    }
    logger.trace("Tree has not cycle");
  }

  private static void checkDistanceCalculation(List<AfekKuttenYungResult> results, Network constructedNetwork, int root) throws IncorrectDistanceException {
    Tree tree = constructedNetwork.getBFSTree(root);  // I already checked if it is a tree. Therefore, any tree building algorithm builds the same tree.
    for (AfekKuttenYungResult r : results) {
      if (r.distance != tree.getLevel(r.node)) {
        throw new IncorrectDistanceException();
      }
    }
  }
}
