package ibis.ipl.apps.safraExperiment.experiment.afekKuttenYungVerification;

import ibis.ipl.apps.safraExperiment.experiment.IncorrectChannelUsedException;
import ibis.ipl.apps.safraExperiment.experiment.IncorrectTreeException;
import ibis.ipl.apps.safraExperiment.experiment.chandyMisraVerification.IncorrectDistanceException;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.network.Tree;
import org.apache.log4j.Logger;

import java.util.*;

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
    logger.trace(String.format("expected root is %04d", expectedRoot));

    // Check connectedness. All nodes reachable from root in the expected network have to be connected to root in the constructed network.
    Network constructedConnectedNetwork = constructedNetwork.getConnectedSubnetwork(expectedRoot);
    Network usedConnectedNetwork = usedNetworkTopology.getConnectedSubnetwork(expectedRoot);

    if (!constructedConnectedNetwork.hasEqualNodes(usedConnectedNetwork)) {
      throw new IncorrectTreeException();
    }
    logger.trace("Network contains expected connected nodes");

    List<AfekKuttenYungResult> connectedResults = filterUnconnectedResults(constructedConnectedNetwork, results);

    checkRootComputations(connectedResults, expectedRoot);

    checkCycleFree(constructedConnectedNetwork, expectedRoot);
    logger.trace("Network is tree");

    checkDistanceCalculation(connectedResults, constructedNetwork, expectedRoot);
    logger.trace("Correct distance calculated");
  }

  private static int getExpectedRoot(Network expectedNetwork) {
    return Collections.max(expectedNetwork.getVertices());
  }

  private static List<AfekKuttenYungResult> filterUnconnectedResults(Network constructedConnectedNetwork, List<AfekKuttenYungResult> results) {
    List<AfekKuttenYungResult> connectedResults = new LinkedList<>();
    for (AfekKuttenYungResult r : results) {
      if (constructedConnectedNetwork.hasNode(r.node)) {
        connectedResults.add(r);
      }
    }
    return connectedResults;
  }

  private static void checkRootComputations(List<AfekKuttenYungResult> results, int expectedRoot) throws IncorrectRootException {
    for (AfekKuttenYungResult r : results) {
      if (r.root != expectedRoot) {
        throw new IncorrectRootException();
      }
    }
    logger.trace("Correct root computed");
  }

  private static void checkCycleFree(Network constructedConnectedNetwork, int root) throws IncorrectTreeException {
    if (constructedConnectedNetwork.hasCycle(root)) {
      throw new IncorrectTreeException();
    }
    logger.trace("Tree has no cycle");
  }

  private static void checkDistanceCalculation(List<AfekKuttenYungResult> results, Network constructedNetwork, int root) throws IncorrectDistanceException {
    Tree tree = constructedNetwork.getSinkTree(root);  // I know the network is a tree. Hence, every tree building algorithm builds the same tree
    for (AfekKuttenYungResult r : results) {
      if (tree.hasNode(r.node) && r.distance != tree.getLevel(r.node)) {
        logger.debug(String.format("%04d has invalid weight %d but should be %d", r.node, r.distance, tree.getLevel(r.node)));
        logger.debug(tree.toString());
        throw new IncorrectDistanceException();
      }
    }
  }
}
