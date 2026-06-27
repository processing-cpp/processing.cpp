package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.ast.decl.FunctionDecl;
import cppmode.parser.ast.decl.NamespaceDecl;
import cppmode.parser.ast.decl.PreprocessorLine;
import cppmode.parser.ast.decl.TopLevelItem;
import cppmode.parser.ast.decl.TypeDef;
import cppmode.parser.ast.decl.UsingNamespaceDecl;
import cppmode.parser.ast.decl.VariableDecl;
import cppmode.parser.passes.ArrayHoister;
import cppmode.parser.passes.ClassHoister;
import cppmode.parser.passes.CodeGen;
import cppmode.parser.passes.DependencyHoister;
import cppmode.parser.passes.EnumScopeExtractor;
import cppmode.parser.passes.ForwardDeclGenerator;
import cppmode.parser.passes.LifecycleRewriter;
import cppmode.parser.passes.PSketchInjector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Real stress test #1: runs the FULL pipeline (every pass, not just the
 * parser) plus a real g++ syntax check against all 131 real example
 * sketches, not just the 10 hand-picked PipelineCompositionTest
 * fixtures. This is a genuinely larger surface than anything tested
 * before -- PipelineCompositionTest's fixtures were chosen because they
 * exercised SPECIFIC confirmed constructs; this sweep instead asks "does
 * the full pipeline survive everything in the real corpus," which can
 * surface combinations of constructs no hand-picked fixture happened to
 * combine.
 *
 * Two outcomes are tracked separately, since they mean different things:
 *   G++FAIL -- g++ rejected the output. Often a stub gap (missing API
 *              symbol this test's narrow stub doesn't define), not
 *              necessarily a real pipeline bug -- read the error.
 *   CRASH   -- the PIPELINE ITSELF threw a Java exception, meaning one
 *              of the passes crashed on real input. This is
 *              unambiguously a real bug worth chasing immediately.
 */
public final class RealHeaderStressTest {

