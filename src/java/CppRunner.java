package processing.mode.cpp;
import java.io.*;
import processing.app.RunnerListener;
public class CppRunner {
  private final File           binary;
  private final File           sketchFolder;
  private final File           runtimeDir;
  private final RunnerListener listener;
  private       Process        process;
  public CppRunner(File binary, File sketchFolder, File runtimeDir, RunnerListener listener) {
    this.binary       = binary;
    this.sketchFolder = sketchFolder;
    this.runtimeDir   = runtimeDir;
    this.listener     = listener;
  }
  public void launch() {
    if (!binary.exists()) {
      listener.statusError("Binary not found: " + binary.getAbsolutePath());
      return;
    }
    binary.setExecutable(true);
    new Thread(() -> {
      try {
        ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath());
        pb.directory(sketchFolder);
        pb.redirectErrorStream(false);
        // Pass sketch folder so loadImage/loadStrings find data/ assets
        pb.environment().put("PROCESSING_SKETCH_NAME", sketchFolder.getName());
        pb.environment().put("PROCESSING_SKETCH_PATH",
            sketchFolder.getAbsolutePath().replace('\\', '/'));
        // Pass mode src/ dir so Processing.cpp can find fonts
        if (runtimeDir != null)
          // Pass the mode root (parent of src/) so fonts/ subdir is reachable
          pb.environment().put("PROCESSING_MODE_PATH",
              runtimeDir.getParentFile().getAbsolutePath().replace('\\', '/'));
        process = pb.start();
        Thread out = new Thread(() -> pipe(process.getInputStream(), System.out));
        Thread err = new Thread(() -> pipe(process.getErrorStream(), System.err));
        out.start();
        err.start();
        int code = process.waitFor();
        out.join();
        err.join();
        if (code != 0 && code != 137 && code != 143) listener.statusError("Sketch exited with code " + code);
        else           listener.statusNotice("Sketch finished.");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        listener.statusError("Could not launch: " + e.getMessage());
      }
    }).start();
  }
  public void stop() {
    if (process != null && process.isAlive())
      process.destroyForcibly();
  }
  private void pipe(InputStream in, PrintStream out) {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
      String line;
      while ((line = br.readLine()) != null)
        out.println(line);
    } catch (IOException ignored) {}
  }
}
