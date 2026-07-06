package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * A function call: "callee(args...)", or a brace-init construction when
 * isBraceInit is true: "callee{args...}" (e.g. "Rect<float>{x, y, w, h}",
 * a templated type's aggregate/brace initialization -- found via a real,
 * sophisticated sketch using "new QuadNode(Rect<float>{...})"). Reusing
 * this record with a boolean flag, rather than adding a new node type,
 * follows this project's own established precedent of preferring a flag
 * over a new node for closely-related call shapes (see the note below on
 * ConstructExpr having already been considered and rejected for a
 * similar reason).
 *
 * Deliberately also covers the "bare construction call" pattern confirmed in
 * the corpus -- "ps = PenroseSnowflakeLSystem();" and
 * "handles = ArrayList&lt;Handle&gt;();" both parse as an ordinary CallExpr whose
 * callee happens to be a type name rather than a function name. The parser
 * cannot tell these apart without a symbol table (a free function and a
 * same-named type constructor are syntactically identical at this stage), so
 * disambiguating "is this a real call or a construction" is left to a later
 * semantic pass, not encoded as a separate node here. See project notes on why
 * a dedicated ConstructExpr node was considered and rejected.
 */
public record CallExpr(
    Expr callee,
    List<Expr> args,
    boolean isBraceInit,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
    /** Convenience constructor for the common paren-call case (isBraceInit=false). */
    public CallExpr(Expr callee, List<Expr> args, int line, int col, List<Token> leadingComments) {
        this(callee, args, false, line, col, leadingComments);
    }
}
