package processing.mode.cpp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/*
 * Consolidated smaller AST analysis passes -- ClassHoister, ArrayHoister,
 * NameUsageScanner, PSketchInjector, ForwardDeclGenerator,
 * EnumScopeExtractor. Each is otherwise unchanged; merged into one file
 * to reduce file count. DependencyHoister, LifecycleRewriter, and
 * CodeGen remain standalone files since each is substantial enough on
 * its own. See DECISION_two_parser_implementations.md (in
 * tools/cpp-parser/) for each pass's individual design history.
 */




/**
 * Replaces CppBuild.java's hoistClassesOnly() + removeHoistedClasses().
 *
 * Ported from a parallel implementation's CppClassHoister (package
 * processing.mode.cpp), retargeted onto this codebase's AST shapes. See
 * DECISION_two_parser_implementations.md for why this is a port of the
 * logic rather than a merge of the two ASTs.
 *
 * The original CppBuild.java pass was two separate character-walking scans
 * over raw text that had to stay in lockstep (one collects class blocks,
 * the other blanks the same ranges from the original source) -- a
 * structural duplication risk: if either scan's class/enum/comment-skipping
 * logic ever drifted from the other's, hoisted classes and "the rest"
 * could silently disagree about where a class started or ended (this is
 * the same bug class as the comment-spanning-newline bug already found and
 * fixed in CppBuild.java's classifyTopLevelDecls).
 *
 * With a real AST this is one operation instead of two: classify
 * cu.items() into "is a TypeDef" vs "isn't," reorder the TypeDefs, and the
 * non-TypeDef items already ARE the correct "rest" with zero risk of the
 * two halves disagreeing, since they're derived from the exact same parsed
 * list rather than two independent re-scans of raw text.
 *
 * Ordering: base classes before derived, matching the original's
 * bubble-style pairwise swap. The original CppBuild.java regex only ever
 * captured a single base-class name per class, so this preserves that same
 * single-base assumption for ordering purposes -- a class with multiple
 * bases (this AST's TypeDef.baseClasses() supports a list, confirmed real
 * by the multiple-inheritance kitchen-sink fixture) is ordered by its
 * FIRST listed base only. This pass doesn't invent new ordering behavior
 * for a case the original CppBuild.java pass never handled either.
 */
final class ClassHoister {
    private ClassHoister() {}

    public static final class Result {
        /** Hoisted TypeDef nodes, ordered so each class's first base class
         * (if any, and if that base is itself one of the hoisted classes)
         * appears before it. */
        public final List<TypeDef> hoistedClasses;
        /** Every other top-level item, in original order, with the
         * hoisted TypeDefs removed. */
        public final List<TopLevelItem> rest;

        public Result(List<TypeDef> hoistedClasses, List<TopLevelItem> rest) {
            this.hoistedClasses = hoistedClasses;
            this.rest = rest;
        }
    }

