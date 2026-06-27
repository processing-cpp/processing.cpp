package cppmode.parser.ast.stmt;

import cppmode.parser.ast.Node;

/**
 * Root of the statement AST hierarchy. Node set confirmed by walking the full
 * test corpus (LSystem, Mandelbrot, Button, Handles, plus two synthetic
 * kitchen-sink batches covering control flow, arrays, OOP features, lambdas,
 * templates, function pointers, and namespaces).
 *
 * Note on if/else-if chains: there is no separate "ElseIfStatement" -- an
 * "else if (...)" is just an IfStatement whose elseBranch is itself another
 * IfStatement, the standard recursive-descent representation. Arbitrary-length
 * chains (confirmed up to 5-way in the LSystem render() method) fall out of
 * this for free with no special grammar rule.
 */
public sealed interface Statement extends Node
    permits ExprStatement, DeclStatement, Block, IfStatement, ForStatement,
            RangeForStatement, WhileStatement, DoWhileStatement, SwitchStatement,
            ReturnStatement, BreakStatement, ContinueStatement, TryStatement,
            DeleteStatement {
}
