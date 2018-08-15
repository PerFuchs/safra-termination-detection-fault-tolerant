package ibis.ipl.apps.safraExperiment.experiment.chandyMisraVerification;

import ibis.ipl.apps.safraExperiment.network.ChandyMisraResult;
import ibis.ipl.apps.safraExperiment.network.Channel;
import ibis.ipl.apps.safraExperiment.network.Network;
import ibis.ipl.apps.safraExperiment.network.Tree;
import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.Set;

public class Verifier {
  private static final Logger logger = Logger.getLogger(Verifier.class);

  private Verifier() {

  }

  public static void check(Set<ChandyMisraResult> results, Network usedNetworkTopology, int root) throws IncorrectChannelUsedException, IncorrectTreeException, IncorrectWeightException {
    Network constructedNetwork = new Network(results);

    if (!usedNetworkTopology.isSuperNetworkOf(constructedNetwork)) {
      throw new IncorrectChannelUsedException();
    }

    checkTree(constructedNetwork, usedNetworkTopology, root);
    checkWeightCalculation(results, usedNetworkTopology, root);
  }

  private static void checkTree(Network constructedNetwork, Network expectedNetwork, int root) throws IncorrectTreeException {
    Tree expectedSinkTree = expectedNetwork.getSinkTree(root);
    Tree constructedSinkTree = constructedNetwork.getSinkTree(root);
    if (!expectedSinkTree.equals(constructedSinkTree)) {
      logger.error("Expected sinktree:");
      logger.error(expectedSinkTree.toString());
      logger.error("Actual sinktree:");
      logger.error(constructedSinkTree.toString());
      throw new IncorrectTreeException();
    }
  }

  private static void checkWeightCalculation(Set<ChandyMisraResult> results, Network expectedNetwork, int root) throws IncorrectWeightException {
    Tree expectedSinkTree = expectedNetwork.getSinkTree(root);
    for (ChandyMisraResult r : results) {
      if (r.dist != expectedSinkTree.getDistance(r.node)) {
        throw new IncorrectWeightException();
      }
    }
  }
}
