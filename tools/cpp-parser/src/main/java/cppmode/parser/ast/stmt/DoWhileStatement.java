package cppmode.parser.ast.stmt;

import cppmode.parser.Token;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

/** "do body while (condition);". */
public record DoWhileStatement(
    Statement body,
    Expr condition,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
