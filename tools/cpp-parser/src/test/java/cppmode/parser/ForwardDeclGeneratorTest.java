package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.ast.decl.FunctionDecl;
import cppmode.parser.passes.CodeGen;
import cppmode.parser.passes.ForwardDeclGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Verifies ForwardDeclGenerator produces a real, g++-compilable forward
 * declaration for a function whose class-calling-site appears earlier in
 * the file than its own full definition -- the exact scenario the
 * original CppBuild.java logic exists to solve ("Agent::update calling
 * dirToVec" pattern, per the original's own comment).
 */
public final class ForwardDeclGeneratorTest {

    public static void main(String[] args) throws IOException, InterruptedException {
        int failures = 0;
        failures += checkForwardDeclShape();
        failures += checkCompilesWhenClassPrecedesDefinition();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL FORWARDDECLGENERATOR TESTS PASSED");
        } else {
            System.out.println(failures + " FORWARDDECLGENERATOR TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int checkForwardDeclShape() {
        CompilationUnit cu = Parser.parse("float dirToVec(float a, float b) { return a + b; }");
        FunctionDecl fd = (FunctionDecl) cu.items().get(0);
        List<FunctionDecl> decls = ForwardDeclGenerator.generate(List.of(fd));
        String rendered = CodeGen.generateNode(decls.get(0), 0);

        boolean correct = rendered.strip().equals("float dirToVec(float a, float b);");
        if (correct) {
            System.out.println("OK    forward decl shape correct: " + rendered.strip());
            return 0;
        } else {
            System.out.println("FAIL  forward decl shape wrong: " + rendered.strip());
            return 1;
        }
    }

    /**
     * The real test: a class appears BEFORE the full definition of a free
     * function it calls, with only the forward declaration bridging the
     * gap -- exactly the scenario this pass exists for. If this doesn't
     * compile, the pass doesn't actually do its job, regardless of how
     * correct the rendered TEXT looks in isolation.
     */
    private static int checkCompilesWhenClassPrecedesDefinition() throws IOException, InterruptedException {
        CompilationUnit cu = Parser.parse("float dirToVec(float a, float b) { return a + b; }");
        FunctionDecl fullDef = (FunctionDecl) cu.items().get(0);
        FunctionDecl forwardDecl = ForwardDeclGenerator.generate(List.of(fullDef)).get(0);

        String combined =
            CodeGen.generateNode(forwardDecl, 0) + "\n"
            + "class Agent {\n"
            + "public:\n"
            + "    float update() { return dirToVec(1.0f, 2.0f); }\n"
            + "};\n"
            + CodeGen.generateNode(fullDef, 0) + "\n"
            + "void useAgent() { Agent a; a.update(); }\n";

        Path tmp = Files.createTempFile("fwd_decl_test", ".cpp");
        Files.writeString(tmp, combined);

        ProcessBuilder pb = new ProcessBuilder("g++", "-std=c++17", "-fsyntax-only", tmp.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        Files.deleteIfExists(tmp);

        if (exit == 0) {
            System.out.println("OK    combined output (class precedes function definition, bridged by forward decl) compiles with g++");
            return 0;
        } else {
            System.out.println("FAIL  g++ rejected the combined output:");
            System.out.println(output);
            System.out.println("--- generated source ---");
            System.out.println(combined);
            return 1;
        }
    }
}
