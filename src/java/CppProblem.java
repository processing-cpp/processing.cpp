package processing.mode.cpp;

import processing.app.Problem;

/**
 * A compile-time error or warning detected by CppLinter (g++ -fsyntax-only).
 */
public record CppProblem(
    boolean error,
    int tabIndex,
    int lineNumber,   // 0-indexed
    int startOffset,
    int stopOffset,
    String message
) implements Problem {
    public boolean isError()   { return error; }
    public boolean isWarning() { return !error; }
    public int getTabIndex()   { return tabIndex; }
    public int getLineNumber() { return lineNumber; }
    public int getStartOffset(){ return startOffset; }
    public int getStopOffset() { return stopOffset; }
    public String getMessage() { return message; }
}