    public static Result hoist(List<TopLevelItem> items) {
        List<TypeDef> classBlocks = new ArrayList<>();
        List<TopLevelItem> rest = new ArrayList<>();

        for (TopLevelItem item : items) {
            if (item instanceof TypeDef td) {
                classBlocks.add(td);
            } else {
                rest.add(item);
            }
        }

        boolean changed = true;
        for (int pass = 0; pass < classBlocks.size() * 2 && changed; pass++) {
            changed = false;
            for (int a = 0; a < classBlocks.size(); a++) {
                TypeDef blockA = classBlocks.get(a);
                if (blockA.baseClasses().isEmpty()) continue;
                String aBase = blockA.baseClasses().get(0);
                for (int b = a + 1; b < classBlocks.size(); b++) {
                    TypeDef blockB = classBlocks.get(b);
                    if (blockB.name().equals(aBase)) {
                        classBlocks.set(a, blockB);
                        classBlocks.set(b, blockA);
                        changed = true;
                        break;
                    }
                }
                if (changed) break;
            }
        }

        return new Result(classBlocks, rest);
    }
}





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
final class ArrayHoister {
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




/**
 * Walks an arbitrary AST subtree looking for references to a given name.
 * Ported from a parallel implementation's NameUsageScanner (package
 * processing.mode.cpp), retargeted onto this codebase's AST shapes.
 * Originally written using exhaustive pattern-matching switches over the
 * sealed Expr/Statement/TopLevelItem hierarchies (which the Java compiler
 * verifies exhaustively, unlike a plain if-instanceof chain), but
 * rewritten as a plain if-instanceof chain after the real processing4
 * Gradle build rejected switch pattern matching as a disabled preview
 * feature -- see the rewritten visit() method's own comment for details.
 * Every branch's logic is unchanged from the original switch version.
 *
 * Exists specifically to answer the dependency questions DependencyHoister
 * needs: "is name X called anywhere in here" and "is name X referenced as
 * a bare identifier anywhere in here," which the original CppBuild.java
 * regex passes answered with "\bname\s*\(" and "\bname\b" respectively
 * over raw text.
 *
 * Correctness note (carried over from the port source): a true textual
 * "\bname\b" regex over raw source would also match `name` inside a
 * comment or a string literal. This walker, operating on the AST instead
 * of text, structurally CANNOT match inside a comment (comments aren't
 * part of the expression/statement tree at all -- they're attached
 * separately as leadingComments) or inside a string/char literal (a
 * Literal's text field is never inspected for substring matches here,
 * only real Identifier nodes are). This is strictly more correct than the
 * original for this edge case, not a casual behavior change.
 */
final class NameUsageScanner {
    private NameUsageScanner() {}

    public static boolean containsIdentifier(Node root, String name) {
        return new Finder(name, false).visit(root);
    }

    public static boolean containsCall(Node root, String name) {
        return new Finder(name, true).visit(root);
    }

    private static final class Finder {
        final String name;
        final boolean callOnly;

        Finder(String name, boolean callOnly) {
            this.name = name;
            this.callOnly = callOnly;
        }

