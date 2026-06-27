package cppmode.parser.ast.decl;

import cppmode.parser.ast.Node;

/**
 * Marker for anything that can appear directly inside a CompilationUnit:
 * a VariableDecl, FunctionDecl, TypeDef, EnumDecl, NamespaceDecl, or
 * UsingNamespaceDecl. Also reused as the "member of a class/struct body"
 * marker (TypeDef.members), since the corpus confirms the same shapes
 * (field VariableDecl, method/constructor/destructor FunctionDecl, nested
 * TypeDef) appear in both top-level and class-body positions identically.
 */
public sealed interface TopLevelItem extends Node
    permits VariableDecl, FunctionDecl, TypeDef, EnumDecl, NamespaceDecl,
            UsingNamespaceDecl, PreprocessorLine, TopLevelStatement {
}
