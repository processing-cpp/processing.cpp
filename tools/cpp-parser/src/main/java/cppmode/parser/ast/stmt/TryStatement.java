package cppmode.parser.ast.stmt;

import cppmode.parser.Token;

import java.util.List;

/**
 * "try { ... } catch (...) { ... } [catch (...) { ... } ...]".
 * Pass-through parsing only, per the original grammar-scope decision --
 * the parser doesn't attempt to model exception-handling semantics.
 */
public record TryStatement(
    Block tryBlock,
    List<CatchClause> catchClauses,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
