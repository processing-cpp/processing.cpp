package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/** A bare identifier reference, e.g. "x", "drawLength", "production". */
public record Identifier(
    String name,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