        boolean visit(Node n) {
            if (n == null) return false;

            // NOTE: this was originally written as a pattern-matching
            // switch expression (Java 21's "case Identifier id -> ...").
            // Rewritten as a plain if-instanceof chain after the real
            // processing4 Gradle build rejected it with "patterns in
            // switch statements are a preview feature and are disabled
            // by default" -- this codebase's actual target Java level
            // doesn't have that preview feature enabled, and changing
            // Gradle's own configuration to enable a language preview
            // feature for this one file was judged more invasive than
            // just not using the feature. Every branch's logic below is
            // IDENTICAL to the switch version; only the dispatch
            // mechanism changed.
            if (n instanceof Identifier id) return !callOnly && id.name().equals(name);
            if (n instanceof ScopedName) return false;
            if (n instanceof CallExpr c) {
                boolean calleeIsTarget = c.callee() instanceof Identifier id && id.name().equals(name);
                if (calleeIsTarget) return true;
                if (visit(c.callee())) return true;
                for (Expr a : c.args()) if (visit(a)) return true;
                return false;
            }
            if (n instanceof TypeDef td) {
                for (TopLevelItem m : td.members()) if (visit(m)) return true;
                return false;
            }
            if (n instanceof FunctionDecl fd) {
                for (Param p : fd.params()) {
                    if (p.defaultValue() != null && visit(p.defaultValue())) return true;
                }
                for (FunctionDecl.ConstructorInit init : fd.initializerList()) {
                    for (Expr a : init.args()) if (visit(a)) return true;
                }
                return fd.body() != null && visit(fd.body());
            }
            if (n instanceof VariableDecl vd) {
                for (Expr dim : vd.arrayDims()) if (visit(dim)) return true;
                return vd.initializer() != null && visit(vd.initializer());
            }
            if (n instanceof EnumDecl) return false;
            if (n instanceof PreprocessorLine) return false;
            if (n instanceof NamespaceDecl nd) {
                for (TopLevelItem item : nd.items()) if (visit(item)) return true;
                return false;
            }
            if (n instanceof UsingNamespaceDecl) return false;
            if (n instanceof TopLevelStatement ts) return visit(ts.statement());

            if (n instanceof Block b) {
                for (Statement s : b.statements()) if (visit(s)) return true;
                return false;
            }
            if (n instanceof DeclStatement ds) {
                for (Expr dim : ds.arrayDims()) if (visit(dim)) return true;
                return ds.initializer() != null && visit(ds.initializer());
            }
            if (n instanceof ExprStatement es) return visit(es.expr());
            if (n instanceof IfStatement s) {
                return visit(s.condition()) || visit(s.thenBranch())
                    || (s.elseBranch() != null && visit(s.elseBranch()));
            }
            if (n instanceof ForStatement s) {
                return (s.init() != null && visit(s.init()))
                    || (s.condition() != null && visit(s.condition()))
                    || (s.update() != null && visit(s.update()))
                    || visit(s.body());
            }
            if (n instanceof RangeForStatement s) return visit(s.iterableExpr()) || visit(s.body());
            if (n instanceof WhileStatement s) return visit(s.condition()) || visit(s.body());
            if (n instanceof DoWhileStatement s) return visit(s.body()) || visit(s.condition());
            if (n instanceof SwitchStatement s) {
                if (visit(s.subject())) return true;
                for (SwitchCase c : s.cases()) {
                    if (c.matchValue() != null && visit(c.matchValue())) return true;
                    for (Statement st : c.body()) if (visit(st)) return true;
                }
                return false;
            }
            if (n instanceof ReturnStatement s) return s.value() != null && visit(s.value());
            if (n instanceof TryStatement s) {
                if (visit(s.tryBlock())) return true;
                for (CatchClause c : s.catchClauses()) {
                    if (c.body() != null && visit(c.body())) return true;
                }
                return false;
            }
            if (n instanceof DeleteStatement s) return visit(s.target());
            if (n instanceof BreakStatement) return false;
            if (n instanceof ContinueStatement) return false;

            if (n instanceof BinaryExpr e) return visit(e.left()) || visit(e.right());
            if (n instanceof UnaryExpr e) return visit(e.operand());
            if (n instanceof PostfixExpr e) return visit(e.operand());
            if (n instanceof AssignExpr e) return visit(e.target()) || visit(e.value());
            if (n instanceof TernaryExpr e) return visit(e.condition()) || visit(e.thenExpr()) || visit(e.elseExpr());
            if (n instanceof MemberAccessExpr e) return visit(e.target());
            if (n instanceof IndexExpr e) return visit(e.target()) || visit(e.index());
            if (n instanceof CastExpr e) return visit(e.expr());
            if (n instanceof NewExpr e) {
                for (Expr a : e.args()) if (visit(a)) return true;
                return false;
            }
            if (n instanceof ArrayNewExpr e) return visit(e.sizeExpr());
            if (n instanceof LambdaExpr e) return visit(e.body());
            if (n instanceof InitializerListExpr e) {
                for (Expr el : e.elements()) if (visit(el)) return true;
                return false;
            }
            if (n instanceof Literal) return false;
            return false;
        }
    }
}





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
final class PSketchInjector {
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





/**
 * Replaces the inline forward-declaration generation in CppBuild.java's
 * writeSketch(), which re-derives a function's signature by regex-
 * matching its own already-generated text back apart.
 *
 * On the AST this needs no pattern at all: a forward declaration of a
 * FunctionDecl is simply the same FunctionDecl with body set to null --
 * CodeGen's emitFunctionDecl already renders a null-body FunctionDecl as
 * "ReturnType name(params);" (no body, trailing semicolon), since that's
 * also the correct rendering for a genuine declaration-without-definition
 * in ordinary C++. No new codegen logic was needed for this -- it already
 * existed for a different reason (the AST always supported a bodyless
 * FunctionDecl, even though nothing produced one until this pass).
 *
 * This lets a class method (e.g. "Agent::update" calling "dirToVec")
 * compile even though the class itself appears earlier in the emitted
 * file than the hoisted function's full definition -- matching the
 * original's exact purpose.
 */
final class ForwardDeclGenerator {
    private ForwardDeclGenerator() {}

