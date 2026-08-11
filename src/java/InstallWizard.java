package processing.mode.cpp;

import java.awt.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

import processing.app.RunnerListener;

/**
 * A guided, step-by-step installer for the C++ toolchain (g++) and the
 * OpenGL libraries (GLFW, GLEW) that CppMode sketches need to compile and
 * run, for Windows and macOS.
 *
 * This is the *primary* path: CppBuild calls {@link #run(RunnerListener)}
 * first when it detects a missing compiler or library. If the wizard is
 * cancelled, or fails, or the platform isn't Windows/macOS, CppBuild falls
 * back to its existing detection/dialog logic unchanged — this class never
 * replaces that fallback, only tries to resolve things more smoothly first.
 *
 * Windows: detects/installs MSYS2 + mingw-w64 g++/GLFW/GLEW via pacman.
 * macOS:   detects/installs Xcode Command Line Tools (g++/clang++), then
 *          GLFW/GLEW via Homebrew if Homebrew is already present.
 */
public class InstallWizard {

  /**
   * Thrown when the user explicitly cancels the wizard (at the initial
   * missing-dependencies prompt, or mid-install). Callers should treat
   * this as "stop entirely" rather than falling back to other dialogs —
   * the user made a deliberate choice not to proceed.
   */
  public static class CancelledByUser extends Exception {
    public CancelledByUser() { super("Installation cancelled by user."); }
  }

