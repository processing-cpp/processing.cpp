package cppmode.parser.ast.decl;

import cppmode.parser.Token;

import java.util.List;

/**
 * "namespace Name { items }".
 *
 * NOTE (carried over from the synthetic test-fixture brief): real .pde-style
 * CppMode sketches have not been confirmed to ever use namespaces. This node
 * exists so the parser doesn't choke if one appears, but semantic-pass
 * support for namespace-aware name resolution is deliberately deprioritized
 * until real sketch input demonstrates the need. Parse-only support for now.
 */
public record NamespaceDecl(
    String name,
    List<TopLevelItem> items,
    int line,
    int col,
    List<Token> leadingComments
) implements TopLevelItem {
}
