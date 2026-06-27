package cppmode.parser.ast.decl;

import cppmode.parser.Token;

import java.util.List;

/** "using namespace Name;". Same low-priority/parse-only status as NamespaceDecl. */
public record UsingNamespaceDecl(
    String name,
    int line,
    int col,
    List<Token> leadingComments
) implements TopLevelItem {
}
