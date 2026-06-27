package cppmode.parser.ast.decl;

import cppmode.parser.Token;

import java.util.List;

/**
 * "enum Name { VALUE1, VALUE2, ... };" (isScoped=false) or
 * "enum class Name { VALUE1, VALUE2 };" (isScoped=true).
 *
 * isScoped is a real semantic difference, not just cosmetic -- confirmed by
 * the corpus that unscoped enum values are usable bare ("Color c = RED;")
 * while scoped enum values require qualification ("Direction d =
 * Direction::UP;"). A later name-resolution pass needs this flag to know
 * whether an enum's values pollute the enclosing scope or not; the parser
 * itself doesn't enforce the distinction, it just records which form was
 * written.
 */
public record EnumDecl(
    String name,
    boolean isScoped,
    List<String> values,
    int line,
    int col,
    List<Token> leadingComments
) implements TopLevelItem {
}
