package cppmode.parser.ast.expr;

import cppmode.parser.Token;
import cppmode.parser.ast.TypeRef;

import java.util.List;

/**
 * Heap allocation of an array: "new Type[sizeExpr]", e.g. "new int[10]".
 * Paired at the statement/codegen level with "delete[] ptr;" (a flag on the
 * delete statement, not on this node -- allocation and deallocation are
 * separate statements in the source and the parser doesn't try to link them).
 */
public record ArrayNewExpr(
    TypeRef elementType,
    Expr sizeExpr,
    int line,
    int col,
    List<Token> leadingComments
) implements Expr {
}
