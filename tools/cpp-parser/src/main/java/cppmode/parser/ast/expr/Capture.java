package cppmode.parser.ast.expr;

/**
 * A single entry in a lambda capture list: a captured variable name and
 * whether it's captured by reference ([&amp;x]) or by value ([x]).
 */
public record Capture(String name, boolean byRef) {
}
