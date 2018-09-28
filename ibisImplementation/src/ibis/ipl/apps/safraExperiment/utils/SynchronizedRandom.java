package ibis.ipl.apps.safraExperiment.utils;


import ibis.ipl.IbisIdentifier;
import ibis.ipl.Registry;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Wrapper around util.Random to ensure the same seed is used on every node.
 *
 * Uses elections to generate a seed out of the hashes of the identifiers of 5 elections winners.
 * This is random as long as different nodes reach the elections first - first to contact a server for an election wins.
 *
 * Always initializes with the same seed as the first SynchronizedRandom created.
 */
public class SynchronizedRandom {
  private final static Logger logger = Logger.getLogger(SynchronizedRandom.class);

  private int seed = 0;
  private final Random random;

  public  SynchronizedRandom(IbisIdentifier me, Registry registry) throws IOException {
    int seedElections = Math.min(5, registry.getPoolSize());
    int completedElection = 0;
    List<IbisIdentifier> seedIdentifier = new LinkedList<>();

    for (int i = 0; i < seedElections; i++) {
      IbisIdentifier winner = registry.elect(Integer.toString(i));
      seed += winner.hashCode();
      completedElection++;
      seedIdentifier.add(winner);

      if (winner.equals(me)) {
        break;
      }
    }

    for (int i = completedElection; i < seedElections; i++) {
      IbisIdentifier id =registry.getElectionResult(Integer.toString(i));
      seedIdentifier.add(id);
      seed += id.hashCode();
    }

    StringBuilder sb = new StringBuilder();
    for (IbisIdentifier id : seedIdentifier) {
      sb.append(id.toString());
      sb.append(", ");
    }
    logger.debug("Seed identifier: " + sb.toString() + "Seed: " + seed);

    random = new Random(seed);
  }


  public int getInt() {
    return random.nextInt();
  }

  public Integer getInt(int max) {
    return random.nextInt(max);
  }

  public Integer getSeed() {
    return seed;
  }
}
