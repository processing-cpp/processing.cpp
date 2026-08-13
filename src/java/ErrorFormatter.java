package processing.mode.cpp;

/**
 * Formats a ParseException into GCC-style error output with source context.
 *
 * Full output (→ System.err):
 *
 *   sketch.cpp:10:30: error: expected ';' after expression
 *      9 |     lights();
 *     10 |     translate(width/2, height/2)
 *        |                              ^
 *        |                              |
 *        |                              missing ';' here
 *     11 |     rotateX(angle * 0.7f);
 *
 * Short output (→ listener.statusError):
 *   sketch.cpp:10: error: expected ';' after expression
 */
public final class ErrorFormatter {

  private static final int CONTEXT_LINES = 1;

  private ErrorFormatter() {}

  // ── Public API ─────────────────────────────────────────────────────────────

  /** Full multi-line formatted error for System.err / console. */
  public static String format(String source, ParseException ex) {
    return format(source, "sketch.cpp", ex);
  }

  public static String format(String source, String filename, ParseException ex) {
    String[] lines = source.split("\n", -1);
    int fixLine = ex.line;  // 1-based; parser sets this to end-of-broken-stmt
    int fixCol  = ex.col;   // 1-based

    StringBuilder sb = new StringBuilder();

    // sketch.cpp:10:30: error: expected ';' after expression
    sb.append(filename).append(':')
      .append(fixLine).append(':')
      .append(fixCol).append(": error: ")
      .append(humanMessage(ex))
      .append('\n');

    int firstLine = Math.max(1, fixLine - CONTEXT_LINES);
    int lastLine  = Math.min(lines.length, fixLine + CONTEXT_LINES);
    int gutterW   = String.valueOf(lastLine).length() + 1; // width of line numbers

    for (int i = firstLine; i <= lastLine; i++) {
      String text = i <= lines.length ? lines[i - 1] : "";
      sb.append(lineGutter(i, gutterW)).append(text).append('\n');
      if (i == fixLine) {
        appendCaret(sb, text, fixCol, gutterW, fixNote(ex));
      }
    }

    return sb.toString().stripTrailing();
  }

