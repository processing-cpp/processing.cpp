package cppmode.parser.ast;

import cppmode.parser.ast.expr.Expr;

/**
 * A single function/lambda parameter: type, name, and an optional default
 * value. Confirmed necessary by the corpus's withDefaultParam fixture
 * ("void withDefaultParam(int x, int y = 5)") -- defaultValue is null when
 * absent.
 *
 * Shared between FunctionDecl and LambdaExpr since both need identical shape;
 * confirmed by corpus that lambda parameter lists ("[](int n) { ... }") use
 * the same grammar as ordinary function parameter lists, just without default
 * values being exercised in practice (though nothing in the grammar forbids
 * them on a lambda either).
 */
public record Param(TypeRef type, String name, Expr defaultValue) {
}
