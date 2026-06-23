package processing.mode.cpp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import processing.app.RunnerListener;
import processing.app.Sketch;


public class CppBuild {

  private final Sketch sketch;
  private final CppMode mode;
  final File buildDir;
  private final File runtimeDir;

  public int headerLineCount = 0;

  private static final Pattern ERR_LINE = Pattern.compile(
      "(?:Sketch_run\\.cpp|sketch\\.pde):(\\d+):\\d+:\\s+error:");

  private static final Pattern FUNC_DEF = Pattern.compile(
      "^[ \\t]*(void|float|int|double|bool|char|long|color|auto|PVector|PImage\\s*\\*?|std::string)" +
      "\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)\\s*\\{",
      Pattern.MULTILINE);

  private static final Set<String> LIFECYCLE = new HashSet<>(Arrays.asList(
      "setup", "draw", "settings",
      "mousePressed", "mouseReleased", "mouseClicked",
      "mouseMoved", "mouseDragged", "mouseWheel",
      "keyPressed", "keyReleased", "keyTyped",
      "windowMoved", "windowResized"));

  private static final String[] RESERVED = {
      "rand", "time", "exit", "abs", "min", "max", "log", "exp", "pow"
  };

  public CppBuild(Sketch sketch, CppMode mode) {
    this.sketch     = sketch;
    this.mode       = mode;
    this.buildDir   = sketch.makeTempFolder();
    this.runtimeDir = mode != null ? mode.getRuntimeDir() : null;
  }

  private void showGppDialog(String html, String btn, String url,
                              RunnerListener listener) throws Exception {
    Object[] opts = { btn, "Cancel" };
    int ch = javax.swing.JOptionPane.showOptionDialog(null,
      new javax.swing.JLabel(html), "C++ Compiler Not Found",
      javax.swing.JOptionPane.YES_NO_OPTION,
      javax.swing.JOptionPane.ERROR_MESSAGE, null, opts, opts[0]);
    if (ch == 0) {
      try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
      catch (Exception ignored) {}
    }
    listener.statusError("g++ not found — see dialog for install instructions");
    throw new Exception("g++ not found");
  }

