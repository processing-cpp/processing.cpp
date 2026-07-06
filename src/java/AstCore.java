package processing.mode.cpp;

import java.util.List;

/*
 * Consolidated core AST support types -- Node (the base interface every
 * AST node implements for position/comment tracking), TypeRef and its
 * three variants, and Param (shared by FunctionDecl and LambdaExpr).
 * Formerly 6 separate files; merged for the same reason as AstExpr.java.
 */




/**
 * Common contract for every AST node: source position (for diagnostics and
 * #line-directive emission during codegen) and any comment tokens that
 * lexically preceded this node and were attached to it by the parser.
 *
 * Comments are attached at parse time (not stripped, not handled by a separate
 * regex pass) specifically so semantic passes walking the tree never need to
 * worry about comment text being mistaken for code -- see CppLexer's COMMENT
 * handling notes.
 */

interface Node {
    int line();
    int col();
    List<CppLexerToken> leadingComments();
}



/**
 * Represents a C++ type as it appears in source: in a variable/field declaration,
 * a function return type, a parameter type, a template argument, etc.
 *
 * This is a sealed hierarchy with three concrete shapes, confirmed necessary by
 * walking the real CppMode test corpus:
 *
 *  - {@link NamedType}: the common case. Covers everything from "int" to
 *    "ArrayList<Handle>*" to "const std::string&" to the outer "std::function"
 *    wrapper of "std::function<void(int)>" (the inner "void(int)" signature
 *    becomes a {@link FunctionSignatureType} nested inside this type's templateArgs).
 *
 *  - {@link FunctionPointerType}: the raw C-style function pointer declarator
 *    shape, e.g. "int (*funcPtr)(int, int)". This does NOT fit NamedType at all --
 *    there is no single "name" being qualified by pointer/template syntax; the
 *    pointer-ness applies to an entire function signature. Confirmed as a genuine
 *    structural fork during corpus validation.
 *
 *  - {@link FunctionSignatureType}: a bare function signature with no pointer
 *    declarator, used only as a template argument inside std::function&lt;...&gt;.
 *
 * Note on std::function<void(int)>: this parses as a NamedType with
 * baseName="std::function" and a single templateArg that is itself a
 * FunctionSignatureType -- NOT a FunctionPointerType (no '(*)' declarator is
 * present in std::function's syntax, it's purely a template argument).
 */

sealed interface TypeRef permits NamedType, FunctionPointerType, FunctionSignatureType {

    /** Plain-English rendering for diagnostics; not used for codegen fidelity. */
    String describe();
}




/**
 * The common-case type reference.
 *
 * @param baseName      e.g. "int", "ArrayList", "std::string", "T", "auto"
 * @param templateArgs  empty for non-template types; each element is itself a
 *                       TypeRef so nested templates (Pair&lt;int, ArrayList&lt;T&gt;&gt;)
 *                       and function-signature template args (std::function&lt;...&gt;)
 *                       both work without a separate node
 * @param pointerDepth  number of '*' suffixes; 0 for a plain value type
 * @param isReference   true for a '&amp;' suffix
 * @param isConst       true if a leading 'const' qualifier was present
 */

record NamedType(
    String baseName,
    List<TypeRef> templateArgs,
    int pointerDepth,
    boolean isReference,
    boolean isConst
) implements TypeRef {

    public static NamedType simple(String baseName) {
        return new NamedType(baseName, List.of(), 0, false, false);
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (isConst) sb.append("const ");
        sb.append(baseName);
        if (!templateArgs.isEmpty()) {
            sb.append("<");
            for (int i = 0; i < templateArgs.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(templateArgs.get(i).describe());
            }
            sb.append(">");
        }
        sb.append("*".repeat(pointerDepth));
        if (isReference) sb.append("&");
        return sb.toString();
    }
}




/**
 * The raw C-style function pointer declarator: "ReturnType (*)(ParamType, ...)".
 * Confirmed necessary by the corpus's useRawFunctionPointer fixture
 * ("int (*funcPtr)(int, int) = someFunc;") -- this does not fit NamedType's shape,
 * since the pointer applies to an entire function signature, not a named type.
 */

record FunctionPointerType(
    TypeRef returnType,
    List<TypeRef> paramTypes
) implements TypeRef {

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder(returnType.describe());
        sb.append(" (*)(");
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramTypes.get(i).describe());
        }
        sb.append(")");
        return sb.toString();
    }
}




/**
 * A bare function signature with no pointer declarator, used only as a template
 * argument inside std::function&lt;...&gt; (e.g. the "void(int)" inside
 * "std::function&lt;void(int)&gt;"). Distinct from FunctionPointerType because no
 * '(*)' appears in the source -- confirmed as a separate shape during corpus
 * validation rather than reusing FunctionPointerType for both cases.
 */

record FunctionSignatureType(
    TypeRef returnType,
    List<TypeRef> paramTypes
) implements TypeRef {

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder(returnType.describe());
        sb.append("(");
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramTypes.get(i).describe());
        }
        sb.append(")");
        return sb.toString();
    }
}




/**
 * A single function/lambda parameter: type, name, and an optional default
 * value. Confirmed necessary by the corpus's withDefaultParam fixture
 * ("void withDefaultParam(int x, int y = 5)") -- defaultValue is null when
 * absent.
 *
 * Shared between FunctionDecl and LambdaExpr since both need identical shape;
 * confirmed by corpus that lambda parameter lists ("[](int n) { ... }") use
 * the same grammar as ordinary function parameter lists, just without default
 * values being exercised in practice (though nothing in the grammar forbids
 * them on a lambda either).
 */

record Param(TypeRef type, String name, Expr defaultValue, List<Integer> innerArrayDims) {
    Param(TypeRef type, String name, Expr defaultValue) {
        this(type, name, defaultValue, List.of());
    }
}

