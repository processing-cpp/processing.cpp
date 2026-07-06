package cppmode.parser.ast;

import cppmode.parser.ast.expr.Expr;
import java.util.List;

/**
 * A single function/lambda parameter: type, name, optional default value,
 * and optional inner array dimensions for multi-dimensional array parameters.
 *
 * innerArrayDims is non-empty when the parameter was declared with multiple
 * bracket pairs (e.g. "float matrix[3][3]" or "float matrix[][3]"), recording
 * the INNER dimensions. The first bracket pair decays the type to a pointer;
 * the inner dims are needed for correct C++ rendering: "float matrix[3][3]"
 * must become "float (*matrix)[3]", not "float* matrix", because matrix[i][j]
 * on "float* matrix" gives "float[int]" -- an invalid subscript.
 *
 * Empty for ordinary scalar and single-bracket parameters.
 * Confirmed necessary by the official Processing Convolution example sketch.
 */
public record Param(TypeRef type, String name, Expr defaultValue, List<Integer> innerArrayDims) {
    /** Convenience constructor for the common no-inner-dims case. */
    public Param(TypeRef type, String name, Expr defaultValue) {
        this(type, name, defaultValue, List.of());
    }
}
