package cppmode.parser.ast.expr;

import cppmode.parser.Token;
import cppmode.parser.ast.TypeRef;

import java.util.List;

/**
 * A C-style cast: "(TargetType)expr", e.g. "(int)production.length()".
 * Confirmed as common in the real corpus (appears repeatedly in LSystem).
 * Only the C-style parenthesized-type form is supported -- static_cast/
 * dynamic_cast/etc. are out of scope per the original grammar-scope decision.
 */
public record CastExpr(
    TypeRef targetType,
    Expr expr,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
