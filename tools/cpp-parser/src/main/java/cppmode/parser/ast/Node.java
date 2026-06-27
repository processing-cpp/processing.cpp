package cppmode.parser.ast;

import cppmode.parser.Token;

import java.util.List;

/**
 * Common contract for every AST node: source position (for diagnostics and
 * #line-directive emission during codegen) and any comment tokens that
 * lexically preceded this node and were attached to it by the parser.
 *
 * Comments are attached at parse time (not stripped, not handled by a separate
 * regex pass) specifically so semantic passes walking the tree never need to
 * worry about comment text being mistaken for code -- see Lexer's COMMENT
 * handling notes.
 */
public interface Node {
    int line();
    int col();
    List<Token> leadingComments();
}
