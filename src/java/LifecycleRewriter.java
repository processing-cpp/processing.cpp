package processing.mode.cpp;

import processing.mode.cpp.NamedType;
import processing.mode.cpp.TypeRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Replaces CppBuild.java's rewriteAsSketchMethods(), which did two
 * unrelated things in one line-by-line text pass: (1) added "override" to
 * any lifecycle function's signature (setup, draw, mousePressed, etc.) by
 * regex-matching against three separate line shapes, since a hand-rolled
 * text pass has to enumerate every way a signature might be split across
 * lines; (2) defaulted every pointer-typed field/variable declaration with
 * no initializer to "= nullptr", via a separate regex matched against
 * every line.
 *
 * Ported from a parallel implementation's CppLifecycleRewriter (package
 * processing.mode.cpp), retargeted onto this codebase's AST. See
 * DECISION_two_parser_implementations.md for the porting rationale.
 *
 * STRUCTURAL DEPARTURE from the port source, required rather than
 * stylistic: that implementation mutates AST nodes in place (plain Java
 * classes with public mutable fields, e.g. "fd.isOverride = true;",
 * "d.initializer = nullLit;"). This codebase's AST is built entirely from
 * Java records, which are immutable by construction -- there is no field
 * to assign into. This port is therefore a genuine tree-REBUILD: every
 * method here takes a node and returns a (possibly new) replacement node,
 * reconstructing each record with the desired field changed rather than
 * mutating the original. The original's exact decision logic (which
 * functions get "override," which declarators get defaulted to nullptr,
 * and the recursion shape into statement bodies) is preserved faithfully;
 * only the mechanism for applying the change differs.
 *
 * Note on this AST's "nullptr": this parser currently parses the bare
 * token "nullptr" as an ordinary Identifier (see Parser notes -- a
 * dedicated NullptrLiteral node was never added since nothing in the
 * corpus walk specifically required one, and "nullptr" flows correctly
 * through expression position as a plain identifier reference for every
 * purpose exercised so far). This pass constructs that same Identifier
 * shape when defaulting a pointer declarator, rather than inventing a new
 * literal node the rest of the codebase doesn't otherwise have a reason
 * to support yet.
 *
 * One behavior preserved exactly rather than "fixed": the original's
 * pointer-defaulting rule requires a declarator to have NEITHER an
 * initializer NOR array dims already -- it does not distinguish "this
 * pointer field already has some other default" from "this pointer field
 * has none." This port mirrors that distinction exactly.
 */
public final class LifecycleRewriter {
    private LifecycleRewriter() {}

    /**
     * Returns a new list with lifecycle functions marked override and
     * uninitialized pointer declarators defaulted to "= nullptr".
     *
     * IMPORTANT, fixed after a real bug found via PipelineCompositionTest:
     * the override-marking rule applies ONLY to true top-level
     * FunctionDecls, NEVER to class members. The original CppBuild.java
     * never has this ambiguity at all, because of pipeline ORDER: classes
     * are hoisted out via hoistClassesOnly()/removeHoistedClasses() and
     * rewriteAsSketchMethods() (the original this class ports) only ever
     * runs on what's left afterward -- a class member's body is never
     * passed to it. This port's first version recursed into TypeDef
     * members and applied the SAME override-marking rule there, which
     * incorrectly marked unrelated user methods like "Drawable::draw()"
     * (a method on a user-defined class with a name that happens to
     * collide with the sketch lifecycle method "draw") as override --
     * which doesn't compile, since Drawable::draw() doesn't actually
     * override anything. Confirmed by a real g++ check
     * (PipelineCompositionTest, oop_features.cpp) that produced exactly
     * this error: "'void Drawable::draw()' marked 'override', but does
     * not override". The pointer-defaulting rule still DOES recurse into
     * class members (e.g. "ArrayList<Handle>* others;" inside the real
     * Handle class), since that rule is unrelated to lifecycle names and
     * the original's pointer-defaulting regex was never restricted to
     * top-level scope either.
     */
    public static List<TopLevelItem> rewrite(List<TopLevelItem> items, Set<String> lifecycleNames) {
        List<TopLevelItem> result = new ArrayList<>(items.size());
        for (TopLevelItem item : items) {
            result.add(rewriteTopLevelItem(item, lifecycleNames));
        }
        return result;
    }