  /**
   * Checks what's missing and, if anything is, shows a plain error dialog
   * first ("Missing: g++, glfw" + Install/Cancel). Only if the user clicks
   * Install does the full progress wizard open and start downloading.
   * Must be called from a background thread, not the EDT.
   *
   * @return true if everything needed is confirmed available by the time
   *         this returns; false if the user cancelled or installation
   *         could not be completed.
   */
  public static boolean run(RunnerListener listener) throws CancelledByUser {
    String os = System.getProperty("os.name").toLowerCase();
    InstallWizard w = new InstallWizard();

    boolean isWin = os.contains("win");
    boolean isMac = os.contains("mac");
    boolean isLinux = os.contains("linux") || os.contains("nix") || os.contains("nux");

    java.util.List<String> missing;
    if (isWin) {
      missing = w.detectWindowsMissing();
    } else if (isMac) {
      missing = w.detectMacMissing();
    } else if (isLinux) {
      missing = w.detectLinuxMissing();
    } else {
      // Unrecognized OS — we don't know how to install anything here, so
      // just tell the user exactly what's needed and let them sort it out.
      // This isn't a cancellation, so it returns false rather than
      // throwing — CppBuild's caller can still decide what to do next.
      java.util.List<String> generic = new java.util.ArrayList<>();
      generic.add("g++ (a C++17-capable compiler)");
      generic.add("glfw");
      generic.add("glew");
      SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
        "Couldn't recognize this operating system (" + System.getProperty("os.name") + "),\n"
          + "so C++ Mode can't install anything automatically here.\n\n"
          + "Missing: " + String.join(", ", generic) + "\n\n"
          + "Please install these manually using your system's package manager.",
        "C++ Mode — Missing Dependencies",
        JOptionPane.WARNING_MESSAGE));
      return false;
    }

    if (missing.isEmpty()) return true;

    final boolean[] proceed = { false };
    final boolean[] answered = { false };
    final Object lock = new Object();
    SwingUtilities.invokeLater(() -> {
      Object[] options = { "Install", "Cancel" };
      String missingMsg = isWin
        ? "C++ Mode needs a C++ compiler (g++) to compile sketches.\n\n"
          + "Click Install to download g++ automatically (~130 MB).\n"
        : "Missing: " + String.join(", ", missing) + "\n\n"
          + "C++ Mode needs these to compile and run sketches.";
      int choice = JOptionPane.showOptionDialog(null,
        missingMsg,
        "C++ Mode — Missing Dependencies",
        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
        null, options, options[0]);
      synchronized (lock) {
        proceed[0] = (choice == 0); // index of "Install"
        answered[0] = true;
        lock.notifyAll();
      }
    });
    synchronized (lock) {
      while (!answered[0]) {
        try { lock.wait(); } catch (InterruptedException ignored) {}
      }
    }

    if (!proceed[0]) throw new CancelledByUser();

    // User confirmed — now open the real progress wizard and install.
    // A cancel from inside the wizard itself also surfaces as
    // CancelledByUser (checked via w.cancelled right after each call),
    // distinguishing "user backed out" from "install genuinely failed".
    boolean result;
    if (isWin) {
      result = w.runWindows(missing);
    } else if (isMac) {
      result = w.runMac(missing);
    } else {
      result = w.runLinux(missing);
    }
    if (!result && w.cancelled.get()) throw new CancelledByUser();
    return result;
  }

  // ── Shared dialog plumbing ────────────────────────────────────────────

  private JDialog dialog;
  private JTextArea log;
  private JLabel stepLabel;
  private JProgressBar progressBar;
  private JButton cancelButton;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicBoolean succeeded = new AtomicBoolean(false);
  private final Object doneLock = new Object();
  private boolean finished = false;

  private void buildDialog(String title) {
    dialog = new JDialog((Frame) null, title, true);
    dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    dialog.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override public void windowClosing(java.awt.event.WindowEvent e) {
        requestCancel();
      }
    });

    JPanel root = new JPanel(new BorderLayout(10, 10));
    root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

    stepLabel = new JLabel("Preparing...");
    stepLabel.setFont(stepLabel.getFont().deriveFont(Font.BOLD, 13f));
    root.add(stepLabel, BorderLayout.NORTH);

    progressBar = new JProgressBar(0, 100);
    progressBar.setStringPainted(true);
    progressBar.setString("");
    progressBar.setValue(0);
    root.add(progressBar, BorderLayout.CENTER);

    log = new JTextArea(10, 56);
    log.setEditable(false);
    log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    JScrollPane scroll = new JScrollPane(log);
    root.add(scroll, BorderLayout.SOUTH);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(e -> requestCancel());
    buttons.add(cancelButton);
    root.add(buttons, BorderLayout.SOUTH);

    dialog.setContentPane(root);
    dialog.pack();
    dialog.setLocationRelativeTo(null);
  }

  private void setStep(String text) {
    SwingUtilities.invokeLater(() -> stepLabel.setText(text));
  }

  private void appendLog(String text) {
    SwingUtilities.invokeLater(() -> {
      log.append(text);
      if (!text.endsWith("\n")) log.append("\n");
      log.setCaretPosition(log.getDocument().getLength());
    });
  }

  private void setProgress(int pct) {
    SwingUtilities.invokeLater(() -> {
      progressBar.setValue(pct);
      progressBar.setString(pct + "%");
      if (pct >= 100) progressBar.setString("Done");
    });
  }

  private void setIndeterminate(boolean b) {
    SwingUtilities.invokeLater(() -> {
      progressBar.setIndeterminate(b);
      if (b) progressBar.setString("Working...");
    });
  }

  private void requestCancel() {
    cancelled.set(true);
    finishDialog(false, "Cancelled.");
  }

  private void finishDialog(boolean success, String finalMessage) {
    succeeded.set(success);
    SwingUtilities.invokeLater(() -> {
      if (finalMessage != null) setStep(finalMessage);
      progressBar.setIndeterminate(false);
      progressBar.setValue(success ? 100 : progressBar.getValue());
      progressBar.setString(success ? "Done" : "Failed");
      cancelButton.setText("Close");
    });
    synchronized (doneLock) {
      finished = true;
      doneLock.notifyAll();
    }
  }

  /** Waits for the wizard to finish (closed, cancelled, or completed). */
  private boolean waitForCompletion() {
    synchronized (doneLock) {
      while (!finished) {
        try { doneLock.wait(); } catch (InterruptedException ignored) {}
      }
    }
    return succeeded.get();
  }

  private void showDialogAndWaitForClose() {
    // dialog.setVisible(true) blocks on the EDT (it's modal), so we show
    // it from the EDT and let the worker thread (already running) drive
    // progress via appendLog/setStep/finishDialog. Once finishDialog has
    // run, we dispose the dialog so setVisible(true) returns.
    SwingUtilities.invokeLater(() -> dialog.setVisible(true));
    synchronized (doneLock) {
      while (!finished) {
        try { doneLock.wait(); } catch (InterruptedException ignored) {}
      }
    }
    SwingUtilities.invokeLater(() -> dialog.dispose());
  }

  private boolean commandExists(String... cmd) {
    try {
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      // On Apple Silicon, Homebrew lives in /opt/homebrew/bin which may
      // not be in the PATH when Processing is launched from the GUI.
      String path = System.getenv("PATH");
      if (path != null && !path.contains("/opt/homebrew/bin"))
        pb.environment().put("PATH", "/opt/homebrew/bin:/usr/local/bin:" + path);
      Process p = pb.start();
      try (InputStream is = p.getInputStream()) { is.readAllBytes(); }
      return p.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  // ── Windows ────────────────────────────────────────────────────────────

  /** Pure detection, no dialog — used by run() before showing anything. */
  private static final String WINLIBS_URL =
    "https://github.com/brechtsanders/winlibs_mingw/releases/download/" +
    "14.2.0posix-19.1.1-12.0.0-ucrt-r2/" +
    "winlibs-x86_64-posix-seh-gcc-14.2.0-mingw-w64ucrt-12.0.0-r2.zip";

  public static File getPortableGccDir() {
    return new File(System.getenv("APPDATA") + "\\CppMode\\gcc\\mingw64\\bin");
  }

  public static File getPortableGpp() {
    return new File(getPortableGccDir(), "g++.exe");
  }

  private boolean installPortableGcc() {
    File gccDir = new File(System.getenv("APPDATA") + "\\CppMode\\gcc");
    gccDir.mkdirs();
    File zipFile = new File(gccDir, "winlibs-gcc.zip");
    try {
      setStep("Downloading portable g++ (WinLibs GCC 14.2)...");
      appendLog("Source: " + WINLIBS_URL);
      appendLog("Destination: " + gccDir.getAbsolutePath());
      appendLog("Size: ~130 MB — please wait...");
      final long WINLIBS_SIZE = 136 * 1024 * 1024L; // ~130 MB
      setProgress(0);
      try (java.io.InputStream in = new java.net.URI(WINLIBS_URL).toURL().openStream();
           java.io.OutputStream out = new java.io.FileOutputStream(zipFile)) {
        byte[] buf = new byte[64 * 1024];
        long total = 0; int n;
        while ((n = in.read(buf)) != -1) {
          if (cancelled.get()) return false;
          out.write(buf, 0, n);
          total += n;
          int pct = (int) Math.min(99, total * 100 / WINLIBS_SIZE);
          setProgress(pct);
          if (total % (10 * 1024 * 1024) < buf.length)
            appendLog("Downloaded " + (total / (1024 * 1024)) + " MB...");
        }
      }
      setProgress(100);
      setStep("Extracting gcc...");
      appendLog("Extracting to " + gccDir.getAbsolutePath());
      // Count entries first for accurate progress
      int totalEntries = 0;
      try (java.util.zip.ZipInputStream zcount =
               new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
        while (zcount.getNextEntry() != null) totalEntries++;
      }
      appendLog("Extracting " + totalEntries + " files...");
      int extracted = 0;
      try (java.util.zip.ZipInputStream zis =
               new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
        java.util.zip.ZipEntry entry;
        byte[] buf = new byte[64 * 1024];
        while ((entry = zis.getNextEntry()) != null) {
          if (cancelled.get()) return false;
          java.io.File outFile = new java.io.File(gccDir, entry.getName());
          if (entry.isDirectory()) {
            outFile.mkdirs();
          } else {
            outFile.getParentFile().mkdirs();
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
              int n;
              while ((n = zis.read(buf)) != -1) fos.write(buf, 0, n);
            }
          }
          extracted++;
          int pct = totalEntries > 0 ? (extracted * 100 / totalEntries) : 0;
          setProgress(pct);
          if (extracted % 500 == 0)
            appendLog("Extracted " + extracted + " / " + totalEntries + " files...");
        }
      }
      zipFile.delete();
      setProgress(100);
      int code = 0; // Java extraction always succeeds or throws
      if (!getPortableGpp().exists()) {
        appendLog("g++.exe not found after extraction -- unexpected zip structure.");
        return false;
      }
      appendLog("Portable g++ installed: " + getPortableGpp().getAbsolutePath());
      return true;
    } catch (Exception e) {
      appendLog("Error: " + e.getMessage());
      return false;
    }
  }

  private java.util.List<String> detectWindowsMissing() {
    // Check for g++ at all known locations including portable WinLibs install.
    boolean haveGpp = commandExists("g++", "--version");
    if (!haveGpp) {
      for (String p : new String[] {
          "C:\\msys64\\mingw64\\bin\\g++.exe",
          "C:\\msys2\\mingw64\\bin\\g++.exe",
          System.getProperty("user.home") + "\\msys64\\mingw64\\bin\\g++.exe",
          getPortableGpp().getAbsolutePath()}) {
        if (new File(p).exists()) { haveGpp = true; break; }
      }
    }
    java.util.List<String> missing = new java.util.ArrayList<>();
    if (!haveGpp) missing.add("g++");
    return missing;
  }

  /**
   * Opens the progress wizard and installs whatever's in `missing`. Only
   * called after the user has already confirmed via the plain error
   * dialog in run() — no further confirmation happens here.
   */
  private boolean runWindows(java.util.List<String> missing) {
    buildDialog("C++ Mode Setup: Windows");
    String pacman = findWindowsPacman();

    Thread worker = new Thread(() -> {
      setStep("Installing: " + String.join(", ", missing));
      boolean ok = installWindowsToolchain(pacman);
      if (cancelled.get()) return;

      if (ok) {
        finishDialog(true, "Setup complete! Please restart Processing to finish.");
      } else {
        finishDialog(false, "Install didn't finish — see the log above.");
      }
    });
    worker.setDaemon(true);
    worker.start();

    showDialogAndWaitForClose();
    return succeeded.get();
  }

  private String findWindowsPacman() {
    for (String p : new String[] {
        "C:\\msys64\\usr\\bin\\pacman.exe",
        "C:\\msys2\\usr\\bin\\pacman.exe",
        System.getProperty("user.home") + "\\msys64\\usr\\bin\\pacman.exe"}) {
      if (new File(p).exists()) return p;
    }
    return null;
  }

  /** Returns {glfwPresent, glewPresent}. */
  private boolean[] windowsLibsPresentDetailed() {
    boolean glfw = false, glew = false;
    for (String dir : new String[] {"C:\\msys64\\mingw64\\bin", "C:\\msys2\\mingw64\\bin"}) {
      if (new File(dir, "glfw3.dll").exists()) glfw = true;
      if (new File(dir, "glew32.dll").exists()) glew = true;
    }
    return new boolean[] { glfw, glew };
  }

  /**
   * Downloads MSYS2 if needed, then installs g++/GLFW/GLEW via pacman,
   * streaming progress into the wizard's log. Runs entirely on the
   * calling (background) thread — no PowerShell window is spawned, so
   * progress is visible directly in the wizard instead of a separate
   * console.
   */
  private boolean installWindowsToolchain(String existingPacman) {
    try {
      // Prefer portable gcc (no MSYS2 needed) unless MSYS2 already installed.
      if (existingPacman == null && !getPortableGpp().exists()) {
        if (!installPortableGcc()) return false;
        if (cancelled.get()) return false;
        // Portable gcc bundles everything -- no pacman step needed.
        return true;
      }
      String pacman = existingPacman;
      if (pacman == null) {
        File installer = File.createTempFile("msys2-installer", ".exe");
        installer.deleteOnExit();
        String url =
          "https://github.com/msys2/msys2-installer/releases/download/nightly-x86_64/msys2-x86_64-latest.exe";
        try (InputStream in = new java.net.URI(url).toURL().openStream();
             OutputStream out = new FileOutputStream(installer)) {
          byte[] buf = new byte[64 * 1024];
          long total = 0;
          int n;
          while ((n = in.read(buf)) != -1) {
            if (cancelled.get()) return false;
            out.write(buf, 0, n);
            total += n;
            if (total % (1024 * 1024) < buf.length) {
              appendLog("Downloaded " + (total / (1024 * 1024)) + " MB...");
            }
          }
        }
        Process installProc = new ProcessBuilder(
            installer.getAbsolutePath(), "install",
            "--confirm-command", "--accept-messages", "--root", "C:/msys64")
          .redirectErrorStream(true).start();
        streamToLog(installProc);
        installProc.waitFor();
        pacman = "C:\\msys64\\usr\\bin\\pacman.exe";
        if (!new File(pacman).exists()) {
          appendLog("MSYS2 installation did not complete as expected.");
          return false;
        }
      }

      if (cancelled.get()) return false;
      Process pacProc = new ProcessBuilder(
          pacman, "-S", "--noconfirm", "--needed", "--overwrite=*",
          "mingw-w64-x86_64-gcc", "mingw-w64-x86_64-glfw", "mingw-w64-x86_64-glew")
        .redirectErrorStream(true).start();
      streamToLog(pacProc);
      int code = pacProc.waitFor();
      if (code != 0) {
        appendLog("pacman exited with code " + code);
        return false;
      }

      try {
        ProcessBuilder setxPb = new ProcessBuilder(
          "setx", "PATH", "C:\\msys64\\mingw64\\bin;%PATH%");
        setxPb.redirectErrorStream(true);
        Process setx = setxPb.start();
        streamToLog(setx);
        setx.waitFor();
      } catch (Exception e) {
        appendLog("Could not update PATH automatically: " + e.getMessage());
        appendLog("You may need to add C:\\msys64\\mingw64\\bin to PATH manually.");
      }

      return true;
    } catch (Exception e) {
      appendLog("Error: " + e.getMessage());
      return false;
    }
  }

  // ── macOS ──────────────────────────────────────────────────────────────

  /** Pure detection, no dialog — used by run() before showing anything. */
  private java.util.List<String> detectMacMissing() {
    boolean haveCompiler = commandExists("xcrun", "-find", "g++")
                        || commandExists("g++", "--version");
    // Check headers (needed at compile time) not just dylibs (needed at link time).
    // Check bundled dylibs first (shipped with the mode, no Homebrew needed)
    String macArch = System.getProperty("os.arch","").contains("aarch64") ? "arm64" : "x64";
    String home = System.getProperty("user.home");
    File bundledLibs = new File(home + "/Library/Application Support/Processing/modes/CppMode/libs/macos");
    boolean glfwOk = new File(bundledLibs, "libglfw.3.dylib").exists()
                  || new File("/opt/homebrew/include/GLFW/glfw3.h").exists()
                  || new File("/usr/local/include/GLFW/glfw3.h").exists()
                  || new File("/opt/homebrew/lib/libglfw.dylib").exists()
                  || new File("/usr/local/lib/libglfw.dylib").exists()
                  || commandExists("pkg-config", "--exists", "glfw3");
    boolean glewOk = new File(bundledLibs, "libGLEW.dylib").exists()
                  || new File("/opt/homebrew/include/GL/glew.h").exists()
                  || new File("/usr/local/include/GL/glew.h").exists()
                  || new File("/opt/homebrew/lib/libGLEW.dylib").exists()
                  || new File("/usr/local/lib/libGLEW.dylib").exists()
                  || commandExists("pkg-config", "--exists", "glew");

    java.util.List<String> missing = new java.util.ArrayList<>();
    if (!haveCompiler) missing.add("g++ (Xcode Command Line Tools)");
    if (!glfwOk) missing.add("glfw");
    if (!glewOk) missing.add("glew");
    return missing;
  }

  /**
   * Opens the progress wizard and installs whatever's in `missing`. Only
   * called after the user has already confirmed via the plain error
   * dialog in run() — no further confirmation happens here.
   */
  private boolean runMac(java.util.List<String> missing) {
    buildDialog("C++ Mode Setup: macOS");
    boolean needsCompiler = missing.contains("g++ (Xcode Command Line Tools)");
    java.util.List<String> missingLibs = new java.util.ArrayList<>();
    if (missing.contains("glfw")) missingLibs.add("glfw");
    if (missing.contains("glew")) missingLibs.add("glew");

    Thread worker = new Thread(() -> {
      String brewExe = null;
      if (new File("/opt/homebrew/bin/brew").exists()) brewExe = "/opt/homebrew/bin/brew";
      else if (new File("/usr/local/bin/brew").exists()) brewExe = "/usr/local/bin/brew";
      else if (commandExists("brew", "--version")) brewExe = "brew";

      // ── Step 1: compiler ────────────────────────────────────────────────
      if (needsCompiler) {
        setStep("Installing: Xcode Command Line Tools");
        appendLog("Opening the Xcode Command Line Tools installer...");
        appendLog("Please click Install in the popup window, then wait here.");
        try { new ProcessBuilder("xcode-select", "--install").start(); }
        catch (Exception e) { finishDialog(false, "Couldn't launch Xcode CLT installer."); return; }
        boolean installed = false;
        for (int i = 0; i < 180; i++) {
          if (cancelled.get()) return;
          try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
          appendLog("Waiting for installation... (" + ((i+1)*5) + "s)");
          if (commandExists("xcrun", "-find", "g++")) { installed = true; break; }
        }
        if (!installed) { finishDialog(false, "Xcode CLT not detected — finish the install and try again."); return; }
      }
      if (cancelled.get()) return;
      if (missingLibs.isEmpty()) { finishDialog(true, "Setup complete."); return; }

      // ── Step 2: GLFW / GLEW ─────────────────────────────────────────────
      if (brewExe == null) {
        // Still no Homebrew -- install it now.
        setStep("Installing Homebrew...");
        appendLog("Homebrew not found — installing it now (you may be asked for your password).");
        try {
          String script = "/bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"";
          ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", script);
          pb.redirectErrorStream(true);
          pb.environment().put("NONINTERACTIVE", "1");
          Process proc = pb.start();
          streamToLog(proc);
          int code = proc.waitFor();
          if (cancelled.get()) return;
          if (code != 0) { finishDialog(false, "Homebrew installation failed — see log above."); return; }
          if (new File("/opt/homebrew/bin/brew").exists()) brewExe = "/opt/homebrew/bin/brew";
          else if (new File("/usr/local/bin/brew").exists()) brewExe = "/usr/local/bin/brew";
          if (brewExe == null) {
            finishDialog(false, "Homebrew installed but not found — restart Processing and try again.");
            return;
          }
        } catch (Exception e) {
          finishDialog(false, "Couldn't install Homebrew: " + e.getMessage()); return;
        }
      }

      setStep("Installing: " + String.join(", ", missingLibs));
      setIndeterminate(true);
      try {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(brewExe); cmd.add("install"); cmd.addAll(missingLibs);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        streamToLog(proc);
        int code = proc.waitFor();
        if (cancelled.get()) return;
        if (code == 0) {
          finishDialog(true, "Setup complete! Restart Processing if needed.");
        } else {
          finishDialog(false, "Homebrew install exited with code " + code + " — see log above.");
        }
      } catch (Exception e) {
        finishDialog(false, "Couldn't run Homebrew: " + e.getMessage());
      }
    });
    worker.setDaemon(true);
    worker.start();
    showDialogAndWaitForClose();
    return succeeded.get();
  }


  // ── Linux ──────────────────────────────────────────────────────────────

  // Package manager -> install command, matching CppBuild's existing
  // mapping exactly so the wizard and the fallback dialog never disagree
  // about what gets installed.
  private static final java.util.LinkedHashMap<String, String[]> LINUX_MANAGERS = new java.util.LinkedHashMap<>();
  static {
    LINUX_MANAGERS.put("apt-get", new String[]{"apt-get","install","-y","g++","libglfw3-dev","libglew-dev"});
    LINUX_MANAGERS.put("apt",     new String[]{"apt","install","-y","g++","libglfw3-dev","libglew-dev"});
    LINUX_MANAGERS.put("pacman",  new String[]{"pacman","-S","--noconfirm","gcc","glfw-x11","glew"});
    LINUX_MANAGERS.put("dnf",     new String[]{"dnf","install","-y","gcc-c++","glfw-devel","glew-devel"});
    LINUX_MANAGERS.put("yum",     new String[]{"yum","install","-y","gcc-c++","glfw-devel","glew-devel"});
    LINUX_MANAGERS.put("zypper",  new String[]{"zypper","install","-y","gcc-c++","glfw-devel","glew-devel"});
    LINUX_MANAGERS.put("emerge",  new String[]{"emerge","media-libs/glfw","media-libs/glew","sys-devel/gcc"});
    LINUX_MANAGERS.put("nix-env", new String[]{"nix-env","-iA","nixpkgs.gcc","nixpkgs.glfw","nixpkgs.glew"});
  }

  /** Pure detection, no dialog — used by run() before showing anything. */
  private java.util.List<String> detectLinuxMissing() {
    boolean haveGpp = commandExists("g++", "--version");
    boolean glfwOk = commandExists("pkg-config", "--exists", "glfw3")
                  || new File("/usr/lib/libglfw.so").exists()
                  || new File("/usr/lib/x86_64-linux-gnu/libglfw.so.3").exists();
    boolean glewOk = commandExists("pkg-config", "--exists", "glew")
                  || new File("/usr/lib/libGLEW.so").exists()
                  || new File("/usr/lib/x86_64-linux-gnu/libGLEW.so").exists();

    java.util.List<String> missing = new java.util.ArrayList<>();
    if (!haveGpp) missing.add("g++");
    if (!glfwOk) missing.add("glfw");
    if (!glewOk) missing.add("glew");
    return missing;
  }

  private String detectLinuxPackageManager() {
    for (String pm : LINUX_MANAGERS.keySet()) {
      if (commandExists("which", pm)) return pm;
    }
    return null;
  }

  /**
   * Opens the progress wizard and installs whatever's in `missing`. Only
   * called after the user has already confirmed via the plain error
   * dialog in run() — no further confirmation happens here.
   *
   * Unlike Windows/macOS, Linux package managers need root, and there's
   * no clean way to capture a sudo password prompt inside our own log
   * box — so this launches a real terminal (same approach CppBuild's
   * existing fallback already uses) rather than streaming output here.
   */
  private boolean runLinux(java.util.List<String> missing) {
    buildDialog("C++ Mode Setup: Linux");
    String pm = detectLinuxPackageManager();

    Thread worker = new Thread(() -> {
      if (pm == null) {
        appendLog("Couldn't detect a supported package manager.");
        appendLog("Missing: " + String.join(", ", missing));
        appendLog("Please install these manually, for example:");
        appendLog("  sudo apt install g++ libglfw3-dev libglew-dev      (Debian/Ubuntu)");
        appendLog("  sudo pacman -S gcc glfw-x11 glew                   (Arch)");
        appendLog("  sudo dnf install gcc-c++ glfw-devel glew-devel     (Fedora)");
        finishDialog(false, "Couldn't detect a package manager — see log above.");
        return;
      }

      String[] pmCmd = LINUX_MANAGERS.get(pm);
      setStep("Installing: " + String.join(", ", missing) + " (via " + pm + ")");
      appendLog("A terminal window will open to run:");
      appendLog("  sudo " + String.join(" ", pmCmd));
      appendLog("Enter your password there if prompted, then return here.");

      boolean launched = launchLinuxTerminalInstall(pmCmd);
      if (cancelled.get()) return;

      if (!launched) {
        finishDialog(false,
          "Couldn't launch a terminal — run the command above manually.");
        return;
      }

      finishDialog(true,
        "Installer launched in a separate terminal. Once it finishes, restart Processing.");
    });
    worker.setDaemon(true);
    worker.start();

    showDialogAndWaitForClose();
    return succeeded.get();
  }

  private boolean launchLinuxTerminalInstall(String[] pmCmd) {
    String cmdStr = "sudo " + String.join(" ", pmCmd);
    String[] terminals = {
      "x-terminal-emulator", "gnome-terminal", "konsole",
      "xfce4-terminal", "mate-terminal", "xterm", "alacritty",
      "kitty", "tilix", "terminator"
    };

    try {
      File tmp = File.createTempFile("cpp_install_", ".sh");
      tmp.deleteOnExit();
      tmp.setExecutable(true);
      try (PrintWriter pw = new PrintWriter(tmp)) {
        pw.println("#!/bin/bash");
        pw.println("echo 'Installing g++, GLFW, GLEW...'");
        pw.println(cmdStr);
        pw.println("echo ''");
        pw.println("echo 'Done! Please restart Processing4.'");
        pw.println("read -p 'Press Enter to close...'");
      }

      for (String term : terminals) {
        if (!commandExists("which", term)) continue;
        try {
          ProcessBuilder pb;
          if (term.equals("gnome-terminal")) {
            pb = new ProcessBuilder(term, "--", "bash", tmp.getAbsolutePath());
          } else if (term.equals("konsole") || term.equals("kitty") || term.equals("alacritty")) {
            pb = new ProcessBuilder(term, "-e", "bash", tmp.getAbsolutePath());
          } else {
            pb = new ProcessBuilder(term, "-e", "bash " + tmp.getAbsolutePath());
          }
          pb.start();
          return true;
        } catch (Exception ignored) {}
      }

      // No terminal found — try pkexec (graphical sudo) as a last resort.
      try {
        new ProcessBuilder("pkexec", "bash", tmp.getAbsolutePath()).start();
        return true;
      } catch (Exception ignored) {}

      return false;
    } catch (Exception e) {
      appendLog("Error: " + e.getMessage());
      return false;
    }
  }

  // ── Shared helpers ─────────────────────────────────────────────────────

  private void streamToLog(Process p) throws IOException {
    try (BufferedReader br = new BufferedReader(
        new InputStreamReader(p.getInputStream()))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (cancelled.get()) {
          p.destroy();
          break;
        }
        appendLog(line);
      }
    }
  }
}
