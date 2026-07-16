package processing.mode.cpp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tokenizes CppMode source text (Java-flavored C++) into a flat list of {@link CppLexerToken}s.
 *
 * Design notes (see project notes for full rationale):
 *  - Comments are emitted as first-class tokens, not discarded. This lets the parser
 *    attach leading comments to the AST node they precede, and means comment *content*
 *    is never mistaken for code by a later regex-style pass (the historical bug class
 *    this parser exists to eliminate).
 *  - Whitespace (including newlines) is discarded after updating line/col tracking.
 *    Statement termination is via ';' and brace nesting, not newlines -- confirmed
 *    necessary by real corpus code that wraps a boolean condition across lines inside
 *    parens.
 *  - Multi-character operators are matched longest-first.
 */
public final class CppLexer {

    private final String src;
    private final int len;
    private int pos = 0;
    private int line = 1;
    private int col = 1;

    private static final Set<String> KEYWORDS = Set.of(
        "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor",
        "bool", "break", "case", "catch", "char", "char16_t", "char32_t", "class",
        "compl", "const", "consteval", "constinit", "constexpr", "const_cast", "continue", "decltype",
        "default", "delete", "do", "double", "dynamic_cast", "else", "enum",
        "explicit", "export", "extern", "false", "float", "for", "friend", "goto",
        "if", "inline", "int", "long", "mutable", "namespace", "new", "noexcept",
        "not", "not_eq", "nullptr", "operator", "or", "or_eq", "override",
        "private", "protected", "public", "register", "reinterpret_cast",
        "return", "short", "signed", "sizeof", "static", "static_assert",
        "static_cast", "struct", "switch", "template", "this", "thread_local",
        "throw", "true", "try", "typedef", "typeid", "typename", "union",
        "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "while",
        "xor", "xor_eq",
        // Processing-flavored / CppMode tolerated extras that behave like keywords
        // in the type-name position. These are not C++ keywords, but are tokenized
        // the same way (IDENTIFIER would work too -- see Parser for how it decides
        // "is this a type-name token", which does not depend on KEYWORD vs IDENTIFIER).
        "color"
    );

    // Multi-character operator/punctuation sequences, longest first within each
    // starting character so the greedy scan below naturally prefers them.
    private static final String[] MULTI_CHAR_SYMBOLS = {
        "<<=", ">>=", "...", "->*",
        "::", "->", "++", "--", "<<", ">>", "<=", ">=", "==", "!=",
        "&&", "||", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
    };

    private static final Set<Character> SINGLE_CHAR_SYMBOLS = Set.of(
        '{', '}', '(', ')', '[', ']', ';', ',', '.', ':', '?',
        '+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~'
    );

    public CppLexer(String src) {
        this.src = src;
        this.len = src.length();
    }

    public List<CppLexerToken> tokenize() {
        List<CppLexerToken> tokens = new ArrayList<>();
        CppLexerToken t;
        do {
            t = nextToken();
            tokens.add(t);
        } while (t.type() != CppLexerTokenType.EOF);
        return tokens;
    }

    private char peekChar() {
        return pos < len ? src.charAt(pos) : '\0';
    }

    private char peekChar(int ahead) {
        int p = pos + ahead;
        return p < len ? src.charAt(p) : '\0';
    }

    private char advanceChar() {
        char c = src.charAt(pos++);
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return c;
    }

    private CppLexerToken nextToken() {
        skipWhitespace();

        if (pos >= len) {
            return new CppLexerToken(CppLexerTokenType.EOF, "", line, col);
        }

        int startLine = line;
        int startCol = col;
        char c = peekChar();

        if (c == '#') {
            return lexPreprocessorDirective(startLine, startCol);
        }
        if (c == '/' && peekChar(1) == '/') {
            return lexLineComment(startLine, startCol);
        }
        if (c == '/' && peekChar(1) == '*') {
            return lexBlockComment(startLine, startCol);
        }
        if (c == '"') {
            return lexStringLiteral(startLine, startCol);
        }
        if (c == '\'') {
            return lexCharLiteral(startLine, startCol);
        }
        if (Character.isDigit(c) || (c == '.' && Character.isDigit(peekChar(1)))) {
            return lexNumericLiteral(startLine, startCol);
        }
        if (Character.isLetter(c) || c == '_') {
            return lexIdentifierOrKeyword(startLine, startCol);
        }

        return lexSymbol(startLine, startCol);
    }

