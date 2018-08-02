package ibis.ipl.apps.safraExperiment.experiment;

import java.io.IOException;
import java.nio.file.Path;

public class OfflineExperiment extends Experiment {

  public OfflineExperiment(Path analysisFolder, Path outputFolder, int nodeCount, boolean isFaultTolerant) {
   super(analysisFolder, outputFolder, nodeCount, isFaultTolerant);
  }
}
