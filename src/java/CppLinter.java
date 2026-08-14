package processing.mode.cpp;

import processing.app.Problem;
import processing.app.Sketch;
import processing.app.SketchCode;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.*;

/**
 * Background lint checker for CppMode.
 *
 * Two optimisations:
 *
 * 1. PCH CACHING -- Processing.h is precompiled to a .gch alongside the
 *    cached .o files in cache/linux-x64/ (or equivalent). The .gch is
 *    reused on every lint run, cutting g++ time from ~2s to ~200ms.
 *    The .gch is regenerated only when Processing.h is newer.
 *
 * 2. MULTI-TAB LINE MAPPING -- Each tab's code is tracked with its start
 *    line in the concatenated source so errors map back to the right tab
 *    and line, matching what the Problem API expects.
 */
public class CppLinter {
    private static final int DEBOUNCE_MS = 800;

    private static final Pattern GCC_DIAG = Pattern.compile(
        "[^:]+:(\\d+)(?::\\d+)?:\\s+(error|warning):\\s+(.+)");

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CppLinter");
            t.setDaemon(true);
            return t;
        });

    private ScheduledFuture<?> pending;
    private final Consumer<List<Problem>> consumer;
    private final CppMode mode;

    // Cached PCH path -- computed once, reused across lint runs
    private volatile File pchFile = null;

    public CppLinter(CppMode mode, Consumer<List<Problem>> consumer) {
        this.mode     = mode;
        this.consumer = consumer;
    }

    /**
     * @param sketch      the current sketch (for tab count and saved code)
     * @param currentTab  index of the tab currently open in the editor
     * @param liveText    live (unsaved) text of the current tab
     */
    public synchronized void scheduleCheck(Sketch sketch, int currentTab, String liveText) {
        if (pending != null) pending.cancel(false);
        pending = scheduler.schedule(() -> runCheck(sketch, currentTab, liveText),
                                     DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    public void shutdown() { scheduler.shutdownNow(); }

    // ── PCH management ────────────────────────────────────────────────────

    /**
     * Returns path to a valid Processing.h.gch, building it if needed.
     * Returns null if PCH build fails (lint will still work, just slower).
     */
    private File ensurePch(File runtimeDir, File processingH, String gpp) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String sub = osName.contains("win") ? "cache/windows-x64"
                   : osName.contains("mac") ? "cache/macos-" +
                     (System.getProperty("os.arch","").contains("aarch64") ? "arm64" : "x64")
                   : "cache/linux-x64";
        File cacheDir = new File(runtimeDir.getParentFile(), sub);
        cacheDir.mkdirs();
        File gch = new File(cacheDir, "Processing.h.gch");

        if (gch.exists()) return gch; // use cached PCH

        // Build PCH: g++ -x c++-header -std=c++23 Processing.h -o Processing.h.gch
        try {
            ProcessBuilder pb = new ProcessBuilder(
                gpp, "-x", "c++-header", "-std=c++23",
                "-I", runtimeDir.getAbsolutePath(),
                processingH.getAbsolutePath(),
                "-o", gch.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.getInputStream().readAllBytes(); // drain
            proc.waitFor(30, TimeUnit.SECONDS);
            if (proc.exitValue() == 0) return gch;
        } catch (Exception ignored) {}
        return null; // PCH failed, lint without it
    }

    // ── Core lint logic ───────────────────────────────────────────────────

    private void runCheck(Sketch sketch, int currentTab, String liveText) {
        List<Problem> problems = new ArrayList<>();
        Path tmp = null;
        try {
            File runtimeDir = mode.getRuntimeDir();
            File processingH = new File(runtimeDir, "Processing.h");
            if (!processingH.exists()) return;

            boolean isWin = System.getProperty("os.name","").toLowerCase().contains("win");
            String gpp = isWin
                ? (InstallWizard.getPortableGpp().exists()
                    ? InstallWizard.getPortableGpp().getAbsolutePath() : "g++")
                : "g++";

            // Ensure PCH is up to date (fast no-op if already fresh)
            File gch = ensurePch(runtimeDir, processingH, gpp);

            // Build concatenated sketch source, tracking tab boundaries.
            // tabStarts[i] = first sketch.pde line number (1-indexed) of tab i
            // after the header lines are accounted for.
            int tabCount = sketch.getCodeCount();
            int[] tabStartLines = new int[tabCount]; // 1-indexed sketch line where each tab starts
            StringBuilder sketchCode = new StringBuilder();
            int currentLine = 1;
            for (int i = 0; i < tabCount; i++) {
                tabStartLines[i] = currentLine;
                // Use live editor text for the current tab so lint
                // reflects unsaved keystrokes, not the last saved version.
                String prog = (i == currentTab) ? liveText : sketch.getCode(i).getProgram();
                if (prog == null) prog = "";
                sketchCode.append(prog).append("\n");
                currentLine += countLines(prog) + 1;
            }

            // Pre-AST transforms
            CppBuild.PreparedCode prepared = CppBuild.prepareCode(sketchCode.toString());
            String code = prepared.code;

            // Build lint source
            StringBuilder sb = new StringBuilder();
            for (String block : prepared.hasIncludeBlocks.values())
                sb.append(block);
            if (gch != null) {
                // Use PCH via -include trick: include a stub that matches the PCH
                sb.append("#include \"").append(processingH.getAbsolutePath()).append("\"\n");
            } else {
                sb.append("#include \"").append(processingH.getAbsolutePath()).append("\"\n");
            }
            sb.append("using namespace std;\n");
            sb.append("namespace Processing {\n");
            sb.append("using namespace std;\n");
            boolean hasSetup = code.contains("void setup(") || code.contains("void draw(");
            if (hasSetup) {
                sb.append("#include \"Processing_api.h\"\n");
                // Use ClassHoister (same as real build) to separate classes from sketch code
                StringBuilder hoistedSb = new StringBuilder();
                StringBuilder sketchSb  = new StringBuilder();
                try {
                    CompilationUnit lintCu = Parser.parse(code);
                    ClassHoister.Result hr = ClassHoister.hoist(lintCu.items());
                    for (TypeDef td : hr.hoistedClasses) {
                        hoistedSb.append(CodeGen.generateNode(td, 0));
                    }
                    for (TopLevelItem item : hr.rest) {
                        if (item instanceof VariableDecl vd && vd.name().contains("::"))
                            hoistedSb.append(CodeGen.generateNode(item, 0));
                        else
                            sketchSb.append(CodeGen.generateNode(item, 0));
                    }
                } catch (Exception _lintEx) {
                    sketchSb.append(code);
                }
                sb.append("struct _PSketch : public PApplet {\n"
                + "  struct _W   { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->logicalW    : 0;     } } width;\n"
                + "  struct _H   { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->logicalH    : 0;     } } height;\n"
                + "  struct _MX  { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseX      : 0.f;   } } mouseX;\n"
                + "  struct _MY  { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseY      : 0.f;   } } mouseY;\n"
                + "  struct _PMX { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->pmouseX     : 0.f;   } } pmouseX;\n"
                + "  struct _PMY { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->pmouseY     : 0.f;   } } pmouseY;\n"
                + "  struct _FC  { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->frameCount  : 0;     } } frameCount;\n"
                + "  struct _MP  { operator bool()  const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->_mousePressed : false; } } _mousePressed;\n"
                + "  struct _KP  { operator bool()  const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->_keyPressed  : false; } } _keyPressed;\n"
                + "  struct _K   { operator char()  const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->key          : 0;     } } key;\n"
                + "  struct _KC  { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->keyCode      : 0;     } } keyCode;\n"
                + "  struct _MB  { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseButton  : 0;     } } mouseButton;\n"
                + "  struct _FR  { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->_frameRate   : 0.f;   } } _frameRate;\n"
                + "  struct _MDX { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseDX      : 0.f;   } } mouseDX;\n"
                + "  struct _MDY { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseDY      : 0.f;   } } mouseDY;\n"
                + "  bool* keysDown()  const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->keysDown  : nullptr; }\n"
                + "  bool* mouseDown() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseDown : nullptr; }\n"
                + "};\n");
                sb.append(hoistedSb);
                sb.append("struct _PSketchImpl : public _PSketch {\n");
                sb.append("#line 1 \"sketch.pde\"\n");
                sb.append(sketchSb);
                sb.append("\n};\n");
            } else {
                // Static sketch: bare statements at file scope — wrap in a function
                sb.append("struct _PSketch : public PApplet {\n");
                sb.append("void setup() override {\n");
                sb.append("#line 1 \"sketch.pde\"\n");
                sb.append(code);
                sb.append("\n}\n};\n");
            }
            sb.append("} // namespace Processing\n");

            tmp = Files.createTempFile("cppmode_lint_", ".cpp");
            Files.writeString(tmp, sb.toString());

            // Build g++ command
            List<String> cmd = new ArrayList<>();
            cmd.add(gpp);
            cmd.add("-fsyntax-only");
            cmd.add("-std=" + CppBuild.bestCppStd(gpp));
            cmd.add("-I");
            cmd.add(runtimeDir.getAbsolutePath());
            // Bundled headers (GLFW, GLEW)
            File bundledInc = new File(runtimeDir.getParentFile(), "libs/include");
            boolean isWinLint = System.getProperty("os.name","").toLowerCase().contains("win");
            if (bundledInc.exists() && isWinLint) {
                cmd.add("-I"); cmd.add(bundledInc.getAbsolutePath());
            } else if (bundledInc.exists()) {
                // Linux/macOS: only use bundled headers if system GLFW missing
                boolean hasSystemGlfw = new java.io.File("/usr/include/GLFW/glfw3.h").exists()
                    || new java.io.File("/usr/local/include/GLFW/glfw3.h").exists()
                    || new java.io.File("/opt/homebrew/include/GLFW/glfw3.h").exists();
                if (!hasSystemGlfw) { cmd.add("-I"); cmd.add(bundledInc.getAbsolutePath()); }
            }
            if (gch != null) {
                cmd.add("-I");
                cmd.add(gch.getParentFile().getAbsolutePath());
            }
            cmd.add(tmp.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            // Cap output to 64KB to avoid OOM on large error outputs
            byte[] rawOut = proc.getInputStream().readNBytes(64 * 1024);
            String output = new String(rawOut);
            proc.waitFor(20, TimeUnit.SECONDS);

            // Parse errors and map back to tab + line
            for (String line : output.split("\n")) {
                Matcher m = GCC_DIAG.matcher(line);
                if (!m.find()) continue;
                int     rawLine = Integer.parseInt(m.group(1));
                boolean isErr   = m.group(2).equals("error");
                String  msg     = CppBuild.rewriteGccError(m.group(3).trim());

                // rawLine is the sketch.pde line (1-indexed) due to #line 1 directive.
                // Find which tab it belongs to.
                int tabIdx  = 0;
                int lineInTab = rawLine - 1; // 0-indexed within the full sketch
                for (int i = tabCount - 1; i >= 0; i--) {
                    if (rawLine >= tabStartLines[i]) {
                        tabIdx    = i;
                        lineInTab = rawLine - tabStartLines[i]; // 0-indexed within tab
                        break;
                    }
                }
                if (lineInTab < 0) continue;
                problems.add(new CppProblem(isErr, tabIdx, lineInTab, 0, -1, msg));
            }
        } catch (Exception ignored) {
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (Exception ignored2) {}
        }

        List<Problem> result = problems;
        javax.swing.SwingUtilities.invokeLater(() -> consumer.accept(result));
    }

    private static int countLines(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) == '\n') n++;
        return n;
    }
}
