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
 * End-to-end PIPELINE COMPOSITION test: runs every ported pass in the
 * exact order writeSketch() actually uses them, on real fixture data,
 * and validates the combined output with a real g++ compile check.
 *
 * This exists because every pass so far has only ever been tested in
 * ISOLATION -- ClassHoisterTest only calls ClassHoister, ArrayHoisterTest
 * only calls ArrayHoister, etc. Nothing has confirmed the passes actually
 * COMPOSE correctly in the real pipeline order, which matters: per
 * writeSketch()'s real structure, the order is roughly
 *
 *   parse -> EnumScopeExtractor -> LifecycleRewriter -> ClassHoister
 *         -> PSketchInjector -> ArrayHoister -> DependencyHoister
 *         -> ForwardDeclGenerator -> CodeGen
 *
 * and a pass that works fine on a raw parsed AST might behave
 * differently (or break) when handed the OUTPUT of an earlier pass
 * instead.
 *
 * This is NOT the same as actually wiring this into writeSketch() (still
 * not done, deliberately -- see DECISION_two_parser_implementations.md).
 * It's the next-cheapest way to gain real confidence the pieces fit
 * together before that step, using real fixture data and a real compiler
 * check rather than just confirming each piece's own isolated contract.
 */
public final class PipelineCompositionTest {

    private static final Set<String> LIFECYCLE_NAMES = Set.of(
        "setup", "draw", "settings",
        "mousePressed", "mouseReleased", "mouseClicked",
        "mouseMoved", "mouseDragged", "mouseWheel",
        "keyPressed", "keyReleased", "keyTyped",
        "windowMoved", "windowResized"
    );

