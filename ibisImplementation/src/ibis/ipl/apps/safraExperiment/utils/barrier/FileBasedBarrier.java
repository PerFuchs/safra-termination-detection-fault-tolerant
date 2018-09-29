package ibis.ipl.apps.safraExperiment.utils.barrier;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;

/**
 * Barrier based on writing files with unique name and common extension in a directory. Should only be used once
 * per name and folder pair. Does not remove files written by it.
 */
public class FileBasedBarrier implements Barrier, Runnable {

  private final String name;
  private final Path directory;
  private final int id;
  private final int size;
  private final Semaphore semaphore = new Semaphore(0);

  public FileBasedBarrier(String name, Path directory, int id, int size) {
    this.name = name;
    this.directory = directory;
    this.id = id;
    this.size = size;
  }

  @Override
  public void await() throws InterruptedException, IOException {
    Files.createFile(directory.resolve(String.format("%04d.%s.lock", id, name)));
    Thread t = new Thread(this);
    t.run();
    semaphore.acquire();
  }

  @Override
  public void run() {
    while(getNumberOfFilesWithExtentsion(directory, name) < size) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    semaphore.release();
  }

  private int getNumberOfFilesWithExtentsion(Path directory, final String extentsion) {
    File d = new File(directory.toString());
    File fs[] = d.listFiles(new FileFilter() {
      @Override
      public boolean accept(File file) {
        return file.getName().endsWith(String.format("%s.lock", extentsion));
      }
    });
    if (fs == null) {
      return 0;
    }
    return fs.length;
  }
}