    private void skipWhitespace() {
        while (pos < len && Character.isWhitespace(peekChar())) {
            advanceChar();
        }
    }

    private CppLexerToken lexPreprocessorDirective(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        sb.append(advanceChar()); // '#'
        while (pos < len) {
            if (peekChar() == '\\' && peekChar(1) == '\n') {
                // line continuation -- consume backslash+newline, keep going
                sb.append(advanceChar());
                sb.append(advanceChar());
                continue;
            }
            if (peekChar() == '\n') break;
            sb.append(advanceChar());
        }
        return new CppLexerToken(CppLexerTokenType.PREPROCESSOR_DIRECTIVE, sb.toString(), startLine, startCol);
    }

    private CppLexerToken lexLineComment(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        // consume "//"
        sb.append(advanceChar());
        sb.append(advanceChar());
        while (pos < len && peekChar() != '\n') {
            sb.append(advanceChar());
        }
        return new CppLexerToken(CppLexerTokenType.LINE_COMMENT, sb.toString(), startLine, startCol);
    }

    private CppLexerToken lexBlockComment(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        sb.append(advanceChar()); // /
        sb.append(advanceChar()); // *
        while (pos < len) {
            if (peekChar() == '*' && peekChar(1) == '/') {
                sb.append(advanceChar());
                sb.append(advanceChar());
                break;
            }
            sb.append(advanceChar());
        }
        // Note: unterminated block comment falls through to EOF without error here;
        // the parser will simply hit EOF mid-construct and report a clear parse
        // error at that point (fail-fast policy, see project notes).
        return new CppLexerToken(CppLexerTokenType.BLOCK_COMMENT, sb.toString(), startLine, startCol);
    }

    private CppLexerToken lexStringLiteral(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        sb.append(advanceChar()); // opening "
        while (pos < len && peekChar() != '"') {
            if (peekChar() == '\\' && pos + 1 < len) {
                sb.append(advanceChar()); // backslash
                sb.append(advanceChar()); // escaped char, whatever it is
            } else if (peekChar() == '\n') {
                // Unterminated string (hit end of line). Stop here; parser/caller
                // can report this as a lex error using the returned token's text.
                break;
            } else {
                sb.append(advanceChar());
            }
        }
        if (pos < len && peekChar() == '"') {
            sb.append(advanceChar()); // closing "
        }
        return new CppLexerToken(CppLexerTokenType.STRING_LITERAL, sb.toString(), startLine, startCol);
    }

    private CppLexerToken lexCharLiteral(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        sb.append(advanceChar()); // opening '
        while (pos < len && peekChar() != '\'') {
            if (peekChar() == '\\' && pos + 1 < len) {
                sb.append(advanceChar());
                sb.append(advanceChar());
            } else if (peekChar() == '\n') {
                break;
            } else {
                sb.append(advanceChar());
            }
        }
        if (pos < len && peekChar() == '\'') {
            sb.append(advanceChar());
        }
        return new CppLexerToken(CppLexerTokenType.CHAR_LITERAL, sb.toString(), startLine, startCol);
    }

