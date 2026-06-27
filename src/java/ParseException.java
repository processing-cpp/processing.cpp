package processing.mode.cpp;


/**
 * Thrown on the first parse error encountered. Per the project's fail-fast
 * policy (confirmed correct by the broken_test.cpp compile-check, where a
 * single missing brace cascaded into five unrelated-looking g++ errors):
 * the parser does not attempt error recovery. It reports the first problem
 * with an accurate line/column and stops.
 */
final class ParseException extends RuntimeException {
    public final int line;
    public final int col;

    public ParseException(String message, int line, int col) {
        super(message + " (at line " + line + ", column " + col + ")");
        this.line = line;
        this.col = col;
    }
}
