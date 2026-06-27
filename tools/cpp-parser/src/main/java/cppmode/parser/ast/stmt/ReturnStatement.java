package cppmode.parser.ast.stmt;

import cppmode.parser.Token;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

/**
 * "return expr;" or bare "return;" (value is null for the latter -- needed by
 * void-returning functions/methods, e.g. early-return patterns not yet seen
 * in the corpus but required by the grammar's own completeness regardless).
 */
public record ReturnStatement(
    Expr value,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
