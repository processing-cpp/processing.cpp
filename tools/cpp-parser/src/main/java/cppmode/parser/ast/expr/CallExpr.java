package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * A function call: "callee(args...)".
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
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
