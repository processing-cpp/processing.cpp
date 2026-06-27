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

        // --- PHASE 1: hoist const-qualified scalar decls matching those names ---
        List<TopLevelItem> afterPhase1 = new ArrayList<>();
        for (TopLevelItem item : items) {
            if (item instanceof VariableDecl vd
                && vd.isConst()
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