    private static final Set<String> LIFECYCLE_NAMES = Set.of(
        "setup", "draw", "settings",
        "mousePressed", "mouseReleased", "mouseClicked",
        "mouseMoved", "mouseDragged", "mouseWheel",
        "keyPressed", "keyReleased", "keyTyped",
        "windowMoved", "windowResized"
    );

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: RealHeaderStressTest <pde-files-dir> <processing-src-dir>");
            System.out.println("  <processing-src-dir> is CppMode's real src/ directory,");
            System.out.println("  containing Processing.h, Processing_api.h, Platform.h, stb_*.h.");
            System.out.println("  Requires GLFW3 and GLEW headers/libs to be installed and on the");
            System.out.println("  system include path (pkg-config glfw3 glew, or equivalent).");
            return;
        }
        Path dir = Path.of(args[0]);
        String processingSrcDir = args[1];
        List<Path> files = Files.list(dir)
            .filter(p -> p.toString().endsWith(".pde"))
            .sorted()
            .toList();

        int pipelineCrashes = 0;
        int gppFails = 0;
        int pass = 0;
        List<String> crashDetails = new ArrayList<>();

        for (Path f : files) {
            try {
                String src = Files.readString(f);
                String generated = runFullPipeline(src);

                String wrapped = buildRealHeaderWrapper(generated, processingSrcDir);
                Path tmp = Files.createTempFile("stress_real", ".cpp");
                Files.writeString(tmp, wrapped);
                List<String> cmd = new ArrayList<>(List.of(
                    "g++", "-std=c++17", "-fsyntax-only",
                    "-I" + processingSrcDir,
                    "-DPROCESSING_HAS_STB_IMAGE", "-DPROCESSING_HAS_STB_TRUETYPE"
                ));
                cmd.add(tmp.toString());
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes());
                int exit = p.waitFor();
                Files.deleteIfExists(tmp);

                if (exit == 0) {
                    pass++;
                } else {
                    gppFails++;
                    System.out.println("G++FAIL " + f.getFileName());
                    System.out.println("        " + firstErrorLine(output));
                }
            } catch (Exception e) {
                pipelineCrashes++;
                crashDetails.add(f.getFileName() + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                System.out.println("CRASH   " + f.getFileName() + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        System.out.println();
        System.out.println("=== REAL CORPUS FULL-PIPELINE STRESS TEST ===");
        System.out.println("Total files:       " + files.size());
        System.out.println("Pass (g++ clean):  " + pass);
        System.out.println("g++ fail (likely stub gaps, check manually): " + gppFails);
        System.out.println("PIPELINE CRASHES (real bugs, not stub gaps): " + pipelineCrashes);
        if (!crashDetails.isEmpty()) {
            System.out.println();
            System.out.println("Crash details:");
            for (String d : crashDetails) System.out.println("  " + d);
        }
    }

    private static String firstErrorLine(String g) {
        for (String line : g.lines().toList()) {
            if (line.contains("error:")) return line.strip();
        }
        return g.lines().findFirst().orElse("(no output)");
    }

    /**
     * Replicates the ONE part of the real writeSketch()'s javaToC() text-
     * rewrite step that matters for parsing: "boolean" -> "bool" and
     * "null" -> "nullptr" (Java keywords with no C++ equivalent token,
     * confirmed real and necessary by Logical_Operators.pde/Rollover.pde/
     * Scrollbar.pde's real top-level "boolean" declarations). The real
     * javaToC() does this via a careful character-walking scan that skips
     * string/char literals and comments (replaceKeywordsOutsideLiterals);
     * this test uses a simpler whole-word regex instead, which is NOT as
     * rigorous for an arbitrary real sketch (a literal containing the
     * word "boolean" would be incorrectly rewritten) -- but confirmed
     * safe for THIS test's actual purpose by checking directly: neither
     * word appears inside a string literal anywhere in the real 131-file
     * corpus. This is a test-harness simplification, not a claim that
     * this approach would be safe to ship in the real pipeline.
     */
    private static String applyJavaToCBooleanNullFix(String src) {
        return src.replaceAll("\\bboolean\\b", "bool").replaceAll("\\bnull\\b", "nullptr");
    }

    private static String runFullPipeline(String src) throws IOException {
        src = applyJavaToCBooleanNullFix(src);
        boolean hasSetup = src.contains("void setup(");
        boolean hasDraw = src.contains("void draw(");
        if (!hasSetup && !hasDraw) {
            return runStaticModePipeline(src);
        }
        return runNormalModePipeline(src);
    }

    /** Mirrors writeSketch()'s static-mode branch (no setup()/draw() -- a flat sequence of top-level statements). */
    private static String runStaticModePipeline(String src) {
        CompilationUnit cu = Parser.parse(src);
        ClassHoister.Result classResult = ClassHoister.hoist(cu.items());
        List<PSketchInjector.Result> injected = PSketchInjector.injectAll(classResult.hoistedClasses);
        List<TypeDef> finalClasses = injected.stream().map(PSketchInjector.Result::typeDef).toList();

        StringBuilder settings = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (TopLevelItem item : classResult.rest) {
            String rendered = CodeGen.generateNode(item, 2);
            String trimmed = rendered.strip();
            if (trimmed.startsWith("size(") || trimmed.startsWith("fullScreen(")) {
                settings.append(rendered);
            } else {
                body.append(rendered);
            }
        }

        StringBuilder generated = new StringBuilder();
        for (TypeDef td : finalClasses) generated.append(CodeGen.generateNode(td, 0));
        generated.append("struct Sketch : public PApplet {\n");
        generated.append("    void setup() override {\n");
        generated.append(settings);
        generated.append("        for (int _s=0; _s<8; _s++) { delay(1); }\n");
        generated.append("        noLoop();\n");
        generated.append(body);
        generated.append("    }\n");
        generated.append("    void draw() override {}\n");
        generated.append("};\n");
        return generated.toString();
    }

    /** Mirrors writeSketch()'s normal-mode branch (has setup()/draw()). */
    private static String runNormalModePipeline(String src) {
        CompilationUnit cu = Parser.parse(src);

        EnumScopeExtractor.Result enumResult = EnumScopeExtractor.extract(cu.items());
        List<TopLevelItem> afterLifecycle = LifecycleRewriter.rewrite(enumResult.rest, LIFECYCLE_NAMES);
        ClassHoister.Result classResult = ClassHoister.hoist(afterLifecycle);

        List<PSketchInjector.Result> injectedClasses = PSketchInjector.injectAll(classResult.hoistedClasses);
        List<TypeDef> finalClasses = injectedClasses.stream().map(PSketchInjector.Result::typeDef).toList();

        ArrayHoister.Result arrayResult = ArrayHoister.hoist(classResult.rest);
        DependencyHoister.Result depResult = DependencyHoister.hoist(arrayResult.rest, finalClasses, LIFECYCLE_NAMES);
        List<FunctionDecl> forwardDecls = ForwardDeclGenerator.generate(depResult.hoistedFunctions);

        List<TopLevelItem> finalOrder = new ArrayList<>();
        List<TopLevelItem> sketchMembers = new ArrayList<>();
        List<TopLevelItem> staticMemberDefs = new ArrayList<>();
        for (TopLevelItem item : depResult.rest) {
            if (item instanceof PreprocessorLine || item instanceof NamespaceDecl || item instanceof UsingNamespaceDecl) {
                finalOrder.add(item);
            } else if (item instanceof VariableDecl vd && vd.name().contains("::")) {
                staticMemberDefs.add(item);
            } else {
                sketchMembers.add(item);
            }
        }

        finalOrder.addAll(forwardDecls);
        finalOrder.addAll(enumResult.enums);
        finalOrder.addAll(depResult.hoistedVariables);
        finalOrder.addAll(arrayResult.hoistedSizingConstants);
        finalOrder.addAll(arrayResult.hoistedArrays);
        finalOrder.addAll(finalClasses);
        finalOrder.addAll(staticMemberDefs);
        finalOrder.addAll(depResult.hoistedFunctions);

        TypeDef sketchWrapper = new TypeDef("struct", "Sketch", List.of(), List.of("PApplet"),
            sketchMembers, 0, 0, List.of());
        finalOrder.add(sketchWrapper);

        return CodeGen.generate(new CompilationUnit(finalOrder));
    }

    /**
     * Builds the REAL writeSketch() scaffolding around the generated
     * code, using the ACTUAL Processing.h/Processing_api.h headers
     * instead of RealCorpusStressTest's hand-built stub. Requires GLFW
     * and GLEW headers to be installed and on the include path (this
     * environment doesn't have them -- see
     * DECISION_two_parser_implementations.md's "considered going
     * further" section -- but a real dev machine that already built the
     * actual engine, as confirmed by a successful real Gradle build and
     * CppMode.jar deployment, should have them already).
     *
     * @param processingSrcDir path to CppMode's real src/ directory
     *                          (containing Processing.h, Processing_api.h,
     *                          Platform.h, stb_*.h)
     */
    private static String buildRealHeaderWrapper(String generated, String processingSrcDir) {
        StringBuilder out = new StringBuilder();
        out.append("#include \"Processing.h\"\n");
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
        out.append("namespace Processing {\n");
        out.append("#include \"Processing_api.h\"\n");
        // The real _PSketch struct, copied verbatim from writeSketch()'s
        // own literal scaffolding in CppBuild.java -- not reconstructed
        // or guessed, the actual text the real pipeline emits.
        out.append("struct _PSketch {\n");
        out.append("  struct _W   { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->logicalW    : 0;     } } width;\n");
        out.append("  struct _H   { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->logicalH    : 0;     } } height;\n");
        out.append("  struct _MX  { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseX      : 0.f;   } } mouseX;\n");
        out.append("  struct _MY  { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseY      : 0.f;   } } mouseY;\n");
        out.append("  struct _PMX { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->pmouseX     : 0.f;   } } pmouseX;\n");
        out.append("  struct _PMY { operator float() const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->pmouseY     : 0.f;   } } pmouseY;\n");
        out.append("  struct _FC  { operator int()   const { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->frameCount  : 0;     } } frameCount;\n");
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
        out.append(generated);
        out.append("void setup() { if(PApplet::g_papplet) PApplet::g_papplet->setup(); }\n");
        out.append("void draw()  { if(PApplet::g_papplet) PApplet::g_papplet->draw();  }\n");
        out.append("void settings() { if(PApplet::g_papplet) PApplet::g_papplet->settings(); }\n");
        out.append("} // namespace Processing\n");
        out.append("int main() { Processing::Sketch sketch; return 0; }\n");
        return out.toString();
    }

    private static String buildWrapper(String generated) throws IOException {
        StringBuilder stub = new StringBuilder();
        stub.append("#include <vector>\n#include <string>\n#include <cstdlib>\n#include <cmath>\n");
        stub.append("using std::sqrt;\n");
        stub.append("template<typename T> struct ArrayList : std::vector<T> {\n");
        stub.append("    T* get(int i) { return &(*this)[i]; }\n");
        stub.append("    void add(const T& v) { this->push_back(v); }\n");
        stub.append("    void add(T* v) { this->push_back(*v); }\n");
        stub.append("    int size() const { return (int)std::vector<T>::size(); }\n");
        stub.append("};\n");
        stub.append("struct PApplet {\n");
        stub.append("    int width = 640, height = 360;\n");
        stub.append("    float mouseX = 0, mouseY = 0, pmouseX = 0, pmouseY = 0;\n");
        stub.append("    int frameCount = 0;\n");
        stub.append("    bool _mousePressed = false, _keyPressed = false;\n");
        stub.append("    char key = 0; int keyCode = 0, mouseButton = 0;\n");
        stub.append("    float deltaTime = 0, _frameRate = 0, mouseDX = 0, mouseDY = 0;\n");
        stub.append("    static PApplet* g_papplet;\n");
        stub.append("    virtual void setup() {}\n");
        stub.append("    virtual void draw() {}\n");
        stub.append("    virtual void settings() {}\n");
        stub.append("    virtual void mousePressed() {}\n");
        stub.append("    virtual void mouseReleased() {}\n");
        stub.append("    virtual void mouseClicked() {}\n");
        stub.append("    virtual void mouseMoved() {}\n");
        stub.append("    virtual void mouseDragged() {}\n");
        stub.append("    virtual void mouseWheel(int) {}\n");
        stub.append("    virtual void keyPressed() {}\n");
        stub.append("    virtual void keyReleased() {}\n");
        stub.append("    virtual void keyTyped() {}\n");
        stub.append("    virtual void windowMoved() {}\n");
        stub.append("    virtual void windowResized() {}\n");
        stub.append("};\n");
        stub.append("inline PApplet* PApplet::g_papplet = nullptr;\n");
        stub.append("struct _PSketch {\n");
        stub.append("    int width = PApplet::g_papplet ? PApplet::g_papplet->width : 0;\n");
        stub.append("    int height = PApplet::g_papplet ? PApplet::g_papplet->height : 0;\n");
        stub.append("    float mouseX = PApplet::g_papplet ? PApplet::g_papplet->mouseX : 0;\n");
        stub.append("    float mouseY = PApplet::g_papplet ? PApplet::g_papplet->mouseY : 0;\n");
        stub.append("    int frameCount = PApplet::g_papplet ? PApplet::g_papplet->frameCount : 0;\n");
        stub.append("};\n");
        stub.append("struct color { unsigned int v=0; color()=default; color(int x):v((unsigned)x){} operator unsigned int() const { return v; } };\n");
        stub.append("inline color pixels_array[4000000];\n");
        stub.append("#define pixels pixels_array\n");
        stub.append("const int CENTER = 0, CORNER = 1;\n");
        stub.append("inline void delay(int){}\n");
        stub.append("struct PImage { void mask(const PImage&){} int width=0,height=0; };\n");
        stub.append("struct PFont {};\n");
        stub.append("struct PGraphics : PImage {};\n");
        stub.append("inline color colorVal(int r,int g,int b,int a=255){ return 0; }\n");
        stub.append(Files.readString(Path.of("fixtures/extracted_real_api_stub.h")));
        stub.append(generated);
        return stub.toString();
    }
}
