package cppmode.parser.passes;

import cppmode.parser.ast.Node;
import cppmode.parser.ast.Param;
import cppmode.parser.ast.decl.EnumDecl;
import cppmode.parser.ast.decl.FunctionDecl;
import cppmode.parser.ast.decl.NamespaceDecl;
import cppmode.parser.ast.decl.PreprocessorLine;
import cppmode.parser.ast.decl.TopLevelItem;
import cppmode.parser.ast.decl.TopLevelStatement;
import cppmode.parser.ast.decl.TypeDef;
import cppmode.parser.ast.decl.UsingNamespaceDecl;
import cppmode.parser.ast.decl.VariableDecl;
import cppmode.parser.ast.expr.ArrayNewExpr;
import cppmode.parser.ast.expr.AssignExpr;
import cppmode.parser.ast.expr.BinaryExpr;
import cppmode.parser.ast.expr.CallExpr;
import cppmode.parser.ast.expr.CastExpr;
import cppmode.parser.ast.expr.Expr;
import cppmode.parser.ast.expr.Identifier;
import cppmode.parser.ast.expr.IndexExpr;
import cppmode.parser.ast.expr.InitializerListExpr;
import cppmode.parser.ast.expr.LambdaExpr;
import cppmode.parser.ast.expr.Literal;
import cppmode.parser.ast.expr.MemberAccessExpr;
import cppmode.parser.ast.expr.NewExpr;
import cppmode.parser.ast.expr.PostfixExpr;
import cppmode.parser.ast.expr.ScopedName;
import cppmode.parser.ast.expr.TernaryExpr;
import cppmode.parser.ast.expr.UnaryExpr;
import cppmode.parser.ast.stmt.Block;
import cppmode.parser.ast.stmt.BreakStatement;
import cppmode.parser.ast.stmt.CatchClause;
import cppmode.parser.ast.stmt.ContinueStatement;
import cppmode.parser.ast.stmt.DeclStatement;
import cppmode.parser.ast.stmt.DeleteStatement;
import cppmode.parser.ast.stmt.DoWhileStatement;
import cppmode.parser.ast.stmt.ExprStatement;
import cppmode.parser.ast.stmt.ForStatement;
import cppmode.parser.ast.stmt.IfStatement;
import cppmode.parser.ast.stmt.RangeForStatement;
import cppmode.parser.ast.stmt.ReturnStatement;
import cppmode.parser.ast.stmt.Statement;
import cppmode.parser.ast.stmt.SwitchCase;
import cppmode.parser.ast.stmt.SwitchStatement;
import cppmode.parser.ast.stmt.TryStatement;
import cppmode.parser.ast.stmt.WhileStatement;

/**
 * Walks an arbitrary AST subtree looking for references to a given name.
 * Ported from a parallel implementation's NameUsageScanner (package
 * processing.mode.cpp), retargeted onto this codebase's AST shapes.
 * Originally written using exhaustive pattern-matching switches over the
 * sealed Expr/Statement/TopLevelItem hierarchies (which the Java compiler
 * verifies exhaustively, unlike a plain if-instanceof chain), but
 * rewritten as a plain if-instanceof chain after the real processing4
 * Gradle build rejected switch pattern matching as a disabled preview
 * feature ("patterns in switch statements are a preview feature and are
 * disabled by default") -- changing Gradle's own configuration to enable
 * a language preview feature for this one file was judged more invasive
 * than just not using the feature. Every branch's logic below is
 * IDENTICAL to the original switch version; only the dispatch mechanism
 * changed. Confirmed behaviorally identical by rerunning this codebase's
 * full fixture and real-corpus test suites after the rewrite -- same
 * hoisted-class/variable/array/function counts on every fixture as
 * before the change.
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
public final class NameUsageScanner {
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
