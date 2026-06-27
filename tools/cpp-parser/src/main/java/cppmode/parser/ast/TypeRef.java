package cppmode.parser.ast;

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
public sealed interface TypeRef permits NamedType, FunctionPointerType, FunctionSignatureType {

    /** Plain-English rendering for diagnostics; not used for codegen fidelity. */
    String describe();
}
