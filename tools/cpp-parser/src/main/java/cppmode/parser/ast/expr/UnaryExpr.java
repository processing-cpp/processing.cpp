package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * A prefix unary operator expression: negation (-x), logical-not (!x),
 * address-of (&amp;x), bitwise-not (~x), and prefix increment/decrement
 * (++x, --x).
 *
 * Postfix increment/decrement (x++, x--) is a separate node, {@link PostfixExpr},
 * since it has different precedence/associativity and applies to the left rather
 * than wrapping to the right.
 */
public record UnaryExpr(
    String op,
    Expr operand,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
