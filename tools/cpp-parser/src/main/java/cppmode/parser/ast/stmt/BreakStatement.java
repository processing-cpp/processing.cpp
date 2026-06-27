package cppmode.parser.ast.stmt;

import cppmode.parser.Token;

import java.util.List;

/** "break;" -- exits the nearest enclosing loop or switch. */
public record BreakStatement(
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