    /** Top-level only: applies BOTH the override-marking rule and the pointer-defaulting rule. */
    private static TopLevelItem rewriteTopLevelItem(TopLevelItem item, Set<String> lifecycleNames) {
        if (item instanceof FunctionDecl fd) {
            boolean shouldOverride = lifecycleNames.contains(fd.name()) && !fd.isConstructor() && !fd.isDestructor();
            Block newBody = fd.body() != null ? defaultPointerFieldsInBlock(fd.body()) : null;
            if (!shouldOverride && newBody == fd.body()) return fd; // no change needed, avoid pointless allocation
            return new FunctionDecl(
                fd.returnType(), fd.name(), fd.templateParams(), fd.params(), fd.initializerList(),
                newBody, fd.isConstructor(), fd.isDestructor(), fd.isVirtual(),
                fd.isOverride() || shouldOverride, fd.isConst(), fd.isConstexpr(), fd.isStatic(), fd.isPureVirtual(), fd.isDefault(), fd.isDelete(),
                fd.line(), fd.col(), fd.leadingComments()
            );
        }
        if (item instanceof VariableDecl vd) {
            return defaultPointerFieldsInVarDecl(vd);
        }
        if (item instanceof TypeDef td) {
            List<TopLevelItem> newMembers = new ArrayList<>(td.members().size());
            for (TopLevelItem member : td.members()) {
                newMembers.add(rewriteClassMember(member));
            }
            return new TypeDef(td.kind(), td.name(), td.templateParams(), td.baseClasses(), newMembers,
                td.line(), td.col(), td.leadingComments());
        }
        // EnumDecl, NamespaceDecl, UsingNamespaceDecl, PreprocessorLine,
        // TopLevelStatement: nothing to rewrite for either rule.
        return item;
    }

    /** Class-member only: applies ONLY the pointer-defaulting rule, never override-marking. */
    private static TopLevelItem rewriteClassMember(TopLevelItem member) {
        if (member instanceof FunctionDecl fd) {
            Block newBody = fd.body() != null ? defaultPointerFieldsInBlock(fd.body()) : null;
            if (newBody == fd.body()) return fd;
            return new FunctionDecl(
                fd.returnType(), fd.name(), fd.templateParams(), fd.params(), fd.initializerList(),
                newBody, fd.isConstructor(), fd.isDestructor(), fd.isVirtual(),
                fd.isOverride(), fd.isConst(), fd.isConstexpr(), fd.isStatic(), fd.isPureVirtual(), fd.isDefault(), fd.isDelete(),
                fd.line(), fd.col(), fd.leadingComments()
            );
        }
        if (member instanceof VariableDecl vd) {
            return defaultPointerFieldsInVarDecl(vd);
        }
        if (member instanceof TypeDef td) {
            // Nested class/struct -- recurse with the same class-member-only rules.
            List<TopLevelItem> newMembers = new ArrayList<>(td.members().size());
            for (TopLevelItem nested : td.members()) {
                newMembers.add(rewriteClassMember(nested));
            }
            return new TypeDef(td.kind(), td.name(), td.templateParams(), td.baseClasses(), newMembers,
                td.line(), td.col(), td.leadingComments());
        }
        return member;
    }

    private static VariableDecl defaultPointerFieldsInVarDecl(VariableDecl vd) {
        if (!(vd.type() instanceof NamedType nt) || nt.pointerDepth() == 0) return vd;
        if (vd.initializer() != null || !vd.arrayDims().isEmpty()) return vd;
        Identifier nullptrLit = new Identifier("nullptr", vd.line(), vd.col(), List.of());
        return new VariableDecl(vd.type(), vd.name(), vd.arrayDims(), nullptrLit,
            vd.isConst(), vd.isStatic(), vd.line(), vd.col(), vd.leadingComments());
    }

