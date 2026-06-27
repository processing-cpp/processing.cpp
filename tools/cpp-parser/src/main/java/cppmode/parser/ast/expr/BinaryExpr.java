package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * A binary operator expression: arithmetic (+ - * / %), comparison
 * (== != &lt; &gt; &lt;= &gt;=), logical (&amp;&amp; ||), bitwise (&amp; | ^ &lt;&lt; &gt;&gt;),
 * and compound-assignment (+= -= *= /= %= &amp;= |= ^= &lt;&lt;= &gt;&gt;=) all share this
 * shape -- the operator is carried as raw text rather than an enum, since the
 * grammar treats them identically (same precedence-climbing parse logic) and an
 * enum would need a near-1:1 mirror of every operator string with no real
 * structural benefit.
 *
 * Plain "=" assignment is intentionally NOT here -- see {@link AssignExpr},
 * which is its own node because assignment is right-associative and can itself
 * appear as the right-hand side of another assignment (confirmed by the corpus's
 * "circleOver = rectOver = false;" chained-assignment case), a property compound
 * operators don't need to share.
 */
public record BinaryExpr(
    String op,
    Expr left,
    Expr right,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
