package processing.mode.cpp;


import java.util.List;

/**
 * Replaces CppBuild.java's checkForUnsupportedJavaArraySyntax(), which used
 * a single regex over a comment/string-blanked copy of the source.
 *
 * This is the flattened, build-compatible copy of
 * tools/cpp-parser/src/main/java/cppmode/parser/passes/JavaArrayCheck.java
 * -- see CppLexerTokenType's javadoc for why a flattened copy exists
 * alongside the organized source, and DECISION_two_parser_implementations.md
 * (in tools/cpp-parser/) for the full history of this check's design.
 *
 * This scans the real token stream produced by CppLexer instead of a
 * regex over blanked text, so it automatically inherits correct
 * comment/string-literal handling -- a token inside a comment or string
 * literal was never tokenized as code in the first place (comments and
 * preprocessor directives are their own distinct CppLexerTokenTypes), so
 * there is no separate "blank it out first" step to keep in sync with
 * the lexer's own literal-recognition rules, unlike the original
 * CppBuild.java regex pass, which needed blankCommentsAndLiterals() as a
 * hand-maintained parallel implementation of the same recognition the
 * lexer already does correctly.
 *
 * Detects: "ElementType[] name = new ElementType[...];" and the
 * multi-dimensional form "ElementType[][] name = new ElementType[...][...];",
 * with the same tolerance for whitespace between type/brackets/name that
 * the original regex had (token-based matching gets this for free, since
 * whitespace was never tokenized to begin with).
 *
 * Confirmed by earlier g++ compile-checking (see tools/cpp-parser's
 * project notes) that this specific shape -- "Type[] name = new
 * Type[size]" -- is the actual, narrow thing the original check catches;
 * a bare "int[] bad;" with no "= new ...[]" initializer is a DIFFERENT,
 * more general C++ parse failure that a full parser's grammar would
 * reject, not something this narrow, standalone check needs to handle.
 */
public final class CppJavaArrayCheck {
    private CppJavaArrayCheck() {}

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
        List<CppLexerToken> tokens = new CppLexer(source).tokenize();
        int n = tokens.size();

        for (int i = 0; i < n; i++) {
            CppLexerToken t = tokens.get(i);
            if (t.type() != CppLexerTokenType.IDENTIFIER && t.type() != CppLexerTokenType.KEYWORD) continue;

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
            if (j >= n || tokens.get(j).type() != CppLexerTokenType.IDENTIFIER) continue;
            int afterName = j + 1;
            if (afterName >= n || !tokens.get(afterName).isOp("=")) continue;
            int newPos = afterName + 1;
            if (newPos >= n || !tokens.get(newPos).isKeyword("new")) continue;
            int newTypePos = newPos + 1;
            if (newTypePos >= n || (tokens.get(newTypePos).type() != CppLexerTokenType.IDENTIFIER
                                      && tokens.get(newTypePos).type() != CppLexerTokenType.KEYWORD)) continue;

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
