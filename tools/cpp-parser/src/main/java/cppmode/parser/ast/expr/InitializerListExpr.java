package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * A brace-enclosed initializer list: "{1, 2, 3, 4, 5}", used as an array
 * initializer (confirmed by the corpus's "int initialized[5] = {1,2,3,4,5};"
 * fixture). Not yet confirmed for other contexts (e.g. aggregate
 * initialization of a struct) but the shape generalizes trivially if needed.
 */
public record InitializerListExpr(
    List<Expr> elements,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
