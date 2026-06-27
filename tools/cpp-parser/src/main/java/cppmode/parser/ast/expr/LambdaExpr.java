package cppmode.parser.ast.expr;

import cppmode.parser.Token;
import cppmode.parser.ast.Param;
import cppmode.parser.ast.TypeRef;
import cppmode.parser.ast.stmt.Block;

import java.util.List;

/**
 * A lambda expression: "[captures](params) [-> ReturnType] { body }".
 *
 * returnType is null when the trailing "-> Type" is omitted (the common case --
 * confirmed by the corpus that most lambdas skip it and rely on return-type
 * deduction; the parser doesn't perform deduction itself, it simply records
 * "no explicit return type was written").
 */
public record LambdaExpr(
    List<Capture> captures,
    List<Param> params,
    TypeRef returnType,
    Block body,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
