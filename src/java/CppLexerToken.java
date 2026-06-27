package processing.mode.cpp;

/**
 * A single lexical token. Ported from tools/cpp-parser's Token.java --
 * see CppLexerTokenType's javadoc for why this is a flattened copy rather
 * than a shared dependency.
 */
public record CppLexerToken(CppLexerTokenType type, String text, int line, int col) {

    public boolean isKeyword(String kw) {
        return type == CppLexerTokenType.KEYWORD && text.equals(kw);
    }

    public boolean isPunct(String p) {
        return type == CppLexerTokenType.PUNCTUATION && text.equals(p);
    }

    public boolean isOp(String op) {
        return type == CppLexerTokenType.OPERATOR && text.equals(op);
    }

    public boolean isComment() {
        return type == CppLexerTokenType.LINE_COMMENT || type == CppLexerTokenType.BLOCK_COMMENT;
    }

    @Override
    public String toString() {
        return type + "('" + text + "' @ " + line + ":" + col + ")";
    }
}
