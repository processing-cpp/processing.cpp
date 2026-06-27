package cppmode.parser.ast.stmt;

import cppmode.parser.Token;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

/**
 * "if (condition) thenBranch [else elseBranch]".
 *
 * elseBranch is null when absent. An "else if (...)" chain is represented as
 * elseBranch being another IfStatement directly (no separate ElseIfStatement
 * node) -- confirmed sufficient for arbitrary-length chains by the LSystem
 * render() method's 5-way else-if chain.
 *
 * thenBranch/elseBranch are themselves Statement, which permits both the brace
 * form ("if (x) { ... }") and the single-statement-no-braces form
 * ("if (i % 2 == 0) continue;") confirmed present in the corpus -- both parse
 * to the same shape, just with elseBranch/thenBranch being a Block in one case
 * and a single non-Block Statement in the other.
 */
public record IfStatement(
    Expr condition,
    Statement thenBranch,
    Statement elseBranch,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
