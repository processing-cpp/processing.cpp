package cppmode.parser.passes;

import cppmode.parser.ast.decl.FunctionDecl;
import cppmode.parser.ast.decl.TopLevelItem;
import cppmode.parser.ast.decl.TypeDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Ports the "_PSketch base injection" step from CppBuild.java's
 * writeSketch(), which currently does this with four chained regexes
 * over hoisted-class text plus a separate removePSketchFromDataStructs()
 * text scan.
 *
 * Every hoisted class/struct gets `_PSketch` injected as an additional
 * public virtual base (giving it access to Processing API free functions
 * via ADL/unqualified lookup), UNLESS it's a pure data type with no
 * methods at all -- detected in the original by a regex testing whether
 * the class body text contains a "ReturnTypeKeyword name(" shape. On the
 * AST this is simply "does this TypeDef have any FunctionDecl member,"
 * which is exact (no risk of a field initializer or comment that merely
 * LOOKS call-shaped being mistaken for a real method, unlike the
 * original's text-pattern match).
 *
 * Also folds in the original's separate "class defaults to public:"
 * injection (Java/Processing has no notion of C++'s class-defaults-to-
 * private rule, so every injected class needs an explicit "public:"
 * right after the brace) -- expressed here as a flag on the result
 * rather than literal text, since codegen decides how to render it.
 */
public final class PSketchInjector {
    private PSketchInjector() {}

    /** One class/struct's injection decision, paired with its (possibly unchanged) TypeDef. */
    public record Result(TypeDef typeDef, boolean injected) {
    }

    /**
     * @param hoistedClasses the TypeDef nodes already hoisted to namespace
     *                        scope (output of ClassHoister), in their final order
     * @return the same TypeDefs, each with `_PSketch` added to baseClasses
     *         (as the LAST entry, matching the original's "$1, public
     *         virtual _PSketch" -- appended after the user's own bases,
     *         never first) unless the class has no methods at all
     */
    public static List<Result> injectAll(List<TypeDef> hoistedClasses) {
        List<Result> results = new ArrayList<>(hoistedClasses.size());
        for (TypeDef td : hoistedClasses) {
            results.add(inject(td));
        }
        return results;
    }

    private static Result inject(TypeDef td) {
        if (!hasAnyMethod(td)) {
            return new Result(td, false);
        }
        List<String> newBases = new ArrayList<>(td.baseClasses());
        newBases.add("virtual _PSketch"); // codegen renders "public " prefix; see CodeGen notes if/when wired in
        TypeDef injected = new TypeDef(td.kind(), td.name(), td.templateParams(), newBases, td.members(),
            td.line(), td.col(), td.leadingComments());
        return new Result(injected, true);
    }

    /**
     * True if this TypeDef has at least one FunctionDecl member. The
     * original's "&& !body.contains(\"constexpr\")" exclusion is not
     * separately modeled here since this AST's FunctionDecl has no
     * constexpr flag at all (not confirmed needed by any corpus fixture
     * so far) -- this check is currently a strict superset of the
     * original's intent and has not yet been exercised against a
     * constexpr-method corpus case to confirm the distinction matters in
     * practice. Flagged here rather than silently assumed identical.
     */
    private static boolean hasAnyMethod(TypeDef td) {
        for (TopLevelItem member : td.members()) {
            if (member instanceof FunctionDecl) {
                return true;
            }
        }
        return false;
    }
}