    private CppLexerToken lexNumericLiteral(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();

        // Hex literal: 0x...
        if (peekChar() == '0' && (peekChar(1) == 'x' || peekChar(1) == 'X')) {
            sb.append(advanceChar());
            sb.append(advanceChar());
            while (pos < len && isHexDigit(peekChar())) {
                sb.append(advanceChar());
            }
            consumeIntSuffix(sb);
            return new CppLexerToken(CppLexerTokenType.INT_LITERAL, sb.toString(), startLine, startCol);
        }
        // Binary literal: 0b...
        if (peekChar() == '0' && (peekChar(1) == 'b' || peekChar(1) == 'B')) {
            sb.append(advanceChar());
            sb.append(advanceChar());
            while (pos < len && (peekChar() == '0' || peekChar() == '1' || peekChar() == '_')) {
                sb.append(advanceChar());
            }
            consumeIntSuffix(sb);
            return new CppLexerToken(CppLexerTokenType.INT_LITERAL, sb.toString(), startLine, startCol);
        }

        boolean isFloat = false;
        while (pos < len && Character.isDigit(peekChar())) {
            sb.append(advanceChar());
        }
        if (pos < len && peekChar() == '.' && Character.isDigit(peekChar(1))) {
            isFloat = true;
            sb.append(advanceChar()); // .
            while (pos < len && Character.isDigit(peekChar())) {
                sb.append(advanceChar());
            }
        } else if (pos < len && peekChar() == '.' && !Character.isLetter(peekChar(1))) {
            // trailing dot like "4." -- still a float literal
            isFloat = true;
            sb.append(advanceChar());
        }
        // exponent
        if (pos < len && (peekChar() == 'e' || peekChar() == 'E')) {
            int save = pos, saveLine = line, saveCol = col;
            StringBuilder expPart = new StringBuilder();
            expPart.append(advanceChar());
            if (pos < len && (peekChar() == '+' || peekChar() == '-')) {
                expPart.append(advanceChar());
            }
            if (pos < len && Character.isDigit(peekChar())) {
                isFloat = true;
                while (pos < len && Character.isDigit(peekChar())) {
                    expPart.append(advanceChar());
                }
                sb.append(expPart);
            } else {
                // not actually an exponent (e.g. identifier starting with 'e' follows);
                // rewind
                pos = save; line = saveLine; col = saveCol;
            }
        }

        if (isFloat) {
            // float suffix: f, F, l, L
            if (pos < len && (peekChar() == 'f' || peekChar() == 'F'
                            || peekChar() == 'l' || peekChar() == 'L')) {
                sb.append(advanceChar());
            }
            return new CppLexerToken(CppLexerTokenType.FLOAT_LITERAL, sb.toString(), startLine, startCol);
        } else {
            consumeIntSuffix(sb);
            return new CppLexerToken(CppLexerTokenType.INT_LITERAL, sb.toString(), startLine, startCol);
        }
    }

    private void consumeIntSuffix(StringBuilder sb) {
        // u, U, l, L, ll, LL in any reasonable combination/order, up to 3 chars
        int count = 0;
        while (pos < len && count < 3 && "uUlL".indexOf(peekChar()) >= 0) {
            sb.append(advanceChar());
            count++;
        }
    }

    private boolean isHexDigit(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private CppLexerToken lexIdentifierOrKeyword(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < len && (Character.isLetterOrDigit(peekChar()) || peekChar() == '_')) {
            sb.append(advanceChar());
        }
        String text = sb.toString();
        if (text.equals("true") || text.equals("false")) {
            return new CppLexerToken(CppLexerTokenType.BOOL_LITERAL, text, startLine, startCol);
        }
        if (KEYWORDS.contains(text)) {
            return new CppLexerToken(CppLexerTokenType.KEYWORD, text, startLine, startCol);
        }
        return new CppLexerToken(CppLexerTokenType.IDENTIFIER, text, startLine, startCol);
    }

    private CppLexerToken lexSymbol(int startLine, int startCol) {
        // Try multi-char symbols first (longest match within fixed candidate list).
        for (String sym : MULTI_CHAR_SYMBOLS) {
            if (matchesAhead(sym)) {
                for (int i = 0; i < sym.length(); i++) advanceChar();
                return new CppLexerToken(symbolTokenType(sym), sym, startLine, startCol);
            }
        }
        char c = peekChar();
        if (SINGLE_CHAR_SYMBOLS.contains(c)) {
            advanceChar();
            return new CppLexerToken(symbolTokenType(String.valueOf(c)), String.valueOf(c), startLine, startCol);
        }
        // Unknown character -- emit it as a single-char PUNCTUATION token rather than
        // throwing, so the parser (not the lexer) is the place that reports a clean
        // "unexpected token" error with full context. Fail-fast happens one layer up.
        advanceChar();
        return new CppLexerToken(CppLexerTokenType.PUNCTUATION, String.valueOf(c), startLine, startCol);
    }

    private boolean matchesAhead(String sym) {
        if (pos + sym.length() > len) return false;
        for (int i = 0; i < sym.length(); i++) {
            if (src.charAt(pos + i) != sym.charAt(i)) return false;
        }
        return true;
    }

    private static final Set<String> PUNCT_SYMBOLS = Set.of(
        "{", "}", "(", ")", "[", "]", ";", ",", ".", "::", "->", "?", ":", "..."
    );

    private CppLexerTokenType symbolTokenType(String sym) {
        return PUNCT_SYMBOLS.contains(sym) ? CppLexerTokenType.PUNCTUATION : CppLexerTokenType.OPERATOR;
    }
}
