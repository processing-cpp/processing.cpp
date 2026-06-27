package cppmode.parser.ast.expr;

import cppmode.parser.Token;
import cppmode.parser.ast.TypeRef;

import java.util.List;

/**
 * Heap allocation of a single object: "new Type(args...)", e.g.
 * "new Handle(width / 2, 10 + i * 15, ...)" or "new int(42)".
 *
 * Distinct from {@link ArrayNewExpr} ("new Type[size]") and from the bare
 * construction-call pattern handled by {@link CallExpr} (no "new" keyword,
 * e.g. "PenroseSnowflakeLSystem()") -- all three are different syntactic forms
 * confirmed present in the real corpus.
 */
public record NewExpr(
    TypeRef type,
    List<Expr> args,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
