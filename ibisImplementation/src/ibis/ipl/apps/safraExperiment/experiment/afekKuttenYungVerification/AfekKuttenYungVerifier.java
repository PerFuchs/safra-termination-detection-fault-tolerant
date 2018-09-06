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
    Network constructedNetwork = Network.fromAfekKuttenYungResults(results);

    if (!usedNetworkTopology.isSuperNetworkOf(constructedNetwork)) {
      throw new IncorrectChannelUsedException();
    }

    int expectedRoot = getExpectedRoot(usedNetworkTopology);
    for (AfekKuttenYungResult r : results) {
      if (r.root != expectedRoot) {
        throw new IncorrectRootException();
      }
    }

    checkTree(constructedNetwork, usedNetworkTopology, expectedRoot);

    checkDistanceCalculation(results, usedNetworkTopology, expectedRoot);
  }

  private static int getExpectedRoot(Network expectedNetwork) {
    return Collections.max(expectedNetwork.getVertices());
  }

  private static void checkTree(Network constructedNetwork, Network expectedNetwork, int root) throws IncorrectTreeException {
   Tree expectedBFSTree = expectedNetwork.getBFSTree(root);
   Tree actualBFSTree = constructedNetwork.getBFSTree(root);

   if (!expectedBFSTree.hasEqualVerticesWith(actualBFSTree)) {
     throw new IncorrectTreeException();
   }

   if (!expectedBFSTree.hasEqualLevels(actualBFSTree)) {
     throw new IncorrectTreeException();
   }
  }

  private static void checkDistanceCalculation(List<AfekKuttenYungResult> results, Network expectedNetwork, int root) throws IncorrectDistanceException {
    Tree expectedSinkTree = expectedNetwork.getBFSTree(root);
    for (AfekKuttenYungResult r : results) {
      if (r.distance != expectedSinkTree.getDistance(r.node)) {
        throw new IncorrectDistanceException();
      }
    }
  }
}
