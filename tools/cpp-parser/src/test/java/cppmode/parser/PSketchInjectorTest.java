package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.passes.ClassHoister;
import cppmode.parser.passes.CodeGen;
import cppmode.parser.passes.PSketchInjector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Verifies PSketchInjector against oop_features.cpp (classes with methods,
 * confirmed real -- should all get _PSketch injected) and a synthetic
 * pure-data struct (no methods -- should NOT get it injected).
 */
public final class PSketchInjectorTest {

    public static void main(String[] args) throws IOException {
        int failures = 0;
        failures += checkMethodBearingClassesInjected();
        failures += checkPureDataStructNotInjected();
        failures += checkRenderedOutputShape();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL PSKETCHINJECTOR TESTS PASSED");
        } else {
            System.out.println(failures + " PSKETCHINJECTOR TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int checkMethodBearingClassesInjected() throws IOException {
        String src = Files.readString(Path.of("fixtures/oop_features.cpp"));
        CompilationUnit cu = Parser.parse(src);
        ClassHoister.Result hoistResult = ClassHoister.hoist(cu.items());
        List<PSketchInjector.Result> injected = PSketchInjector.injectAll(hoistResult.hoistedClasses);

        // Handle, Movable, Drawable, Sprite all have at least one method each
        // (operator==, move(), draw(), update() respectively) -- all should be injected.
        boolean allInjected = injected.stream().allMatch(PSketchInjector.Result::injected);
        boolean allHaveBase = injected.stream().allMatch(r -> r.typeDef().baseClasses().contains("virtual _PSketch"));

        if (allInjected && allHaveBase) {
            System.out.println("OK    oop_features.cpp: all " + injected.size() + " method-bearing classes got _PSketch injected");
            return 0;
        } else {
            System.out.println("FAIL  oop_features.cpp: allInjected=" + allInjected + " allHaveBase=" + allHaveBase);
            for (PSketchInjector.Result r : injected) {
                System.out.println("      " + r.typeDef().name() + ": injected=" + r.injected() + " bases=" + r.typeDef().baseClasses());
            }
            return 1;
        }
    }

    private static int checkPureDataStructNotInjected() throws IOException {
        // Synthetic pure-data struct: only fields, no methods at all.
        String src = "struct Point { float x; float y; };";
        CompilationUnit cu = Parser.parse(src);
        ClassHoister.Result hoistResult = ClassHoister.hoist(cu.items());
        List<PSketchInjector.Result> injected = PSketchInjector.injectAll(hoistResult.hoistedClasses);

        boolean notInjected = injected.size() == 1 && !injected.get(0).injected();
        boolean noPSketchBase = injected.size() == 1 && !injected.get(0).typeDef().baseClasses().contains("virtual _PSketch");

        if (notInjected && noPSketchBase) {
            System.out.println("OK    pure-data struct 'Point' correctly NOT injected with _PSketch");
            return 0;
        } else {
            System.out.println("FAIL  pure-data struct: notInjected=" + notInjected + " noPSketchBase=" + noPSketchBase);
            return 1;
        }
    }

    /** Confirms the rendered text actually looks like the original's expected output shape. */
    private static int checkRenderedOutputShape() throws IOException {
        String src = "class Handle { public: void move() {} };";
        CompilationUnit cu = Parser.parse(src);
        ClassHoister.Result hoistResult = ClassHoister.hoist(cu.items());
        List<PSketchInjector.Result> injected = PSketchInjector.injectAll(hoistResult.hoistedClasses);
        String rendered = CodeGen.generateNode(injected.get(0).typeDef(), 0);

        boolean correct = rendered.contains("class Handle : public virtual _PSketch {")
            && rendered.contains("public:");

        if (correct) {
            System.out.println("OK    rendered shape matches expected: " + rendered.lines().findFirst().orElse(""));
            return 0;
        } else {
            System.out.println("FAIL  rendered shape incorrect:");
            System.out.println(rendered);
            return 1;
        }
    }
}
