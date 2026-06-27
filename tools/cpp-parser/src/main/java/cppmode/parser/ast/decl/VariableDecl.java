package cppmode.parser.ast.decl;

import cppmode.parser.Token;
import cppmode.parser.ast.TypeRef;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

/**
 * A top-level or class-field variable declaration, e.g. "int steps = 0;",
 * "std::string axiom;", "PenroseSnowflakeLSystem ps;", "static int count;".
 *
 * Per the multi-declarator desugaring decision (see DeclStatement's notes,
 * which apply identically here): "int rectX, rectY;" and "color rectColor,
 * circleColor, baseColor;" both desugar into multiple single-name
 * VariableDecl nodes at parse time, sharing one TypeRef each, confirmed
 * necessary at both top-level scope (Button fixture) and inside a class
 * body (Handles fixture's Handle class fields).
 *
 * isConst/isStatic confirmed needed by the type_system kitchen-sink batch
 * ("const int LIMIT = 100;", "static int count;").
 */
public record VariableDecl(
    TypeRef type,
    String name,
    List<Expr> arrayDims,
    Expr initializer,
    boolean isConst,
    boolean isStatic,
    int line,
    int col,
    List<Token> leadingComments
) implements TopLevelItem {
}
