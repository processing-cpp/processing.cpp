package cppmode.parser.ast.stmt;

import cppmode.parser.Token;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

/**
 * "delete expr;" or "delete[] expr;" -- isArray distinguishes the two forms,
 * both confirmed present in the corpus (arrayNewAndDelete / plainNewAndDelete
 * fixtures). Modeled as a statement rather than an expression since "delete"
 * is always used as a bare statement in every occurrence seen, and C++ itself
 * treats it as an expression of type void in a context that's only ever used
 * statement-like in practice -- no corpus evidence of "delete" appearing
 * nested inside another expression, so the simpler statement-only shape is
 * used unless a counterexample shows up.
 */
public record DeleteStatement(
    Expr target,
    boolean isArray,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
