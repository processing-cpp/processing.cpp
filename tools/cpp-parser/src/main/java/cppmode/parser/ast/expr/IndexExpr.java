package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * Array/container subscript: "target[index]".
 *
 * Covers both real array indexing and std::string character indexing
 * uniformly -- the corpus showed "production[i]" (string char access) and
 * "pixels[i + j * width]" (array access, also confirmed valid as an
 * AssignExpr target) using identical syntax. Disambiguating "is this a
 * string or an array" is a type-resolution concern for a later pass, not
 * something the grammar needs to know.
 */
public record IndexExpr(
    Expr target,
    Expr index,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
