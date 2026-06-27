package cppmode.parser;

public enum TokenType {
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
