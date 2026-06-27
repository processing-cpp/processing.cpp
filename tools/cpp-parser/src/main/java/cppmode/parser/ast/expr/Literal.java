package cppmode.parser.ast.expr;

import cppmode.parser.Token;

import java.util.List;

/**
 * A literal value: int, float, string, char, or bool.
 *
 * The raw lexer text is preserved verbatim in {@code text} (including quotes for
 * strings, the leading 0x for hex, suffixes like f/L/u, escape sequences as
 * written) rather than being eagerly converted to a Java int/double/etc. Parsing
 * the actual numeric/string value is a later concern (codegen just re-emits the
 * text; a semantic pass that needs the real value can parse {@code text} itself).
 * This avoids the parser making lossy decisions about e.g. whether "0xFF" should
 * become an int or a long -- that's not the parser's call to make.
 */
public record Literal(
    Kind kind,
    String text,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {

    public enum Kind { INT, FLOAT, STRING, CHAR, BOOL }
}
