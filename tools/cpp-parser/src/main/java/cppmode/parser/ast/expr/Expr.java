package cppmode.parser.ast.expr;

import cppmode.parser.ast.Node;

/**
 * Root of the expression AST hierarchy. Every concrete expression node lives in
 * this package and implements this interface, in addition to {@link Node} for
 * position/comment tracking.
 *
 * Confirmed shapes (see project corpus-validation notes for the reasoning behind
 * each one -- this list is the consolidated result of walking LSystem, Mandelbrot,
 * Button, Handles, and two rounds of synthetic kitchen-sink fixtures):
 *
 *  Literal, Identifier, ScopedName, BinaryExpr, UnaryExpr, PostfixExpr,
 *  AssignExpr, CallExpr, MemberAccessExpr, IndexExpr, CastExpr, TernaryExpr,
 *  NewExpr, ArrayNewExpr, LambdaExpr, InitializerListExpr
 */
public sealed interface Expr extends Node
    permits Literal, Identifier, ScopedName, BinaryExpr, UnaryExpr, PostfixExpr,
            AssignExpr, CallExpr, MemberAccessExpr, IndexExpr, CastExpr,
            TernaryExpr, NewExpr, ArrayNewExpr, LambdaExpr, InitializerListExpr {
}
