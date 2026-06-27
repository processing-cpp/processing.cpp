package cppmode.parser.ast.stmt;

import cppmode.parser.Token;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

/**
 * "switch (subject) { case ...: ...; case ...: ...; default: ...; }".
 * Confirmed by the corpus to work uniformly whether switching on an int or a
 * char subject -- the grammar doesn't need to know the subject's type, that's
 * purely a later type-checking concern.
 */
public record SwitchStatement(
    Expr subject,
    List<SwitchCase> cases,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
