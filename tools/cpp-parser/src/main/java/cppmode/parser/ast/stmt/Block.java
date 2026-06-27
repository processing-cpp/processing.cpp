package cppmode.parser.ast.stmt;

import cppmode.parser.Token;

import java.util.List;

/** A brace-delimited sequence of statements: "{ stmt; stmt; ... }". */
public record Block(
    List<Statement> statements,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