  private void autoInstallWindows(RunnerListener listener) {
    String script =
      "$ErrorActionPreference = 'Stop'\n"
    + "Write-Host '=== C++ Mode Auto-Installer ==='\n"
    + "Write-Host ''\n"
    + "$msys2Dirs = @('C:\\msys64','C:\\msys2','C:\\tools\\msys64',"
    + "\"$env:USERPROFILE\\msys64\",\"$env:LOCALAPPDATA\\msys64\")\n"
    + "$pacman = $null\n"
    + "foreach ($dir in $msys2Dirs) {\n"
    + "    $p = \"$dir\\usr\\bin\\pacman.exe\"\n"
    + "    if (Test-Path $p) { $pacman = $p; Write-Host \"Found MSYS2 at $dir\"; break }\n"
    + "}\n"
    + "if (-not $pacman) {\n"
    + "    $inPath = Get-Command pacman -ErrorAction SilentlyContinue\n"
    + "    if ($inPath) { $pacman = $inPath.Source; Write-Host \"Found pacman on PATH\" }\n"
    + "}\n"
    + "if (-not $pacman) {\n"
    + "    Write-Host 'Downloading MSYS2 installer (this may take a minute)...'\n"
    + "    $url = 'https://github.com/msys2/msys2-installer/releases/download/nightly-x86_64/msys2-x86_64-latest.exe'\n"
    + "    $dest = \"$env:TEMP\\msys2-installer.exe\"\n"
    + "    $wc = New-Object System.Net.WebClient\n"
    + "    $wc.add_DownloadProgressChanged({\n"
    + "        param($s,$e)\n"
    + "        $pct = $e.ProgressPercentage\n"
    + "        $bar = '#' * [int]($pct/2)\n"
    + "        $empty = '-' * (50 - [int]($pct/2))\n"
    + "        Write-Host -NoNewline \"\r  [$bar$empty] $pct%  \"\n"
    + "    })\n"
    + "    $done = $false\n"
    + "    $wc.add_DownloadFileCompleted({ $done = $true })\n"
    + "    $wc.DownloadFileAsync([uri]$url, $dest)\n"
    + "    while (-not $done) { Start-Sleep -Milliseconds 100 }\n"
    + "    Write-Host ''\n"
    + "    Write-Host 'Running MSYS2 installer...'\n"
    + "    Start-Process -Wait $dest -ArgumentList 'install','--confirm-command','--accept-messages','--root','C:/msys64'\n"
    + "    $pacman = 'C:\\msys64\\usr\\bin\\pacman.exe'\n"
    + "    Write-Host 'MSYS2 installed.'\n"
    + "}\n"
    + "Write-Host ''\n"
    + "Write-Host 'Installing g++, GLFW, GLEW...'\n"
    + "& $pacman -S --noconfirm --needed '--overwrite=*' mingw-w64-x86_64-gcc mingw-w64-x86_64-glfw mingw-w64-x86_64-glew\n"
    + "Write-Host 'Adding MSYS2 to system PATH...'\n"
    + "$mingwBin = 'C:\\msys64\\mingw64\\bin'\n"
    + "$currentPath = [Environment]::GetEnvironmentVariable('PATH','Machine')\n"
    + "if ($currentPath -notlike \"*$mingwBin*\") {\n"
    + "    [Environment]::SetEnvironmentVariable('PATH', \"$mingwBin;$currentPath\", 'Machine')\n"
    + "    Write-Host 'Added to system PATH.'\n"
    + "} else { Write-Host 'Already in PATH.' }\n"
    + "Write-Host ''\n"
    + "Write-Host '============================================'\n"
    + "Write-Host 'Installation complete!'\n"
    + "Write-Host 'Please restart Processing4 to use C++ Mode.'\n"
    + "Write-Host '============================================'\n"
    + "Read-Host 'Press Enter to close'\n";
    try {
      java.io.File tmp = java.io.File.createTempFile("cpp_install_", ".ps1");
      tmp.deleteOnExit();
      try (java.io.PrintWriter pw = new java.io.PrintWriter(tmp)) { pw.print(script); }
      listener.statusNotice("Launching installer...");
      new Thread(() -> {
        try {
          // Run as admin so PATH can be updated system-wide
          ProcessBuilder pb2 = new ProcessBuilder(
            "powershell.exe", "-ExecutionPolicy", "Bypass",
            "-Command",
            "Start-Process powershell -Verb RunAs -ArgumentList "
            + "'-ExecutionPolicy Bypass -NoExit -File \"" + tmp.getAbsolutePath().replace("\\", "\\\\") + "\"'");
          pb2.start();
          javax.swing.JOptionPane.showMessageDialog(null,
            new javax.swing.JLabel(
              "<html><b>Installer launched!</b><br><br>"
              + "A PowerShell window is installing g++, GLFW, and GLEW.<br><br>"
              + "When it says <b>Installation complete!</b>:<br>"
              + "&nbsp;&nbsp;1. Close the installer window<br>"
              + "&nbsp;&nbsp;2. <b>Restart Processing4</b></html>"),
            "Installing...", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e2) { listener.statusError("Error: "+e2.getMessage()); }
      }).start();
    } catch (Exception e) { listener.statusError("Error: "+e.getMessage()); }
  }


  private void autoInstallLinux(String[] pmCmd, RunnerListener listener) {
    // Build a shell script that runs the package manager with sudo
    // and opens in a visible terminal so the user can enter their password
    String cmdStr = "sudo " + String.join(" ", pmCmd);

    // Try common terminal emulators
    String[] terminals = {
      "x-terminal-emulator", "gnome-terminal", "konsole",
      "xfce4-terminal", "mate-terminal", "xterm", "alacritty",
      "kitty", "tilix", "terminator"
    };

    new Thread(() -> {
      try {
        // Write a small shell script
        java.io.File tmp = java.io.File.createTempFile("cpp_install_", ".sh");
        tmp.deleteOnExit();
        tmp.setExecutable(true);
        try (java.io.PrintWriter pw = new java.io.PrintWriter(tmp)) {
          pw.println("#!/bin/bash");
          pw.println("echo '=== C++ Mode Auto-Installer ==='");
          pw.println("echo ''");
          pw.println("echo 'Installing g++, GLFW, GLEW...'");
          pw.println(cmdStr);
          pw.println("echo ''");
          pw.println("echo '============================================'");
          pw.println("echo 'Done! Please restart Processing4.'");
          pw.println("echo '============================================'");
          pw.println("read -p 'Press Enter to close...'");
        }

        boolean launched = false;
        for (String term : terminals) {
          try {
            Process which = Runtime.getRuntime().exec(new String[]{"which", term});
            if (which.waitFor() != 0) continue;
            ProcessBuilder pb2;
            if (term.equals("gnome-terminal")) {
              pb2 = new ProcessBuilder(term, "--", "bash", tmp.getAbsolutePath());
            } else if (term.equals("konsole")) {
              pb2 = new ProcessBuilder(term, "-e", "bash", tmp.getAbsolutePath());
            } else if (term.equals("kitty") || term.equals("alacritty")) {
              pb2 = new ProcessBuilder(term, "-e", "bash", tmp.getAbsolutePath());
            } else {
              pb2 = new ProcessBuilder(term, "-e", "bash " + tmp.getAbsolutePath());
            }
            pb2.start();
            launched = true;
            break;
          } catch (Exception ignored) {}
        }

        if (!launched) {
          // Fallback: try pkexec (graphical sudo) without terminal
          try {
            new ProcessBuilder("pkexec", "bash", tmp.getAbsolutePath()).start();
            launched = true;
          } catch (Exception ignored) {}
        }

        if (launched) {
          javax.swing.JOptionPane.showMessageDialog(null,
            new javax.swing.JLabel(
              "<html><b>Installer launched!</b><br><br>"
              + "A terminal window is installing g++, GLFW, and GLEW.<br>"
              + "You may need to enter your password.<br><br>"
              + "When it says <b>Done!</b>:<br>"
              + "&nbsp;&nbsp;1. Close the terminal<br>"
              + "&nbsp;&nbsp;2. <b>Restart Processing4</b></html>"),
            "Installing...", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } else {
          javax.swing.JOptionPane.showMessageDialog(null,
            new javax.swing.JLabel(
              "<html><b>Could not launch terminal.</b><br><br>"
              + "Please run manually:<br>"
              + "&nbsp;&nbsp;<code>" + cmdStr + "</code><br><br>"
              + "Then restart Processing4.</html>"),
            "Manual Install Required", javax.swing.JOptionPane.WARNING_MESSAGE);
        }
      } catch (Exception e) { listener.statusError("Error: " + e.getMessage()); }
    }).start();
  }

  private void checkGppAvailable(RunnerListener listener) throws Exception {
    String os = System.getProperty("os.name").toLowerCase();
    boolean isWin = os.contains("win");
    boolean isMac = os.contains("mac");
    if (isWin) {
      for (String p : new String[]{
          "C:\\msys64\\mingw64\\bin\\g++.exe",
          "C:\\msys2\\mingw64\\bin\\g++.exe",
          System.getProperty("user.home") + "\\msys64\\mingw64\\bin\\g++.exe",
          "C:\\msys64\\ucrt64\\bin\\g++.exe"})
        if (new File(p).exists()) return;
      try { if (Runtime.getRuntime().exec(new String[]{"g++","--version"}).waitFor()==0) return; }
      catch (Exception ignored) {}

      // Try the guided installer first. If it succeeds, we're done. If the
      // user cancels, InstallWizard throws CancelledByUser, which stops
      // the build entirely instead of falling through. Only a genuine
      // wizard failure (not a cancel) falls through to the dialog below.
      if (InstallWizard.run(listener)) return;

      Object[] opts = { "Install Automatically", "Download MSYS2", "Cancel" };
      int ch = javax.swing.JOptionPane.showOptionDialog(null,
        new javax.swing.JLabel(
          "<html><b>g++ (C++ compiler) not found.</b><br><br>"
          + "C++ Mode requires MSYS2 with g++, GLFW, and GLEW.<br><br>"
          + "<b>Auto-Install (recommended):</b><br>"
          + "&nbsp;&nbsp;Click <b>Auto-Install</b> to install everything automatically.<br><br>"
          + "<b>Manual:</b><br>"
          + "&nbsp;&nbsp;1. Install MSYS2 from https://www.msys2.org<br>"
          + "&nbsp;&nbsp;2. Open <b>MSYS2 MinGW 64-bit</b> terminal<br>"
          + "&nbsp;&nbsp;3. Run:<br>"
          + "&nbsp;&nbsp;&nbsp;&nbsp;<code>pacman -S mingw-w64-x86_64-gcc"
          + " mingw-w64-x86_64-glfw mingw-w64-x86_64-glew</code><br>"
          + "&nbsp;&nbsp;4. Restart Processing</html>"),
        "C++ Compiler Not Found",
        javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
        javax.swing.JOptionPane.ERROR_MESSAGE,
        null, opts, opts[0]);
      if (ch == 0) { autoInstallWindows(listener); return; }
      else if (ch == 1) {
        try { java.awt.Desktop.getDesktop().browse(new java.net.URI("https://www.msys2.org")); }
        catch (Exception ignored) {}
      }
      listener.statusError("g++ not found — install MSYS2 to use C++ Mode");
      throw new Exception("g++ not found");
    } else {
      try { if (Runtime.getRuntime().exec(new String[]{"g++","--version"}).waitFor()==0) return; }
      catch (Exception ignored) {}
      if (isMac) {
        // Try the guided installer first (Xcode Command Line Tools, then
        // GLFW/GLEW via Homebrew if present). A cancel throws and stops
        // the build; only a genuine failure falls through to the dialog.
        if (InstallWizard.run(listener)) return;

        showGppDialog(
          "<html><b>g++ not found.</b><br><br>"
          + "Install via Homebrew:<br>"
          + "&nbsp;&nbsp;<code>brew install gcc glfw glew</code><br><br>"
          + "Click Download Homebrew if not installed.</html>",
          "Download Homebrew", "https://brew.sh", listener);
      } else {
        // Try the guided installer first. A cancel throws and stops the
        // build; only a genuine failure falls through to the dialog below.
        if (InstallWizard.run(listener)) return;

        // Detect package manager and offer auto-install
        String[] aptPkgs    = {"g++", "libglfw3-dev", "libglew-dev"};
        String[] pacmanPkgs = {"gcc", "glfw-x11", "glew"};
        String[] dnfPkgs    = {"gcc-c++", "glfw-devel", "glew-devel"};
        String[] zypperPkgs = {"gcc-c++", "glfw-devel", "glew-devel"};
        String[] emergePkgs = {"media-libs/glfw", "media-libs/glew"};
        String[] nixPkgs    = {"gcc", "glfw", "glew"};

        String pm = null;
        String[] pmCmd = null;
        String pmName = null;

        // Detect package manager
        java.util.LinkedHashMap<String,String[]> managers = new java.util.LinkedHashMap<>();
        managers.put("apt-get",  new String[]{"apt-get","install","-y","g++","libglfw3-dev","libglew-dev"});
        managers.put("apt",      new String[]{"apt","install","-y","g++","libglfw3-dev","libglew-dev"});
        managers.put("pacman",   new String[]{"pacman","-S","--noconfirm","gcc","glfw-x11","glew"});
        managers.put("dnf",      new String[]{"dnf","install","-y","gcc-c++","glfw-devel","glew-devel"});
        managers.put("yum",      new String[]{"yum","install","-y","gcc-c++","glfw-devel","glew-devel"});
        managers.put("zypper",   new String[]{"zypper","install","-y","gcc-c++","glfw-devel","glew-devel"});
        managers.put("emerge",   new String[]{"emerge","media-libs/glfw","media-libs/glew","sys-devel/gcc"});
        managers.put("nix-env",  new String[]{"nix-env","-iA","nixpkgs.gcc","nixpkgs.glfw","nixpkgs.glew"});
        managers.put("brew",     new String[]{"brew","install","gcc","glfw","glew"});

        for (java.util.Map.Entry<String,String[]> e : managers.entrySet()) {
          try {
            Process p2 = Runtime.getRuntime().exec(new String[]{"which", e.getKey()});
            if (p2.waitFor() == 0) { pm = e.getKey(); pmCmd = e.getValue(); pmName = e.getKey(); break; }
          } catch (Exception ignored) {}
        }

        String installLine = pm != null
          ? "Detected: <b>" + pm + "</b><br><br>"
          + "Will run:<br>&nbsp;&nbsp;<code>sudo " + String.join(" ", pmCmd) + "</code>"
          : "Could not detect package manager.<br>Install manually and restart Processing.";

        Object[] opts = pm != null
          ? new Object[]{"Install Automatically", "More Info", "Cancel"}
          : new Object[]{"More Info", "Cancel"};

        int ch = javax.swing.JOptionPane.showOptionDialog(null,
          new javax.swing.JLabel(
            "<html><b>g++ (C++ compiler) not found.</b><br><br>"
            + installLine + "<br><br>"
            + "<b>Manual install:</b><br>"
            + "&nbsp;&nbsp;<b>apt/apt-get</b> (Ubuntu, Debian, Pop!_OS, Mint):<br>"
            + "&nbsp;&nbsp;&nbsp;&nbsp;<code>sudo apt install g++ libglfw3-dev libglew-dev</code><br>"
            + "&nbsp;&nbsp;<b>pacman</b> (Arch, Manjaro, EndeavourOS):<br>"
            + "&nbsp;&nbsp;&nbsp;&nbsp;<code>sudo pacman -S gcc glfw-x11 glew</code><br>"
            + "&nbsp;&nbsp;<b>dnf/yum</b> (Fedora, RHEL, CentOS):<br>"
            + "&nbsp;&nbsp;&nbsp;&nbsp;<code>sudo dnf install gcc-c++ glfw-devel glew-devel</code><br>"
            + "&nbsp;&nbsp;<b>zypper</b> (openSUSE):<br>"
            + "&nbsp;&nbsp;&nbsp;&nbsp;<code>sudo zypper install gcc-c++ glfw-devel glew-devel</code><br>"
            + "&nbsp;&nbsp;<b>emerge</b> (Gentoo):<br>"
            + "&nbsp;&nbsp;&nbsp;&nbsp;<code>sudo emerge media-libs/glfw media-libs/glew</code><br>"
            + "&nbsp;&nbsp;<b>brew</b> (macOS/Linuxbrew):<br>"
            + "&nbsp;&nbsp;&nbsp;&nbsp;<code>brew install gcc glfw glew</code></html>"),
          "C++ Compiler Not Found",
          javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
          javax.swing.JOptionPane.ERROR_MESSAGE,
          null, opts, opts[0]);

        if (pm != null && ch == 0) {
          autoInstallLinux(pmCmd, listener);
          return;
        } else if ((pm != null && ch == 1) || (pm == null && ch == 0)) {
          try { java.awt.Desktop.getDesktop().browse(new java.net.URI("https://gcc.gnu.org/install/")); }
          catch (Exception ignored) {}
        }
        listener.statusError("g++ not found — install with your package manager");
        throw new Exception("g++ not found");
      }
    }
  }

  public File compile(RunnerListener listener) throws Exception {

    // Check g++ is available before doing anything else
    checkGppAvailable(listener);

    File sketchSrc = writeSketch(listener);

    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    boolean isMac     = System.getProperty("os.name").toLowerCase().contains("mac");
    String  ext       = isWindows ? ".exe" : "";
    File    binary    = new File(buildDir, sketch.getName() + ext);
    String  gpp       = findGpp(isWindows);

    List<String> cmd = buildCommand(gpp, sketchSrc, binary, isWindows, isMac);
    listener.statusNotice("$ " + String.join(" ", cmd));
    // Only print the full compile command when debug mode is actually on
    // (i.e. the DEBUG file already caused buildCommand() to add
    // -DPROCESSING_DEBUG above) -- this is itself a debugging aid, so it
    // follows the same toggle as everything else PDEBUG-related, instead
    // of always printing regardless of the DEBUG file's contents.
    if (cmd.contains("-DPROCESSING_DEBUG")) {
      System.err.println("=== FULL COMPILE COMMAND ===");
      System.err.println(String.join(" ", cmd));
    }

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    pb.directory(buildDir);
    // Pass sketch folder so Processing.cpp can find data/ assets
    pb.environment().put("PROCESSING_SKETCH_PATH", sketch.getFolder().getAbsolutePath());
    Process proc = pb.start();

    StringBuilder fullOutput   = new StringBuilder();
    int           firstErrLine = -1;

    try (BufferedReader br = new BufferedReader(
            new InputStreamReader(proc.getInputStream()))) {
      String line;
      while ((line = br.readLine()) != null) {
        fullOutput.append(line).append("\n");
        if (firstErrLine < 0) {
          Matcher m = ERR_LINE.matcher(line);
          if (m.find()) {
            try { firstErrLine = Integer.parseInt(m.group(1)); }
            catch (NumberFormatException ignored) {}
          }
        }
        if (line.contains("error:") || line.contains("warning:")) {
          for (String w : wordWrap(line, 120)) System.err.println(w);
        } else if (!line.isBlank()) {
          for (String w : wordWrap(line, 120)) System.out.println(w);
        }
      }
    }

    int exitCode = proc.waitFor();
    if (exitCode != 0) {
      if (firstErrLine > 0 && listener instanceof CppEditor) {
        final int el = Math.max(1, firstErrLine - headerLineCount);
        javax.swing.SwingUtilities.invokeLater(() ->
          ((CppEditor) listener).highlightErrorLine(el));
      }
      listener.statusError("Build failed — see console for errors.");
      throw new Exception(fullOutput.toString());
    }

    // Copy default.ttf to sketch folder so the binary finds it at runtime
    File font = new File(runtimeDir, "default.ttf");
    if (font.exists()) {
      File dest = new File(sketch.getFolder(), "default.ttf");
      if (!dest.exists())
        java.nio.file.Files.copy(font.toPath(), dest.toPath());
    }
    listener.statusNotice("Built: " + binary.getName());

    // Check for required native libraries on all platforms
    checkNativeLibs(listener, binary);

    return binary;
  }

  private boolean commandExists(String cmd) {
    try {
      Process p = Runtime.getRuntime().exec(new String[]{ cmd, "--version" });
      return p.waitFor() == 0;
    } catch (Exception e) { return false; }
  }

  private void checkNativeLibs(RunnerListener listener, File binary) throws Exception {
    String os   = System.getProperty("os.name").toLowerCase();
    boolean win = os.contains("win");
    boolean mac = os.contains("mac");

    if (win) {
      checkWindowsDLLs(listener, binary);
    } else if (mac) {
      checkMacLibs(listener);
    } else {
      checkLinuxLibs(listener);
    }
  }

  // ── Windows ────────────────────────────────────────────────────────────────
  private boolean windowsLibsNowPresent(java.util.List<String> searchDirs, String[] required) {
    outer:
    for (String dll : required) {
      for (String dir : searchDirs)
        if (new File(dir, dll).exists()) continue outer;
      return false;
    }
    return true;
  }

  private void checkWindowsDLLs(RunnerListener listener, File binary) throws Exception {
    String[] required = { "glfw3.dll", "glew32.dll" };
    java.util.List<String> searchDirs = new java.util.ArrayList<>();
    searchDirs.add(binary.getParent());
    searchDirs.add("C:\\msys64\\mingw64\\bin");
    searchDirs.add("C:\\msys2\\mingw64\\bin");
    String path = System.getenv("PATH");
    if (path != null) for (String p : path.split(";")) searchDirs.add(p);

    java.util.List<String> missing = new java.util.ArrayList<>();
    outer:
    for (String dll : required) {
      for (String dir : searchDirs)
        if (new File(dir, dll).exists()) continue outer;
      missing.add(dll);
    }
    if (missing.isEmpty()) return;

    // Try the guided installer first.
    if (InstallWizard.run(listener)) {
      if (windowsLibsNowPresent(searchDirs, required)) return;
    }

    int choice = javax.swing.JOptionPane.showConfirmDialog(null,
      "Missing libraries: " + String.join(", ", missing) + "\n\n" +
      "C++ Mode requires GLFW and GLEW to run sketches.\n" +
      "Click OK to install them automatically via MSYS2.",
      "Missing Libraries", javax.swing.JOptionPane.OK_CANCEL_OPTION,
      javax.swing.JOptionPane.WARNING_MESSAGE);

    if (choice != javax.swing.JOptionPane.OK_OPTION) return;

    // Find MSYS2 pacman
    String pacman = null;
    for (String p : new String[]{
        "C:\\msys64\\usr\\bin\\pacman.exe",
        "C:\\msys2\\usr\\bin\\pacman.exe"})
      if (new File(p).exists()) { pacman = p; break; }

    if (pacman == null) {
      try { java.awt.Desktop.getDesktop().browse(new java.net.URI("https://www.msys2.org")); }
      catch (Exception ignored) {}
      javax.swing.JOptionPane.showMessageDialog(null,
        "MSYS2 not found. Please install it from https://www.msys2.org\n\n" +
        "Then open MSYS2 MinGW 64-bit and run:\n" +
        "  pacman -S mingw-w64-x86_64-glfw mingw-w64-x86_64-glew\n\n" +
        "Then restart Processing.",
        "Install MSYS2", javax.swing.JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    final String finalPacman = pacman;
    new Thread(() -> {
      try {
        listener.statusNotice("Installing GLFW and GLEW via MSYS2...");
        ProcessBuilder pb = new ProcessBuilder(finalPacman, "-S", "--noconfirm",
          "mingw-w64-x86_64-glfw", "mingw-w64-x86_64-glew");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (java.io.BufferedReader br = new java.io.BufferedReader(
            new java.io.InputStreamReader(p.getInputStream()))) {
          String line; while ((line = br.readLine()) != null) System.out.println(line);
        }
        if (p.waitFor() == 0) {
          // Copy DLLs next to binary
          for (String dll : new String[]{ "glfw3.dll","glew32.dll",
              "libgcc_s_seh-1.dll","libstdc++-6.dll","libwinpthread-1.dll" }) {
            for (String dir : new String[]{
                "C:\\msys64\\mingw64\\bin","C:\\msys2\\mingw64\\bin"}) {
              File src = new File(dir, dll);
              if (src.exists()) {
                try { java.nio.file.Files.copy(src.toPath(),
                  new File(binary.getParent(), dll).toPath(),
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                catch (Exception ignored) {}
                break;
              }
            }
          }
          listener.statusNotice("Libraries installed — you can now run your sketch.");
          javax.swing.JOptionPane.showMessageDialog(null,
            "GLFW and GLEW installed successfully!\nYou can now run your sketch.",
            "Done", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } else {
          listener.statusError("Installation failed — try manually in MSYS2 MinGW 64-bit terminal.");
        }
      } catch (Exception e) { listener.statusError("Install error: " + e.getMessage()); }
    }).start();
  }

  // ── macOS ──────────────────────────────────────────────────────────────────
  private void checkMacLibs(RunnerListener listener) throws Exception {
    // Check for glfw and glew via pkg-config or known Homebrew paths
    boolean glfwOk = new File("/opt/homebrew/lib/libglfw.dylib").exists()
                  || new File("/usr/local/lib/libglfw.dylib").exists()
                  || commandExists("pkg-config glfw3");
    boolean glewOk = new File("/opt/homebrew/lib/libGLEW.dylib").exists()
                  || new File("/usr/local/lib/libGLEW.dylib").exists()
                  || commandExists("pkg-config glew");

    if (glfwOk && glewOk) return;

    java.util.List<String> missing = new java.util.ArrayList<>();
    if (!glfwOk) missing.add("glfw");
    if (!glewOk) missing.add("glew");

    // Try the guided installer first.
    if (InstallWizard.run(listener)) {
      boolean glfwNow = new File("/opt/homebrew/lib/libglfw.dylib").exists()
                      || new File("/usr/local/lib/libglfw.dylib").exists()
                      || commandExists("pkg-config glfw3");
      boolean glewNow = new File("/opt/homebrew/lib/libGLEW.dylib").exists()
                      || new File("/usr/local/lib/libGLEW.dylib").exists()
                      || commandExists("pkg-config glew");
      if (glfwNow && glewNow) return;
    }

    int choice = javax.swing.JOptionPane.showConfirmDialog(null,
      "Missing libraries: " + String.join(", ", missing) + "\n\n" +
      "C++ Mode requires GLFW and GLEW to run sketches.\n" +
      "Click OK to install them via Homebrew.",
      "Missing Libraries", javax.swing.JOptionPane.OK_CANCEL_OPTION,
      javax.swing.JOptionPane.WARNING_MESSAGE);

    if (choice != javax.swing.JOptionPane.OK_OPTION) return;

    // Check if brew is available
    if (!commandExists("brew")) {
      try { java.awt.Desktop.getDesktop().browse(new java.net.URI("https://brew.sh")); }
      catch (Exception ignored) {}
      javax.swing.JOptionPane.showMessageDialog(null,
        "Homebrew not found. Please install it from https://brew.sh\n\n" +
        "Then run:\n  brew install glfw glew\n\nThen restart Processing.",
        "Install Homebrew", javax.swing.JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    new Thread(() -> {
      try {
        listener.statusNotice("Installing GLFW and GLEW via Homebrew...");
        ProcessBuilder pb = new ProcessBuilder("brew", "install", "glfw", "glew");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (java.io.BufferedReader br = new java.io.BufferedReader(
            new java.io.InputStreamReader(p.getInputStream()))) {
          String line; while ((line = br.readLine()) != null) System.out.println(line);
        }
        if (p.waitFor() == 0) {
          listener.statusNotice("Libraries installed — you can now run your sketch.");
          javax.swing.JOptionPane.showMessageDialog(null,
            "GLFW and GLEW installed successfully!\nYou can now run your sketch.",
            "Done", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } else {
          listener.statusError("Installation failed — try: brew install glfw glew");
        }
      } catch (Exception e) { listener.statusError("Install error: " + e.getMessage()); }
    }).start();
  }

  // ── Linux ──────────────────────────────────────────────────────────────────
  private void checkLinuxLibs(RunnerListener listener) throws Exception {
    boolean glfwOk = new File("/usr/lib/libglfw.so").exists()
                  || new File("/usr/lib/x86_64-linux-gnu/libglfw.so").exists()
                  || new File("/usr/lib/x86_64-linux-gnu/libglfw.so.3").exists()
                  || commandExists("pkg-config glfw3");
    boolean glewOk = new File("/usr/lib/libGLEW.so").exists()
                  || new File("/usr/lib/x86_64-linux-gnu/libGLEW.so").exists()
                  || commandExists("pkg-config glew");

    if (glfwOk && glewOk) return;

    // Try the guided installer first.
    if (InstallWizard.run(listener)) {
      boolean glfwNow = new File("/usr/lib/libglfw.so").exists()
                     || new File("/usr/lib/x86_64-linux-gnu/libglfw.so").exists()
                     || new File("/usr/lib/x86_64-linux-gnu/libglfw.so.3").exists()
                     || commandExists("pkg-config glfw3");
      boolean glewNow = new File("/usr/lib/libGLEW.so").exists()
                     || new File("/usr/lib/x86_64-linux-gnu/libGLEW.so").exists()
                     || commandExists("pkg-config glew");
      if (glfwNow && glewNow) return;
    }

    java.util.List<String> missing = new java.util.ArrayList<>();
    if (!glfwOk) missing.add("libglfw3-dev");
    if (!glewOk) missing.add("libglew-dev");

    // Detect package manager
    String installCmd = detectLinuxInstallCmd(missing);

    int choice = javax.swing.JOptionPane.showConfirmDialog(null,
      "Missing libraries: " + String.join(", ", missing) + "\n\n" +
      "C++ Mode requires GLFW and GLEW to run sketches.\n" +
      "Click OK to install them automatically.",
      "Missing Libraries", javax.swing.JOptionPane.OK_CANCEL_OPTION,
      javax.swing.JOptionPane.WARNING_MESSAGE);

    if (choice != javax.swing.JOptionPane.OK_OPTION) return;

    if (installCmd == null) {
      javax.swing.JOptionPane.showMessageDialog(null,
        "<html><b>Unknown Linux distribution.</b><br><br>"
        + "Please install g++, GLFW, and GLEW manually<br>"
        + "using your distro's package manager,<br>"
        + "then restart Processing.</html>",
        "Missing Libraries", javax.swing.JOptionPane.WARNING_MESSAGE);
      return;
    }

    final String cmd = installCmd;
    new Thread(() -> {
      try {
        listener.statusNotice("Installing libraries...");
        // Write install script to temp file and run it in a terminal
        java.io.File tmp = java.io.File.createTempFile("cpp_install_", ".sh");
        tmp.deleteOnExit();
        try (java.io.PrintWriter pw = new java.io.PrintWriter(tmp)) {
          pw.println("#!/bin/bash");
          pw.println("echo '==============================='");
          pw.println("echo '  C++ Mode Library Installer  '");
          pw.println("echo '==============================='");
          pw.println("echo ''");
          pw.println("show_progress() {");
          pw.println("  local pid=$1 msg=$2 i=0");
          pw.println("  local spin='|/-\\\\'");
          pw.println("  while kill -0 $pid 2>/dev/null; do");
          pw.println("    i=$(( (i+1) % 4 ))");
          pw.println("    printf '\\r  [%s] %s' \"${spin:$i:1}\" \"$msg\"");
          pw.println("    sleep 0.15");
          pw.println("  done");
          pw.println("  printf '\\r  [OK] %s\\n' \"$msg\"");
          pw.println("}");
          pw.println("echo 'Installing g++, GLFW, GLEW...'");          pw.println("echo 'Installing g++, GLFW, GLEW...'");
          pw.println("echo ''");
          pw.println(cmd + " &");
          pw.println("PKG_PID=$!");
          pw.println("spinner $PKG_PID 'Installing packages...'");
          pw.println("wait $PKG_PID");
          pw.println("EXIT=$?");
          pw.println("echo ''");
          pw.println("if [ $EXIT -eq 0 ]; then");
          pw.println("  echo '==============================='");
          pw.println("  echo '  Installation complete!'");
          pw.println("  echo '  Please restart Processing.'");
          pw.println("  echo '==============================='");
          pw.println("else");
          pw.println("  echo 'Installation failed. Try running manually:'");
          pw.println("  echo '" + cmd + "'");
          pw.println("fi");
          pw.println("read -p 'Press Enter to close...'");
        }
        tmp.setExecutable(true);

        // Try to open in a terminal emulator
        String[] terminals = {
          "x-terminal-emulator", "gnome-terminal", "konsole",
          "xfce4-terminal", "xterm", "alacritty", "kitty"
        };
        boolean launched = false;
        for (String term : terminals) {
          if (!cmdExists(term)) continue;
          String[] termCmd;
          if (term.equals("gnome-terminal"))
            termCmd = new String[]{ term, "--", "bash", tmp.getAbsolutePath() };
          else if (term.equals("alacritty") || term.equals("kitty"))
            termCmd = new String[]{ term, "-e", "bash", tmp.getAbsolutePath() };
          else
            termCmd = new String[]{ term, "-e", "bash " + tmp.getAbsolutePath() };
          try {
            new ProcessBuilder(termCmd).start();
            launched = true;
            break;
          } catch (Exception ignored) {}
        }
        if (!launched) {
          // Fallback: run directly
          ProcessBuilder pb = new ProcessBuilder("bash", tmp.getAbsolutePath());
          pb.redirectErrorStream(true);
          Process p = pb.start();
          try (java.io.BufferedReader br = new java.io.BufferedReader(
              new java.io.InputStreamReader(p.getInputStream()))) {
            String line; while ((line = br.readLine()) != null) System.out.println(line);
          }
          p.waitFor();
        }
        listener.statusNotice("Installer launched — restart Processing when done.");
        javax.swing.JOptionPane.showMessageDialog(null,
          "<html><b>Installer launched!</b><br><br>"
          + "When it says <b>Installation complete!</b>:<br>"
          + "&nbsp;&nbsp;1. Close the terminal<br>"
          + "&nbsp;&nbsp;2. <b>Restart Processing</b></html>",
          "Installing...", javax.swing.JOptionPane.INFORMATION_MESSAGE);
      } catch (Exception e) { listener.statusError("Install error: " + e.getMessage()); }
    }).start();
  }

  private boolean cmdExists(String cmd) {
    try {
      String os = System.getProperty("os.name","").toLowerCase();
      ProcessBuilder pb;
      if (os.contains("win")) {
        // On Windows use 'where' instead of 'which'
        pb = new ProcessBuilder("where", cmd);
      } else {
        pb = new ProcessBuilder("which", cmd);
      }
      pb.redirectErrorStream(true);
      return pb.start().waitFor() == 0;
    } catch (Exception e) { return false; }
  }

  private boolean compilerExists(ExportTarget t) {
    String os = System.getProperty("os.name","").toLowerCase();
    boolean isWin = os.contains("win");

    // macOS cross-compile needs osxcross - not bundled
    if (t == ExportTarget.MACOS_X64 || t == ExportTarget.MACOS_ARM64)
      return cmdExists(t.compiler);

    // Linux targets: need Linux/Mac to cross-compile
    if (!t.isWindows) {
      if (t == ExportTarget.MACOS_X64 || t == ExportTarget.MACOS_ARM64)
        return cmdExists(t.compiler);
      if (isWin) return false; // not available on Windows - need Linux machine
      if (muslToolchainInstalled(t)) return true;
      return cmdExists(t.compiler);
    }

    // Windows target
    if (isWin) {
      String[] msysPaths = {
        "C:\\msys64\\mingw64\\bin\\g++.exe",
        "C:\\msys2\\mingw64\\bin\\g++.exe",
        System.getProperty("user.home") + "\\msys64\\mingw64\\bin\\g++.exe"
      };
      for (String p : msysPaths)
        if (new java.io.File(p).exists()) return true;
      return cmdExists("g++");
    }
    return cmdExists(t.compiler);
  }

  private String detectLinuxInstallCmd(java.util.List<String> aptPkgs) {
    if (cmdExists("apt-get"))
      return "pkexec apt-get install -y libglfw3-dev libglew-dev g++";
    if (cmdExists("pacman"))
      return "pkexec bash -c 'pacman -S --noconfirm --overwrite=* gcc glfw-x11 glew"
           + " || pacman -S --noconfirm --overwrite=* gcc glfw glew'";
    if (cmdExists("dnf"))
      return "pkexec dnf install -y gcc-c++ glfw-devel glew-devel";
    if (cmdExists("zypper"))
      return "pkexec zypper install -y gcc-c++ libglfw3 libglfw-devel glew-devel";
    if (cmdExists("emerge"))
      return "pkexec emerge --ask=n media-libs/glfw media-libs/glew sys-devel/gcc";
    if (cmdExists("xbps-install"))
      return "pkexec xbps-install -y gcc glfw-devel glew";
    if (cmdExists("apk"))
      return "pkexec apk add --no-cache gcc g++ glfw-dev glew-dev";
    return null;
  }

  private File writeSketch(RunnerListener listener) throws IOException {
    StringBuilder raw = new StringBuilder();
    for (int i = 0; i < sketch.getCodeCount(); i++) {
      String prog = sketch.getCode(i).getProgram();
      boolean hasLifecycle = prog.contains("void setup(") || prog.contains("void draw(");
      if (!hasLifecycle) raw.insert(0, prog + "\n");
      else raw.append(prog).append("\n");
    }

    String code = sanitize(raw.toString());
    code = removeUserIncludes(code);
    warnReservedNames(code, listener);
    code = javaToC(code);
    // pointerizeNewAssignedVars() was removed: it auto-rewrote sketch
    // declarations like "PImage img;" to "PImage* img;" based on
    // pattern-matching later code, silently changing the user's own
    // declared type without them asking for it. That's a structural
    // decision about the user's code, not a well-designed library type
    // behaving correctly (unlike ArrayList<T>/String, which are real,
    // value/reference-correct C++ types the user opts into explicitly).
    // Sketch authors now write real C++ pointer syntax themselves --
    // "PImage* img; img = loadImage(...); img->width;" -- for PImage,
    // PFont, PShape, and PGraphics, including explicit delete when
    // reassigning a pointer they're done with. This is more honest
    // about what's actually happening and removes an entire category of
    // bugs that came from CppBuild guessing at the user's intent from
    // pattern-matching their code (comment-blind false positives,
    // function-body-local-scope gaps, etc.).
    code = stripNamespaceProcessing(code);
    code = preprocessMacros(code);

    boolean hasSetup = code.contains("void setup(");
    boolean hasDraw  = code.contains("void draw(");

    // Detect which lifecycle/event methods the sketch defines
    String[] lifecycleMethods = {
      "setup", "draw", "settings",
      "mousePressed", "mouseReleased", "mouseClicked",
      "mouseMoved", "mouseDragged", "mouseWheel",
      "keyPressed", "keyReleased", "keyTyped",
      "windowMoved", "windowResized"
    };

    StringBuilder out = new StringBuilder();
    out.append("#include \"Processing.h\"\n");
    // STL convenience using-declarations (sketch code can use vector, string, etc. without std::)
    out.append("using std::vector; using std::string; using std::wstring;\n");
    out.append("using std::pair; using std::make_pair; using std::tuple;\n");
    out.append("using std::deque; using std::list; using std::stack; using std::queue;\n");
    out.append("using std::unordered_map; using std::unordered_set;\n");
    out.append("using std::sort; using std::shuffle; using std::reverse;\n");
    out.append("using std::unique_ptr; using std::shared_ptr;\n");
    out.append("using std::make_unique; using std::make_shared;\n");
    out.append("using std::to_string; using std::stoi; using std::stof; using std::stod;\n");
    out.append("using std::cout; using std::cerr; using std::endl;\n");
    out.append("using std::ifstream; using std::ofstream; using std::stringstream;\n");

    // ── PApplet-based Sketch struct ──────────────────────────────────────────
    // All user globals become fields of Sketch, solving the hoisting / firstMousePress problem.
    // User lifecycle methods become virtual overrides.
    // Processing::run() is called via PApplet::run() which wires everything up.
    out.append("namespace Processing {\n");
    out.append("#include \"Processing_api.h\"\n");
    // _PSketch: empty virtual base injected into user-defined classes.
    // Inheriting from it gives access to Processing API via namespace lookup —
    // the free-function templates in Processing.h are already in namespace
    // Processing and are found via ADL/unqualified lookup from within user classes.
    out.append("struct _PSketch {\n");
    out.append("  struct _W   { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->logicalW    : 0;     } } width;\n");
    out.append("  struct _H   { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->logicalH    : 0;     } } height;\n");
    out.append("  struct _MX  { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseX      : 0.f;   } } mouseX;\n");
    out.append("  struct _MY  { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseY      : 0.f;   } } mouseY;\n");
    out.append("  struct _PMX { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->pmouseX     : 0.f;   } } pmouseX;\n");
    out.append("  struct _PMY { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->pmouseY     : 0.f;   } } pmouseY;\n");
    out.append("  struct _FC  { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->frameCount  : 0;     } } frameCount;\n");
    // rewriteBoolEventName() rewrites EVERY value-usage of bare "mousePressed"/
    // "keyPressed" (not followed by '(') to "_mousePressed"/"_keyPressed",
    // across the entire sketch source -- this runs BEFORE hoisting, so a
    // hoisted class's body can never contain a literal value-usage of the
    // un-underscored name; only "_mousePressed"/"_keyPressed" can appear.
    // _PSketch therefore intentionally exposes ONLY the underscored names --
    // exposing the un-underscored ones too would be dead surface area a
    // hoisted class could never legitimately reach.
    out.append("  struct _MP  { operator bool()  const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->_mousePressed : false; } } _mousePressed;\n");
    out.append("  struct _KP  { operator bool()  const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->_keyPressed  : false; } } _keyPressed;\n");
    out.append("  struct _K   { operator char()  const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->key          : 0;     } } key;\n");
    out.append("  struct _KC  { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->keyCode      : 0;     } } keyCode;\n");
    out.append("  struct _MB  { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseButton  : 0;     } } mouseButton;\n");
    out.append("  struct _DT  { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->deltaTime    : 0.f;   } } deltaTime;\n");
    out.append("  struct _FR  { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->_frameRate   : 0.f;   } } _frameRate;\n");
    out.append("  struct _MDX { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseDX      : 0.f;   } } mouseDX;\n");
    out.append("  struct _MDY { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseDY      : 0.f;   } } mouseDY;\n");
    out.append("  bool* keysDown()  const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->keysDown  : nullptr; }\n");
    out.append("  bool* mouseDown() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseDown : nullptr; }\n");
    out.append("};\n");
    out.append("\n");

    // Forward-declare any user helper functions so they can call each other
    // in any order (same as before, but scoped inside struct via friend trick --
    // actually: since they're methods, forward decls aren't needed; the struct
    // body is parsed in one pass. We skip forward decls for methods.)

    // Hoist class/struct definitions (user-defined types must come before fields that use them)
    String hoistedClasses = hoistClassesOnly(code);
    // Inject _PSketch base into user classes so they can call Processing API
    // Inject _PSketch into classes that don't already inherit from it
    hoistedClasses = hoistedClasses
      // Inject _PSketch into classes with base: skip forward decls (;) and constructor inits (()
      // Also inject "public:" right after the brace when the keyword is
      // "class" (not "struct") -- Java/Processing has no concept of
      // C++'s class-defaults-to-private rule. Source like
      // "class HScrollbar { int x; void update(){...} }" with no explicit
      // access specifier is meant to behave like Java, where members are
      // implicitly callable from outside the class. Without this, every
      // member silently becomes private and any external call
      // (hs1.update(), hs1.display(), etc.) fails to compile.
      .replaceAll("(?m)^(class\\s+\\w+\\s*:\\s*(?:public|private|protected)(?:\\s+\\w+)+)(?![^{]*[;(])\\s*\\{", "$1, public virtual _PSketch { public:")
      .replaceAll("(?m)^(struct\\s+\\w+\\s*:\\s*(?:public|private|protected)(?:\\s+\\w+)+)(?![^{]*[;(])\\s*\\{", "$1, public virtual _PSketch {")
      // Inject _PSketch into plain classes: skip forward decls and constructor inits
      .replaceAll("(?m)^(class\\s+\\w+)(?!\\s*:)(?![^{\\n]*[;(])\\s*\\{", "$1 : public virtual _PSketch { public:")
      .replaceAll("(?m)^(struct\\s+\\w+)(?!\\s*:)(?![^{\\n]*[;(])\\s*\\{", "$1 : public virtual _PSketch {");
    // Remove _PSketch from pure data structs (no methods — no return types before parens)
    // A data struct only has field declarations, no "type name(" method signatures
    hoistedClasses = removePSketchFromDataStructs(hoistedClasses);
    String restCode       = removeHoistedClasses(code);

    // #line directive so error line numbers point at sketch.pde
    out.append("#line 1 \"sketch.pde\"\n");
    headerLineCount = 0;

    // Count lines consumed by hoisted classes so #line stays accurate
    // (hoisted classes appear first in output, before restCode)
    int hoistedLines = hoistedClasses.split("\n", -1).length;

    if (!hasSetup && !hasDraw) {
      // ── Static sketch (no setup/draw) ───────────────────────────────────
      // Wrap in a Sketch : PApplet with a generated setup() that runs the code.
      StringBuilder settings = new StringBuilder();
      StringBuilder body     = new StringBuilder();
      for (String line : restCode.split("\n", -1)) {
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("size(") || trimmed.startsWith("fullScreen("))
          settings.append("        ").append(trimmed).append("\n");
        else
          body.append("        ").append(line).append("\n");
      }
      out.append(hoistedClasses);
      out.append("struct Sketch : public PApplet {\n");
      out.append("    void setup() override {\n");
      out.append(settings);
      out.append("        for (int _s=0; _s<8; _s++) { delay(1); }\n");
      out.append("        noLoop();\n");
      out.append(body);
      out.append("    }\n");
      out.append("    void draw() override {}\n");
      out.append("};\n");
    } else {
      // ── Normal sketch (has setup/draw) ───────────────────────────────────
      // Rewrite lifecycle function definitions as override methods.
      // Everything else (globals, helper functions) becomes Sketch fields/methods.
      // Extract namespace-scope items from restCode BEFORE rewriting:
      // constexpr vars/funcs, static_assert, enum/enum class, using declarations
      // enumScope: enum/enum class — must come FIRST (before classes that use them)
      // constexprScope: constexpr/static_assert — can come after hoisted classes
      // (since e.g. "constexpr RGBA RED{...}" needs RGBA to be defined first)
      StringBuilder enumScope = new StringBuilder();
      StringBuilder constexprScope = new StringBuilder();
      StringBuilder restCodeClean = new StringBuilder();
      {
        // Walk restCode once, char-by-char, so a multi-line "enum Name { ... };"
        // block is captured as a single unit (header + every body line + the
        // closing brace/semicolon), instead of a naive per-line startsWith()
        // scan that only ever matched the OPENING line and silently left the
        // enum's body (its enumerator list) behind in restCodeClean -- which
        // then ended up misplaced inside struct Sketch instead of staying
        // attached to its own header in enumScope.
        String rc = restCode;
        int n2 = rc.length(), i2 = 0;
        while (i2 < n2) {
          // Find start of current line
          int lineStart = i2;
          int lineEnd = rc.indexOf('\n', i2);
          if (lineEnd < 0) lineEnd = n2;
          String line = rc.substring(lineStart, lineEnd);
          String t = line.strip();

          boolean isEnumHeader = t.startsWith("enum class") || t.startsWith("enum struct") || t.startsWith("enum ");
          if (isEnumHeader && t.contains("{") ) {
            // Single-line-header case: walk forward from here tracking brace
            // depth until the block closes, regardless of how many lines it
            // spans, and append the WHOLE thing (header through closing ";")
            // to enumScope as one unit.
            int depth = 0, k = lineStart;
            boolean sawOpenBrace = false;
            while (k < n2) {
              char c = rc.charAt(k);
              if (c=='"') { k++; while (k<n2 && rc.charAt(k)!='"') { if(rc.charAt(k)=='\\') k++; k++; } }
              else if (c=='\'') { k++; while (k<n2 && rc.charAt(k)!='\'') { if(rc.charAt(k)=='\\') k++; k++; } }
              else if (c=='{') { depth++; sawOpenBrace = true; }
              else if (c=='}') { if (--depth==0 && sawOpenBrace) { k++; break; } }
              k++;
            }
            int end = k;
            while (end < n2 && (rc.charAt(end)==' ' || rc.charAt(end)=='\n')) end++;
            if (end < n2 && rc.charAt(end)==';') end++;
            enumScope.append(rc, lineStart, end).append("\n");
            i2 = end;
            continue;
          }

          boolean isConstexprScope =
            t.startsWith("static_assert") ||
            t.startsWith("using ") ||
            (t.startsWith("constexpr") && !t.contains("(") ) ||
            t.matches("constexpr\\s+\\w.*=.*") ||
            t.matches("constexpr\\s+\\w+\\s+\\w+\\s*\\(.*");
          // If this constexpr declares a value used elsewhere as an array
          // SIZE (e.g. "constexpr int N4 = 40;" followed by "float arr[N4];"),
          // do NOT extract it here -- leave it in restCodeClean so the
          // dedicated array-hoisting pass (classifyTopLevelDecls-based,
          // further down) hoists it in the correct order relative to the
          // array it sizes. Splitting a sizing constant and its array
          // across two emission buckets (constexprScope vs hoistedArrays)
          // makes their relative emission order impossible to control.
          if (isConstexprScope && t.startsWith("constexpr")) {
            java.util.regex.Matcher cnm = java.util.regex.Pattern.compile("constexpr\\s+[A-Za-z_]\\w*\\s+([A-Za-z_]\\w*)\\s*=").matcher(t);
            if (cnm.find()) {
              String declaredName = cnm.group(1);
              boolean usedAsArraySize = java.util.regex.Pattern.compile(
                "\\[\\s*" + java.util.regex.Pattern.quote(declaredName) + "\\s*\\]").matcher(rc).find();
              if (usedAsArraySize) isConstexprScope = false;
            }
          }
          if (isConstexprScope) {
            constexprScope.append(line).append("\n");
          } else {
            restCodeClean.append(line).append("\n");
          }
          i2 = (lineEnd < n2) ? lineEnd + 1 : n2;
        }
      }
      restCode = restCodeClean.toString();

      String transformed = rewriteAsSketchMethods(restCode, lifecycleMethods);

      // Extract forward declarations from hoistedClasses
      StringBuilder fwdDecls = new StringBuilder();
      StringBuilder hoistedNoFwd = new StringBuilder();
      for (String line : hoistedClasses.split("\n", -1)) {
        String t = line.strip();
        String tNC = t.contains("//") ? t.substring(0, t.indexOf("//")).strip() : t;
        if ((tNC.startsWith("class ") || tNC.startsWith("struct ")) && tNC.endsWith(";")
            && !tNC.contains("{")) {
          fwdDecls.append(line).append("\n");
        } else {
          hoistedNoFwd.append(line).append("\n");
        }
      }

      // From transformed: extract
      //   (a) forward declarations — "class Foo;" lines  
      //   (b) static member definitions — "Type Foo::bar = val;"
      // Both must live at namespace scope, not inside Sketch
      StringBuilder staticDefs = new StringBuilder();
      StringBuilder transformedClean = new StringBuilder();
      for (String line : transformed.split("\n", -1)) {
        String t = line.strip();
        // Forward declaration: class/struct Foo; (strip inline comments first)
        String tNoComment = t.contains("//") ? t.substring(0, t.indexOf("//")).strip() : t;
        boolean isFwdDecl = (tNoComment.startsWith("class ") || tNoComment.startsWith("struct "))
                            && tNoComment.endsWith(";") && !tNoComment.contains("{");
        // Static member def: contains "::" and "=" but no "(", not a comment
        boolean isStaticDef = !t.startsWith("//") && !t.contains("(")
                              && t.contains("::") && t.contains("=")
                              && t.matches("[A-Za-z_][\\w:<>*& ]*::[A-Za-z_]\\w*\\s*=.*");
        if (isFwdDecl) {
          fwdDecls.append(line).append("\n");
        } else if (isStaticDef) {
          staticDefs.append(line).append("\n");
        } else {
          transformedClean.append(line).append("\n");
        }
      }

      // ── Hoist free functions that hoisted classes depend on ──────────
      // A class hoisted to namespace scope (above Sketch) cannot call a free
      // function that still lives inside Sketch. Detect such functions by
      // scanning transformedClean for top-level "RetType name(...) { ... }"
      // blocks (4-space indented, not a lifecycle method) whose name is
      // referenced as a call ("name(") inside the hoisted class text, and
      // move them to namespace scope alongside the classes. Iterate to a
      // fixed point since a hoisted function may itself call another
      // currently-unhoisted helper.
      StringBuilder hoistedFunctions = new StringBuilder();
      java.util.Set<String> lifecycleSet = new java.util.HashSet<>(java.util.Arrays.asList(lifecycleMethods));
      boolean hoistedSomething = true;
      while (hoistedSomething) {
        hoistedSomething = false;
        // Find candidate top-level function definitions in transformedClean:
        // lines starting (after 4-space indent) with a return type, a name,
        // "(", ending the signature line with "{" (single or multi-line body).
        java.util.regex.Pattern fnPat = java.util.regex.Pattern.compile(
          "(?m)^    ((?:[A-Za-z_][\\w:<>,\\s\\*&]*?)\\s+([A-Za-z_]\\w*)\\s*\\([^;{]*\\)\\s*)\\{");
        java.util.regex.Matcher fm = fnPat.matcher(transformedClean.toString());
        while (fm.find()) {
          String fnName = fm.group(2);
          if (lifecycleSet.contains(fnName)) continue;
          // Only hoist if this function is actually called from inside the
          // already-hoisted class text (or previously hoisted functions).
          String haystack = hoistedNoFwd.toString() + hoistedFunctions.toString();
          if (!java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(fnName) + "\\s*\\(").matcher(haystack).find())
            continue;
          // Skip if this exact signature was already hoisted (avoid loops)
          if (hoistedFunctions.indexOf(fm.group(1)) >= 0) continue;
          // Extract the full function body starting at fm.start() (depth-matched braces)
          int bodyOpen = fm.end() - 1; // index of the matched '{'
          String src2 = transformedClean.toString();
          int depth = 0, k = bodyOpen;
          int n2 = src2.length();
          while (k < n2) {
            char c = src2.charAt(k);
            if (c=='"') { k++; while (k<n2 && src2.charAt(k)!='"') { if(src2.charAt(k)=='\\') k++; k++; } }
            else if (c=='\'') { k++; while (k<n2 && src2.charAt(k)!='\'') { if(src2.charAt(k)=='\\') k++; k++; } }
            else if (c=='{') depth++;
            else if (c=='}') { if (--depth==0) { k++; break; } }
            k++;
          }
          int fnStart = fm.start();
          // Walk fnStart back to the start of its line to capture full indent
          int lineStart = src2.lastIndexOf('\n', fnStart) + 1;
          String fullFn = src2.substring(lineStart, k);
          // Dedent by 4 spaces (it was inside Sketch at 4-space indent)
          String dedented = fullFn.replaceAll("(?m)^    ", "");
          hoistedFunctions.append(dedented).append("\n");
          // Remove from transformedClean
          transformedClean = new StringBuilder(src2.substring(0, lineStart) + src2.substring(k));
          hoistedSomething = true;
          break; // restart scan since transformedClean changed
        }
      }

      // ── Hoist global C-array declarations to namespace scope, unconditionally ──
      // C-style arrays ("Type name[N];" or "Type name[] = {...};") work
      // exactly like Java arrays IF they stay at true global/namespace
      // scope: zero-initialized automatically (static storage duration),
      // and any const/constexpr identifier used as the size is a genuine
      // compile-time constant. Both guarantees disappear the moment such a
      // declaration becomes a non-static Sketch member field instead.
      // Uses classifyTopLevelDecls() (a single character-walking scan,
      // shared with other hoisting passes) instead of bespoke regex --
      // every array-declaration shape (with/without initializer, literal
      // vs. named-constant size, const/constexpr/static const sizing) is
      // handled uniformly since the classifier already extracted
      // typeName/name/sizeExpr/isConst correctly for each declaration.
      StringBuilder hoistedArrays = new StringBuilder();
      {
        java.util.List<TopLevelDecl> decls = classifyTopLevelDecls(transformedClean.toString());
        java.util.Set<String> arraySizeIdentifiers = new java.util.HashSet<>();
        for (TopLevelDecl d : decls) {
          if (d.kind == TopLevelDecl.Kind.ARRAY_VAR && d.sizeExpr != null && !d.sizeExpr.isEmpty()
              && !d.sizeExpr.matches("\\d+")) {
            arraySizeIdentifiers.add(d.sizeExpr);
          }
        }

        // PHASE 1: hoist every sizing constant FIRST, in its own complete
        // pass, so they always precede the arrays that need them in
        // hoistedArrays regardless of original source order or how many
        // arrays reference the same constant.
        boolean changed = true;
        while (changed) {
          changed = false;
          java.util.List<TopLevelDecl> current = classifyTopLevelDecls(transformedClean.toString());
          for (TopLevelDecl d : current) {
            if (d.kind == TopLevelDecl.Kind.PLAIN_VAR && d.isConst
                && d.name != null && arraySizeIdentifiers.contains(d.name)) {
              hoistedArrays.append(d.fullText.replaceAll("(?m)^    ", ""));
              String src = transformedClean.toString();
              transformedClean = new StringBuilder(src.substring(0, d.startPos) + src.substring(d.endPos));
              changed = true;
              break;
            }
          }
        }

        // PHASE 2: hoist every array declaration, now that all sizing
        // constants are already in hoistedArrays ahead of them.
        changed = true;
        while (changed) {
          changed = false;
          java.util.List<TopLevelDecl> current = classifyTopLevelDecls(transformedClean.toString());
          for (TopLevelDecl d : current) {
            if (d.kind == TopLevelDecl.Kind.ARRAY_VAR) {
              hoistedArrays.append(d.fullText.replaceAll("(?m)^    ", ""));
              String src = transformedClean.toString();
              transformedClean = new StringBuilder(src.substring(0, d.startPos) + src.substring(d.endPos));
              changed = true;
              break;
            }
          }
        }
      }

      // ── Fix C++'s "most vexing parse" for object direct-initialization ──
      // "Eye e1(250, 16, 120);" is grammatically ambiguous in C++ at this
      // scope -- the compiler resolves it as a MEMBER FUNCTION DECLARATION
      // named e1 taking (int,int,int) and returning Eye, not as "construct
      // an Eye object named e1 with these initial values," which is what
      // the sketch author actually meant. Rewriting the parens to braces
      // -- "Eye e1{250, 16, 120};" -- uses direct-list-initialization
      // instead, which has no most-vexing-parse ambiguity in C++.
      {
        boolean changedDI = true;
        while (changedDI) {
          changedDI = false;
          java.util.List<TopLevelDecl> current = classifyTopLevelDecls(transformedClean.toString());
          for (TopLevelDecl d : current) {
            if (d.kind == TopLevelDecl.Kind.OBJECT_DIRECT_INIT) {
              String rewritten = d.fullText.replaceFirst("\\(", "{").replaceFirst("\\)\\s*;", "};");
              String src = transformedClean.toString();
              transformedClean = new StringBuilder(src.substring(0, d.startPos) + rewritten + src.substring(d.endPos));
              changedDI = true;
              break;
            }
          }
        }
      }

      // ── Hoist plain global variables that hoisted classes depend on ──
      // Same problem as free functions above, but for plain data: a class
      // hoisted to namespace scope cannot see a global variable (e.g.
      // "bool firstMousePress = false;") that still lives inside Sketch as
      // a member field. Detect single-line "Type name [= value];"
      // declarations in transformedClean whose name is referenced from
      // inside the hoisted class text, and move them to namespace scope.
      // Both Sketch's own methods and the hoisted class still see the SAME
      // variable either way (there's exactly one Sketch instance per
      // program), so this is purely about visibility, not aliasing.
      StringBuilder hoistedVariables = new StringBuilder();
      {
        boolean hoistedVarSomething = true;
        while (hoistedVarSomething) {
          hoistedVarSomething = false;
          // Match: 4-space-indented "Type name = expr;" or "Type name;"
          // Deliberately conservative: single identifier name, simple
          // initializer with no semicolons inside it (covers the common
          // "bool flag = false;" / "int counter = 0;" / "float t = 0;"
          // case without trying to parse arbitrary C++ initializers).
          java.util.regex.Pattern varPat = java.util.regex.Pattern.compile(
            "(?m)^    ([A-Za-z_][\\w:<>,\\s\\*&]*?\\s+([A-Za-z_]\\w*)\\s*(?:=[^;]*)?;)\\s*$");
          java.util.regex.Matcher vm = varPat.matcher(transformedClean.toString());
          while (vm.find()) {
            String varName = vm.group(2);
            if (lifecycleSet.contains(varName)) continue;
            String haystack = hoistedNoFwd.toString() + hoistedFunctions.toString();
            if (!java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(varName) + "\\b").matcher(haystack).find())
              continue;
            // Avoid re-hoisting the same declaration if seen again
            if (hoistedVariables.indexOf(vm.group(1)) >= 0) continue;
            String src3 = transformedClean.toString();
            int lineStart = src3.lastIndexOf('\n', vm.start()) + 1;
            int lineEnd = src3.indexOf('\n', vm.end());
            if (lineEnd < 0) lineEnd = src3.length(); else lineEnd += 1;
            String fullLine = src3.substring(lineStart, lineEnd);
            String dedented = fullLine.replaceAll("(?m)^    ", "");
            hoistedVariables.append(dedented);
            transformedClean = new StringBuilder(src3.substring(0, lineStart) + src3.substring(lineEnd));
            hoistedVarSomething = true;
            break;
          }
        }
      }

      // Forward-declare every hoisted function before the hoisted classes,
      // so a class method (e.g. Agent::update calling dirToVec) compiles
      // even though the class itself appears earlier in the file than the
      // function's full definition.
      StringBuilder hoistedFnForwardDecls = new StringBuilder();
      {
        java.util.regex.Pattern sigPat = java.util.regex.Pattern.compile(
          "(?m)^((?:[A-Za-z_][\\w:<>,\\s\\*&]*?)\\s+([A-Za-z_]\\w*)\\s*\\([^;{]*\\))\\s*\\{");
        java.util.regex.Matcher sm = sigPat.matcher(hoistedFunctions.toString());
        while (sm.find()) {
          hoistedFnForwardDecls.append(sm.group(1)).append(";\n");
        }
      }

      out.append(fwdDecls);              // forward decls before everything
      out.append(enumScope);             // enums first - classes may use them
      out.append(hoistedVariables);      // plain global vars hoisted classes reference
      out.append(hoistedArrays);         // C-array decls + their sizing consts, unconditionally hoisted
      out.append(hoistedFnForwardDecls); // forward-declare hoisted functions
      out.append(hoistedNoFwd);          // hoisted classes (may call the functions/vars above)
      out.append(hoistedFunctions);      // full function definitions
      out.append(constexprScope);        // constexpr vars/funcs, static_assert (may use classes above)
      out.append("struct Sketch : public PApplet {\n");
      out.append(transformedClean).append("\n");
      out.append("};\n");
      out.append(staticDefs);            // static member defs after Sketch closes
    }

    // ── Free function forwarding ─────────────────────────────────────────────
    // Processing::run() calls setup() and draw() as free functions in the namespace.
    // We forward those to the active PApplet instance (set by PApplet::run()).
    out.append("void setup() { if(PApplet::g_papplet) PApplet::g_papplet->setup(); }\n");
    out.append("void draw()  { if(PApplet::g_papplet) PApplet::g_papplet->draw();  }\n");
    out.append("void settings() { if(PApplet::g_papplet) PApplet::g_papplet->settings(); }\n");

    // ── main() ──────────────────────────────────────────────────────────────
    out.append("} // namespace Processing\n");
    out.append("\n");
    out.append("#ifdef _WIN32\n");
    out.append("#include <windows.h>\n");
    out.append("int WINAPI WinMain(HINSTANCE,HINSTANCE,LPSTR,int) {\n");
    out.append("    Processing::Sketch sketch;\n");
    out.append("    sketch.run();\n");
    out.append("    return 0;\n");
    out.append("}\n");
    out.append("#else\n");
    out.append("int main(int argc, char** argv) {\n");
    out.append("    Processing::Sketch sketch;\n");
    out.append("    sketch.run();\n");
    out.append("    return 0;\n");
    out.append("}\n");
    out.append("#endif\n");

    File dest = new File(buildDir, "Sketch_run.cpp");
    Files.writeString(dest.toPath(), out.toString());
    return dest;
  }

  // Hoist only class/struct definitions (same logic as hoistClasses but returns just the class blocks)
  /**
   * Structured representation of one top-level declaration found by
   * classifyTopLevelDecls(). Every later hoisting pass (classes, enums,
   * functions, arrays, plain variables) should consume THIS shared list
   * instead of re-scanning raw text with its own bespoke regex -- the
   * scanning (comment/string-skipping, brace/bracket-depth tracking) is
   * done exactly ONCE here, correctly, and every downstream pass just
   * filters/reads already-extracted fields instead of re-deriving them.
   */
  static class TopLevelDecl {
    enum Kind { CLASS_OR_STRUCT, ENUM, FUNCTION, ARRAY_VAR, PLAIN_VAR, OBJECT_DIRECT_INIT, OTHER }
    Kind kind;
    String fullText;   // exact original source text of this declaration, including trailing ;
    String typeName;   // element/return type (best-effort)
    String name;       // variable/function/class name
    String sizeExpr;   // ARRAY_VAR only: text inside [ ] -- empty string, a digit, or an identifier
    boolean isConst;    // PLAIN_VAR only: declared with const/constexpr/static (any combination)
    int startPos, endPos; // position in the source this was found in
  }

  /**
   * Walk `code` once, character-by-character, skipping comments and
   * string/char literals correctly, and classify every top-level
   * (4-space-indented, i.e. inside struct Sketch) statement into a
   * TopLevelDecl. This does NOT hoist anything itself -- it just produces
   * a structured list that hoisting passes can filter and act on without
   * needing their own regex.
   */
  /**
   * Returns a copy of `code` with the CONTENTS of every line comment,
   * block comment, and string/char literal replaced by spaces of the
   * same length (newlines preserved as-is). Every character's POSITION
   * is unchanged -- only what's inside comments/literals is blanked out.
   *
   * This exists so that any regex-based scan (the kind CppBuild has many
   * of, for detecting things like "name = new Type(...)" assignments)
   * can run against the RETURN VALUE of this function instead of raw
   * `code`, and be automatically immune to false-positive matches inside
   * comments or string literals -- e.g. a comment that says
   * "// like obj = new Foo()" can no longer be mistaken for a real
   * assignment statement, since by the time the regex runs, that text
   * has already been replaced with blank spaces.
   *
   * This does NOT replace classifyTopLevelDecls() for detecting actual
   * DECLARATIONS (which needs real structural parsing -- brace/bracket
   * depth, statement boundaries) -- it's a lighter-weight tool for scans
   * that only need "find text matching this shape, but not inside a
   * comment or string," which covers most of CppBuild's regex-based
   * passes that aren't already using the classifier.
   */
  private static String blankCommentsAndLiterals(String code) {
    StringBuilder out = new StringBuilder(code.length());
    int n = code.length(), i = 0;
    while (i < n) {
      char c = code.charAt(i);
      if (c == '/' && i+1 < n && code.charAt(i+1) == '/') {
        int eol = code.indexOf('\n', i);
        int end = (eol < 0) ? n : eol;
        for (int k = i; k < end; k++) out.append(' ');
        i = end;
        continue;
      }
      if (c == '/' && i+1 < n && code.charAt(i+1) == '*') {
        int close = code.indexOf("*/", i+2);
        int end = (close < 0) ? n : close + 2;
        for (int k = i; k < end; k++) out.append(code.charAt(k) == '\n' ? '\n' : ' ');
        i = end;
        continue;
      }
      if (c == '"') {
        int j = i + 1;
        while (j < n && code.charAt(j) != '"') { if (code.charAt(j) == '\\') j++; j++; }
        int end = Math.min(j + 1, n);
        for (int k = i; k < end; k++) out.append(code.charAt(k) == '\n' ? '\n' : ' ');
        i = end;
        continue;
      }
      if (c == '\'') {
        int j = i + 1;
        while (j < n && code.charAt(j) != '\'') { if (code.charAt(j) == '\\') j++; j++; }
        int end = Math.min(j + 1, n);
        for (int k = i; k < end; k++) out.append(code.charAt(k) == '\n' ? '\n' : ' ');
        i = end;
        continue;
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  private static java.util.List<TopLevelDecl> classifyTopLevelDecls(String code) {
    java.util.List<TopLevelDecl> result = new java.util.ArrayList<>();
    int n = code.length(), i = 0;
    while (i < n) {
      // Skip blank/whitespace-only stretches between statements
      int lineStart = i;
      // Only consider lines starting with exactly 4-space indent (Sketch body)
      if (i + 4 > n || !code.startsWith("    ", i) ||
          (i > 0 && code.charAt(i-1) != '\n' && i != 0)) {
        i++;
        continue;
      }
      // Confirm this position is truly the start of a line
      if (i > 0 && code.charAt(i-1) != '\n') { i++; continue; }

      int contentStart = i + 4;
      if (contentStart >= n) break;
      char c0 = code.charAt(contentStart);
      // Skip comment-only or blank lines entirely
      if (c0 == '\n') { i = contentStart + 1; continue; }
      if (c0 == '/' && contentStart+1 < n && code.charAt(contentStart+1) == '/') {
        int eol = code.indexOf('\n', contentStart);
        i = (eol < 0) ? n : eol + 1;
        continue;
      }

      // Skip a block comment that starts this line, INCLUDING multi-line
      // ones (e.g. a "/**...*/" Javadoc-style header comment at the top of
      // a sketch). Without this, the comment's opening line is treated as
      // the start of "the next statement," and since the main scanning
      // loop below skips comments internally without resetting what
      // counts as the declaration's start, the comment and the REAL
      // following statement (e.g. "const int num = 60;") get merged into
      // one unrecognized OTHER blob -- making that constant invisible to
      // every later pass that needs to find it by name.
      if (c0 == '/' && contentStart+1 < n && code.charAt(contentStart+1) == '*') {
        int end = code.indexOf("*/", contentStart+2);
        i = (end < 0) ? n : end + 2;
        while (i < n && code.charAt(i) != '\n' && (code.charAt(i)==' '||code.charAt(i)=='\t')) i++;
        if (i < n && code.charAt(i) == '\n') i++;
        continue;
      }
      // Find the end of this logical statement: walk forward tracking
      // (), [], {}, and skipping strings/chars/comments, until we hit a
      // top-level ';' or '{' (the latter meaning class/enum/function body).
      int depthParen = 0, depthBracket = 0, depthBrace = 0;
      int j = contentStart;
      int firstBrace = -1, firstBracket = -1, firstParen = -1;
      while (j < n) {
        char c = code.charAt(j);
        if (c == '/' && j+1 < n && code.charAt(j+1) == '/') {
          int eol = code.indexOf('\n', j); j = (eol < 0) ? n : eol; continue;
        }
        if (c == '/' && j+1 < n && code.charAt(j+1) == '*') {
          int end = code.indexOf("*/", j+2); j = (end < 0) ? n : end + 2; continue;
        }
        if (c == '"') {
          j++; while (j < n && code.charAt(j) != '"') { if (code.charAt(j) == '\\') j++; j++; } j++; continue;
        }
        if (c == '\'') {
          j++; while (j < n && code.charAt(j) != '\'') { if (code.charAt(j) == '\\') j++; j++; } j++; continue;
        }
        if (c == '(') { if (depthParen==0 && firstParen<0) firstParen=j; depthParen++; }
        else if (c == ')') depthParen--;
        else if (c == '[') { if (depthBracket==0 && firstBracket<0) firstBracket=j; depthBracket++; }
        else if (c == ']') depthBracket--;
        else if (c == '{') {
          if (depthBrace==0 && firstBrace<0) firstBrace=j;
          depthBrace++;
        } else if (c == '}') {
          depthBrace--;
          if (depthBrace == 0 && firstBrace >= 0) { j++; break; } // end of a {...} body
        } else if (c == ';' && depthParen==0 && depthBracket==0 && depthBrace==0) {
          j++; break; // end of a simple statement
        }
        j++;
      }
      // Consume trailing ';' after a '{...}' body if present (class/struct Foo {...};)
      int stmtEnd = j;
      { int k = stmtEnd; while (k < n && (code.charAt(k)==' '||code.charAt(k)=='\t')) k++;
        if (k < n && code.charAt(k) == ';') stmtEnd = k + 1; }

      String stmtText = code.substring(contentStart, stmtEnd);
      String fullLineText = code.substring(lineStart, stmtEnd);
      // Consume the trailing newline too, if present, so re-insertion is clean
      int afterEnd = stmtEnd;
      if (afterEnd < n && code.charAt(afterEnd) == '\n') afterEnd++;

      TopLevelDecl d = new TopLevelDecl();
      d.fullText = code.substring(lineStart, afterEnd);
      d.startPos = lineStart;
      d.endPos = afterEnd;

      String trimmed = stmtText.strip();
      if (trimmed.startsWith("class ") || trimmed.startsWith("struct ")) {
        d.kind = TopLevelDecl.Kind.CLASS_OR_STRUCT;
        java.util.regex.Matcher nm = java.util.regex.Pattern.compile("^(?:class|struct)\\s+([A-Za-z_]\\w*)").matcher(trimmed);
        if (nm.find()) d.name = nm.group(1);
      } else if (trimmed.startsWith("enum ")) {
        d.kind = TopLevelDecl.Kind.ENUM;
        java.util.regex.Matcher nm = java.util.regex.Pattern.compile("^enum(?:\\s+class|\\s+struct)?\\s+([A-Za-z_]\\w*)").matcher(trimmed);
        if (nm.find()) d.name = nm.group(1);
      } else if (firstParen >= 0 && firstBrace >= 0 && firstParen < firstBrace
                 && (firstBracket < 0 || firstParen < firstBracket)) {
        // "RetType name(...) { ... }" -- a function definition
        d.kind = TopLevelDecl.Kind.FUNCTION;
        java.util.regex.Matcher nm = java.util.regex.Pattern.compile(
          "^(?:[A-Za-z_][\\w:<>,\\s\\*&]*?)\\s+([A-Za-z_]\\w*)\\s*\\(").matcher(trimmed);
        if (nm.find()) d.name = nm.group(1);
      } else if (firstBracket >= 0 && (firstBrace < 0 || firstBracket < firstBrace)
                 && (firstParen < 0 || firstBracket < firstParen)) {
        // "Type name[sizeExpr];" or "Type name[sizeExpr] = {...};" -- an array
        d.kind = TopLevelDecl.Kind.ARRAY_VAR;
        java.util.regex.Matcher am = java.util.regex.Pattern.compile(
          "^([A-Za-z_]\\w*)\\s+([A-Za-z_]\\w*)\\s*\\[\\s*([A-Za-z_0-9]*)\\s*\\]").matcher(trimmed);
        if (am.find()) { d.typeName = am.group(1); d.name = am.group(2); d.sizeExpr = am.group(3); }
      } else if (firstParen >= 0 && firstBrace < 0) {
        // "Type name(arg1, arg2, ...);" -- C++'s "most vexing parse":
        // direct-initialization syntax is grammatically indistinguishable
        // from a member-FUNCTION DECLARATION at this scope, and C++
        // resolves the ambiguity in favor of "it's a function declaration"
        // every time. Detected here so a later pass can rewrite the parens
        // to braces (direct-list-initialization), which has no such
        // ambiguity in C++.
        d.kind = TopLevelDecl.Kind.OBJECT_DIRECT_INIT;
        java.util.regex.Matcher om = java.util.regex.Pattern.compile(
          "^([A-Za-z_]\\w*)\\s+([A-Za-z_]\\w*)\\s*\\(").matcher(trimmed);
        if (om.find()) { d.typeName = om.group(1); d.name = om.group(2); }
      } else {
        // Plain variable declaration: "Type name [= expr];"
        java.util.regex.Matcher vm = java.util.regex.Pattern.compile(
          "^((?:const\\s+|constexpr\\s+|static\\s+)*)([A-Za-z_][\\w:<>,\\s\\*&]*?)\\s+([A-Za-z_]\\w*)\\s*(?:=.*)?$").matcher(trimmed.replaceAll(";\\s*$",""));
        if (vm.find()) {
          d.kind = TopLevelDecl.Kind.PLAIN_VAR;
          d.isConst = !vm.group(1).isBlank();
          d.typeName = vm.group(2);
          d.name = vm.group(3);
        } else {
          d.kind = TopLevelDecl.Kind.OTHER;
        }
      }
      result.add(d);
      i = afterEnd;
    }
    return result;
  }

  private String hoistClassesOnly(String code) {
    java.util.List<String> classBlocks = new java.util.ArrayList<>();
    int n = code.length(), i = 0;
    while (i < n) {
      if (i+1 < n && code.charAt(i)=='/' && code.charAt(i+1)=='/') {
        while (i < n && code.charAt(i) != '\n') i++; continue;
      }
      if (i+1 < n && code.charAt(i)=='/' && code.charAt(i+1)=='*') {
        i += 2; while (i+1 < n && !(code.charAt(i)=='*' && code.charAt(i+1)=='/')) i++; i += 2; continue;
      }
      if (code.charAt(i)=='"') {
        i++; while (i < n && code.charAt(i)!='"') { if (code.charAt(i)=='\\') i++; i++; } i++; continue;
      }
      if (code.charAt(i)=='\'') {
        i++; while (i < n && code.charAt(i)!='\'') { if (code.charAt(i)=='\\') i++; i++; } i++; continue;
      }
      // Plain "enum Name { ... };" (no "class"/"struct" after "enum") must
      // still be walked-past correctly here, even though it isn't hoisted by
      // THIS scanner (enumScope, extracted later from restCode, owns enums).
      // Without this, the scanner has no concept of "enum" at all, leaves
      // its position pointer sitting in the middle of the enum's body text,
      // and the next "class"/"struct" token it stumbles across downstream
      // gets mistakenly treated as starting exactly where the scan resumed --
      // silently corrupting both the enum and whatever class came after it.
      boolean isPlainEnum = i+5 <= n && code.substring(i, Math.min(i+5,n)).equals("enum ")
                            && !(i+11 <= n && code.substring(i+5, Math.min(i+11,n)).equals("class "))
                            && !(i+12 <= n && code.substring(i+5, Math.min(i+12,n)).equals("struct "));
      if (isPlainEnum) {
        char before = i > 0 ? code.charAt(i-1) : ' ';
        if (!Character.isLetterOrDigit(before) && before != '_') {
          int open = i;
          while (open < n && code.charAt(open) != '{' && code.charAt(open) != ';') open++;
          if (open < n && code.charAt(open) == '{') {
            int depth = 0, k = open;
            while (k < n) {
              char c = code.charAt(k);
              if (c=='"') { k++; while (k<n && code.charAt(k)!='"') { if(code.charAt(k)=='\\') k++; k++; } }
              else if (c=='\'') { k++; while (k<n && code.charAt(k)!='\'') { if(code.charAt(k)=='\\') k++; k++; } }
              else if (c=='{') depth++;
              else if (c=='}') { if (--depth==0) { k++; break; } }
              k++;
            }
            int end = k;
            while (end < n && (code.charAt(end)==' ' || code.charAt(end)=='\n')) end++;
            if (end < n && code.charAt(end)==';') end++;
            i = end; continue; // skip past the whole enum block; do NOT hoist it here
          }
        }
      }
      boolean isClass  = i+5 <= n && code.substring(i, Math.min(i+5,n)).equals("class");
      boolean isStruct = i+6 <= n && code.substring(i, Math.min(i+6,n)).equals("struct");
      if (isClass || isStruct) {
        int klen = isClass ? 5 : 6;
        char before = i > 0 ? code.charAt(i-1) : ' ';
        char after  = i+klen < n ? code.charAt(i+klen) : ' ';
        // Skip "enum class"/"enum struct" -- handled separately as enumScope
        boolean precededByEnum = i >= 5 && code.substring(i-5,i).equals("enum ");
        if (!precededByEnum &&
            !Character.isLetterOrDigit(before) && before != '_' &&
            !Character.isLetterOrDigit(after)  && after  != '_') {
          int open = i;
          while (open < n && code.charAt(open) != '{' && code.charAt(open) != ';') open++;
          if (open < n && code.charAt(open) == '{') {
            int depth = 0, k = open;
            while (k < n) {
              char c = code.charAt(k);
              if (c=='"') { k++; while (k<n && code.charAt(k)!='"') { if(code.charAt(k)=='\\') k++; k++; } }
              else if (c=='\'') { k++; while (k<n && code.charAt(k)!='\'') { if(code.charAt(k)=='\\') k++; k++; } }
              else if (c=='{') depth++;
              else if (c=='}') { if (--depth==0) { k++; break; } }
              k++;
            }
            int end = k;
            while (end < n && (code.charAt(end)==' ' || code.charAt(end)=='\n')) end++;
            if (end < n && code.charAt(end)==';') end++;
            // Carry template<...> prefix if it immediately precedes this class
            String classText = code.substring(i, end);
            // Java/Processing classes don't require a trailing ";" after the
            // closing brace; C++ does. If the source omitted it, add one now so
            // the hoisted class is valid standalone C++.
            if (!classText.strip().endsWith(";")) classText = classText.stripTrailing() + ";\n";
            String templatePrefix = "";
            // Look back in code for a template<...> line immediately before position i
            int lookback = i - 1;
            while (lookback > 0 && (code.charAt(lookback)==' ' || code.charAt(lookback)=='\n' || code.charAt(lookback)=='\r')) lookback--;
            int lineEnd = lookback;
            while (lookback > 0 && code.charAt(lookback) != '\n') lookback--;
            String prevLine = code.substring(lookback > 0 ? lookback+1 : 0, lineEnd+1).strip();
            if (prevLine.startsWith("template")) {
              templatePrefix = prevLine + "\n";
            }
            classBlocks.add(templatePrefix + classText);
            i = end; continue;
          }
        }
      }
      i++;
    }
    // Sort: base classes before derived
    boolean changed = true;
    for (int pass = 0; pass < classBlocks.size()*2 && changed; pass++) {
      changed = false;
      for (int a = 0; a < classBlocks.size(); a++) {
        String blockA = classBlocks.get(a);
        java.util.regex.Matcher mA = java.util.regex.Pattern.compile(
          "(?:class|struct)\\s+\\w+\\s*:\\s*(?:public|protected|private)\\s+(\\w+)").matcher(blockA);
        if (!mA.find()) continue;
        String aBase = mA.group(1);
        for (int b = a+1; b < classBlocks.size(); b++) {
          String blockB = classBlocks.get(b);
          java.util.regex.Matcher mB = java.util.regex.Pattern.compile(
            "(?:class|struct)\\s+(\\w+)").matcher(blockB);
          if (mB.find() && mB.group(1).equals(aBase)) {
            classBlocks.set(a, blockB); classBlocks.set(b, blockA);
            changed = true; break;
          }
        }
        if (changed) break;
      }
    }
    StringBuilder out = new StringBuilder();
    for (String b : classBlocks) out.append(b).append("\n\n");
    return out.toString();
  }

  // Remove the class blocks that hoistClassesOnly() extracted, leaving the rest
  private String removeHoistedClasses(String code) {
    // Re-run the same extraction logic and blank out those ranges
    java.util.List<int[]> ranges = new java.util.ArrayList<>();
    int n = code.length(), i = 0;
    while (i < n) {
      if (i+1 < n && code.charAt(i)=='/' && code.charAt(i+1)=='/') {
        while (i < n && code.charAt(i) != '\n') i++; continue;
      }
      if (i+1 < n && code.charAt(i)=='/' && code.charAt(i+1)=='*') {
        i += 2; while (i+1 < n && !(code.charAt(i)=='*' && code.charAt(i+1)=='/')) i++; i += 2; continue;
      }
      if (code.charAt(i)=='"') {
        i++; while (i < n && code.charAt(i)!='"') { if (code.charAt(i)=='\\') i++; i++; } i++; continue;
      }
      if (code.charAt(i)=='\'') {
        i++; while (i < n && code.charAt(i)!='\'') { if (code.charAt(i)=='\\') i++; i++; } i++; continue;
      }
      // Plain "enum Name { ... };" (no "class"/"struct" after "enum") must
      // still be walked-past correctly here, even though it isn't hoisted by
      // THIS scanner (enumScope, extracted later from restCode, owns enums).
      // Without this, the scanner has no concept of "enum" at all, leaves
      // its position pointer sitting in the middle of the enum's body text,
      // and the next "class"/"struct" token it stumbles across downstream
      // gets mistakenly treated as starting exactly where the scan resumed --
      // silently corrupting both the enum and whatever class came after it.
      boolean isPlainEnum = i+5 <= n && code.substring(i, Math.min(i+5,n)).equals("enum ")
                            && !(i+11 <= n && code.substring(i+5, Math.min(i+11,n)).equals("class "))
                            && !(i+12 <= n && code.substring(i+5, Math.min(i+12,n)).equals("struct "));
      if (isPlainEnum) {
        char before = i > 0 ? code.charAt(i-1) : ' ';
        if (!Character.isLetterOrDigit(before) && before != '_') {
          int open = i;
          while (open < n && code.charAt(open) != '{' && code.charAt(open) != ';') open++;
          if (open < n && code.charAt(open) == '{') {
            int depth = 0, k = open;
            while (k < n) {
              char c = code.charAt(k);
              if (c=='"') { k++; while (k<n && code.charAt(k)!='"') { if(code.charAt(k)=='\\') k++; k++; } }
              else if (c=='\'') { k++; while (k<n && code.charAt(k)!='\'') { if(code.charAt(k)=='\\') k++; k++; } }
              else if (c=='{') depth++;
              else if (c=='}') { if (--depth==0) { k++; break; } }
              k++;
            }
            int end = k;
            while (end < n && (code.charAt(end)==' ' || code.charAt(end)=='\n')) end++;
            if (end < n && code.charAt(end)==';') end++;
            i = end; continue; // skip past the whole enum block; do NOT hoist it here
          }
        }
      }
      boolean isClass  = i+5 <= n && code.substring(i, Math.min(i+5,n)).equals("class");
      boolean isStruct = i+6 <= n && code.substring(i, Math.min(i+6,n)).equals("struct");
      if (isClass || isStruct) {
        int klen = isClass ? 5 : 6;
        char before = i > 0 ? code.charAt(i-1) : ' ';
        char after  = i+klen < n ? code.charAt(i+klen) : ' ';
        // Skip "enum class"/"enum struct" -- handled separately as enumScope
        boolean precededByEnum = i >= 5 && code.substring(i-5,i).equals("enum ");
        if (!precededByEnum &&
            !Character.isLetterOrDigit(before) && before != '_' &&
            !Character.isLetterOrDigit(after)  && after  != '_') {
          int open = i;
          while (open < n && code.charAt(open) != '{' && code.charAt(open) != ';') open++;
          if (open < n && code.charAt(open) == '{') {
            int depth = 0, k = open;
            while (k < n) {
              char c = code.charAt(k);
              if (c=='"') { k++; while (k<n && code.charAt(k)!='"') { if(code.charAt(k)=='\\') k++; k++; } }
              else if (c=='\'') { k++; while (k<n && code.charAt(k)!='\'') { if(code.charAt(k)=='\\') k++; k++; } }
              else if (c=='{') depth++;
              else if (c=='}') { if (--depth==0) { k++; break; } }
              k++;
            }
            int end = k;
            while (end < n && (code.charAt(end)==' ' || code.charAt(end)=='\n')) end++;
            if (end < n && code.charAt(end)==';') end++;
            // Also consume a preceding bare "template<...>" line (for template
            // functions/classes), so it gets blanked out along with the class
            // body instead of being left as an orphaned, dangling header line
            // in restCode once the class itself has been removed.
            int rangeStart = i;
            {
              int lookback = i - 1;
              while (lookback > 0 && (code.charAt(lookback)==' ' || code.charAt(lookback)=='\n' || code.charAt(lookback)=='\r')) lookback--;
              int lineEndLB = lookback;
              while (lookback > 0 && code.charAt(lookback) != '\n') lookback--;
              int lineStartLB = lookback > 0 ? lookback + 1 : 0;
              String prevLineLB = code.substring(lineStartLB, lineEndLB + 1).strip();
              if (prevLineLB.startsWith("template")) {
                rangeStart = lineStartLB;
              }
            }
            ranges.add(new int[]{rangeStart, end});
            i = end; continue;
          }
        }
      }
      i++;
    }
    if (ranges.isEmpty()) return code;
    StringBuilder sb = new StringBuilder();
    int pos = 0;
    for (int[] r : ranges) {
      sb.append(code, pos, r[0]);
      pos = r[1];
    }
    sb.append(code, pos, n);
    return sb.toString();
  }

  // Rewrite free function definitions as Sketch struct methods.
  // Lifecycle functions get "override" appended. Everything else becomes a method too.
  // Variable declarations at top level become struct fields.
  /**
   * Remove _PSketch injection from pure data structs (structs with only fields, no methods).
   * A struct is "pure data" if it has no method bodies — detected by absence of
   * return-type method signatures inside the struct body.
   */
  private String removePSketchFromDataStructs(String code) {
    // Parse each struct/class block and check if it has methods
    StringBuilder result = new StringBuilder();
    int i = 0;
    while (i < code.length()) {
      // Look for "_PSketch {" pattern
      int psk = code.indexOf(": public virtual _PSketch {", i);
      if (psk < 0) { result.append(code.substring(i)); break; }
      // Find the struct keyword before this
      int lineStart = code.lastIndexOf("\n", psk) + 1;
      String beforeBrace = code.substring(lineStart, psk);
      // Find the matching closing brace
      int braceOpen = psk + ": public virtual _PSketch {".length() - 1;
      int depth = 1, j = braceOpen + 1;
      while (j < code.length() && depth > 0) {
        if (code.charAt(j) == '{') depth++;
        else if (code.charAt(j) == '}') depth--;
        j++;
      }
      String body = code.substring(braceOpen + 1, j - 1);
      // Check if body has any method signatures (return type + name + '(')
      // A method has: word whitespace word '(' — e.g. "void update(" or "float distTo("
      boolean hasMethod = body.matches("(?s).*\\b(void|int|float|bool|double|color|auto|std::\\w+)\\s+\\w+\\s*\\(.*")
                         && !body.contains("constexpr"); // skip constexpr structs
      result.append(code, i, lineStart);
      if (hasMethod) {
        // Keep _PSketch injection
        result.append(code, lineStart, j);
      } else {
        // Remove _PSketch — revert to plain struct/class name {
        result.append(beforeBrace).append(" {").append(body).append("}");
      }
      i = j;
    }
    return result.toString();
  }

  private String rewriteAsSketchMethods(String code, String[] lifecycleMethods) {
    java.util.Set<String> lifecycle = new java.util.HashSet<>(java.util.Arrays.asList(lifecycleMethods));
    // Indent each line by 4 spaces (they're now inside struct Sketch { ... })
    // Lifecycle functions: add "override" to their signature
    StringBuilder out = new StringBuilder();
    String[] lines = code.split("\n", -1);
    for (String line : lines) {
      String trimmed = line.strip();
      String indented = "    " + line;
      // Initialize pointer fields to nullptr
      if (indented.matches("\\s+\\w[\\w:<>*,\\s]+\\*\\s+\\w+\\s*;")) {
        indented = indented.replaceFirst(";\\s*$", " = nullptr;");
      }
      // Detect lifecycle function definition lines and add "override"
      for (String lc : lifecycle) {
        // Match: void setup() { or void setup() \n (with optional spaces)
        // Pattern: void <name>( at start of trimmed line (not inside a longer name)
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
          "^(\\s*(?:virtual\\s+)?void\\s+" + java.util.regex.Pattern.quote(lc) + "\\s*\\([^)]*\\)\\s*)\\{(.*)\\}\\s*$");
        java.util.regex.Matcher m = p.matcher(line);
        if (m.find()) {
          String bodyContent = m.group(2).strip();
          if (bodyContent.isEmpty()) {
            indented = "    " + m.group(1) + "override {}";
          } else {
            // Single-line function with body — expand to multi-line
            indented = "    " + m.group(1) + "override {\n        " + bodyContent + "\n    }";
          }
          break;
        }
        // Match opening brace only (multi-line body follows)
        java.util.regex.Pattern p1b = java.util.regex.Pattern.compile(
          "^(\\s*(?:virtual\\s+)?void\\s+" + java.util.regex.Pattern.quote(lc) + "\\s*\\([^)]*\\)\\s*)\\{\\s*$");
        java.util.regex.Matcher m1b = p1b.matcher(line);
        if (m1b.find()) {
          indented = "    " + m1b.group(1) + "override {";
          break;
        }
        // Also handle: void mouseWheel(int delta) {
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile(
          "^(\\s*(?:virtual\\s+)?void\\s+" + java.util.regex.Pattern.quote(lc) + "\\s*\\([^)]*\\)\\s*)$");
        java.util.regex.Matcher m2 = p2.matcher(line);
        if (m2.find()) {
          indented = "    " + m2.group(1) + "override";
          break;
        }
      }
      out.append(indented).append("\n");
    }
    return out.toString();
  }

  // Extract class/struct definitions and move them to the top, sorted by inheritance.
  private String hoistClasses(String code) {
    java.util.List<String> classBlocks = new java.util.ArrayList<>();
    StringBuilder rest = new StringBuilder();
    int n = code.length(), i = 0;
    while (i < n) {
      if (i+1 < n && code.charAt(i)=='/' && code.charAt(i+1)=='/') {
        while (i < n && code.charAt(i) != '\n') i++; continue;
      }
      if (i+1 < n && code.charAt(i)=='/' && code.charAt(i+1)=='*') {
        i += 2; while (i+1 < n && !(code.charAt(i)=='*' && code.charAt(i+1)=='/')) i++; i += 2; continue;
      }
      if (code.charAt(i)=='"') {
        i++; while (i < n && code.charAt(i)!='"') { if (code.charAt(i)=='\\') i++; i++; } i++; continue;
      }
      if (code.charAt(i)=='\'') {
        i++; while (i < n && code.charAt(i)!='\'') { if (code.charAt(i)=='\\') i++; i++; } i++; continue;
      }
      boolean isClass  = i+5 <= n && code.substring(i, Math.min(i+5,n)).equals("class");
      boolean isStruct = i+6 <= n && code.substring(i, Math.min(i+6,n)).equals("struct");
      if (isClass || isStruct) {
        int klen = isClass ? 5 : 6;
        char before = i > 0 ? code.charAt(i-1) : ' ';
        char after  = i+klen < n ? code.charAt(i+klen) : ' ';
        if (!Character.isLetterOrDigit(before) && before != '_' &&
            !Character.isLetterOrDigit(after)  && after  != '_') {
          int open = i;
          while (open < n && code.charAt(open) != '{' && code.charAt(open) != ';') open++;
          if (open < n && code.charAt(open) == '{') {
            int depth = 0, k = open;
            while (k < n) {
              char c = code.charAt(k);
              if (c=='"') { k++; while (k<n && code.charAt(k)!='"') { if(code.charAt(k)=='\\') k++; k++; } }
              else if (c=='\'') { k++; while (k<n && code.charAt(k)!='\'') { if(code.charAt(k)=='\\') k++; k++; } }
              else if (c=='{') depth++;
              else if (c=='}') { if (--depth==0) { k++; break; } }
              k++;
            }
            int end = k;
            while (end < n && (code.charAt(end)==' ' || code.charAt(end)=='\n')) end++;
            if (end < n && code.charAt(end)==';') end++;
            classBlocks.add(code.substring(i, end));
            i = end; continue;
          }
        }
      }
      rest.append(code.charAt(i)); i++;
    }
    // Sort: base classes before derived
    boolean changed = true;
    for (int pass = 0; pass < classBlocks.size()*2 && changed; pass++) {
      changed = false;
      for (int a = 0; a < classBlocks.size(); a++) {
        String blockA = classBlocks.get(a);
        java.util.regex.Matcher mA = java.util.regex.Pattern.compile(
          "(?:class|struct)\\s+\\w+\\s*:\\s*(?:public|protected|private)\\s+(\\w+)").matcher(blockA);
        if (!mA.find()) continue;
        String aBase = mA.group(1);
        for (int b = a+1; b < classBlocks.size(); b++) {
          String blockB = classBlocks.get(b);
          java.util.regex.Matcher mB = java.util.regex.Pattern.compile(
            "(?:class|struct)\\s+(\\w+)").matcher(blockB);
          if (mB.find() && mB.group(1).equals(aBase)) {
            classBlocks.set(a, blockB); classBlocks.set(b, blockA);
            changed = true; break;
          }
        }
        if (changed) break;
      }
    }
    StringBuilder out = new StringBuilder();
    for (String b : classBlocks) out.append(b).append("\n\n");
    out.append(rest);
    return out.toString();
  }

  private String buildForwardDeclarations(String code) {
    StringBuilder decls = new StringBuilder();
    Matcher m = FUNC_DEF.matcher(code);
    while (m.find()) {
      String name = m.group(2).trim();
      if (!LIFECYCLE.contains(name)) {
        decls.append(m.group(1).trim())
             .append(" ").append(name)
             .append("(").append(m.group(3).trim()).append(");\n");
      }
    }
    return decls.toString();
  }

  private void warnReservedNames(String code, RunnerListener listener) {
    for (String name : RESERVED) {
      if (java.util.regex.Pattern.compile("\\b" + name + "\\s*=", java.util.regex.Pattern.MULTILINE).matcher(code).find())
        listener.statusNotice("[WARN] '" + name +
          "' is a C stdlib name — consider renaming your variable.");
    }
  }

  private String removeUserIncludes(String code) {
    return code
      .replaceAll("(?m)^\\s*#include\\s+[\"<]Processing\\.h[\">]\\s*$\\n?", "")
      .replaceAll("(?m)^\\s*#include\\s+[\"<]Processing\\.cpp[\">]\\s*$\\n?", "");
  }

  private String stripNamespaceProcessing(String code) {
    Pattern p = Pattern.compile("namespace\\s+Processing\\s*\\{", Pattern.MULTILINE);
    Matcher m = p.matcher(code);
    if (!m.find()) return code;
    int open = code.indexOf('{', m.start());
    if (open < 0) return code;
    int depth = 0, close = -1;
    for (int i = open; i < code.length(); i++) {
      char c = code.charAt(i);
      if (c == '{') depth++;
      else if (c == '}' && --depth == 0) { close = i; break; }
    }
    if (close < 0) return code;
    return code.substring(0, m.start())
         + code.substring(open + 1, close)
         + code.substring(close + 1);
  }

  private String sanitize(String code) {
    if (code.startsWith("\uFEFF")) code = code.substring(1);
    return code
      .replace('\u2018', '\'').replace('\u2019', '\'')
      .replace('\u201C', '"') .replace('\u201D', '"')
      .replace('\u2013', '-') .replace('\u2014', '-');
  }


  // Replace keywords only outside string/char literals and comments
  private String replaceKeywordsOutsideLiterals(String code, String[][] replacements) {
    StringBuilder out = new StringBuilder();
    int i = 0, n = code.length();
    while (i < n) {
      char c = code.charAt(i);
      // Line comment -- copy to end of line verbatim
      if (c == '/' && i+1 < n && code.charAt(i+1) == '/') {
        int end = code.indexOf('\n', i);
        if (end < 0) end = n;
        out.append(code, i, end);
        i = end;
        continue;
      }
      // Block comment -- copy verbatim
      if (c == '/' && i+1 < n && code.charAt(i+1) == '*') {
        int end = code.indexOf("*/", i+2);
        if (end < 0) end = n; else end += 2;
        out.append(code, i, end);
        i = end;
        continue;
      }
      // String literal -- copy verbatim
      if (c == '"') {
        out.append(c); i++;
        while (i < n) {
          char sc = code.charAt(i);
          out.append(sc); i++;
          if (sc == '\\' && i < n) { out.append(code.charAt(i++)); continue; }
          if (sc == '"') break;
        }
        continue;
      }
      // Char literal -- copy verbatim
      if (c == '\'') {
        out.append(c); i++;
        while (i < n) {
          char sc = code.charAt(i);
          out.append(sc); i++;
          if (sc == '\\' && i < n) { out.append(code.charAt(i++)); continue; }
          if (sc == '\'') break;
        }
        continue;
      }
      // Outside literals: check for keyword match
      boolean matched = false;
      for (String[] pair : replacements) {
        String from = pair[0], to = pair[1];
        int flen = from.length();
        if (i + flen <= n && code.substring(i, i+flen).equals(from)) {
          boolean leftOk  = i == 0 || !Character.isLetterOrDigit(code.charAt(i-1)) && code.charAt(i-1) != '_';
          boolean rightOk = i+flen >= n || !Character.isLetterOrDigit(code.charAt(i+flen)) && code.charAt(i+flen) != '_';
          if (leftOk && rightOk) {
            out.append(to);
            i += flen;
            matched = true;
            break;
          }
        }
      }
      if (!matched) { out.append(c); i++; }
    }
    return out.toString();
  }

  // Detect variables assigned color()/lerpColor() and fix their type from int to color.
  // Also propagates through function calls so params that receive color vars become color too.
  private String fixColorTypes(String code) {
    // Pass 1: find all variables directly assigned color()/lerpColor()
    java.util.Set<String> colorVars = new java.util.HashSet<>();
    Pattern assignPat = Pattern.compile("\\b(\\w+)\\s*=\\s*(?:color|lerpColor)\\s*\\(");
    Matcher am = assignPat.matcher(code);
    while (am.find()) colorVars.add(am.group(1));

    // Pass 2: propagate through function calls iteratively until stable
    boolean changed = true;
    while (changed) {
      changed = false;
      // Find function definitions
      Pattern fnDef = Pattern.compile(
        "(?:void|int|float|bool|color)\\s+(\\w+)\\s*\\(([^)]*)\\)",
        Pattern.MULTILINE);
      Matcher fd = fnDef.matcher(code);
      while (fd.find()) {
        String fnName = fd.group(1);
        String params  = fd.group(2);
        // Extract param names
        List<String> paramNames = new ArrayList<>();
        for (String p : params.split(",")) {
          String[] parts = p.trim().split("\\s+");
          if (parts.length >= 2) paramNames.add(parts[parts.length - 1].replaceAll("[\\[\\]*]",""));
        }
        if (paramNames.isEmpty()) continue;
        // Find calls to this function
        Pattern callPat = Pattern.compile("\\b" + Pattern.quote(fnName) + "\\s*\\(([^;{]*)\\)");
        Matcher cm = callPat.matcher(code);
        while (cm.find()) {
          String[] args = cm.group(1).split(",");
          for (int ai = 0; ai < args.length && ai < paramNames.size(); ai++) {
            String arg = args[ai].trim();
            if (colorVars.contains(arg) && !colorVars.contains(paramNames.get(ai))) {
              colorVars.add(paramNames.get(ai));
              changed = true;
            }
          }
        }
      }
    }

    // Pass 3: fix int arrays that hold color values
    Pattern arrPat = Pattern.compile("\\bint\\s+(\\w+)\\[(\\d+)\\]\\s*=\\s*\\{([^}]*)\\}");
    Matcher arr = arrPat.matcher(code);
    StringBuffer sb3 = new StringBuffer();
    while (arr.find()) {
      boolean anyColor = false;
      for (String a : arr.group(3).split(","))
        if (colorVars.contains(a.trim())) { anyColor = true; break; }
      if (anyColor) {
        colorVars.add(arr.group(1));
        arr.appendReplacement(sb3, "color " + arr.group(1) + "[" + arr.group(2) + "] = {" + arr.group(3) + "}");
      } else arr.appendReplacement(sb3, arr.group(0));
    }
    arr.appendTail(sb3);
    code = sb3.toString();

    // Pass 4: replace int varname declarations with color varname for all colorVars
    for (String v : colorVars) {
      code = code.replaceAll("\\bint\\s+(" + Pattern.quote(v) + ")\\b", "color $1");
    }

    return code;
  }

  /**
   * Java/Processing has no value-vs-pointer distinction for user-defined
   * types -- "Foo f; f = new Foo(...);" means f is a REFERENCE to a newly
   * allocated Foo, and every later "f.method()"/"f.field" is reference
   * access. A naive translation keeps "Foo f;" as a C++ VALUE declaration
   * (since that\'s what the text literally says), but then "f = new Foo(...)"
   * tries to assign a Foo* into a Foo, which fails to compile (no implicit
   * pointer-to-value conversion, and most user classes correctly have
   * copy-assignment either deleted or simply not viable for this).
   *
   * Fix: detect every variable that is ever assigned via "name = new Type(...)"
   * anywhere in the source, rewrite its declaration from "Type name;" (or
   * "Type nameA, nameB;") to "Type* name = nullptr;", and rewrite every
   * later "name.member" access for that variable to "name->member" to match
   * pointer semantics, exactly mirroring what Java\'s reference semantics
   * actually meant in the first place.
   */
  /**
   * "Type name[] = { a, b, c };" is an ordinary, fixed-size global in
   * Java/Processing -- but once CppBuild wraps every top-level global into
   * a field of struct Sketch, a C-style array sized purely by its
   * initializer becomes what C++ calls a "flexible array member," which is
   * only legal as the LAST member of a struct, and never with an
   * initializer at all. Any such declaration anywhere else (i.e. almost
   * always, since sketches rarely declare exactly one array as their very
   * last global) fails to compile.
   *
   * Fix: rewrite "Type name[] = { ...N items... };" to
   * "std::array<Type,N> name = { ...N items... };", which has no such
   * restriction as a class member. Also rewrite the common companion
   * idiom "sizeof(name)/sizeof(name[0])" (used to compute element count)
   * to "name.size()", since std::array exposes that directly and sizeof
   * no longer makes sense once the type is no longer a raw C array.
   */

  private String javaToC(String code) {
    // Run color type propagation first
    code = fixColorTypes(code);

    // ── 1. Whole-word keyword replacements (skip string/char literals) ───────
    String[][] words = {
      { "boolean",   "bool"        },
      // Integer/Float/Double/Long/Byte/Character are intentionally NOT
      // rewritten to their primitive equivalents here -- they're real
      // Java wrapper classes (Processing.h provides matching C++ wrapper
      // classes for the same reason String does: real boxing semantics
      // like nullability, valueOf()/parseXxx() static factories, and
      // xxxValue() accessors, none of which a bare "int"/"float"/etc.
      // can express). Rewriting these to plain primitives as text was
      // the same category of bug as the old String->std::string rename
      // -- it silently changes what the user's declared type actually
      // is, breaking any wrapper-specific method call.
      // "boolean"->"bool" stays as a rename since Java's boolean really
      // is a primitive (Boolean is the separate wrapper class, not
      // handled by this list either, for the same reason).
      // "String" is intentionally NOT renamed to "std::string" here --
      // String is a real wrapper class (Processing.h) with Java-named
      // methods (trim(), contains(), toLowerCase(), etc.) that
      // std::string doesn't have. Renaming "String" to "std::string" as
      // plain text would silently strip the sketch author's declared
      // type down to the wrong class, exactly causing methods like
      // .trim()/.contains() to fail to compile -- the wrapper class
      // already inherits from std::string and is fully compatible with
      // it, so there is no reason to rewrite the type name away at all.
      { "null",      "nullptr"     },
    };
    code = replaceKeywordsOutsideLiterals(code, words);
    // List<T> varname = ... -> std::vector<T> varname = ...
    code = code.replaceAll("\\bList<", "std::vector<");
    // .add( -> .push_back(  (vector method)
    // Note: only when not preceded by a word char to avoid replacing e.g. "loadPixels"
    // We keep .add() as push_back only for vector types - risky so skip for now

    // ── 2. Java cast syntax: float(x) → (float)(x) ──────────────────────────
    // Java cast syntax: float(x) → (float)(x)
    // Exclude when inside template args (preceded by < or ,) to avoid
    // mangling std::function<bool(...)> into std::function<(bool)(...)>
    for (String t : new String[]{ "float","int","double","long","char","bool" })
      code = code.replaceAll("(?<![\\w<,\\s])(" + t + ")\\(", "($1)(");

    // ── 3. Hex color literals: #RRGGBB → color(0xFFRRGGBB) ──────────────────
    code = code.replaceAll("#([0-9A-Fa-f]{8})", "color(0x$1)");
    code = code.replaceAll("#([0-9A-Fa-f]{6})", "color(0xFF$1)");

    // ── 4. String concatenation: "literal" + x → std::string("literal") + x ─
    code = code.replaceAll("(\"[^\"]*\")\\s*\\+", "std::string($1) +");

    // ── 5. .charAt(i) → [i] ──────────────────────────────────────────────────
    code = code.replaceAll("\\.charAt\\((\\w+)\\)", "[$1]");

    // ── 6. .equals("...") → == "..." ─────────────────────────────────────────
    code = code.replaceAll("\\.equals\\(([^)]*)\\)", " == $1");

    // ── 6b. Wrap .length()/.size() in std::to_string() whenever they appear
    // in a string concatenation context (adjacent to + operator).
    // Run multiple passes to catch all patterns.
    for (int _pass = 0; _pass < 3; _pass++) {
      code = code.replaceAll("\\+\\s*(\\w+)\\.length\\(\\)", "+ std::to_string($1.length())");
      code = code.replaceAll("(\\w+)\\.length\\(\\)\\s*\\+", "std::to_string($1.length()) +");
      code = code.replaceAll("\\+\\s*(\\w+)\\.size\\(\\)",   "+ std::to_string($1.size())");
      code = code.replaceAll("(\\w+)\\.size\\(\\)\\s*\\+",   "std::to_string($1.size()) +");
    }

    // ── 7. float % int → (int)(float) % int (modulo on floats invalid in C++) ─
    code = code.replaceAll("\\(([^)]*\\.[^)]*)\\)\\s*%\\s*(\\w+)", "(int)($1) % $2");

    // ── 8. Windows reserved: far/near → farVal/nearVal (Windows only) ────────
    if (System.getProperty("os.name").toLowerCase().contains("win")) {
      code = code.replaceAll("\\bfar\\b",  "farVal");
      code = code.replaceAll("\\bnear\\b", "nearVal");
    }

    // (color type fixing handled by fixColorTypes() before javaToC())

    // ── 10. color() ambiguity: color(r,g,b,floatAlpha) → cast last arg ───────
    code = code.replaceAll(
      "\\bcolor\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*([^)\\d][^)]*)\\)",
      "color($1,$2,$3,(int)($4))");

    // ── 11. API-conflicting variable names: scale/fill/stroke/etc. ───────────
    // If used as a variable (after a type keyword), rename to avoid shadowing API
    String[] apiConflicts = {
      "scale", "stroke", "background", "translate", "rotate",
      "map", "dist", "noise"
    };
    String typeKw = "(?:int|float|double|bool|char|long|unsigned|auto|color|PVector)\\s+";
    for (String word : apiConflicts) {
      // Only rename if it appears as a variable declaration
      if (java.util.regex.Pattern.compile(typeKw + word + "\\b(?!\\s*\\()", java.util.regex.Pattern.MULTILINE).matcher(code).find()) {
        // Rename all bare occurrences (not followed by '(')
        code = code.replaceAll("\\b" + word + "\\b(?!\\s*\\()", "_" + word);
      }
    }

    // ── 12. mousePressed / keyPressed as bool variables → _mousePressed / _keyPressed ─
    // In Processing Java, 'mousePressed' is both a bool variable (read in draw)
    // and an event callback method name (void mousePressed()).
    // In C++ PApplet they can't share a name: the virtual method keeps the natural
    // name, so occurrences used as a BOOL EXPRESSION (not followed by '(') must be
    // rewritten to _mousePressed / _keyPressed.
    // We use replaceKeywordsOutsideLiterals with a negative-lookahead-equivalent:
    // replace \bmousePressed\b not followed by \s*( -- done via manual scan below.
    code = rewriteBoolEventName(code, "mousePressed", "_mousePressed");
    code = rewriteBoolEventName(code, "keyPressed",   "_keyPressed");

    return code;
  }

  // Replace all occurrences of `name` that are NOT immediately followed by '('
  // (i.e. used as a variable, not as a function call or definition).
  // Operates outside string/char literals and comments.
  private String rewriteBoolEventName(String code, String name, String replacement) {
    StringBuilder out = new StringBuilder();
    int i = 0, n = code.length(), nlen = name.length();
    while (i < n) {
      char c = code.charAt(i);
      // Skip line comments
      if (c == '/' && i+1 < n && code.charAt(i+1) == '/') {
        int end = code.indexOf('\n', i); if (end < 0) end = n;
        out.append(code, i, end); i = end; continue;
      }
      // Skip block comments
      if (c == '/' && i+1 < n && code.charAt(i+1) == '*') {
        int end = code.indexOf("*/", i+2); end = end < 0 ? n : end+2;
        out.append(code, i, end); i = end; continue;
      }
      // Skip string literals
      if (c == '"') {
        out.append(c); i++;
        while (i < n) { char sc = code.charAt(i); out.append(sc); i++;
          if (sc == '\\' && i < n) { out.append(code.charAt(i++)); continue; }
          if (sc == '"') break; }
        continue;
      }
      // Skip char literals
      if (c == '\'') {
        out.append(c); i++;
        while (i < n) { char sc = code.charAt(i); out.append(sc); i++;
          if (sc == '\\' && i < n) { out.append(code.charAt(i++)); continue; }
          if (sc == '\'') break; }
        continue;
      }
      // Check for name match
      if (i + nlen <= n && code.substring(i, i+nlen).equals(name)) {
        // Word boundary check: char before must not be letter/digit/_
        boolean leftOk  = i == 0 || (!Character.isLetterOrDigit(code.charAt(i-1)) && code.charAt(i-1) != '_');
        // Char after must not be letter/digit/_
        boolean rightOk = i+nlen >= n || (!Character.isLetterOrDigit(code.charAt(i+nlen)) && code.charAt(i+nlen) != '_');
        if (leftOk && rightOk) {
          // Check what follows (skipping whitespace): if it's '(' this is a call/def, don't replace
          int j = i + nlen;
          while (j < n && (code.charAt(j) == ' ' || code.charAt(j) == '\t')) j++;
          boolean isCall = j < n && code.charAt(j) == '(';
          if (isCall) {
            out.append(code, i, i+nlen); i += nlen;
          } else {
            out.append(replacement); i += nlen;
          }
          continue;
        }
      }
      out.append(c); i++;
    }
    return out.toString();
  }

  private List<String> wordWrap(String line, int max) {
    List<String> r = new ArrayList<>();
    while (line.length() > max) {
      int s = line.lastIndexOf(' ', max);
      if (s < 0) s = max;
      r.add(line.substring(0, s));
      line = "    " + line.substring(s).stripLeading();
    }
    r.add(line);
    return r;
  }

  private List<String> buildCommand(String gpp, File src, File bin, boolean win, boolean mac) {
    List<String> cmd = new ArrayList<>();
    cmd.add(gpp);
    cmd.add("-std=c++17");
    cmd.add("-O2");
    // -march=native isn't reliably supported by Apple's clang (which is
    // what g++/gcc resolve to on macOS via Xcode Command Line Tools) —
    // skip it there rather than risk a build failing on an unsupported flag.
    if (!mac) cmd.add("-march=native");

    // ── Homebrew include/lib paths (macOS only) ─────────────────────────────
    // Homebrew installs to /opt/homebrew on Apple Silicon and /usr/local on
    // Intel Macs — neither is guaranteed to be on g++'s default search path,
    // so add both explicitly. Harmless if a path doesn't exist.
    String homebrewPrefix = null;
    if (mac) {
      if (new File("/opt/homebrew/include").exists()) homebrewPrefix = "/opt/homebrew";
      else if (new File("/usr/local/include").exists()) homebrewPrefix = "/usr/local";
      if (homebrewPrefix != null) {
        cmd.add("-I" + homebrewPrefix + "/include");
      }
    }

    // ── Cached precompiled objects ──────────────────────────────────────────
    // Processing.cpp takes ~9s to compile. Cache the .o and reuse it.
    try {
    String osName = System.getProperty("os.name","").toLowerCase();
    String cacheSubDir = osName.contains("win")  ? "cache/windows-x64"
                        : osName.contains("mac")  ? "cache/macos-" + (System.getProperty("os.arch","").contains("aarch64") ? "arm64" : "x64")
                        : "cache/linux-x64";
    File cacheDir = new File(runtimeDir.getParentFile(), cacheSubDir);
    cacheDir.mkdirs();
    File processingO  = new File(cacheDir, "Processing.o");
    File defaultsO    = new File(cacheDir, "Processing_defaults.o");
    File processingCpp = new File(runtimeDir, "Processing.cpp");
    File defaultsCpp   = new File(runtimeDir, "Processing_defaults.cpp");

    File processingH = new File(runtimeDir, "Processing.h");
    boolean needsProcessing = !processingO.exists()
      || processingCpp.lastModified() > processingO.lastModified()
      || (processingH.exists() && processingH.lastModified() > processingO.lastModified());
    boolean needsDefaults   = !defaultsO.exists()
      || defaultsCpp.lastModified() > defaultsO.lastModified();

    if (needsProcessing) {
      List<String> preCmd = new ArrayList<>();
      preCmd.add(gpp); preCmd.add("-std=c++17"); preCmd.add("-O2");
      if (!mac) preCmd.add("-march=native");
      if (homebrewPrefix != null) preCmd.add("-I" + homebrewPrefix + "/include");
      preCmd.add("-I" + runtimeDir.getAbsolutePath());
      preCmd.add("-DPROCESSING_HAS_STB_IMAGE");
      preCmd.add("-DPROCESSING_HAS_STB_TRUETYPE");
      preCmd.add("-c"); preCmd.add(processingCpp.getAbsolutePath());
      preCmd.add("-o"); preCmd.add(processingO.getAbsolutePath());
      ProcessBuilder prePb = new ProcessBuilder(preCmd);
      prePb.redirectErrorStream(true);
      Process preP = prePb.start();
      StringBuilder preLog = new StringBuilder();
      try (java.io.BufferedReader br = new java.io.BufferedReader(
          new java.io.InputStreamReader(preP.getInputStream()))) {
        String line; while ((line=br.readLine())!=null) preLog.append(line).append('\n');
      }
      if (preP.waitFor() != 0 || !processingO.exists()) {
        // Cache failed - fall back to inline compilation
        cmd.add(processingCpp.getAbsolutePath());
      } else {
      }
    }
    if (needsDefaults) {
      List<String> preCmd2 = new ArrayList<>();
      preCmd2.add(gpp); preCmd2.add("-std=c++17"); preCmd2.add("-O2");
      if (!mac) preCmd2.add("-march=native");
      if (homebrewPrefix != null) preCmd2.add("-I" + homebrewPrefix + "/include");
      preCmd2.add("-I" + runtimeDir.getAbsolutePath());
      preCmd2.add("-DPROCESSING_HAS_STB_IMAGE");
      preCmd2.add("-DPROCESSING_HAS_STB_TRUETYPE");
      preCmd2.add("-c"); preCmd2.add(defaultsCpp.getAbsolutePath());
      preCmd2.add("-o"); preCmd2.add(defaultsO.getAbsolutePath());
      ProcessBuilder prePb2 = new ProcessBuilder(preCmd2);
      prePb2.redirectErrorStream(true);
      if (prePb2.start().waitFor() != 0 || !defaultsO.exists()) {
        cmd.add(defaultsCpp.getAbsolutePath());
      }
    }

    // Add cached .o files if they exist, otherwise inline source
    if (processingO.exists() && !needsProcessing)
      cmd.add(processingO.getAbsolutePath());
    else if (!cmd.contains(processingCpp.getAbsolutePath()))
      cmd.add(processingCpp.getAbsolutePath());

    if (defaultsO.exists() && !needsDefaults)
      cmd.add(defaultsO.getAbsolutePath());
    else if (!cmd.contains(defaultsCpp.getAbsolutePath()))
      cmd.add(defaultsCpp.getAbsolutePath());
    } catch (Exception _cacheEx) {
      // Cache failed - fall back to compiling inline
      System.err.println("[cache] error: " + _cacheEx.getMessage());
      cmd.add(new File(runtimeDir, "Processing.cpp").getAbsolutePath());
      cmd.add(new File(runtimeDir, "Processing_defaults.cpp").getAbsolutePath());
    }

    cmd.add(src.getAbsolutePath());
    // main() is now emitted inside Sketch_run.cpp by writeSketch() -- don't link main.cpp
    cmd.add("-I" + runtimeDir.getAbsolutePath());
    cmd.add("-o"); cmd.add(bin.getAbsolutePath());
    // Enable stb_image if present in mode's src/ folder
    File stbImage = new File(runtimeDir, "stb_image.h");
    if (stbImage.exists()) cmd.add("-DPROCESSING_HAS_STB_IMAGE");
    File stbTTF = new File(runtimeDir, "stb_truetype.h");
    if (stbTTF.exists()) cmd.add("-DPROCESSING_HAS_STB_TRUETYPE");

    if (win) {
      Collections.addAll(cmd,
        "-lglfw3", "-lglew32", "-lopengl32", "-lglu32",
        "-lcomdlg32", "-lshell32", "-lole32", "-luuid",
        "-mwindows", "-pthread", "-D_USE_MATH_DEFINES");
    } else if (mac) {
      // macOS has no separate libGL/libGLU — OpenGL is a system framework,
      // bundled with Xcode Command Line Tools (no extra install needed).
      // GLFW/GLEW come from Homebrew, so link against its lib directory too.
      if (homebrewPrefix != null) {
        cmd.add("-L" + homebrewPrefix + "/lib");
      }
      Collections.addAll(cmd, "-lglfw", "-lGLEW", "-lm", "-pthread");
      Collections.addAll(cmd, "-framework", "OpenGL");
      Collections.addAll(cmd, "-framework", "Cocoa");
      Collections.addAll(cmd, "-framework", "IOKit");
      Collections.addAll(cmd, "-framework", "CoreVideo");
    } else {
      Collections.addAll(cmd, "-lglfw", "-lGLEW", "-lGL", "-lGLU", "-lm", "-pthread");
    }
    return cmd;
  }

  /**
   * Run the sketch text through the real C/C++ preprocessor (g++ -E) so that
   * all #define macros -- including multi-line X-macros with backslash
   * continuations, token pasting (##), stringification (#), and conditional
   * compilation (#ifdef) -- are fully expanded BEFORE any of CppBuild's own
   * text-scanning passes (hoistClasses, removeHoistedClasses, enum/constexpr
   * extraction, lifecycle-method rewriting) run.
   *
   * This is necessary because those passes are simple line/brace scanners
   * that assume "what you see textually is what the C++ grammar sees." That
   * assumption only holds true AFTER macro expansion -- a multi-line #define
   * or an X-macro expanding into enum/switch bodies will otherwise corrupt
   * the brace-depth tracking and produce garbage output.
   *
   * We preserve #line markers (no -P flag) so that g++'s own line-tracking
   * stays roughly aligned; CppBuild re-stamps "#line 1 \"sketch.pde\"" right
   * before emitting user code anyway, so exact line fidelity through the
   * macro-expansion step is a secondary concern -- correctness of the
   * expanded code is the primary goal.
   */
  private String preprocessMacros(String code) {
    // Fast path: if there's no #define/#ifdef/#undef/#include anywhere,
    // skip invoking an external process entirely.
    if (!code.contains("#define") && !code.contains("#ifdef")
        && !code.contains("#ifndef") && !code.contains("#undef")
        && !code.contains("#if ")) {
      return code;
    }
    try {
      File scratch = new File(buildDir, "_macro_preprocess_input.cpp");
      Files.writeString(scratch.toPath(), code);

      boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
      String gpp = findGpp(isWindows);

      // -E: stop after preprocessing.  -x c++: force C++ mode regardless of
      // the .cpp extension's default.  -P would strip line markers; we keep
      // them (omit -P) since downstream code already re-stamps its own
      // #line directive, and keeping g++'s markers costs nothing here.
      ProcessBuilder pb = new ProcessBuilder(gpp, "-E", "-x", "c++", scratch.getAbsolutePath());
      pb.directory(buildDir);
      pb.redirectErrorStream(false);
      Process proc = pb.start();

      String expanded = new String(proc.getInputStream().readAllBytes());
      String errOutput = new String(proc.getErrorStream().readAllBytes());
      int exitCode = proc.waitFor();

      if (exitCode != 0 || expanded.isBlank()) {
        // Preprocessing failed (e.g. malformed macro) -- fall back to the
        // original, unexpanded code rather than silently producing nothing.
        // The downstream compile step will surface a clearer error in that
        // case, and we don't want a preprocessing hiccup to mask the
        // sketch entirely.
        System.err.println("[CppMode] macro preprocessing failed, using original code: " + errOutput);
        return code;
      }

      // g++ -E emits "# <line> \"<file>\" <flags>" GNU-style line markers
      // (or "#line <n> \"<file>\"" depending on version). Strip any line
      // beginning with "# " followed by a digit, or "#line", since CppBuild's
      // downstream scanners don't need them and they'd otherwise be mistaken
      // for ordinary text by the brace/class scanners.
      StringBuilder cleaned = new StringBuilder();
      for (String line : expanded.split("\n", -1)) {
        String t = line.strip();
        if (t.startsWith("# ") && t.length() > 2 && Character.isDigit(t.charAt(2))) continue;
        if (t.startsWith("#line")) continue;
        cleaned.append(line).append("\n");
      }
      return cleaned.toString();
    } catch (Exception e) {
      System.err.println("[CppMode] macro preprocessing exception, using original code: " + e);
      return code;
    }
  }

  private String findGpp(boolean win) {
    if (win) {
      for (String p : new String[]{
            "C:\\msys64\\mingw64\\bin\\g++.exe",
            "C:\\msys2\\mingw64\\bin\\g++.exe",
            System.getProperty("user.home") + "\\msys64\\mingw64\\bin\\g++.exe"})
        if (new File(p).exists()) return p;
    }
    return "g++";
  }

  // ════════════════════════════════════════════════════════════════════════════
  // EXPORT APPLICATION SYSTEM
  // ════════════════════════════════════════════════════════════════════════════

  public enum ExportTarget {
    LINUX_X64   ("linux-x64",    "g++",                        "Linux 64-bit (x86_64)",         false),
    LINUX_ARM64 ("linux-arm64",  "aarch64-linux-gnu-g++",      "Linux ARM64 (RPi 4/5, 64-bit)", false),
    LINUX_ARM32 ("linux-arm32",  "arm-linux-gnueabihf-g++",    "Linux ARM32 (RPi 2/3, 32-bit)", false),
    WINDOWS_X64 ("windows-x64", "x86_64-w64-mingw32-g++",     "Windows 64-bit",                true),
    MACOS_X64   ("macos-x64",   "x86_64-apple-darwin23-c++",  "macOS Intel 64-bit",            false),
    MACOS_ARM64 ("macos-arm64", "aarch64-apple-darwin23-c++", "macOS Apple Silicon",           false);

    public final String dirName, compiler, label;
    public final boolean isWindows;
    ExportTarget(String d, String c, String l, boolean w) {
      dirName=d; compiler=c; label=l; isWindows=w;
    }
  }

  public void showExportDialog(Sketch sketch, RunnerListener listener) {
    java.util.Map<ExportTarget,Boolean> avail = new java.util.LinkedHashMap<>();
    for (ExportTarget t : ExportTarget.values()) avail.put(t, compilerExists(t));

    String os = System.getProperty("os.name","").toLowerCase();
    boolean isWin = os.contains("win");
    java.awt.Color bg    = new java.awt.Color(28,30,38);
    java.awt.Color bgRow = new java.awt.Color(35,37,48);
    java.awt.Color green = new java.awt.Color(100,220,120);
    java.awt.Color red   = new java.awt.Color(240,80,80);
    java.awt.Color blue  = new java.awt.Color(210,228,255);

    javax.swing.JDialog dlg = new javax.swing.JDialog((java.awt.Frame)null,"Export Application",true);
    dlg.setLayout(new java.awt.BorderLayout(8,8));
    dlg.getContentPane().setBackground(bg);

    // ── Title ────────────────────────────────────────────────────────────────
    javax.swing.JLabel title = new javax.swing.JLabel("  Export Application");
    title.setForeground(blue);
    title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD,15f));
    title.setBorder(javax.swing.BorderFactory.createEmptyBorder(14,8,4,8));
    dlg.add(title, java.awt.BorderLayout.NORTH);

    javax.swing.JPanel center = new javax.swing.JPanel(new java.awt.BorderLayout(4,6));
    center.setBackground(bg);
    center.setBorder(javax.swing.BorderFactory.createEmptyBorder(0,14,4,14));

    // ── Icon picker ──────────────────────────────────────────────────────────
    javax.swing.JPanel iconPanel = new javax.swing.JPanel(new java.awt.BorderLayout(6,4));
    iconPanel.setBackground(bg);
    iconPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(4,0,8,0));

    // Top row: label + hint
    javax.swing.JPanel iconTop = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,4,0));
    iconTop.setBackground(bg);
    javax.swing.JLabel iconLbl = new javax.swing.JLabel("App Icon (PNG)  ");
    iconLbl.setForeground(blue);
    iconLbl.setFont(iconLbl.getFont().deriveFont(java.awt.Font.BOLD, 12f));
    javax.swing.JLabel iconHint = new javax.swing.JLabel("optional · 256×256 recommended · leave blank to skip");
    iconHint.setForeground(new java.awt.Color(110,115,150));
    iconHint.setFont(iconHint.getFont().deriveFont(10f));
    iconTop.add(iconLbl); iconTop.add(iconHint);
    iconPanel.add(iconTop, java.awt.BorderLayout.NORTH);

    // Bottom row: preview + path field + buttons
    javax.swing.JPanel iconBottom = new javax.swing.JPanel(new java.awt.BorderLayout(6,0));
    iconBottom.setBackground(bg);

    // Icon preview label (shows thumbnail)
    javax.swing.JLabel iconPreview = new javax.swing.JLabel();
    iconPreview.setPreferredSize(new java.awt.Dimension(48,48));
    iconPreview.setOpaque(true);
    iconPreview.setBackground(new java.awt.Color(40,42,55));
    iconPreview.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(60,65,90)));
    iconPreview.setHorizontalAlignment(javax.swing.JLabel.CENTER);
    iconBottom.add(iconPreview, java.awt.BorderLayout.WEST);

    // Path field - read only, updated by file chooser
    javax.swing.JTextField iconField = new javax.swing.JTextField();
    iconField.setBackground(new java.awt.Color(40,42,55));
    iconField.setForeground(blue); iconField.setCaretColor(blue);
    iconField.setBorder(javax.swing.BorderFactory.createCompoundBorder(
      javax.swing.BorderFactory.createLineBorder(new java.awt.Color(60,65,90)),
      javax.swing.BorderFactory.createEmptyBorder(2,6,2,6)));
    iconField.setFont(iconField.getFont().deriveFont(11f));

    // Helper to update preview
    Runnable[] updatePreview = {null};
    updatePreview[0] = () -> {
      String p = iconField.getText().trim();
      if (!p.isEmpty() && new File(p).exists()) {
        try {
          java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new File(p));
          if (img != null) {
            java.awt.Image scaled = img.getScaledInstance(44, 44, java.awt.Image.SCALE_SMOOTH);
            iconPreview.setIcon(new javax.swing.ImageIcon(scaled));
            iconPreview.setText("");
          }
        } catch (Exception ignored) { iconPreview.setIcon(null); iconPreview.setText("?"); }
      } else {
        iconPreview.setIcon(null);
        iconPreview.setText(p.isEmpty() ? "none" : "!");
        iconPreview.setForeground(new java.awt.Color(120,125,155));
      }
    };

    // Pre-fill if sketch has icon.png
    for (String p : new String[]{"icon.png","data/icon.png"}) {
      File ic = new File(sketch.getFolder(), p);
      if (ic.exists()) { iconField.setText(ic.getAbsolutePath()); break; }
    }
    updatePreview[0].run();

    // Buttons panel
    javax.swing.JPanel iconBtns = new javax.swing.JPanel(new java.awt.GridLayout(2,1,2,2));
    iconBtns.setBackground(bg);

    javax.swing.JButton iconBrowse = new javax.swing.JButton("Browse...");
    iconBrowse.setFont(iconBrowse.getFont().deriveFont(11f));
    iconBrowse.addActionListener(e -> {
      javax.swing.JFileChooser fc = new javax.swing.JFileChooser(
        iconField.getText().isEmpty() ? sketch.getFolder().getAbsolutePath()
          : new File(iconField.getText()).getParent());
      fc.setDialogTitle("Select App Icon (PNG)");
      fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images (*.png)","png"));
      fc.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
      fc.setPreferredSize(new java.awt.Dimension(700, 500));
      if (fc.showOpenDialog(dlg) == javax.swing.JFileChooser.APPROVE_OPTION) {
        iconField.setText(fc.getSelectedFile().getAbsolutePath());
        updatePreview[0].run();
      }
    });

    javax.swing.JButton iconClear = new javax.swing.JButton("Clear");
    iconClear.setFont(iconClear.getFont().deriveFont(11f));
    iconClear.setForeground(new java.awt.Color(180,80,80));
    iconClear.addActionListener(e -> {
      iconField.setText("");
      updatePreview[0].run();
    });

    iconBtns.add(iconBrowse); iconBtns.add(iconClear);
    iconBottom.add(iconField, java.awt.BorderLayout.CENTER);
    iconBottom.add(iconBtns, java.awt.BorderLayout.EAST);
    iconPanel.add(iconBottom, java.awt.BorderLayout.CENTER);
    center.add(iconPanel, java.awt.BorderLayout.NORTH);

    // ── Target checkboxes ────────────────────────────────────────────────────
    javax.swing.JPanel targets = new javax.swing.JPanel();
    targets.setLayout(new javax.swing.BoxLayout(targets, javax.swing.BoxLayout.Y_AXIS));
    targets.setBackground(bg);
    java.util.Map<ExportTarget,javax.swing.JCheckBox> boxes = new java.util.LinkedHashMap<>();

    for (ExportTarget t : ExportTarget.values()) {
      // Show all targets everywhere - unavailable ones shown grayed out
      boolean ok = avail.getOrDefault(t, false);
      boolean isMac = t==ExportTarget.MACOS_X64 || t==ExportTarget.MACOS_ARM64;

      javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,5,2));
      row.setBackground(bgRow);
      row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 34));

      javax.swing.JCheckBox cb = new javax.swing.JCheckBox(t.label);
      cb.setBackground(bgRow); cb.setEnabled(ok);
      cb.setForeground(ok ? blue : red);
      if (!ok) cb.setFont(cb.getFont().deriveFont(java.awt.Font.ITALIC));
      cb.setSelected(ok && (
        (isWin && t==ExportTarget.WINDOWS_X64) ||
        (!isWin && (t==ExportTarget.LINUX_X64 || t==ExportTarget.WINDOWS_X64))));
      if (isMac && ok) cb.addActionListener(e -> {
        if (cb.isSelected()) javax.swing.JOptionPane.showMessageDialog(dlg,
          "<html><b>macOS export</b> requires <b>osxcross</b> on your PATH.<br>"+
          "This may take several minutes to compile.<br>"+
          "If osxcross is not installed, export will fail.</html>",
          "macOS Warning", javax.swing.JOptionPane.WARNING_MESSAGE);
      });
      row.add(cb);
      boxes.put(t, cb);

      javax.swing.JLabel statusLbl = new javax.swing.JLabel(ok ? "✓" : "✗ not found");
      statusLbl.setForeground(ok ? green : red);
      statusLbl.setFont(statusLbl.getFont().deriveFont(10f));
      row.add(statusLbl);

      if (!ok) {
        javax.swing.JButton installBtn = new javax.swing.JButton("Install");
        installBtn.setBackground(new java.awt.Color(50,80,150));
        installBtn.setForeground(java.awt.Color.WHITE);
        installBtn.setFont(installBtn.getFont().deriveFont(10f));
        installBtn.setMargin(new java.awt.Insets(1,6,1,6));
        final ExportTarget ft=t; final javax.swing.JCheckBox fcb=cb;
        final javax.swing.JLabel fsl=statusLbl;
        installBtn.addActionListener(e -> {
          installBtn.setText("Installing...");
          installBtn.setEnabled(false);
          new Thread(() -> {
            boolean ok2 = installCompilerForTarget(ft, listener);
            javax.swing.SwingUtilities.invokeLater(() -> {
              if (ok2 && compilerExists(ft)) {
                fcb.setEnabled(true); fcb.setForeground(blue);
                fcb.setFont(fcb.getFont().deriveFont(java.awt.Font.PLAIN));
                fsl.setText("✓ ready"); fsl.setForeground(green);
                installBtn.setText("✓ Done");
                installBtn.setBackground(new java.awt.Color(40,110,55));
              } else {
                installBtn.setText("Retry"); installBtn.setEnabled(true);
                fsl.setText("✗ failed");
              }
            });
          }).start();
        });
        row.add(installBtn);
        String hint = getInstallHint(t);
        if (!hint.isEmpty()) {
          javax.swing.JLabel hl = new javax.swing.JLabel("  " + hint);
          hl.setForeground(new java.awt.Color(110,115,145));
          hl.setFont(hl.getFont().deriveFont(9f));
          row.add(hl);
        }
      }
      targets.add(row);
      targets.add(javax.swing.Box.createVerticalStrut(2));
    }

    javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(targets);
    scroll.setBorder(null); scroll.getViewport().setBackground(bg);
    center.add(scroll, java.awt.BorderLayout.CENTER);
    dlg.add(center, java.awt.BorderLayout.CENTER);

    // ── Buttons ──────────────────────────────────────────────────────────────
    javax.swing.JPanel btnRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,10,10));
    btnRow.setBackground(bg);
    javax.swing.JButton cancel = new javax.swing.JButton("Cancel");
    javax.swing.JButton export = new javax.swing.JButton("Export");
    export.setBackground(new java.awt.Color(55,95,175)); export.setForeground(java.awt.Color.WHITE);
    boolean[] go = {false};
    cancel.addActionListener(e -> dlg.dispose());
    export.addActionListener(e -> { go[0]=true; dlg.dispose(); });
    btnRow.add(cancel); btnRow.add(export);
    dlg.add(btnRow, java.awt.BorderLayout.SOUTH);

    dlg.pack();
    dlg.setMinimumSize(new java.awt.Dimension(560, 380));
    dlg.setLocationRelativeTo(null);
    dlg.setVisible(true);
    if (!go[0]) return;

    java.util.List<ExportTarget> selected = new java.util.ArrayList<>();
    for (java.util.Map.Entry<ExportTarget,javax.swing.JCheckBox> e : boxes.entrySet())
      if (e.getValue().isSelected()) selected.add(e.getKey());
    if (selected.isEmpty()) return;

    final String iconPath = iconField.getText().trim();
    new Thread(() -> runExport(sketch, listener, selected, iconPath)).start();
  }

  private String getInstallHint(ExportTarget t) {
    String os = System.getProperty("os.name","").toLowerCase();
    boolean isWin = os.contains("win");
    if (isWin) {
      switch (t) {
        case WINDOWS_X64: return "opens MSYS2 installer";
        case LINUX_X64: case LINUX_ARM64: case LINUX_ARM32:
          return "requires Linux - export from a Linux machine";
        case MACOS_X64: case MACOS_ARM64: return "opens osxcross guide";
        default: return "";
      }
    }
    switch (t) {
      case WINDOWS_X64: return isWin ? "opens MSYS2 installer" : "pacman -S mingw-w64-gcc";
      case LINUX_X64:   return "downloads ~50MB toolchain";
      case LINUX_ARM64: return "downloads ~100MB toolchain";
      case LINUX_ARM32: return "downloads ~100MB toolchain";
      case MACOS_X64: case MACOS_ARM64: return "opens osxcross guide";
      default: return "";
    }
  }

  private boolean installCompilerForTarget(ExportTarget t, RunnerListener listener) {
    String os = System.getProperty("os.name","").toLowerCase();
    boolean isWin = os.contains("win");
    try {



      String cmd = null;
      switch (t) {
        case WINDOWS_X64:
          if (isWin) {
            // On Windows, need MSYS2
            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://www.msys2.org"));
            javax.swing.JOptionPane.showMessageDialog(null,
              "<html><b>Opening MSYS2 download page.</b><br><br>" +
              "After installing MSYS2, open the MSYS2 terminal and run:<br><br>" +
              "<code>pacman -S mingw-w64-x86_64-gcc mingw-w64-x86_64-glfw mingw-w64-x86_64-glew</code><br><br>" +
              "Then restart Processing and try again.</html>",
              "Install MSYS2", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return false;
          }
          cmd = cmdExists("yay")
            ? "yay -S --noconfirm --needed mingw-w64-gcc mingw-w64-glfw mingw-w64-glew"
            : "pkexec pacman -S --noconfirm --needed mingw-w64-gcc mingw-w64-glfw mingw-w64-glew";
          break;
        case LINUX_X64:
        case LINUX_ARM64:
        case LINUX_ARM32:
          if (isWin) {
            javax.swing.JOptionPane.showMessageDialog(null,
              "<html><b>Linux export not available on Windows.</b><br><br>" +
              "C++ must be compiled natively for each platform.<br><br>" +
              "To build Linux exports:<br>" +
              "&nbsp;&nbsp;• Open this sketch on a Linux machine<br>" +
              "&nbsp;&nbsp;• Use File → Export Application there<br><br>" +
              "The exported Linux AppImage will run on any Linux distro.</html>",
              "Linux Export", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return false;
          }
          // On Linux/Mac: download musl toolchain
          return downloadMuslToolchain(t, listener);
        case MACOS_X64: case MACOS_ARM64:
          java.awt.Desktop.getDesktop().browse(
            new java.net.URI("https://github.com/tpoechtrager/osxcross#installation"));
          javax.swing.JOptionPane.showMessageDialog(null,
            "<html><b>Opening osxcross setup guide.</b><br><br>" +
            "Follow the instructions to install osxcross,<br>" +
            "then add it to your PATH and restart Processing.</html>",
            "macOS Cross-Compiler", javax.swing.JOptionPane.INFORMATION_MESSAGE);
          return false;
        default: return false;
      }
      if (cmd != null) {
        listener.statusNotice("Installing " + t.label + " compiler...");
        ProcessBuilder pb = isWin
          ? new ProcessBuilder("cmd", "/c", cmd)
          : new ProcessBuilder("bash", "-c", cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (java.io.BufferedReader br = new java.io.BufferedReader(
            new java.io.InputStreamReader(p.getInputStream()))) {
          String line;
          while ((line = br.readLine()) != null) {
            System.out.println("[install] " + line);
            out.append(line).append('\n');
          }
        }
        int ret = p.waitFor();
        if (ret != 0) {
          String log = out.toString();
          String snippet = log.length() > 800 ? log.substring(log.length()-800) : log;
          javax.swing.SwingUtilities.invokeLater(() ->
            javax.swing.JOptionPane.showMessageDialog(null,
              "<html><b>Install failed (exit " + ret + ")</b><br><br>" +
              "<pre>" + snippet.replace("<","&lt;").replace(">","&gt;") + "</pre></html>",
              "Install Failed", javax.swing.JOptionPane.ERROR_MESSAGE));
          return false;
        }
        return true;
      }
    } catch (Exception e) {
      System.err.println("Install failed: " + e.getMessage());
      javax.swing.JOptionPane.showMessageDialog(null,
        "<html>Install failed: " + e.getMessage() + "</html>",
        "Install Error", javax.swing.JOptionPane.ERROR_MESSAGE);
    }
    return false;
  }

  private void runExport(Sketch sketch, RunnerListener listener,
                         java.util.List<ExportTarget> targets, String iconPath) {
    listener.statusNotice("Exporting...");
    String skName = sketch.getName();
    File outBase = new File(sketch.getFolder().getParentFile(), skName + "-export");
    outBase.mkdirs();
    String modeSrc = runtimeDir.getParentFile().getAbsolutePath() + "/src";

    // Use writeSketch() to preprocess the sketch into Sketch_run.cpp
    java.util.List<File> sources = new java.util.ArrayList<>();
    File exportBuildDir = sketch.makeTempFolder();
    try {
      // writeSketch produces Sketch_run.cpp with all preprocessing done
      CppBuild helper = new CppBuild(sketch, mode);
      helper.writeSketch(listener);
      File sketchCpp = new File(helper.buildDir, "Sketch_run.cpp");
      if (sketchCpp.exists()) {
        sources.add(sketchCpp);
      } else {
        throw new Exception("Sketch_run.cpp not found after writeSketch");
      }
    } catch (Exception ex) {
      // Fallback: copy .pde as .cpp
      for (processing.app.SketchCode sc : sketch.getCode()) {
        File f = sc.getFile();
        if (f != null && f.exists()) {
          String name = f.getName().replaceAll("\\.pde$", ".cpp");
          File cpp = new File(exportBuildDir, name);
          try {
            java.nio.file.Files.copy(f.toPath(), cpp.toPath(),
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            sources.add(cpp);
          } catch (Exception ignored) {}
        }
      }
    }
    if (sources.isEmpty()) { listener.statusError("No source files"); return; }

    // Auto-install missing dependencies for each target before compiling
    installExportDeps(targets, listener);

    int done = 0;
    java.util.List<String> results = new java.util.ArrayList<>();
    for (ExportTarget t : targets) {
      listener.statusNotice("Exporting " + t.label + "...");
      try {
        exportTarget(sketch, t, outBase, modeSrc, skName, sources, listener, iconPath);
        results.add("\u2713 " + t.label); done++;
      } catch (Exception ex) {
        results.add("\u2717 " + t.label + ": " + ex.getMessage());
        System.err.println("Export failed " + t.label + ": " + ex.getMessage());
      }
    }

    final int d = done, tot = targets.size();
    final String sum = String.join("<br>", results);
    final String path = outBase.getAbsolutePath();
    listener.statusNotice(d + "/" + tot + " targets exported");
    javax.swing.SwingUtilities.invokeLater(() -> {
      javax.swing.JOptionPane.showMessageDialog(null,
        "<html><b>Export: " + d + "/" + tot + " targets</b><br><br>"
        + sum + "<br><br>Output: " + path + "</html>",
        "Export Result", d>0
          ? javax.swing.JOptionPane.INFORMATION_MESSAGE
          : javax.swing.JOptionPane.ERROR_MESSAGE);
      if (d > 0) {
        try { new ProcessBuilder("xdg-open", path).start(); }
        catch (Exception ignored) {}
      }
    });
  }

  private void exportTarget(Sketch sketch, ExportTarget t,
                            File outBase, String modeSrc, String skName,
                            java.util.List<File> sources,
                            RunnerListener listener, String iconPath) throws Exception {
    File dir = new File(outBase, t.dirName); dir.mkdirs();
    String binName = skName + (t.isWindows ? ".exe" : "");
    File binOut = new File(dir, binName);

    java.util.List<String> cmd = new java.util.ArrayList<>();
    String os = System.getProperty("os.name","").toLowerCase();
    boolean onWin = os.contains("win");
    if (onWin && t == ExportTarget.WINDOWS_X64) {
      // Native Windows build - find MSYS2 g++
      String compiler = "g++";
      String[] msysPaths = {
        "C:\\msys64\\mingw64\\bin\\g++.exe",
        "C:\\msys2\\mingw64\\bin\\g++.exe",
        System.getProperty("user.home") + "\\msys64\\mingw64\\bin\\g++.exe"
      };
      for (String p : msysPaths)
        if (new File(p).exists()) { compiler = p; break; }
      cmd.add(compiler);
    } else if (!t.isWindows && t != ExportTarget.MACOS_X64
               && t != ExportTarget.MACOS_ARM64) {
      // Linux targets: use bundled musl toolchain if available
      File muslComp = getMuslCompiler(t);
      if (muslComp != null) {
        cmd.add(muslComp.getAbsolutePath());
      } else {
        cmd.add(t.compiler); // fall back to system compiler
      }
    } else {
      cmd.add(t.compiler);
    }
    cmd.add("-std=c++17"); cmd.add("-O2");
    for (File s : sources) cmd.add(s.getAbsolutePath());
    // Use pre-built cached .o if available for this target
    String exportCacheDir = t.isWindows ? "cache/windows-x64"
      : (t == ExportTarget.LINUX_ARM64 ? "cache/linux-arm64"
      : (t == ExportTarget.LINUX_ARM32 ? "cache/linux-arm32"
      : "cache/linux-x64"));
    File exportCache = new File(runtimeDir.getParentFile(), exportCacheDir);
    File cachedProcessingO = new File(exportCache, "Processing.o");
    File cachedDefaultsO   = new File(exportCache, "Processing_defaults.o");
    if (cachedProcessingO.exists()) {
      cmd.add(cachedProcessingO.getAbsolutePath());
    } else {
      cmd.add(modeSrc + "/Processing.cpp");
    }
    if (cachedDefaultsO.exists()) {
      cmd.add(cachedDefaultsO.getAbsolutePath());
    } else {
      cmd.add(modeSrc + "/Processing_defaults.cpp");
    }
    cmd.add("-I" + modeSrc);
    cmd.add("-DPROCESSING_HAS_STB_IMAGE");
    // Debug mode is controlled by a "DEBUG" file at the root of the
    // CppMode folder (a sibling of src/), containing either "0" or "1".
    // Shipped builds always have it set to "0". To see PDEBUG(...)
    // output, edit that file to contain "1" and re-run the sketch --
    // no environment variables to remember, no rebuilding CppBuild
    // itself, works the same regardless of which terminal (or none at
    // all) launched the IDE.
    java.io.File debugFile = new java.io.File(runtimeDir.getParentFile(), "DEBUG");
    System.err.println("=== TRACE debugFile.getAbsolutePath()=" + debugFile.getAbsolutePath() + " exists=" + debugFile.exists() + " ===");
    if (debugFile.exists()) {
      try {
        String contents = new String(java.nio.file.Files.readAllBytes(debugFile.toPath())).trim();
        System.err.println("=== TRACE debugFile contents=[" + contents + "] equals1=" + "1".equals(contents) + " ===");
        if ("1".equals(contents)) {
          cmd.add("-DPROCESSING_DEBUG");
        }
      } catch (java.io.IOException e) {
        // If the DEBUG file can't be read for some reason, just proceed
        // with a normal, non-debug build rather than failing the
        // compile entirely over a missing diagnostic toggle.
      }
    }
    cmd.add("-DPROCESSING_EXPORT");


    // main() is now emitted inside Sketch_run.cpp -- don't link main.cpp separately

    // For Windows: pre-compile icon resource and add to link
    if (t.isWindows && iconPath != null && !iconPath.isEmpty()
        && new File(iconPath).exists()) {
      File resFile = compileIconResource(iconPath, dir, t);
      if (resFile != null) cmd.add(resFile.getAbsolutePath());
    }

    if (t.isWindows)      addWinFlags(cmd);
    else if (t==ExportTarget.MACOS_X64 || t==ExportTarget.MACOS_ARM64) addMacFlags(cmd);
    else                  addLinuxFlags(cmd);

    cmd.add("-o"); cmd.add(binOut.getAbsolutePath());

    System.out.println("$ " + String.join(" ", cmd));
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    pb.directory(sketch.getFolder());
    Process p = pb.start();
    StringBuilder log = new StringBuilder();
    try (java.io.BufferedReader br = new java.io.BufferedReader(
        new java.io.InputStreamReader(p.getInputStream()))) {
      String line; while ((line=br.readLine())!=null) { System.out.println(line); log.append(line).append("\n"); }
    }
    if (p.waitFor() != 0) throw new Exception(log.substring(0, Math.min(400, log.length())));

    // Post-build
    if (t.isWindows) { mkBat(dir, binName); } // no DLLs needed - statically linked
    else if (t==ExportTarget.MACOS_X64 || t==ExportTarget.MACOS_ARM64) mkAppBundle(dir, skName, binOut, iconPath);
    else {
      // Try to produce an AppImage (single-file, shows icon in Dolphin)
      boolean madeAppImage = mkAppImage(dir, binName, skName, iconPath, listener);
      if (!madeAppImage) mkSh(dir, binName); // fallback to run.sh
    }

    // Copy data/
    File data = new File(sketch.getFolder(), "data");
    if (data.exists()) copyDir(data, new File(dir, "data"));

    // Copy font
    File fontSrc = new File(runtimeDir.getParentFile(), "fonts/ProcessingSansPro-Regular.ttf");
    if (fontSrc.exists()) copyFile(fontSrc, new File(dir, "ProcessingSansPro-Regular.ttf"));
  }

  private void addLinuxFlags(java.util.List<String> cmd) {
    // Use static GLFW/GLEW if available, otherwise dynamic
    boolean staticGlfw = new java.io.File("/usr/lib/libglfw3.a").exists()
                      || new java.io.File("/usr/local/lib/libglfw3.a").exists();
    boolean staticGlew = new java.io.File("/usr/lib/libGLEW.a").exists()
                      || new java.io.File("/usr/local/lib/libGLEW.a").exists();
    if (staticGlfw && staticGlew) {
      cmd.add("-Wl,-Bstatic"); cmd.add("-lglfw3"); cmd.add("-lGLEW");
      cmd.add("-Wl,-Bdynamic");
    } else {
      cmd.add("-lglfw"); cmd.add("-lGLEW");
    }
    cmd.add("-lGL"); cmd.add("-lm"); cmd.add("-ldl"); cmd.add("-lpthread");
    cmd.add("-lX11"); cmd.add("-lXrandr"); cmd.add("-lXi");
    cmd.add("-lXxf86vm"); cmd.add("-lXinerama"); cmd.add("-lXcursor");
    cmd.add("-static-libgcc"); cmd.add("-static-libstdc++");
  }

  private void addWinFlags(java.util.List<String> cmd) {
    String os = System.getProperty("os.name","").toLowerCase();
    String[] roots;
    if (os.contains("win")) {
      // On Windows, use MSYS2 mingw64 paths
      roots = new String[]{
        "C:\\msys64\\mingw64",
        "C:\\msys2\\mingw64",
        System.getProperty("user.home") + "\\msys64\\mingw64"
      };
    } else {
      // On Linux, use cross-compile sysroot
      roots = new String[]{
        "/usr/x86_64-w64-mingw32",
        "/usr/local/x86_64-w64-mingw32",
        "/opt/mingw-w64/x86_64-w64-mingw32"
      };
    }
    String root = roots[0];
    for (String r : roots) if (new File(r+(os.contains("win")?"\\lib":"/lib")).exists()) { root=r; break; }
    String sep = os.contains("win") ? "\\" : "/";

    // Include paths - add all that exist
    for (String inc : new String[]{root+sep+"include", root+sep+"include"+sep+"GL"})
      if (new File(inc).exists()) cmd.add("-I"+inc);

    // Lib path
    cmd.add("-L"+root+sep+"lib");

    // Also try bin dir for DLL import libs
    if (new File(root+sep+"bin").exists()) cmd.add("-L"+root+sep+"bin");

    // Link libraries - DLLs shipped alongside exe
    cmd.add("-lglfw3");
    cmd.add("-lglew32");
    cmd.add("-lopengl32");
    cmd.add("-lgdi32");
    cmd.add("-luser32");
    cmd.add("-lshell32");
    cmd.add("-lwinmm");
    cmd.add("-lole32");
    cmd.add("-luuid");
    cmd.add("-mwindows");       // WinMain = no console terminal
    cmd.add("-static-libgcc");
    cmd.add("-static-libstdc++");
  }

  private void addMacFlags(java.util.List<String> cmd) {
    String[] roots = {
      System.getProperty("user.home")+"/osxcross/target/SDK/MacOSX14.0.sdk",
      "/opt/osxcross/target/SDK/MacOSX14.0.sdk"
    };
    for (String r : roots) if (new File(r).exists()) { cmd.add("--sysroot="+r); break; }
    cmd.add("-framework"); cmd.add("OpenGL");
    cmd.add("-framework"); cmd.add("Cocoa");
    cmd.add("-framework"); cmd.add("IOKit");
    cmd.add("-framework"); cmd.add("CoreVideo");
    cmd.add("-lglfw"); cmd.add("-lGLEW");
    cmd.add("-mmacosx-version-min=11.0");
  }

  private void copyWinDlls(File dir) {
    String[] dlls = {"glfw3.dll","glew32.dll","libgcc_s_seh-1.dll",
                     "libstdc++-6.dll","libwinpthread-1.dll"};
    // Bundled DLLs ship with CppMode - use these first (always available)
    File bundledLibs = new File(runtimeDir.getParentFile(), "libs/windows-x64");
    // Fallback: system mingw installation
    String[] searchRoots = {
      bundledLibs.getAbsolutePath(),
      "/usr/x86_64-w64-mingw32/bin",
      "/usr/x86_64-w64-mingw32/lib",
      "/usr/lib/gcc/x86_64-w64-mingw32"
    };
    for (String dll : dlls) {
      File found = findFile(dll, searchRoots);
      if (found != null) {
        try { copyFile(found, new File(dir, dll)); System.out.println("[dll] copied " + dll); }
        catch (Exception e) { System.err.println("[dll] failed " + dll + ": " + e.getMessage()); }
      } else {
        System.err.println("[dll] WARNING: not found: " + dll);
      }
    }
  }

  private File findFile(String name, String[] roots) {
    for (String root : roots) {
      File f = new File(root, name);
      if (f.exists()) return f;
      // Search one level deep
      File dir = new File(root);
      if (dir.exists() && dir.listFiles() != null) {
        for (File sub : dir.listFiles()) {
          if (sub.isDirectory()) {
            File f2 = new File(sub, name);
            if (f2.exists()) return f2;
            // One more level
            if (sub.listFiles() != null) {
              for (File sub2 : sub.listFiles()) {
                if (sub2.isDirectory()) {
                  File f3 = new File(sub2, name);
                  if (f3.exists()) return f3;
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  private void mkBat(File dir, String bin) throws Exception {
    String skName = bin.replace(".exe","");
    try (PrintWriter pw = new PrintWriter(new File(dir,"run.bat"))) {
      pw.println("@echo off");
      pw.println("cd /d \"%~dp0\"");
      pw.println("set PROCESSING_SKETCH_NAME=" + skName);
      pw.println("set PROCESSING_SKETCH_PATH=%CD%");
      pw.println("start \"\" \"" + bin + "\"");
    }
    try (PrintWriter pw = new PrintWriter(new File(dir,"launch.vbs"))) {
      pw.println("Set sh = CreateObject(\"WScript.Shell\")");
      pw.println("sh.CurrentDirectory = CreateObject(\"Scripting.FileSystemObject\").GetParentFolderName(WScript.ScriptFullName)");
      pw.println("sh.Run Chr(34) & sh.CurrentDirectory & \"\\\\" + bin + "\" & Chr(34), 0, False");
    }
  }

  private void mkSh(File dir, String bin) throws Exception {
    String skName = bin;
    File sh = new File(dir,"run.sh");
    try (PrintWriter pw = new PrintWriter(sh)) {
      pw.println("#!/bin/bash");
      pw.println("cd \"$(dirname \"$0\")\"");
      pw.println("export PROCESSING_SKETCH_NAME=\"" + skName + "\"");
      pw.println("export PROCESSING_SKETCH_PATH=\"$(pwd)\"");
      pw.println("./" + bin + " \"$@\"");
    }
    sh.setExecutable(true);
    new File(dir,bin).setExecutable(true);
  }

  private void mkAppBundle(File dir, String name, File bin, String iconPath) throws Exception {
    File app = new File(dir, name + ".app");
    File macosDir = new File(app, "Contents/MacOS"); macosDir.mkdirs();
    File res = new File(app, "Contents/Resources"); res.mkdirs();
    File bundleBin = new File(macosDir, name);
    if (bin.exists()) { bin.renameTo(bundleBin); bundleBin.setExecutable(true); }
    try (PrintWriter pw = new PrintWriter(new File(app, "Contents/Info.plist"))) {
      pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      pw.println("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">");
      pw.println("<plist version=\"1.0\"><dict>");
      pw.println("  <key>CFBundleName</key><string>" + name + "</string>");
      pw.println("  <key>CFBundleExecutable</key><string>" + name + "</string>");
      pw.println("  <key>CFBundleIdentifier</key><string>processing.cpp." + name + "</string>");
      pw.println("  <key>CFBundleVersion</key><string>1.0</string>");
      pw.println("  <key>CFBundlePackageType</key><string>APPL</string>");
      pw.println("  <key>NSHighResolutionCapable</key><true/>");
      if (iconPath != null && !iconPath.isEmpty() && new File(iconPath).exists()) {
        pw.println("  <key>CFBundleIconFile</key><string>" + name + "</string>");
        try { copyFile(new File(iconPath), new File(res, name + ".png")); } catch (Exception ignored) {}
      }
      pw.println("</dict></plist>");
    }
    // run.sh inside bundle
    File sh = new File(macosDir, "run.sh");
    try (PrintWriter pw = new PrintWriter(sh)) {
      pw.println("#!/bin/bash");
      pw.println("cd \"$(dirname \"$0\")\"");
      pw.println("export PROCESSING_SKETCH_NAME=\"" + name + "\"");
      pw.println("./" + name + " \"$@\"");
    }
    sh.setExecutable(true);
  }

  private void embedIcon(File bin, ExportTarget t, String iconPng, File outDir) {
    try {
      if (t.isWindows) embedIconWindows(bin, iconPng, outDir);
      else if (t == ExportTarget.LINUX_X64 || t == ExportTarget.LINUX_ARM64
            || t == ExportTarget.LINUX_ARM32) {
        // Copy icon.png next to binary for Linux
        copyFile(new File(iconPng), new File(outDir, "icon.png"));
        // Create .desktop file
        File desktop = new File(outDir, bin.getName() + ".desktop");
        try (PrintWriter pw = new PrintWriter(desktop)) {
          pw.println("[Desktop Entry]");
          pw.println("Type=Application");
          pw.println("Name=" + bin.getName());
          pw.println("Exec=" + bin.getAbsolutePath());
          pw.println("Icon=" + new File(outDir, "icon.png").getAbsolutePath());
          pw.println("Terminal=false");
        }
      }
    } catch (Exception e) { System.err.println("[icon] " + e.getMessage()); }
  }

  private void embedIconWindows(File exe, String iconPng, File outDir) throws Exception {
    // Convert PNG -> ICO using ImageMagick
    File icoFile = new File(outDir, "_icon.ico");
    String convert = System.getProperty("os.name","").toLowerCase().contains("win") ? "magick" : "convert";
    if (cmdExists(convert)) {
      new ProcessBuilder(convert, iconPng,
        "-define", "icon:auto-resize=256,128,64,48,32,16",
        icoFile.getAbsolutePath())
        .redirectErrorStream(true).start().waitFor();
    }
    if (!icoFile.exists()) {
      System.err.println("[icon] ICO conversion failed - copying PNG alongside exe");
      copyFile(new File(iconPng), new File(outDir, "icon.png"));
      return;
    }
    // Create .rc and compile with windres
    File rcFile = new File(outDir, "_icon.rc");
    try (PrintWriter pw = new PrintWriter(rcFile)) {
      pw.println("1 ICON \"" + icoFile.getAbsolutePath().replace("\\", "/") + "\"");
    }
    String windres = cmdExists("x86_64-w64-mingw32-windres")
      ? "x86_64-w64-mingw32-windres" : "windres";
    File resFile = new File(outDir, "_icon.res");
    int rc = new ProcessBuilder(windres, rcFile.getAbsolutePath(),
      "-O", "coff", "-o", resFile.getAbsolutePath())
      .redirectErrorStream(true).start().waitFor();
    // Clean up temp files; copy ico alongside exe regardless
    copyFile(icoFile, new File(outDir, exe.getName().replace(".exe","") + ".ico"));
    icoFile.delete(); rcFile.delete(); resFile.delete();
    System.out.println("[icon] Windows icon created alongside exe");
  }

  private void installExportDeps(java.util.List<ExportTarget> targets, RunnerListener listener) {
    boolean needsWin = targets.stream().anyMatch(t -> t.isWindows);
    boolean needsLinux = targets.stream().anyMatch(t ->
      t==ExportTarget.LINUX_X64||t==ExportTarget.LINUX_ARM64||t==ExportTarget.LINUX_ARM32);
    if (needsWin) {
      boolean glfwOk = new File("/usr/x86_64-w64-mingw32/lib/libglfw3.a").exists()
                    || new File("/usr/x86_64-w64-mingw32/lib/libglfw3dll.a").exists();
      boolean glewOk = new File("/usr/x86_64-w64-mingw32/include/GL/glew.h").exists();
      if (!glfwOk || !glewOk) {
        listener.statusNotice("Installing Windows cross-compile libs...");
        try {
          String cmd = cmdExists("yay")
            ? "yay -S --noconfirm --needed mingw-w64-glfw mingw-w64-glew"
            : "pkexec pacman -S --noconfirm --needed mingw-w64-glfw mingw-w64-glew";
          new ProcessBuilder("bash","-c",cmd).inheritIO().start().waitFor();
        } catch (Exception ignored) {}
      }
    }
    if (needsLinux) {
      boolean glfwOk = new File("/usr/lib/libglfw3.a").exists()
                    || new File("/usr/lib/libglfw.so").exists();
      if (!glfwOk) {
        listener.statusNotice("Installing Linux native libs...");
        try {
          String cmd = cmdExists("pacman")
            ? "pkexec pacman -S --noconfirm --needed glfw-x11 glew"
            : "pkexec apt-get install -y libglfw3-dev libglew-dev";
          new ProcessBuilder("bash","-c",cmd).inheritIO().start().waitFor();
        } catch (Exception ignored) {}
      }
    }
  }

  private void copyFile(File src, File dst) throws Exception {
    dst.getParentFile().mkdirs();
    Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }

  private void copyDir(File src, File dst) throws Exception {
    dst.mkdirs();
    File[] files = src.listFiles();
    if (files == null) return;
    for (File f : files) {
      if (f.isDirectory()) copyDir(f, new File(dst, f.getName()));
      else copyFile(f, new File(dst, f.getName()));
    }
  }

  private boolean mkAppImage(File dir, String binName, String skName,
                             String iconPath, RunnerListener listener) {
    try {
      File tools = new File(runtimeDir.getParentFile(), "tools");
      File appImageTool = new File(tools, "appimagetool-x86_64.AppImage");
      if (!appImageTool.exists()) {
        System.err.println("[appimage] appimagetool not found");
        return false;
      }

      // Build AppDir
      File appDir = new File(dir, skName + ".AppDir");
      File usrBin = new File(appDir, "usr/bin");
      usrBin.mkdirs();

      // Copy binary
      File binary = new File(dir, binName);
      File bundledBin = new File(usrBin, skName);
      copyFile(binary, bundledBin);
      bundledBin.setExecutable(true);

      // Copy data/ and font
      File dataDir = new File(dir, "data");
      if (dataDir.exists()) copyDir(dataDir, new File(usrBin, "data"));
      File font = new File(dir, "ProcessingSansPro-Regular.ttf");
      if (font.exists()) copyFile(font, new File(usrBin, "ProcessingSansPro-Regular.ttf"));

      // Icon - use skName directly, no dashes (Dolphin is picky)
      String iconName = skName;
      File iconDst = new File(appDir, iconName + ".png");
      if (iconPath != null && !iconPath.isEmpty() && new File(iconPath).exists()) {
        copyFile(new File(iconPath), iconDst);
      } else if (cmdExists("convert")) {
        new ProcessBuilder("convert", "-size", "256x256", "xc:#3366cc",
          iconDst.getAbsolutePath()).redirectErrorStream(true).start().waitFor();
      }
      // .DirIcon is required for Dolphin/Nautilus to show the icon
      if (iconDst.exists()) copyFile(iconDst, new File(appDir, ".DirIcon"));

      // .desktop file
      File desktop = new File(appDir, skName + ".desktop");
      try (PrintWriter pw = new PrintWriter(desktop)) {
        pw.println("[Desktop Entry]");
        pw.println("Version=1.0");
        pw.println("Type=Application");
        pw.println("Name=" + skName);
        pw.println("Comment=Made with Processing C++ Mode");
        pw.println("Exec=" + skName);
        pw.println("Icon=" + iconName);
        pw.println("Terminal=false");
        pw.println("Categories=Application;");
      }

      // AppRun - write via Files.write to avoid quote escaping issues
      File appRun = new File(appDir, "AppRun");
      String appRunContent = "#!/bin/bash\n"
        + "SELF=$(readlink -f \"$0\")\n"
        + "HERE=$(dirname \"$SELF\")\n"
        + "export PROCESSING_SKETCH_NAME=" + skName + "\n"
        + "export PROCESSING_SKETCH_PATH=\"$HERE/usr/bin\"\n"
        + "cd \"$HERE/usr/bin\"\n"
        + "exec \"$HERE/usr/bin/" + skName + "\" \"$@\"\n";
      Files.write(appRun.toPath(), appRunContent.getBytes());
      appRun.setExecutable(true);

      // Run appimagetool
      listener.statusNotice("Packaging AppImage...");
      File appImageOut = new File(dir, skName + "-x86_64.AppImage");
      ProcessBuilder pb = new ProcessBuilder(
        appImageTool.getAbsolutePath(), "--no-appstream",
        appDir.getAbsolutePath(), appImageOut.getAbsolutePath());
      pb.environment().put("ARCH", "x86_64");
      pb.redirectErrorStream(true);
      StringBuilder log = new StringBuilder();
      Process p = pb.start();
      try (java.io.BufferedReader br = new java.io.BufferedReader(
          new java.io.InputStreamReader(p.getInputStream()))) {
        String line;
        while ((line = br.readLine()) != null) {
          System.out.println("[appimage] " + line);
          log.append(line).append('\n');
        }
      }
      int exit = p.waitFor();
      if (exit != 0 || !appImageOut.exists()) {
        System.err.println("[appimage] failed:\n" + log);
        return false;
      }
      appImageOut.setExecutable(true);
      binary.delete();
      deleteDir(appDir);

      // Create a .desktop wrapper for Dolphin/file managers
      // This avoids display rotation issues when launching AppImage from file manager
      File desktopLauncher = new File(dir, skName + ".desktop");
      String iconPath2 = (iconPath != null && !iconPath.isEmpty() && new File(iconPath).exists())
        ? iconPath : "";
      try (PrintWriter pw = new PrintWriter(desktopLauncher)) {
        pw.println("[Desktop Entry]");
        pw.println("Version=1.0");
        pw.println("Type=Application");
        pw.println("Name=" + skName);
        pw.println("Comment=Made with Processing C++ Mode");
        pw.println("Exec=bash -c \"cd $(dirname %k) && ./" + appImageOut.getName() + "\"");
        pw.println("Icon=" + (iconPath2.isEmpty() ? "application-x-executable" : iconPath2));
        pw.println("Terminal=false");
        pw.println("Categories=Application;");
        pw.println("StartupNotify=true");
      }
      desktopLauncher.setExecutable(true);
      System.out.println("[appimage] created: " + appImageOut.getName());
      return true;
    } catch (Exception e) {
      System.err.println("[appimage] " + e.getMessage());
      return false;
    }
  }

  private void deleteDir(File d) {
    if (!d.exists()) return;
    File[] fs = d.listFiles();
    if (fs != null) for (File f : fs) { if (f.isDirectory()) deleteDir(f); else f.delete(); }
    d.delete();
  }




  // Compile PNG icon into a Windows .res file for linking
  private File compileIconResource(String iconPng, File outDir, ExportTarget t) {
    try {
      String os = System.getProperty("os.name","").toLowerCase();
      boolean onWin = os.contains("win");

      // PNG -> ICO using ImageMagick (magick on Windows, convert on Linux)
      File icoFile = new File(outDir, "_icon_tmp.ico");
      String imageMagick = onWin ? "magick" : "convert";

      // On Windows, also try full MSYS2 path
      if (onWin && !cmdExists("magick")) {
        String[] magickPaths = {
          "C:\\msys64\\usr\\bin\\magick.exe",
          "C:\\msys64\\mingw64\\bin\\magick.exe",
          "C:\\Program Files\\ImageMagick-7.0.0-Q16\\magick.exe"
        };
        for (String p : magickPaths)
          if (new File(p).exists()) { imageMagick = p; break; }
      }

      if (!cmdExists(imageMagick) && !new File(imageMagick).exists()) {
        System.err.println("[icon] ImageMagick not found - skipping icon embed");
        System.err.println("[icon] Install ImageMagick: https://imagemagick.org/script/download.php#windows");
        return null;
      }

      int rc = new ProcessBuilder(imageMagick, iconPng,
        "-define", "icon:auto-resize=256,128,64,48,32,16",
        icoFile.getAbsolutePath())
        .redirectErrorStream(true).start().waitFor();

      if (rc != 0 || !icoFile.exists()) {
        System.err.println("[icon] PNG->ICO conversion failed");
        return null;
      }

      // .rc file - use forward slashes for windres
      File rcFile = new File(outDir, "_icon_tmp.rc");
      String icoPath = icoFile.getAbsolutePath().replace("\\", "/");
      String rcContent = "1 ICON \"" + icoPath + "\"\n";
      Files.write(rcFile.toPath(), rcContent.getBytes());

      // Find windres - on Windows use MSYS2 windres, on Linux use mingw windres
      String windres;
      if (onWin) {
        windres = "C:\\msys64\\mingw64\\bin\\windres.exe";
        if (!new File(windres).exists())
          windres = "C:\\msys2\\mingw64\\bin\\windres.exe";
        if (!new File(windres).exists())
          windres = "windres"; // fallback to PATH
      } else {
        windres = cmdExists("x86_64-w64-mingw32-windres")
          ? "x86_64-w64-mingw32-windres" : "windres";
      }

      File resFile = new File(outDir, "_icon_tmp.res");
      StringBuilder windresLog = new StringBuilder();
      ProcessBuilder wpb = new ProcessBuilder(windres,
        rcFile.getAbsolutePath(), "-O", "coff", "-o", resFile.getAbsolutePath());
      wpb.redirectErrorStream(true);
      Process wp = wpb.start();
      try (java.io.BufferedReader br = new java.io.BufferedReader(
          new java.io.InputStreamReader(wp.getInputStream()))) {
        String line; while ((line=br.readLine())!=null) windresLog.append(line).append('\n');
      }
      int rc2 = wp.waitFor();
      icoFile.delete(); rcFile.delete();

      if (rc2 != 0 || !resFile.exists()) {
        System.err.println("[icon] windres failed: " + windresLog);
        return null;
      }
      System.out.println("[icon] Windows icon resource compiled successfully");
      return resFile;
    } catch (Exception e) {
      System.err.println("[icon] compileIconResource: " + e.getMessage());
      return null;
    }
  }


  // ── Musl cross-compiler toolchains (pre-built, no system install needed) ──

  private static final String MUSL_BASE = "https://musl.cc/";

  private static final java.util.Map<ExportTarget, String[]> TOOLCHAIN_INFO =
    new java.util.LinkedHashMap<ExportTarget, String[]>() {{
      put(ExportTarget.LINUX_X64,   new String[]{
        "x86_64-linux-musl-cross.tgz",
        "x86_64-linux-musl-cross/bin/x86_64-linux-musl-g++"});
      put(ExportTarget.LINUX_ARM64, new String[]{
        "aarch64-linux-musl-cross.tgz",
        "aarch64-linux-musl-cross/bin/aarch64-linux-musl-g++"});
      put(ExportTarget.LINUX_ARM32, new String[]{
        "armv7l-linux-musleabihf-cross.tgz",
        "armv7l-linux-musleabihf-cross/bin/armv7l-linux-musleabihf-g++"});
    }};

  // Get the bundled musl toolchain compiler path for a target
  private File getMuslCompiler(ExportTarget t) {
    String[] info = TOOLCHAIN_INFO.get(t);
    if (info == null) return null;
    File toolsDir = new File(runtimeDir.getParentFile(), "tools/cross");
    File compiler = new File(toolsDir, info[1]);
    return compiler.exists() ? compiler : null;
  }

  // Check if musl toolchain is installed for target
  private boolean muslToolchainInstalled(ExportTarget t) {
    return getMuslCompiler(t) != null;
  }

  // Download and extract musl toolchain with progress dialog
  private boolean downloadMuslToolchain(ExportTarget t, RunnerListener listener) {
    String[] info = TOOLCHAIN_INFO.get(t);
    if (info == null) return false;

    String tgzName = info[0];
    String url = MUSL_BASE + tgzName;
    File toolsDir = new File(runtimeDir.getParentFile(), "tools/cross");
    toolsDir.mkdirs();
    File tgzFile = new File(toolsDir, tgzName);

    // Progress dialog
    javax.swing.JDialog prog = new javax.swing.JDialog((java.awt.Frame)null,
      "Downloading " + t.label + " toolchain", false);
    javax.swing.JProgressBar bar = new javax.swing.JProgressBar(0, 100);
    bar.setStringPainted(true);
    bar.setString("Connecting...");
    javax.swing.JLabel lbl = new javax.swing.JLabel(
      "  Downloading from musl.cc (~100MB)  ");
    lbl.setBorder(javax.swing.BorderFactory.createEmptyBorder(8,12,4,12));
    prog.setLayout(new java.awt.BorderLayout());
    prog.add(lbl, java.awt.BorderLayout.NORTH);
    prog.add(bar, java.awt.BorderLayout.CENTER);
    prog.pack();
    prog.setMinimumSize(new java.awt.Dimension(400, prog.getHeight()));
    prog.setLocationRelativeTo(null);
    prog.setVisible(true);

    try {
      // Download with progress
      java.net.URL urlObj = new java.net.URL(url);
      java.net.HttpURLConnection conn =
        (java.net.HttpURLConnection) urlObj.openConnection();
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(60000);
      long total = conn.getContentLengthLong();
      listener.statusNotice("Downloading " + tgzName + "...");

      try (java.io.InputStream in = conn.getInputStream();
           java.io.FileOutputStream out = new java.io.FileOutputStream(tgzFile)) {
        byte[] buf = new byte[65536];
        long downloaded = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
          out.write(buf, 0, n);
          downloaded += n;
          if (total > 0) {
            final int pct = (int)(downloaded * 100 / total);
            final String mb = String.format("%.1f / %.1f MB",
              downloaded/1048576.0, total/1048576.0);
            javax.swing.SwingUtilities.invokeLater(() -> {
              bar.setValue(pct);
              bar.setString(mb);
            });
          }
        }
      }
      conn.disconnect();

      // Extract
      bar.setString("Extracting...");
      bar.setIndeterminate(true);
      listener.statusNotice("Extracting " + tgzName + "...");

      // Use tar command (available on Linux, Mac, and Windows 10+)
      String os = System.getProperty("os.name","").toLowerCase();
      ProcessBuilder pb;
      if (os.contains("win")) {
        pb = new ProcessBuilder("tar", "-xzf",
          tgzFile.getAbsolutePath(), "-C", toolsDir.getAbsolutePath());
      } else {
        pb = new ProcessBuilder("tar", "-xzf",
          tgzFile.getAbsolutePath(), "-C", toolsDir.getAbsolutePath());
      }
      pb.redirectErrorStream(true);
      int exitCode = pb.start().waitFor();

      tgzFile.delete(); // clean up archive after extraction
      prog.dispose();

      if (exitCode != 0 || !muslToolchainInstalled(t)) {
        listener.statusError("Toolchain extraction failed");
        return false;
      }

      // Make compiler executable (Linux/Mac)
      File compiler = getMuslCompiler(t);
      if (compiler != null) {
        compiler.setExecutable(true);
        // Make all binaries in bin/ executable
        File binDir = compiler.getParentFile();
        if (binDir.listFiles() != null)
          for (File f : binDir.listFiles()) f.setExecutable(true);
      }

      listener.statusNotice(t.label + " toolchain ready");
      return true;

    } catch (Exception e) {
      prog.dispose();
      System.err.println("[toolchain] download failed: " + e.getMessage());
      javax.swing.SwingUtilities.invokeLater(() ->
        javax.swing.JOptionPane.showMessageDialog(null,
          "<html><b>Download failed:</b><br>" + e.getMessage() + "<br><br>" +
          "Check your internet connection and try again.</html>",
          "Download Failed", javax.swing.JOptionPane.ERROR_MESSAGE));
      if (tgzFile.exists()) tgzFile.delete();
      return false;
    }
  }

}
