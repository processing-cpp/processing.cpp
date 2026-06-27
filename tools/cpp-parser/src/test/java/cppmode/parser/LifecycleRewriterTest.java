package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.ast.decl.FunctionDecl;
import cppmode.parser.ast.decl.TopLevelItem;
import cppmode.parser.ast.decl.VariableDecl;
import cppmode.parser.ast.expr.Identifier;
import cppmode.parser.passes.LifecycleRewriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Verifies the ported LifecycleRewriter against alpha_mask.cpp's real
 * uninitialized pointer globals ("PImage* img;", "PImage* imgMask;") and
 * its setup()/draw() lifecycle functions.
 */
public final class LifecycleRewriterTest {

    private static final Set<String> LIFECYCLE_NAMES = Set.of(
        "setup", "draw", "mousePressed", "mouseReleased", "keyPressed", "keyReleased"
    );

    public static void main(String[] args) throws IOException {
        int failures = 0;
        failures += checkAlphaMask();
        failures += checkAlreadyInitializedPointerUntouched();
        failures += checkNonPointerUntouched();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL LIFECYCLEREWRITER TESTS PASSED");
        } else {
            System.out.println(failures + " LIFECYCLEREWRITER TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int checkAlphaMask() throws IOException {
        String src = Files.readString(Path.of("fixtures/alpha_mask.cpp"));
        CompilationUnit cu = Parser.parse(src);
        List<TopLevelItem> rewritten = LifecycleRewriter.rewrite(cu.items(), LIFECYCLE_NAMES);

        VariableDecl img = findVarDecl(rewritten, "img");
        VariableDecl imgMask = findVarDecl(rewritten, "imgMask");
        FunctionDecl setupFn = findFunctionDecl(rewritten, "setup");
        FunctionDecl drawFn = findFunctionDecl(rewritten, "draw");

        boolean imgDefaulted = img != null && img.initializer() instanceof Identifier id && id.name().equals("nullptr");
        boolean imgMaskDefaulted = imgMask != null && imgMask.initializer() instanceof Identifier id && id.name().equals("nullptr");
        boolean setupIsOverride = setupFn != null && setupFn.isOverride();
        boolean drawIsOverride = drawFn != null && drawFn.isOverride();
        boolean totalPreserved = rewritten.size() == cu.items().size();

        if (imgDefaulted && imgMaskDefaulted && setupIsOverride && drawIsOverride && totalPreserved) {
            System.out.println("OK    alpha_mask.cpp: 'img'/'imgMask' both defaulted to nullptr, "
                + "'setup'/'draw' both marked override, total preserved");
            return 0;
        } else {
            System.out.println("FAIL  alpha_mask.cpp: imgDefaulted=" + imgDefaulted + " imgMaskDefaulted=" + imgMaskDefaulted
                + " setupIsOverride=" + setupIsOverride + " drawIsOverride=" + drawIsOverride
                + " totalPreserved=" + totalPreserved);
            return 1;
        }
    }

    /** A pointer field that's ALREADY initialized to something must be left untouched (matches original behavior exactly). */
    private static int checkAlreadyInitializedPointerUntouched() throws IOException {
        String src = "int* p = new int(42);";
        CompilationUnit cu = Parser.parse(src);
        List<TopLevelItem> rewritten = LifecycleRewriter.rewrite(cu.items(), LIFECYCLE_NAMES);
        VariableDecl p = findVarDecl(rewritten, "p");

        boolean unchanged = p != null && !(p.initializer() instanceof Identifier id && id.name().equals("nullptr"));
        if (unchanged) {
            System.out.println("OK    already-initialized pointer left untouched (not overwritten with nullptr)");
            return 0;
        } else {
            System.out.println("FAIL  already-initialized pointer was incorrectly defaulted to nullptr");
            return 1;
        }
    }

    /** A non-pointer declaration must never be touched by the pointer-defaulting rule. */
    private static int checkNonPointerUntouched() throws IOException {
        String src = "int x;";
        CompilationUnit cu = Parser.parse(src);
        List<TopLevelItem> rewritten = LifecycleRewriter.rewrite(cu.items(), LIFECYCLE_NAMES);
        VariableDecl x = findVarDecl(rewritten, "x");

        boolean stillNull = x != null && x.initializer() == null;
        if (stillNull) {
            System.out.println("OK    non-pointer declaration ('int x;') left with no initializer, as expected");
            return 0;
        } else {
            System.out.println("FAIL  non-pointer declaration was incorrectly given an initializer");
            return 1;
        }
    }

    private static VariableDecl findVarDecl(List<TopLevelItem> items, String name) {
        for (TopLevelItem item : items) {
            if (item instanceof VariableDecl vd && vd.name().equals(name)) return vd;
        }
        return null;
    }

    private static FunctionDecl findFunctionDecl(List<TopLevelItem> items, String name) {
        for (TopLevelItem item : items) {
            if (item instanceof FunctionDecl fd && fd.name().equals(name)) return fd;
        }
        return null;
    }
}
