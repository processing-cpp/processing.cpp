package cppmode.parser.ast.decl;

import cppmode.parser.Token;

import java.util.List;

/**
 * An opaque preprocessor directive line ("#include ...", "#define ...", etc.),
 * passed through verbatim per the original grammar-scope decision: the parser
 * never deeply parses preprocessor directives, only preserves their raw text
 * and position so codegen can re-emit them unchanged in the right place.
 */
public record PreprocessorLine(
    String rawText,
    int line,
    int col,
    List<Token> leadingComments
) implements TopLevelItem {
}