  /** One-liner for listener.statusError() — fits in the IDE status bar. */
  public static String statusLine(ParseException ex) {
    return "sketch.cpp:" + ex.line + ": error: " + humanMessage(ex);
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  /**
   * Translate parser-internal message to GCC-style human message.
   * ParseException bakes "... (at line N, column M)" into getMessage() —
   * strip that suffix since we already emit location in the prefix.
   */
  private static String humanMessage(ParseException ex) {
    String msg = ex.getMessage();
    if (msg == null) return "syntax error";

    // Strip trailing " (at line N, column M)"
    msg = msg.replaceAll("\\s*\\(at line \\d+, column \\d+\\)\\s*$", "").trim();

    // Digit where name expected (e.g. "float 3x") — only for identifier-position errors
    if (msg.contains("expected identifier") || msg.contains("expected a type name")) {
      String _tok = msg.replaceAll(".*(?:but found|before) \'(.+?)\'.*", "$1");
      if (_tok.length() > 0 && Character.isDigit(_tok.charAt(0)))
        msg = "identifier '" + _tok + "' is invalid — names cannot start with a digit";
    }

    // Invalid non-word token (e.g. \'@\') — before but found→before rewrite
    if (msg.contains("expected \';\' but found \'") && msg.replaceAll(".*but found \'(.+?)\'.*", "$1").matches("[^a-zA-Z0-9_]+")) {
      msg = "unexpected token '" + msg.replaceAll(".*but found \'(.+?)\'.*", "$1") + "'";
    } else if (msg.matches("expected \';\' before \'[a-zA-Z_][a-zA-Z0-9_]*\'")
        || msg.contains("expected \';\' before \'}\'")
        || msg.contains("expected \';\' before \'{\'")
        || msg.contains("expected \';\' before \')\'")) {
      msg = "expected \';\' after expression";
    }

    // "expected \'X\' but found \'Y\'" → "expected \'X\' before \'Y\'"
    msg = msg.replaceAll("expected (\'.*?\') but found (\'.*?\')", "expected $1 before $2");

    // Bracket/paren mismatches
    if (msg.contains("expected \')\' before")) msg = "expected \')\' — missing closing parenthesis";
    if (msg.contains("expected \'}\'  before")) msg = "expected \'}\'  — missing closing brace";
    if (msg.contains("expected \'>\'  before")) msg = "expected \'>\'  — unmatched template bracket";

    // Keyword used as identifier (e.g. "int for = 10")
    { java.util.Set<String> KW = new java.util.HashSet<>(java.util.Arrays.asList(
        "for","while","if","else","switch","case","return","break","continue",
        "class","struct","namespace","template","typename","auto","void",
        "int","float","double","bool","char","long","short","unsigned","signed",
        "const","constexpr","static","inline","virtual","override","new","delete","this"));
      String _kw = msg.replaceAll(".*(?:but found|before) '(.+?)'.*", "$1");
      if (KW.contains(_kw))
        msg = "'"+_kw+"' is a reserved keyword and cannot be used as a name";
    }
    // Identifier expected
    if (msg.startsWith("expected identifier but found"))
      msg = msg.replace("expected identifier but found", "expected a name but found");

    // Unexpected token in expression
    if (msg.startsWith("unexpected token") && msg.contains("while parsing an expression"))
      msg = msg.replaceAll("unexpected token (\'.*?\') while parsing an expression", "unexpected token $1 in expression");

    // Unclosed block/struct/namespace
    if (msg.contains("unexpected end of input inside class/struct body"))
      msg = msg.replaceAll("unexpected end of input inside class/struct body for (\'.*?\')", "unterminated class body for $1 — missing closing \'}\'");
    if (msg.contains("unexpected end of input inside namespace"))
      msg = msg.replaceAll("unexpected end of input inside namespace (\'.*?\')", "unterminated namespace $1 — missing closing \'}\'");
    if (msg.contains("unexpected end of input while looking for closing \'}\'"))
      msg = "unterminated block — missing closing \'}\'";

    // delete misuse
    if (msg.contains("\'delete\' is only valid as a statement"))
      msg = "\'delete\' cannot be used inside an expression";

    // Multiple declarators
    if (msg.contains("multiple comma-separated declarators"))
      msg = "multiple declarations in a single \'if\'/\'for\'/\'while\' condition are not supported";

    return msg;
  }


  /** Short inline annotation shown below the caret. */
  private static String fixNote(ParseException ex) {
    String msg = ex.getMessage();
    if (msg == null) return null;
    if (msg.contains("expected ','"))              return "missing ',' here";
    if (msg.contains("expected 'while'"))           return "do-while requires 'while (condition);' after the closing '}'";
    if (msg.contains("expected ':'"))              return "missing ':' after 'case'/'default'";
    if (msg.contains("expected identifier") || msg.contains("expected a type name")) {
      String _d = msg.replaceAll(".*(?:but found|before) '(.+?)'.*", "$1");
      if (_d.length() > 0 && Character.isDigit(_d.charAt(0))) return "names cannot start with a digit";
      java.util.Set<String> KW = new java.util.HashSet<>(java.util.Arrays.asList(
        "for","while","if","else","switch","case","return","break","continue",
        "class","struct","namespace","template","typename","auto","void",
        "int","float","double","bool","char","long","short","unsigned","signed",
        "const","constexpr","static","inline","virtual","override","new","delete","this"));
      if (KW.contains(_d)) return "'"+_d+"' is a reserved keyword";
    }
    if (msg.contains("expected ';' but found '")) {
      String tok = msg.replaceAll(".*but found '(.+?)'.*", "$1");
      if (tok.matches("[^a-zA-Z0-9_]+")) return "invalid token '" + tok + "'";
    }
    if (msg.contains("expected ';'"))              return "missing ';' here";
    if (msg.contains("expected '(' before") || msg.contains("expected '(' but found"))  return "'(' is required after 'if'/'while'/'for'/'switch'";
    if (msg.contains("expected ')'"))                               return "missing ')' to close this";
    if (msg.contains("expected '}'"))                               return "missing '}' to close block";
    if (msg.contains("expected '>'"))                               return "missing '>' here";
    if (msg.contains("unexpected end of input while looking for closing '}'")) return "block opened but never closed";
    if (msg.contains("unexpected end of input inside class/struct")) return "class body opened but never closed";
    if (msg.contains("unexpected end of input inside namespace"))    return "namespace body opened but never closed";
    if (msg.contains("unterminated string literal"))               return "closing '\"' is missing";
    if (msg.contains("unterminated character literal"))            return "closing \"'\" is missing";
    if (msg.contains("unterminated character literal"))            return "closing \"'\" is missing";
    if (msg.contains("unexpected end of file"))                     return "file ended unexpectedly";
    if (msg.contains("'delete' is only valid as a statement"))      return "move 'delete' outside of the expression";
    if (msg.contains("multiple comma-separated declarators"))       return "split into separate declarations";
    if (msg.contains("unexpected token '}'" ) && msg.contains("expression")) return "empty else body — add a statement or { }";
    if (msg.contains("unexpected token '}'" ) && msg.contains("expression")) return "empty else body — add a statement or { }";
    if (msg.contains("unexpected token") && msg.contains("expression")) return "this token is not valid in an expression";
    { String _tok = msg.replaceAll(".*but found '(.+?)'.*", "$1");
      if (_tok.length() > 0 && Character.isDigit(_tok.charAt(0)))
        return "names cannot start with a digit"; }
    if ((msg.contains("expected ';' but found") || msg.contains("expected identifier but found"))
        && msg.replaceAll(".*found '(.+?)'.*", "$1").matches("\\d.*"))
      return "names cannot start with a digit";
    if (msg.contains("expected identifier"))                        return "a variable or function name is required here";
    if (msg.contains("expected a type name"))                       return "a type name (int, float, PVector, ...) is required here";
    return null;
  }

  /**
   * Append caret block below the highlighted line:
   *
   *    |                              ^
   *    |                              |
   *    |                              missing ';' here
   */
  private static void appendCaret(StringBuilder sb, String lineText,
                                   int col, int gutterW, String note) {
    int caretPos = Math.max(0, Math.min(col - 1, lineText.length()));
    String indent = mirror(lineText, caretPos);
    String blank  = " ".repeat(gutterW) + " | ";

    sb.append(blank).append(indent).append('^').append('\n');
    if (note != null) {
      sb.append(blank).append(indent).append('|').append('\n');
      sb.append(blank).append(indent).append(note).append('\n');
    }
  }

  /**
   * Reproduce the whitespace shape of `text` for the first `len` chars,
   * preserving tabs so the caret stays visually aligned even in tab-indented
   * source.
   */
  private static String mirror(String text, int len) {
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      sb.append(i < text.length() && text.charAt(i) == '\t' ? '\t' : ' ');
    }
    return sb.toString();
  }

  /** Right-aligned line number gutter: "  10 | " */
  private static String lineGutter(int n, int width) {
    String num = String.valueOf(n);
    return " ".repeat(Math.max(0, width - num.length())) + num + " | ";
  }
}
