package cppmode.parser.ast.stmt;

import cppmode.parser.Token;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

/** "while (condition) body". */
public record WhileStatement(
    Expr condition,
    Statement body,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
