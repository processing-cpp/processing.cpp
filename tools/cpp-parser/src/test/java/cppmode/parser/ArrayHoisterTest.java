package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.ast.decl.VariableDecl;
import cppmode.parser.passes.ArrayHoister;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies the ported ArrayHoister against storing_input.cpp's real "const int num; float mx[num]; float my[num];" pattern. */
public final class ArrayHoisterTest {

    public static void main(String[] args) throws IOException {
        int failures = 0;
        failures += checkStoringInput();
        failures += checkNoArraysIsNoOp();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL ARRAYHOISTER TESTS PASSED");
        } else {
            System.out.println(failures + " ARRAYHOISTER TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int checkStoringInput() throws IOException {
        String src = Files.readString(Path.of("fixtures/storing_input.cpp"));
        CompilationUnit cu = Parser.parse(src);
        ArrayHoister.Result result = ArrayHoister.hoist(cu.items());

        boolean sizingConstFound = result.hoistedSizingConstants.size() == 1
            && result.hoistedSizingConstants.get(0).name().equals("num");
        boolean bothArraysFound = result.hoistedArrays.size() == 2
            && result.hoistedArrays.stream().anyMatch(v -> v.name().equals("mx"))
            && result.hoistedArrays.stream().anyMatch(v -> v.name().equals("my"));
        boolean restHasNoVariableDecls = result.rest.stream().noneMatch(item -> item instanceof VariableDecl);
        boolean totalPreserved = result.hoistedSizingConstants.size() + result.hoistedArrays.size() + result.rest.size()
            == cu.items().size();

        if (sizingConstFound && bothArraysFound && restHasNoVariableDecls && totalPreserved) {
            System.out.println("OK    storing_input.cpp: 'num' hoisted as sizing const, 'mx'/'my' hoisted as arrays, "
                + result.rest.size() + " items in rest, total preserved");
            return 0;
        } else {
            System.out.println("FAIL  storing_input.cpp: sizingConstFound=" + sizingConstFound
                + " bothArraysFound=" + bothArraysFound + " restHasNoVariableDecls=" + restHasNoVariableDecls
                + " totalPreserved=" + totalPreserved);
            System.out.println("      hoistedSizingConstants=" + result.hoistedSizingConstants.size()
                + " hoistedArrays=" + result.hoistedArrays.size() + " rest=" + result.rest.size()
                + " cu.items=" + cu.items().size());
            return 1;
        }
    }

    /** A file with no array declarations at all should hoist nothing and preserve everything in rest. */
    private static int checkNoArraysIsNoOp() throws IOException {
        String src = Files.readString(Path.of("fixtures/mandelbrot.cpp"));
        CompilationUnit cu = Parser.parse(src);
        ArrayHoister.Result result = ArrayHoister.hoist(cu.items());

        boolean nothingHoisted = result.hoistedSizingConstants.isEmpty() && result.hoistedArrays.isEmpty();
        boolean totalPreserved = result.rest.size() == cu.items().size();

        if (nothingHoisted && totalPreserved) {
            System.out.println("OK    mandelbrot.cpp: no top-level arrays present, hoister is a no-op as expected");
            return 0;
        } else {
            System.out.println("FAIL  mandelbrot.cpp: nothingHoisted=" + nothingHoisted + " totalPreserved=" + totalPreserved);
            return 1;
        }
    }
}