    /**
     * @param hoistedFunctions the FunctionDecl nodes hoisted to namespace
     *                          scope (e.g. via a free-function dependency
     *                          pass analogous to DependencyHoister's
     *                          function-hoisting half)
     * @return one bodyless FunctionDecl per input, in the same order,
     *         suitable for emitting before the hoisted classes that call them
     */
    public static List<FunctionDecl> generate(List<FunctionDecl> hoistedFunctions) {
        List<FunctionDecl> decls = new ArrayList<>(hoistedFunctions.size());
        for (FunctionDecl fd : hoistedFunctions) {
            decls.add(new FunctionDecl(
                fd.returnType(), fd.name(), fd.templateParams(), fd.params(),
                fd.initializerList(), null /* body -- this is what makes it a forward decl */,
                fd.isConstructor(), fd.isDestructor(), fd.isVirtual(), fd.isOverride(),
                fd.isConst(), fd.isStatic(), fd.line(), fd.col(), List.of() /* no comments on the forward decl */
            ));
        }
        return decls;
    }
}





/**
 * Replaces the enum-extraction half of the combined enumScope/
 * constexprScope block in CppBuild.java's writeSketch(). That block does
 * two unrelated things in one character-walking pass: pull out enum
 * declarations (so they can be emitted before classes that use them) and
 * pull out constexpr/static_assert/using-alias declarations. This pass
 * covers only the enum half -- see "constexpr/static_assert/using-alias
 * NOT ported" below for why the other half is intentionally left out for
 * now rather than faked.
 *
 * The original's enum-extraction logic exists almost entirely to solve a
 * problem this AST doesn't have: a naive per-line scan only matches an
 * enum's OPENING line ("enum Direction {"), silently leaving the
 * enumerator list itself behind in the "rest" bucket -- so the original
 * needs a careful character-walking, brace-depth-tracking scan
 * specifically to capture the whole multi-line block as one unit. On
 * this AST, an EnumDecl produced by the parser is ALREADY one complete
 * node (name, isScoped flag, and the full list of enumerator values) --
 * there is no way for "half an enum" to exist in the tree at all, so
 * this pass is just a filter: separate TopLevelItems into "is an
 * EnumDecl" and "isn't," same pattern as ClassHoister/ArrayHoister.
 *
 * NOT PORTED: constexpr declarations, static_assert, and type-alias
 * "using Name = Type;" declarations. Checked directly against the real
 * 131-file example corpus (see tools/cpp-parser/real-corpus-snapshot/):
 * zero files use "constexpr", "static_assert", or a type-alias "using"
 * declaration anywhere. This AST also has no constexpr concept at all
 * (VariableDecl/FunctionDecl only track isConst/isStatic) -- adding
 * speculative support for a construct with zero corpus evidence would
 * mean shipping untested guesswork dressed up as a port. If a future
 * sketch (real or fixture) is found using any of these, that's the
 * trigger to design and test the corresponding AST support properly,
 * not to retrofit it blind now.
 */
final class EnumScopeExtractor {
    private EnumScopeExtractor() {}

    public static final class Result {
        public final List<EnumDecl> enums = new ArrayList<>();
        public final List<TopLevelItem> rest = new ArrayList<>();
    }

    public static Result extract(List<TopLevelItem> items) {
        Result result = new Result();
        for (TopLevelItem item : items) {
            if (item instanceof EnumDecl ed) {
                result.enums.add(ed);
            } else {
                result.rest.add(item);
            }
        }
        return result;
    }
}
