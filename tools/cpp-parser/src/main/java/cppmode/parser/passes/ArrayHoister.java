package cppmode.parser.passes;

import cppmode.parser.ast.decl.TopLevelItem;
import cppmode.parser.ast.decl.VariableDecl;
import cppmode.parser.ast.expr.Expr;
import cppmode.parser.ast.expr.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Replaces the two-phase array-hoisting block inside CppBuild.java's
 * writeSketch(), which used classifyTopLevelDecls() (a character-walking
 * scan) called repeatedly inside two separate "while (changed)" fixed-point
 * loops -- one pass per single removal, since the original mutated a raw
 * String and had to re-scan from scratch after every hoisted declaration.
 *
 * Ported from a parallel implementation's CppArrayHoister (package
 * processing.mode.cpp), retargeted onto this codebase's AST. See
 * DECISION_two_parser_implementations.md for the porting rationale.
 *
 * SIMPLIFICATION vs. the original port source: that implementation's
 * VariableDecl groups multiple comma-separated declarators under one
 * shared type ("int rectX, rectY;" stays as ONE VariableDecl with two
 * Declarators), which meant its hoister had to split a single
 * statement's declarators between "hoisted" and "kept" buckets. This
 * codebase's VariableDecl is already single-declarator -- multi-declarator
 * statements are desugared into separate VariableDecl nodes at PARSE time
 * (see VariableDecl's javadoc) -- so that splitting logic doesn't apply
 * here at all: each VariableDecl already represents exactly one
 * declarator, so hoisting is just "does this VariableDecl go in this
 * bucket or not," with no per-declarator partitioning step needed.
 *
 * Preserves the original's exact two-phase contract:
 *   PHASE 1: collect every array's non-numeric size identifier, then hoist
 *            every const-qualified scalar declaration whose name matches
 *            one of those identifiers.
 *   PHASE 2: hoist every array declaration, unconditionally, regardless of
 *            whether it depends on anything hoisted in phase 1.
 * This guarantees sizing constants precede the arrays that use them in the
 * hoisted-arrays output, matching the original's stated intent.
 */
public final class ArrayHoister {
    private ArrayHoister() {}

    public static final class Result {
        /** Sizing-constant VariableDecls (phase 1), in the order found. */
        public final List<VariableDecl> hoistedSizingConstants = new ArrayList<>();
        /** Array VariableDecls (phase 2), in the order found. */
        public final List<VariableDecl> hoistedArrays = new ArrayList<>();
        /** Every remaining top-level item, with hoisted declarations removed. */
        public final List<TopLevelItem> rest = new ArrayList<>();
    }

    public static Result hoist(List<TopLevelItem> items) {
        Result result = new Result();

        // --- Identify every array declaration's non-numeric size identifier ---
        Set<String> arraySizeIdentifiers = new LinkedHashSet<>();
        for (TopLevelItem item : items) {
            if (!(item instanceof VariableDecl vd)) continue;
            if (vd.arrayDims().isEmpty()) continue;
            for (Expr dim : vd.arrayDims()) {
                String sizeName = nonNumericIdentifierName(dim);
                if (sizeName != null) arraySizeIdentifiers.add(sizeName);
            }
        }

        // BUG FIX, found via a real, official Processing example sketch
        // (Blur.pde): "float kernel[3][3] = {{v,v,v},{v,v,v},{v,v,v}};"
        // depends on a plain (non-const) "float v" referenced inside its
        // INITIALIZER, not its dimensions -- a completely different
        // relationship than the sizing-constant case above, which this
        // pass was never built to detect. Confirmed via direct g++
        // testing that the actual fix needed is purely about ORDER: "v"
        // just needs to be declared before "kernel" at the same (file)
        // scope, with no const-qualification requirement at all.
        //
        // Scan every array declaration's INITIALIZER for identifier
        // references too, and add them to the same hoist-candidate set
        // used for sizing identifiers -- the two cases (a dimension
        // identifier, an initializer identifier) need the exact same
        // treatment (hoist whatever scalar that name refers to, so it
        // precedes the array using it), so they share one collection
        // pass and one set, rather than duplicating the logic for a
        // second, parallel "initializer identifiers" set.
        for (TopLevelItem item : items) {
            if (!(item instanceof VariableDecl vd)) continue;
            if (vd.arrayDims().isEmpty()) continue;
            if (vd.initializer() == null) continue;
            collectIdentifierNames(vd.initializer(), arraySizeIdentifiers);
        }

        // --- PHASE 1: hoist scalar decls matching those names ---
        // NOTE: no longer requires vd.isConst() -- a non-const scalar
        // referenced inside an array's initializer has the exact same
        // "must precede the array, at the same scope" requirement as a
        // const sizing constant does; the original const-only
        // restriction only ever made sense for the sizing-identifier
        // case this pass was originally built for, not for the
        // initializer-identifier case found via Blur.pde.
        List<TopLevelItem> afterPhase1 = new ArrayList<>();
        for (TopLevelItem item : items) {
            if (item instanceof VariableDecl vd
                && vd.arrayDims().isEmpty()
                && arraySizeIdentifiers.contains(vd.name())) {
                result.hoistedSizingConstants.add(vd);
            } else {
                afterPhase1.add(item);
            }
        }

        // --- PHASE 2: hoist every array declaration, unconditionally ---
        for (TopLevelItem item : afterPhase1) {
            if (item instanceof VariableDecl vd && !vd.arrayDims().isEmpty()) {
                result.hoistedArrays.add(vd);
            } else {
                result.rest.add(item);
            }
        }

        return result;
    }

    /**
     * Recursively walks an expression tree collecting every bare
     * Identifier's name into `out`. Deliberately broad (any identifier
     * anywhere in the initializer, not just direct children) since an
     * array initializer can nest arbitrarily (e.g.
     * "{{v, v+1, foo(v)}, {v, v, v}}") -- correctness here means never
     * MISSING a real dependency, even at the cost of occasionally
     * collecting an identifier that doesn't correspond to any real
     * top-level scalar (handled safely: such a name simply never
     * matches anything in the PHASE 1 hoisting loop above, so it's
     * silently ignored, not a problem).
     */
    private static void collectIdentifierNames(Expr e, Set<String> out) {
        if (e instanceof Identifier id) {
            out.add(id.name());
        } else if (e instanceof cppmode.parser.ast.expr.InitializerListExpr il) {
            for (Expr el : il.elements()) collectIdentifierNames(el, out);
        } else if (e instanceof cppmode.parser.ast.expr.BinaryExpr be) {
            collectIdentifierNames(be.left(), out);
            collectIdentifierNames(be.right(), out);
        } else if (e instanceof cppmode.parser.ast.expr.UnaryExpr ue) {
            collectIdentifierNames(ue.operand(), out);
        } else if (e instanceof cppmode.parser.ast.expr.CallExpr ce) {
            collectIdentifierNames(ce.callee(), out);
            for (Expr arg : ce.args()) collectIdentifierNames(arg, out);
        } else if (e instanceof cppmode.parser.ast.expr.CastExpr cae) {
            collectIdentifierNames(cae.expr(), out);
        }
        // Other Expr kinds (Literal, etc.) contain no identifiers to collect.
    }

    /**
     * If `e` is a bare Identifier (not a numeric literal), returns its name;
     * otherwise null. Mirrors the original CppBuild.java pass's
     * "!d.sizeExpr.matches(\"\\d+\")" check, which only ever needed to
     * distinguish a literal digit run from a named constant -- arbitrary
     * non-identifier size expressions (e.g. "N+1") weren't handled by the
     * original either, so this preserves that same scope rather than trying
     * to extract every identifier from an arbitrary expression.
     */
    private static String nonNumericIdentifierName(Expr e) {
        if (e instanceof Identifier id) {
            if (!id.name().matches("\\d+")) return id.name();
        }
        return null;
    }
}
