package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.ast.decl.TopLevelItem;
import cppmode.parser.ast.decl.TypeDef;
import cppmode.parser.ast.decl.VariableDecl;
import cppmode.parser.passes.ClassHoister;
import cppmode.parser.passes.DependencyHoister;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Verifies the ported DependencyHoister against handles.cpp's real
 * dependency: the top-level global "bool firstMousePress" is referenced
 * as a bare identifier inside Handle::pressEvent(), so once Handle is
 * hoisted to namespace scope, firstMousePress must be hoisted too.
 */
public final class DependencyHoisterTest {

    private static final Set<String> LIFECYCLE_NAMES = Set.of(
        "setup", "draw", "mousePressed", "mouseReleased", "keyPressed", "keyReleased"
    );

    public static void main(String[] args) throws IOException {
        int failures = 0;
        failures += checkHandlesFirstMousePress();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL DEPENDENCYHOISTER TESTS PASSED");
        } else {
            System.out.println(failures + " DEPENDENCYHOISTER TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int checkHandlesFirstMousePress() throws IOException {
        String src = Files.readString(Path.of("fixtures/handles.cpp"));
        CompilationUnit cu = Parser.parse(src);

        ClassHoister.Result classResult = ClassHoister.hoist(cu.items());
        List<TypeDef> hoistedClasses = classResult.hoistedClasses;

        DependencyHoister.Result depResult = DependencyHoister.hoist(classResult.rest, hoistedClasses, LIFECYCLE_NAMES);

        boolean firstMousePressHoisted = depResult.hoistedVariables.stream()
            .anyMatch(v -> v.name().equals("firstMousePress"));
        boolean notInRest = depResult.rest.stream()
            .noneMatch(item -> item instanceof VariableDecl vd && vd.name().equals("firstMousePress"));
        boolean handlesArrayDeclStillInRest = depResult.rest.stream()
            .anyMatch(item -> item instanceof VariableDecl vd && vd.name().equals("handles"));

        int totalAfter = hoistedClasses.size() + depResult.hoistedFunctions.size()
            + depResult.hoistedVariables.size() + depResult.rest.size();

        if (firstMousePressHoisted && notInRest && handlesArrayDeclStillInRest && totalAfter == cu.items().size()) {
            System.out.println("OK    handles.cpp: 'firstMousePress' correctly hoisted as a Handle-class dependency, "
                + "'handles' (not referenced inside Handle's own body) correctly stays in rest, total preserved ("
                + totalAfter + ")");
            return 0;
        } else {
            System.out.println("FAIL  handles.cpp: firstMousePressHoisted=" + firstMousePressHoisted
                + " notInRest=" + notInRest + " handlesArrayDeclStillInRest=" + handlesArrayDeclStillInRest
                + " totalAfter=" + totalAfter + " cu.items=" + cu.items().size());
            System.out.println("      hoistedVariables=" + depResult.hoistedVariables.stream().map(VariableDecl::name).toList());
            System.out.println("      rest=" + depResult.rest.stream().map(TopLevelItem::getClass).toList());
            return 1;
        }
    }
}
