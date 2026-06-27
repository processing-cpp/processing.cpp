package cppmode.parser;

/**
 * A single lexical token.
 *
 * @param type   the token's category
 * @param text   the raw source text of the token (for STRING_LITERAL/CHAR_LITERAL,
 *               this includes the surrounding quotes and escape sequences as written;
 *               for COMMENT tokens, this includes the comment markers themselves)
 * @param line   1-indexed line number where the token starts
 * @param col    1-indexed column number where the token starts
 */
public record Token(TokenType type, String text, int line, int col) {

    public boolean isKeyword(String kw) {
        return type == TokenType.KEYWORD && text.equals(kw);
    }

    public boolean isPunct(String p) {
        return type == TokenType.PUNCTUATION && text.equals(p);
    }

    public boolean isOp(String op) {
        return type == TokenType.OPERATOR && text.equals(op);
    }

    public boolean isComment() {
        return type == TokenType.LINE_COMMENT || type == TokenType.BLOCK_COMMENT;
    }

    @Override
    public String toString() {
        return type + "('" + text + "' @ " + line + ":" + col + ")";
    }
}
