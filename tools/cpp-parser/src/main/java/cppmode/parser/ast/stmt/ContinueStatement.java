package cppmode.parser.ast.stmt;

import cppmode.parser.Token;

import java.util.List;

/** "continue;" -- skips to the next iteration of the nearest enclosing loop. */
public record ContinueStatement(
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
