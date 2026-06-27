package cppmode.parser.ast.stmt;

import cppmode.parser.ast.expr.Expr;

import java.util.List;

/**
 * A single "case" (or "default") clause within a switch statement.
 *
 * @param matchValue  the case's constant expression (e.g. a Literal); null for
 *                     the "default:" clause
 * @param body        the statements under this case, up to (but not including)
 *                     the next case/default/closing brace. Fallthrough (a case
 *                     with no "break;" at the end of its body) is simply
 *                     represented as-is -- confirmed by the corpus's
 *                     switchOnIntWithFallthrough fixture, where case 1 falls
 *                     into case 2 with no special node needed; the absence of
 *                     a trailing BreakStatement in body is itself the
 *                     fallthrough signal for any later pass that cares.
 */
public record SwitchCase(Expr matchValue, List<cppmode.parser.ast.stmt.Statement> body) {
}
