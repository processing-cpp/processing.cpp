package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * The ternary conditional: "cond ? thenExpr : elseExpr".
 * Confirmed by the kitchen-sink fixture to nest (a ? b : (c ? d : e)) and to
 * appear as a call argument and as an array index -- no special handling
 * needed for those positions beyond ordinary expression-grammar composition.
 */
public record TernaryExpr(
    Expr condition,
    Expr thenExpr,
    Expr elseExpr,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
