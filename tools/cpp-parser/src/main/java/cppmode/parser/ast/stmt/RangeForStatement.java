package cppmode.parser.ast.stmt;

import cppmode.parser.Token;
import cppmode.parser.ast.TypeRef;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

/**
 * Range-based for loop: "for (Type&amp; name : iterableExpr) body", e.g.
 * "for (auto&amp; item : items) { ... }". Confirmed as a distinct grammar form
 * from the classic 3-part ForStatement by the synthetic kitchen-sink fixture.
 */
public record RangeForStatement(
    TypeRef declType,
    String declName,
    boolean isReference,
    Expr iterableExpr,
    Statement body,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
