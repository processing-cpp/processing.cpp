package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.ast.decl.TypeDef;
import cppmode.parser.passes.ClassHoister;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Verifies the ported ClassHoister produces correctly base-before-derived ordering on real corpus files. */
public final class ClassHoisterTest {

    public static void main(String[] args) throws IOException {
        int failures = 0;

        // penrose_lsystem.cpp: PenroseSnowflakeLSystem : public LSystem --
        // LSystem must come first after hoisting.
        failures += checkOrdering("fixtures/penrose_lsystem.cpp", "LSystem", "PenroseSnowflakeLSystem");

        // Sprite : public Movable, public Drawable (oop_features.cpp) --
        // Movable (first listed base) must come before Sprite.
        failures += checkOrdering("fixtures/oop_features.cpp", "Movable", "Sprite");

        // Handles.cpp has one class (Handle) with no base -- hoisting should
        // still work and leave it as the only hoisted class, with everything
        // else (the global "handles" var, setup/draw/etc.) in "rest".
        failures += checkSingleClassNoBase("fixtures/handles.cpp", "Handle");

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CLASSHOISTER TESTS PASSED");
        } else {
            System.out.println(failures + " CLASSHOISTER TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int checkOrdering(String file, String expectedBaseFirst, String expectedDerivedSecond) throws IOException {
        String src = Files.readString(Path.of(file));
        CompilationUnit cu = Parser.parse(src);
        ClassHoister.Result result = ClassHoister.hoist(cu.items());

        List<TypeDef> hoisted = result.hoistedClasses;
        int baseIdx = indexOfClassNamed(hoisted, expectedBaseFirst);
        int derivedIdx = indexOfClassNamed(hoisted, expectedDerivedSecond);

        if (baseIdx < 0 || derivedIdx < 0) {
            System.out.println("FAIL  " + file + ": couldn't find both classes (" + expectedBaseFirst + "=" + baseIdx + ", " + expectedDerivedSecond + "=" + derivedIdx + ")");
            return 1;
        }
        if (baseIdx < derivedIdx) {
            System.out.println("OK    " + file + ": " + expectedBaseFirst + " (idx " + baseIdx + ") before " + expectedDerivedSecond + " (idx " + derivedIdx + ")");
            return 0;
        } else {
            System.out.println("FAIL  " + file + ": expected " + expectedBaseFirst + " before " + expectedDerivedSecond + " but got indices " + baseIdx + ", " + derivedIdx);
            return 1;
        }
    }

    private static int checkSingleClassNoBase(String file, String expectedClassName) throws IOException {
        String src = Files.readString(Path.of(file));
        CompilationUnit cu = Parser.parse(src);
        ClassHoister.Result result = ClassHoister.hoist(cu.items());

        boolean foundClass = result.hoistedClasses.size() == 1 && result.hoistedClasses.get(0).name().equals(expectedClassName);
        boolean restHasNoTypeDefs = result.rest.stream().noneMatch(item -> item instanceof TypeDef);
        boolean totalPreserved = result.hoistedClasses.size() + result.rest.size() == cu.items().size();

        if (foundClass && restHasNoTypeDefs && totalPreserved) {
            System.out.println("OK    " + file + ": single class '" + expectedClassName + "' hoisted, " + result.rest.size() + " items in rest, total item count preserved");
            return 0;
        } else {
            System.out.println("FAIL  " + file + ": foundClass=" + foundClass + " restHasNoTypeDefs=" + restHasNoTypeDefs + " totalPreserved=" + totalPreserved);
            return 1;
        }
    }

    private static int indexOfClassNamed(List<TypeDef> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equals(name)) return i;
        }
        return -1;
    }
}
