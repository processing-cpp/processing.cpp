package processing.mode.cpp;

/**
 * Token categories produced by CppLexer. Ported from the standalone
 * cpp-parser project (tools/cpp-parser/src/main/java/cppmode/parser/TokenType.java)
 * -- flattened into this package specifically because the real build's
 * dev-symlink script (scripts/setup-dev-symlinks.sh) only links files
 * directly inside src/java/ into a single flat directory on the
 * processing4 side, with no subpackage support. The original, organized
 * multi-package source remains the source of truth in tools/cpp-parser/
 * and continues to be tested there; this file (and its siblings
 * CppLexerToken, CppLexer, CppJavaArrayCheck) are the flattened,
 * build-compatible copies actually wired into CppBuild.java.
 *
 * If you change the lexer's token grammar, change it in
 * tools/cpp-parser/ first, re-run its test suite, then re-flatten these
 * four files to match -- do not edit these in isolation, or the two
 * copies will drift.
 */
public enum CppLexerTokenType {
    IDENTIFIER,
    KEYWORD,

    INT_LITERAL,
    FLOAT_LITERAL,
    STRING_LITERAL,
    CHAR_LITERAL,
    BOOL_LITERAL,

    PUNCTUATION,   // { } ( ) [ ] ; , . -> :: ? :
    OPERATOR,      // + - * / % = == != < > <= >= && || ! & | ^ << >> += -= etc.

    LINE_COMMENT,  // // ...
    BLOCK_COMMENT, // /* ... */
    PREPROCESSOR_DIRECTIVE, // #include ..., #define ..., etc. -- passed through opaquely, never deeply parsed

    EOF
}
