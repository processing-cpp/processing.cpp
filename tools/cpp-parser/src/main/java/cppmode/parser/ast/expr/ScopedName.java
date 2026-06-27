package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * A "::"-qualified name, e.g. "std::string", "MyNamespace::someFunction",
 * "Direction::UP". Confirmed as its own node (rather than just baking "::" into
 * raw identifier text) since semantic passes care about namespace/scope
 * structure separately from a plain identifier reference -- e.g. resolving
 * "Direction::UP" needs to know "Direction" is the scope and "UP" is the member,
 * not just see one opaque token "Direction::UP".
 *
 * @param parts  the dot-free, "::"-separated segments in order, e.g.
 *               ["std", "string"] or ["MyNamespace", "someFunction"]
 */
public record ScopedName(
    List<String> parts,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {

    public String joined() {
        return String.join("::", parts);
    }
}