    private static DeclStatement defaultPointerFieldsInDeclStatement(DeclStatement ds) {
        if (!(ds.type() instanceof NamedType nt) || nt.pointerDepth() == 0) return ds;
        if (ds.initializer() != null || !ds.arrayDims().isEmpty()) return ds;
        Identifier nullptrLit = new Identifier("nullptr", ds.line(), ds.col(), List.of());
        return new DeclStatement(ds.type(), ds.name(), ds.arrayDims(), nullptrLit,
            ds.isStatic(), ds.isConst(), ds.line(), ds.col(), ds.leadingComments());
    }

    private static Block defaultPointerFieldsInBlock(Block b) {
        List<Statement> newStatements = new ArrayList<>(b.statements().size());
        boolean changed = false;
        for (Statement s : b.statements()) {
            Statement rewritten = defaultPointerFieldsInStatement(s);
            if (rewritten != s) changed = true;
            newStatements.add(rewritten);
        }
        return changed ? new Block(newStatements, b.line(), b.col(), b.leadingComments()) : b;
    }

    private static Statement defaultPointerFieldsInStatement(Statement s) {
        if (s instanceof DeclStatement ds) {
            return defaultPointerFieldsInDeclStatement(ds);
        }
        if (s instanceof Block b) {
            return defaultPointerFieldsInBlock(b);
        }
        if (s instanceof IfStatement ifs) {
            Statement newThen = defaultPointerFieldsInStatement(ifs.thenBranch());
            Statement newElse = ifs.elseBranch() != null ? defaultPointerFieldsInStatement(ifs.elseBranch()) : null;
            return new IfStatement(ifs.condition(), newThen, newElse, ifs.isConstexpr(), ifs.line(), ifs.col(), ifs.leadingComments());
        }
        if (s instanceof ForStatement f) {
            Statement newInit = f.init() instanceof DeclStatement ds ? defaultPointerFieldsInDeclStatement(ds) : f.init();
            Statement newBody = defaultPointerFieldsInStatement(f.body());
            return new ForStatement(newInit, f.condition(), f.update(), newBody, f.line(), f.col(), f.leadingComments());
        }
        if (s instanceof RangeForStatement rf) {
            Statement newBody = defaultPointerFieldsInStatement(rf.body());
            return new RangeForStatement(rf.declType(), rf.declName(), rf.isReference(), rf.iterableExpr(), newBody,
                rf.line(), rf.col(), rf.leadingComments());
        }
        if (s instanceof WhileStatement w) {
            Statement newBody = defaultPointerFieldsInStatement(w.body());
            return new WhileStatement(w.condition(), newBody, w.line(), w.col(), w.leadingComments());
        }
        if (s instanceof DoWhileStatement dw) {
            Statement newBody = defaultPointerFieldsInStatement(dw.body());
            return new DoWhileStatement(newBody, dw.condition(), dw.line(), dw.col(), dw.leadingComments());
        }
        if (s instanceof SwitchStatement sw) {
            List<SwitchCase> newCases = new ArrayList<>(sw.cases().size());
            for (SwitchCase c : sw.cases()) {
                List<Statement> newCaseBody = new ArrayList<>(c.body().size());
                for (Statement st : c.body()) {
                    newCaseBody.add(defaultPointerFieldsInStatement(st));
                }
                newCases.add(new SwitchCase(c.matchValue(), newCaseBody));
            }
            return new SwitchStatement(sw.subject(), newCases, sw.line(), sw.col(), sw.leadingComments());
        }
        if (s instanceof TryStatement t) {
            Block newTryBlock = defaultPointerFieldsInBlock(t.tryBlock());
            List<CatchClause> newCatches = new ArrayList<>(t.catchClauses().size());
            for (CatchClause c : t.catchClauses()) {
                Block newCatchBody = c.body() != null ? defaultPointerFieldsInBlock(c.body()) : null;
                newCatches.add(new CatchClause(c.exceptionType(), c.varName(), newCatchBody, c.isCatchAll()));
            }
            return new TryStatement(newTryBlock, newCatches, t.line(), t.col(), t.leadingComments());
        }
        // ExprStatement, ReturnStatement, BreakStatement, ContinueStatement,
        // DeleteStatement: no declarator inside any of these can be a
        // pointer-typed local declaration, nothing to rewrite.
        return s;
    }
}
