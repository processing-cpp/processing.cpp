package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * Plain "=" assignment. Right-associative and usable as the right-hand side of
 * another AssignExpr -- confirmed necessary by the corpus's chained-assignment
 * case ("circleOver = rectOver = false;").
 *
 * {@code target} may be an Identifier, a MemberAccessExpr, or an IndexExpr --
 * confirmed by the corpus that array-index ("pixels[i + j * width] = ...") and
 * (implicitly) member-access targets both need to be valid assignment LHS forms,
 * not just plain identifiers. The parser does not restrict which Expr subtype
 * appears here at parse time; rejecting non-lvalue targets (e.g. assigning to a
 * literal) is left to a later semantic check if one is ever needed, not encoded
 * in the grammar itself.
 */
public record AssignExpr(
    Expr target,
    Expr value,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
