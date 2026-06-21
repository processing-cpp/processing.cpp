package processing.mode.cpp;
import java.io.File;
import processing.app.Base;
import processing.app.Mode;
import processing.app.RunnerListener;
import processing.app.Sketch;
import processing.app.ui.EditorException;
import processing.app.ui.EditorState;
public class CppMode extends Mode {
  public CppMode(Base base, File folder) {
    super(base, folder);
    // Precompile Processing.cpp in background so first Run is fast
    new Thread(() -> warmupCache(), "cppmode-warmup").start();
  }

  private void warmupCache() {
    try {
      File runtimeDir = getRuntimeDir();
      String cacheSubDir = System.getProperty("os.name","").toLowerCase().contains("win")
        ? "cache/windows-x64" : "cache/linux-x64";
      File cacheDir = new File(getRuntimeDir().getParentFile(), cacheSubDir);
      cacheDir.mkdirs();
      File processingO   = new File(cacheDir, "Processing.o");
      File processingCpp = new File(runtimeDir, "Processing.cpp");
      File processingH   = new File(runtimeDir, "Processing.h");

      boolean needsRebuild = !processingO.exists()
        || processingCpp.lastModified() > processingO.lastModified()
        || (processingH.exists() && processingH.lastModified() > processingO.lastModified());

      if (!needsRebuild) return;

      // Find g++ 
      String gpp = "g++";
      String os = System.getProperty("os.name","").toLowerCase();
      if (os.contains("win")) {
        String[] msys = {
          "C:\\msys64\\mingw64\\bin\\g++.exe",
          "C:\\msys2\\mingw64\\bin\\g++.exe"
        };
        for (String p : msys) if (new File(p).exists()) { gpp = p; break; }
      }

      java.util.List<String> cmd = new java.util.ArrayList<>();
      cmd.add(gpp);
      cmd.add("-std=c++17"); cmd.add("-O2");
      cmd.add("-I" + runtimeDir.getAbsolutePath());
      cmd.add("-DPROCESSING_HAS_STB_IMAGE");
      cmd.add("-c"); cmd.add(processingCpp.getAbsolutePath());
      cmd.add("-o"); cmd.add(processingO.getAbsolutePath());

      new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor();

      // Also precompile Processing_defaults.cpp
      File defaultsO   = new File(cacheDir, "Processing_defaults.o");
      File defaultsCpp = new File(runtimeDir, "Processing_defaults.cpp");
      if (!defaultsO.exists() || defaultsCpp.lastModified() > defaultsO.lastModified()) {
        java.util.List<String> cmd2 = new java.util.ArrayList<>();
        cmd2.add(gpp);
        cmd2.add("-std=c++17"); cmd2.add("-O2");
        cmd2.add("-I" + runtimeDir.getAbsolutePath());
        cmd2.add("-DPROCESSING_HAS_STB_IMAGE");
        cmd2.add("-c"); cmd2.add(defaultsCpp.getAbsolutePath());
        cmd2.add("-o"); cmd2.add(defaultsO.getAbsolutePath());
        new ProcessBuilder(cmd2).redirectErrorStream(true).start().waitFor();
      }
    } catch (Exception ignored) {}
  }
  @Override public String getTitle()            { return "C++"; }
  @Override public String getDefaultExtension() { return "pde"; }
  @Override public String[] getExtensions()     { return new String[] { "pde", "cpp" }; }
  @Override public String[] getIgnorable()      { return new String[] { "pde~", "cpp~" }; }
  @Override
  public CppEditor createEditor(Base base, String path, EditorState state)
      throws EditorException {
    return new CppEditor(base, path, state, this);
  }
  @Override
  public File getTemplateFolder() {
    return new File(folder, "template");
  }
  public File getRuntimeDir() {
    return new File(folder, "src");
  }
  public CppRunner handleLaunch(Sketch sketch, RunnerListener listener)
      throws Exception {
    CppBuild build  = new CppBuild(sketch, this);
    File     binary = build.compile(listener);
    if (binary == null) return null;
    File sketchFolder = sketch.getFolder();
    File runtimeDir   = getRuntimeDir();
    CppRunner runner = new CppRunner(binary, sketchFolder, runtimeDir, listener);
    runner.launch();
    return runner;
  }
  public void handleStop(CppRunner runner) {
    if (runner != null) runner.stop();
  }
}
