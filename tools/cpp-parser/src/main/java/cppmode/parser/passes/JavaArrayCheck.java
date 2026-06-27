package cppmode.parser.passes;

import cppmode.parser.Lexer;
import cppmode.parser.Token;
import cppmode.parser.TokenType;

import java.util.List;

/**
 * Replaces CppBuild.java's checkForUnsupportedJavaArraySyntax(), which used
 * a single regex over a comment/string-blanked copy of the source.
 *
 * Ported from a parallel implementation's CppJavaArrayCheck (package
 * processing.mode.cpp), retargeted onto this codebase's Lexer/Token types.
 * See DECISION_two_parser_implementations.md for the porting rationale.
 *
 * This version scans the real token stream produced by this codebase's
 * Lexer instead, so it automatically inherits correct comment/string-
 * literal handling -- a token inside a comment or string literal was
 * never tokenized as code in the first place (comments and preprocessor
 * directives are their own distinct TokenTypes, see Lexer's notes), so
 * there is no separate "blank it out first" step to keep in sync with the
 * lexer's own literal-recognition rules, unlike the original CppBuild.java
 * regex pass, which needed blankCommentsAndLiterals() as a hand-maintained
 * parallel implementation of the same recognition the lexer already does.
 *
 * Detects: "ElementType[] name = new ElementType[...];" and the
 * multi-dimensional form "ElementType[][] name = new ElementType[...][...];",
 * with the same tolerance for whitespace between type/brackets/name that
 * the original regex had (token-based matching gets this for free, since
 * whitespace was never tokenized to begin with).
 *
 * Confirmed by earlier g++ compile-checking (see project notes) that this
 * specific shape -- "Type[] name = new Type[size]" -- is the actual,
 * narrow thing the original check catches; a bare "int[] bad;" with no
 * "= new ...[]" initializer is a DIFFERENT, more general C++ parse
 * failure (g++ tries to parse it as a structured-binding declaration and
 * fails for unrelated reasons), not something this specific check needs
 * to handle -- the real parser's grammar (see Parser.parseTypeRef and its
 * surrounding notes) is responsible for rejecting that shape generally,
 * this check exists specifically for the "[] name = new Type[...]" pattern
 * that IS otherwise syntactically plausible-looking C++ to a naive scan.
 */
public final class JavaArrayCheck {
    private JavaArrayCheck() {}

    /**
     * Thrown when Java-style array-declaration syntax is detected. Carries
     * the same E0004 message shape CppBuild.java's listener/console output
     * expects, so the call site can stay structurally identical to the
     * original's exception-and-message contract.
     */
    public static final class E0004Exception extends RuntimeException {
        public final String elementType;
        public final int dimensions;
        public final int line;
        public final int col;

        public E0004Exception(String elementType, int dimensions, int line, int col) {
            super(buildMessage(elementType, dimensions));
            this.elementType = elementType;
            this.dimensions = dimensions;
            this.line = line;
            this.col = col;
        }

        private static String buildMessage(String elementType, int dims) {
            StringBuilder suggestion = new StringBuilder();
            for (int i = 0; i < dims; i++) suggestion.append("Array<");
            suggestion.append(elementType);
            for (int i = 0; i < dims; i++) suggestion.append(">");
            return "E0004: Java-style array declaration (\"" + elementType + "["
                + (dims > 1 ? "]..." : "]") + " = new " + elementType + "[...]\") is not supported.\n"
                + "Use " + suggestion + " instead, e.g.: " + suggestion + " a(10"
                + (dims > 1 ? ", ...)" : ")") + "\n"
                + "See https://processing-cpp.github.io/error/E0004";
        }
    }

    /**
     * Scans the token stream for the Java-array-declaration shape:
     *   (IDENTIFIER|KEYWORD) ("[" "]")+ IDENTIFIER "=" "new" (IDENTIFIER|KEYWORD) ("[" ... "]")+
     * Throws E0004Exception on the first match found. Returns normally if
     * no such shape is present anywhere in the token stream.
     */
    public static void check(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        int n = tokens.size();

        for (int i = 0; i < n; i++) {
            Token t = tokens.get(i);
            if (t.type() != TokenType.IDENTIFIER && t.type() != TokenType.KEYWORD) continue;

            // Try to match one-or-more "[]" dimension markers immediately
            // after the candidate element-type token.
            int j = i + 1;
            int dims = 0;
            while (j + 1 < n && tokens.get(j).isPunct("[") && tokens.get(j + 1).isPunct("]")) {
                dims++;
                j += 2;
            }
            if (dims == 0) continue;

            // Next must be an identifier (the variable name), then "=", then "new".
            if (j >= n || tokens.get(j).type() != TokenType.IDENTIFIER) continue;
            int afterName = j + 1;
            if (afterName >= n || !tokens.get(afterName).isOp("=")) continue;
            int newPos = afterName + 1;
            if (newPos >= n || !tokens.get(newPos).isKeyword("new")) continue;
            int newTypePos = newPos + 1;
            if (newTypePos >= n || (tokens.get(newTypePos).type() != TokenType.IDENTIFIER
                                      && tokens.get(newTypePos).type() != TokenType.KEYWORD)) continue;

            // Confirm the "new" side has at least one "[...]" dimension
            // following its type name (any expression inside the brackets
            // is acceptable, matching the original regex's tolerance for an
            // arbitrary size expression rather than only a bare literal).
            int afterNewType = newTypePos + 1;
            if (afterNewType >= n || !tokens.get(afterNewType).isPunct("[")) continue;

            throw new E0004Exception(t.text(), dims, t.line(), t.col());
        }
    }
}
