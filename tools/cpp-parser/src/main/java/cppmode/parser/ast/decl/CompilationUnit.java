package cppmode.parser.ast.decl;

import java.util.List;

/** The root AST node: an entire parsed source file as a list of top-level items. */
public record CompilationUnit(List<TopLevelItem> items) {
}
