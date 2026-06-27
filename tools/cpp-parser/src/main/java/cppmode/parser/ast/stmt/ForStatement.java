package cppmode.parser.ast.stmt;

import cppmode.parser.Token;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

/**
 * The classic 3-part for loop: "for (init; condition; update) body".
 *
 * init is a Statement (typically a DeclStatement, e.g. "int i = 0", or an
 * ExprStatement) rather than a bare Expr, since the corpus shows the init
 * clause is itself a declaration in every real occurrence -- modeling it as
 * Statement keeps this uniform with how declarations are represented
 * everywhere else rather than inventing a separate inline-decl shape just
 * for this one grammar position. update is a plain Expr (e.g. "i++"),
 * matched against the corpus's exclusively-expression update clauses.
 *
 * Distinct from {@link RangeForStatement} ("for (auto&amp; x : items)"), a
 * separate grammar form confirmed present in the corpus's synthetic fixtures.
 */
public record ForStatement(
    Statement init,
    Expr condition,
    Expr update,
    Statement body,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
