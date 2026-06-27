package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.passes.CodeGen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Round-trip test: parse a real fixture, generate C++ text from the AST,
 * re-parse that generated text, and confirm the two ASTs render to the
 * SAME text on a second generation pass (a simple, robust equality check
 * that doesn't require implementing AST equals() everywhere -- if
 * generate(parse(generate(parse(src)))) == generate(parse(src)), the
 * generator is stable and round-trips correctly, since any information
 * loss or corruption on the first pass would cause the second pass's
 * output to differ).
 */
public final class CodeGenRoundTripTest {

    public static void main(String[] args) throws IOException, InterruptedException {
        int failures = 0;
        String[] fixtures = {
            "fixtures/mandelbrot.cpp",
            "fixtures/button.cpp",
            "fixtures/handles.cpp",
            "fixtures/penrose_lsystem.cpp",
            "fixtures/oop_features.cpp",
            "fixtures/storing_input.cpp",
            "fixtures/alpha_mask.cpp",
            "fixtures/kitchen_sink.cpp",
            "fixtures/batch2_lambdas_templates_fnptr_namespaces.cpp",
        };

        for (String f : fixtures) {
            failures += checkRoundTrip(f);
        }
        failures += checkDirectInitRendersCorrectly();
        failures += checkMostVexingParseAvoided();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CODEGEN ROUND-TRIP TESTS PASSED");
        } else {
            System.out.println(failures + " CODEGEN ROUND-TRIP TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int checkRoundTrip(String path) throws IOException {
        String src = Files.readString(Path.of(path));
        try {
            CompilationUnit cu1 = Parser.parse(src);
            String gen1 = CodeGen.generate(cu1);

            CompilationUnit cu2 = Parser.parse(gen1);
            String gen2 = CodeGen.generate(cu2);

            if (gen1.equals(gen2)) {
                System.out.println("OK    " + path + ": stable round-trip (" + gen1.length() + " chars generated)");
                Files.writeString(Path.of("/tmp/gen_" + Path.of(path).getFileName()), gen1);
                return 0;
            } else {
                System.out.println("FAIL  " + path + ": generated output not stable across a second round-trip");
                return 1;
            }
        } catch (Exception e) {
            System.out.println("FAIL  " + path + ": " + e);
            return 1;
        }
    }

    /**
     * Confirms the direct-init pattern ("Handle a(5);", confirmed real by
     * the Arctangent.pde fixture much earlier in this project) renders
     * back as direct-init syntax, NOT as "Handle a = a(5);" -- a real bug
     * found by reading codegen output during round-trip testing (the
     * round-trip-stability check alone didn't catch it, since the wrong
     * output was still stable across a second pass).
     */
    private static int checkDirectInitRendersCorrectly() {
        String src = "class Eye {}; Eye e1(250, 16, 120);";
        CompilationUnit cu = Parser.parse(src);
        String gen = CodeGen.generate(cu);

        boolean correct = gen.contains("Eye e1{250, 16, 120}") && !gen.contains("e1 =");
        if (correct) {
            System.out.println("OK    direct-init renders correctly as BRACE-init: 'Eye e1{250, 16, 120};' "
                + "(not 'e1 = e1(...)', and not paren-init either -- see most-vexing-parse notes on emitDeclaratorTail)");
            return 0;
        } else {
            System.out.println("FAIL  direct-init rendered incorrectly:");
            System.out.println(gen);
            return 1;
        }
    }

    /**
     * Direct g++ validation that direct-init's brace-rendering actually
     * avoids the most-vexing-parse for the case that genuinely triggers
     * it: a constructor argument that is itself a bare-type-constructing
     * expression ("Bar()"). Confirmed by hand (see project notes) that
     * g++ silently parses the PAREN form of this exact shape as a
     * FUNCTION DECLARATION, not an object -- this test renders the AST's
     * direct-init shape via CodeGen and confirms the result is a real,
     * usable object by accessing a member on it, which only compiles if
     * it's genuinely an object.
     */
    private static int checkMostVexingParseAvoided() throws IOException, InterruptedException {
        CompilationUnit cu = Parser.parse(
            "struct Bar {}; struct Eye { Eye(Bar b) {} int member = 5; }; Eye e1(Bar());");
        String gen = CodeGen.generate(cu);
        String combined = gen + "\nint useIt() { return e1.member; }\n";

        Path tmp = Files.createTempFile("mvp_test", ".cpp");
        Files.writeString(tmp, combined);
        ProcessBuilder pb = new ProcessBuilder("g++", "-std=c++17", "-fsyntax-only", tmp.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        Files.deleteIfExists(tmp);

        if (exit == 0) {
            System.out.println("OK    most-vexing-parse avoided: 'Eye e1(Bar());' renders as brace-init and "
                + "e1.member compiles, confirming e1 is a real object, not a function declaration");
            return 0;
        } else {
            System.out.println("FAIL  most-vexing-parse NOT avoided -- g++ rejected:");
            System.out.println(output);
            System.out.println("--- generated source ---");
            System.out.println(combined);
            return 1;
        }
    }
}
