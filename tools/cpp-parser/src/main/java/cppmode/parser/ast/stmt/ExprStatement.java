package cppmode.parser.ast.stmt;

import cppmode.parser.Token;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

/**
 * An expression used as a statement, terminated by ";": assignments
 * ("production = iterate(...);"), bare calls ("ps.render();"), increments
 * ("generations++;"), etc.
 */
public record ExprStatement(
    Expr expr,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
