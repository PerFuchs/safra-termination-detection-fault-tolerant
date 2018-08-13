package ibis.ipl.apps.safraExperiment.experiment;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Used to analyse the log events offline independent from the run.
 *
 * Different from the online logging system it cannot verify network topology related things as the Chandy Misra Result.
 */
public class OfflineExperiment extends Experiment {

  public OfflineExperiment(Path analysisFolder, Path outputFolder, int nodeCount, boolean isFaultTolerant, TerminationDefinitions terminationDefinition) {
   super(analysisFolder, outputFolder, nodeCount, isFaultTolerant, terminationDefinition);
  }
}
