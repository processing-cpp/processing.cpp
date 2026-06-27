package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * Member access via "." or "->": "object.member" or "pointer->member".
 *
 * Confirmed by the corpus to need an explicit isArrow flag (rather than two
 * separate node types) since both forms compose identically in every other
 * respect, including chaining arbitrarily ("others->get(i)->locked",
 * "handles.get(i)->releaseEvent()") and mixing dot/arrow within one chain.
 */
public record MemberAccessExpr(
    Expr target,
    String memberName,
    boolean isArrow,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