    public static void main(String[] args) throws IOException, InterruptedException {
        int failures = 0;
        failures += runPipeline("fixtures/handles.cpp");
        failures += runPipeline("fixtures/storing_input.cpp");
        failures += runPipeline("fixtures/alpha_mask.cpp");
        failures += runPipeline("fixtures/oop_features.cpp");
        failures += runPipeline("fixtures/mandelbrot.cpp");
        failures += runPipeline("fixtures/button.cpp");
        failures += runPipeline("fixtures/penrose_lsystem.cpp");
        failures += runPipeline("fixtures/kitchen_sink.cpp");
        failures += runPipeline("fixtures/batch2_lambdas_templates_fnptr_namespaces.cpp");
        failures += runStaticModePipeline("fixtures/coordinates_static_mode.cpp");

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL PIPELINE COMPOSITION TESTS PASSED");
        } else {
            System.out.println(failures + " PIPELINE COMPOSITION TEST(S) FAILED");
            System.exit(1);
        }
    }

    /**
     * Mirrors writeSketch()'s REAL static-mode branch (no setup()/draw()
     * in the source -- a flat sequence of top-level statements, the
     * Processing "static mode" confirmed real by Coordinates.pde, the
     * original motivating example for the TopLevelStatement AST node
     * much earlier in this project). Until now, every
     * PipelineCompositionTest fixture had real setup()/draw(), so this
     * branch -- a genuinely different code path in the real
     * writeSketch() -- had never been g++-validated at all, only the
     * normal-mode branch had. This closes that gap.
     */
    private static int runStaticModePipeline(String fixturePath) throws IOException, InterruptedException {
        String src = Files.readString(Path.of(fixturePath));
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

        String wrapped = buildCompilableWrapper(generated.toString());
        Path tmp = Files.createTempFile("pipeline_test_static", ".cpp");
        Files.writeString(tmp, wrapped);
        ProcessBuilder pb = new ProcessBuilder("g++", "-std=c++17", "-fsyntax-only", tmp.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();

        if (exit == 0) {
            System.out.println("OK    " + fixturePath + " (static mode): full pipeline composed correctly, g++ accepts output");
            Files.deleteIfExists(tmp);
            return 0;
        } else {
            System.out.println("FAIL  " + fixturePath + " (static mode): g++ rejected the composed pipeline output:");
            System.out.println(output);
            System.out.println("--- wrapped source written to " + tmp + " for inspection ---");
            return 1;
        }
    }

    private static int runPipeline(String fixturePath) throws IOException, InterruptedException {
        String src = Files.readString(Path.of(fixturePath));
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

        // Two DIFFERENT categories of "can't go inside struct Sketch,"
        // needing two DIFFERENT placements -- found as real bugs via
        // THIS test, against two different real fixtures:
        //
        //  1. Preprocessor directives, namespace blocks, and
        //     using-namespace declarations (batch2_..._namespaces.cpp's
        //     real "#include <functional>"/"#include <string>") --
        //     placed at the very FRONT, ahead of even forward decls,
        //     since a forward-declared signature could reference
        //     something from one of these.
        //  2. Out-of-class static member definitions ("Type
        //     Counter::count = 0;", confirmed real by kitchen_sink.cpp)
        //     -- these must come AFTER the class they qualify
        //     (Counter itself) is declared, so they're placed after the
        //     hoisted classes, just before the Sketch struct -- NOT at
        //     the front, which would reference Counter before it exists.
        //
        // See DECISION_two_parser_implementations.md for the full writeup.
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

        CompilationUnit finalCu = new CompilationUnit(finalOrder);
        String generated = CodeGen.generate(finalCu);
        String wrapped = buildCompilableWrapper(generated);

        Path tmp = Files.createTempFile("pipeline_test", ".cpp");
        Files.writeString(tmp, wrapped);
        ProcessBuilder pb = new ProcessBuilder("g++", "-std=c++17", "-fsyntax-only", tmp.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();

        if (exit == 0) {
            System.out.println("OK    " + fixturePath + ": full pipeline composed correctly, g++ accepts output "
                + "(" + finalClasses.size() + " classes, " + depResult.hoistedFunctions.size() + " hoisted fns, "
                + depResult.hoistedVariables.size() + " hoisted vars, " + arrayResult.hoistedArrays.size() + " hoisted arrays)");
            Files.deleteIfExists(tmp);
            return 0;
        } else {
            System.out.println("FAIL  " + fixturePath + ": g++ rejected the composed pipeline output:");
            System.out.println(output);
            System.out.println("--- wrapped source written to " + tmp + " for inspection ---");
            return 1;
        }
    }

    /**
     * Builds a syntax-checkable stub for _PSketch plus the REAL Processing
     * API surface, extracted mechanically from the actual
     * src/Processing_api.h shipped with CppMode (162 function signatures,
     * see fixtures/extracted_real_api_stub.h and
     * tools/extract_api_signatures.py for how this was produced) rather
     * than hand-guessed. This is a deliberate upgrade from this test's
     * first version, which used a small, hand-written, NECESSARILY
     * incomplete stub (a dozen or so functions guessed from reading each
     * fixture) -- several of that version's "failures" were never real
     * pass bugs, just gaps in the guessed stub (wrong overload arity for
     * fill()/background(), missing width/height/mouseX as free names).
     *
     * Still NOT a full compile: the real Processing.h unconditionally
     * #includes <GL/glew.h> and <GLFW/glfw3.h>, which aren't available in
     * this sandbox (no network access to fetch them, and hand-stubbing
     * the full GL/GLFW API surface would risk a stub that's wrong in a
     * way that happens to compile -- false confidence, not real
     * validation). This stub closes the gap as far as is honestly
     * possible without those libraries: real function signatures, real
     * _PSketch member names matching writeSketch()'s actual literal
     * scaffolding text, but no real OpenGL/windowing behavior.
     */
    private static String buildCompilableWrapper(String generated) throws IOException {
        StringBuilder stub = new StringBuilder();
        stub.append("#include <vector>\n#include <string>\n#include <cstdlib>\n#include <cmath>\n");
        stub.append("using std::sqrt;\n");
        stub.append("template<typename T> struct ArrayList : std::vector<T> {\n");
        stub.append("    T* get(int i) { return &(*this)[i]; }\n");
        stub.append("    void add(const T& v) { this->push_back(v); }\n");
        stub.append("    void add(T* v) { this->push_back(*v); }\n");
        stub.append("    int size() const { return (int)std::vector<T>::size(); }\n");
        stub.append("};\n");
        stub.append("struct color { unsigned int v=0; color()=default; color(int x):v((unsigned)x){} operator unsigned int() const { return v; } };\n");
        stub.append("inline color pixels_array[1000000];\n");
        stub.append("#define pixels pixels_array\n");
        stub.append("struct PImage { void mask(const PImage&){} int width=0, height=0; };\n");
        stub.append("struct PFont {};\n");
        stub.append("struct PGraphics : PImage {};\n");
        stub.append("inline color colorVal(int r,int g,int b,int a=255){ return 0; }\n");
        // PApplet stub: Sketch (built below) inherits from this directly,
        // matching writeSketch()'s real "struct Sketch : public PApplet"
        // shape. Real member names (width/height/mouseX/etc.) match the
        // real _PSketch/PApplet surface in CppBuild.java's literal
        // scaffolding text, with "virtual" + "override" support for the
        // lifecycle methods LifecycleRewriter marks as override.
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
        // _PSketch: the REAL engine's separate virtual base injected into
        // user-defined classes (NOT Sketch itself, which inherits PApplet
        // directly) so a hoisted class can reach width/mouseX/etc. via the
        // same g_papplet-forwarding trick the real CppBuild.java scaffolding
        // text uses (see writeSketch()'s literal _PSketch struct).
        stub.append("struct _PSketch {\n");
        stub.append("    int width = PApplet::g_papplet ? PApplet::g_papplet->width : 0;\n");
        stub.append("    int height = PApplet::g_papplet ? PApplet::g_papplet->height : 0;\n");
        stub.append("    float mouseX = PApplet::g_papplet ? PApplet::g_papplet->mouseX : 0;\n");
        stub.append("    float mouseY = PApplet::g_papplet ? PApplet::g_papplet->mouseY : 0;\n");
        stub.append("    int frameCount = PApplet::g_papplet ? PApplet::g_papplet->frameCount : 0;\n");
        stub.append("};\n");
        stub.append("const int CENTER = 0, CORNER = 1;\n");

        stub.append("inline void delay(int){}\n");
        stub.append(Files.readString(Path.of("fixtures/extracted_real_api_stub.h")));

        stub.append(generated);
        return stub.toString();
    }
}
