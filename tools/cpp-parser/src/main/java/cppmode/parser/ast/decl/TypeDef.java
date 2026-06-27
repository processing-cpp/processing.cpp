package cppmode.parser.ast.decl;

import cppmode.parser.Token;

import java.util.List;

/**
 * A class or struct declaration: "class Name [: public Base1, public Base2] { members }"
 * or "struct Name { members }".
 *
 * kind distinguishes "class" vs "struct" textually -- confirmed by the corpus
 * that both forms are used (LSystem/Handle as class, Point/Counter as struct)
 * but behave identically in every other respect for parsing purposes, so one
 * node with a kind field is sufficient (no separate ClassDecl/StructDecl).
 *
 * baseClasses is a List, not a nullable single name -- confirmed necessary by
 * the multiple-inheritance fixture ("class Sprite : public Movable, public
 * Drawable"). Empty list for no inheritance.
 *
 * templateParams is empty for non-template types; confirmed necessary by
 * "template&lt;typename T&gt; class Box" and the two-parameter
 * "template&lt;typename K, typename V&gt; class Pair" form.
 */
public record TypeDef(
    String kind,           // "class" or "struct"
    String name,
    List<String> templateParams,
    List<String> baseClasses,
    List<TopLevelItem> members,
    int line,
    int col,
    List<Token> leadingComments
) implements TopLevelItem {
}
