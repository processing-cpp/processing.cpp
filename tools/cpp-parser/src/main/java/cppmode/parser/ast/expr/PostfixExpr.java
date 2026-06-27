package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/** A postfix increment/decrement expression: "x++" or "x--". */
public record PostfixExpr(
    String op,
    Expr operand,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
