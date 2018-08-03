import ibis.ipl.apps.safraExperiment.experiment.Experiment;
import ibis.ipl.apps.safraExperiment.experiment.OfflineExperiment;
import org.apache.log4j.BasicConfigurator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AnalysisOnly {
  private Path reanalysisFolder;

  public AnalysisOnly(Path repetitionFolder, int networkSize, boolean isFaultTolerant) throws IOException {
    this.reanalysisFolder = Paths.get(repetitionFolder.toString(), "reanalysis");

    setupReanalysisFolder();

    Experiment experiment = new OfflineExperiment(repetitionFolder, reanalysisFolder, networkSize, isFaultTolerant);
    experiment.writeSafraStatitistics();
  }

  public static void main(String[] args) throws IOException {
    BasicConfigurator.configure();

    File experimentFolder = new File(args[0]);

    for (File configurationFolder : experimentFolder.listFiles()) {
      if (configurationFolder.getName().endsWith(".run")) {
        for (File repititionFolder : configurationFolder.listFiles()) {
          AnalysisOnly analysis = new AnalysisOnly(Paths.get(repititionFolder.getAbsolutePath()), getNetworkSize(configurationFolder), getFaultTolerance(configurationFolder));
        }
      }
    }
  }

  private void setupReanalysisFolder() throws IOException {
    if (reanalysisFolder.toFile().exists()) {
      deleteFolder(reanalysisFolder.toFile());
    }
    Files.createDirectory(reanalysisFolder);
  }

  private static int getNetworkSize(File configurationFolder) {
    return Integer.valueOf(configurationFolder.getName().split("-")[0]);
  }

  private static boolean getFaultTolerance(File configurationFolder) {
    return !configurationFolder.getName().split("-")[1].equals("fs");
  }

  static void deleteFolder(File folder) {
    File[] files = folder.listFiles();
    if(files!=null) {
      for(File f: files) {
        if(f.isDirectory()) {
          deleteFolder(f);
        } else {
          f.delete();
        }
      }
    }
    folder.delete();
  }

}
