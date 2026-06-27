package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.ast.decl.EnumDecl;
import cppmode.parser.passes.EnumScopeExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Verifies EnumScopeExtractor against kitchen_sink.cpp's three enums:
 * unscoped "Color", scoped "Direction", and a multi-line scoped
 * "CardSuit" -- the multi-line case specifically being the shape the
 * original writeSketch() needed careful brace-depth tracking for, which
 * this AST gets for free since EnumDecl is already a single complete
 * node by the time the parser produces it.
 */
public final class EnumScopeExtractorTest {

    public static void main(String[] args) throws IOException {
        int failures = 0;
        failures += checkKitchenSinkEnums();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL ENUMSCOPEEXTRACTOR TESTS PASSED");
        } else {
            System.out.println(failures + " ENUMSCOPEEXTRACTOR TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int checkKitchenSinkEnums() throws IOException {
        String src = Files.readString(Path.of("fixtures/kitchen_sink.cpp"));
        CompilationUnit cu = Parser.parse(src);
        EnumScopeExtractor.Result result = EnumScopeExtractor.extract(cu.items());

        EnumDecl color = findEnum(result.enums, "Color");
        EnumDecl direction = findEnum(result.enums, "Direction");
        EnumDecl cardSuit = findEnum(result.enums, "CardSuit");

        boolean colorCorrect = color != null && !color.isScoped() && color.values().equals(List.of("RED", "GREEN", "BLUE"));
        boolean directionCorrect = direction != null && direction.isScoped() && direction.values().equals(List.of("UP", "DOWN"));
        // The multi-line case: confirms every enumerator survived, not just
        // the opening line -- exactly the bug class the original's careful
        // char-walking scan existed to avoid.
        boolean cardSuitCorrect = cardSuit != null && cardSuit.isScoped()
            && cardSuit.values().equals(List.of("HEARTS", "DIAMONDS", "CLUBS", "SPADES"));

        boolean restHasNoEnums = result.rest.stream().noneMatch(item -> item instanceof EnumDecl);
        boolean totalPreserved = result.enums.size() + result.rest.size() == cu.items().size();

        if (colorCorrect && directionCorrect && cardSuitCorrect && restHasNoEnums && totalPreserved) {
            System.out.println("OK    kitchen_sink.cpp: all 3 enums extracted correctly (including the multi-line "
                + "CardSuit case with all 4 enumerators intact), rest has no enums, total preserved");
            return 0;
        } else {
            System.out.println("FAIL  colorCorrect=" + colorCorrect + " directionCorrect=" + directionCorrect
                + " cardSuitCorrect=" + cardSuitCorrect + " restHasNoEnums=" + restHasNoEnums
                + " totalPreserved=" + totalPreserved);
            if (cardSuit != null) System.out.println("      CardSuit values found: " + cardSuit.values());
            return 1;
        }
    }

    private static EnumDecl findEnum(List<EnumDecl> enums, String name) {
        for (EnumDecl e : enums) {
            if (e.name().equals(name)) return e;
        }
        return null;
    }
}
