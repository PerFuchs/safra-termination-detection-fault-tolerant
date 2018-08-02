import ibis.ipl.apps.safraExperiment.experiment.Experiment;
import ibis.ipl.apps.safraExperiment.experiment.OfflineExperiment;
import org.apache.log4j.BasicConfigurator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AnalysisOnly {

  private static Path analysisFolder;
  private static Path reanalysisFolder;

  public static void main(String[] args) throws IOException {
    BasicConfigurator.configure();
    analysisFolder = Paths.get(args[0]);
    reanalysisFolder = Paths.get(analysisFolder.toString(), "reanalysis");

    setupReanalysisFolder();

    Experiment experiment = new OfflineExperiment(analysisFolder, reanalysisFolder, getNetworkSize(), getFaultTolerance());
    experiment.writeSafraStatitistics();
  }

  private static void setupReanalysisFolder() throws IOException {
    if (reanalysisFolder.toFile().exists()) {
      deleteFolder(reanalysisFolder.toFile());
    }
    Files.createDirectory(reanalysisFolder);
  }

  /**
   * Assumes the repetition to analyse in a folder as '<network-size>-<fault-percentage>-....run'
   */
  private static int getNetworkSize() {
    return Integer.valueOf(analysisFolder.getParent().getFileName().toString().split("-")[0]);
  }

  /**
   * Assumes the repetition to analyse in a folder as '<network-size>-<fault-percentage>-....run'
   */
  private static boolean getFaultTolerance() {
    return !analysisFolder.getParent().getFileName().toString().split("-")[1].equals("fs");
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
