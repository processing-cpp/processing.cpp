package cppmode.parser.passes;

import cppmode.parser.ast.decl.FunctionDecl;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the inline forward-declaration generation in CppBuild.java's
 * writeSketch(), which re-derives a function's signature by regex-
 * matching its own already-generated text back apart.
 *
 * On the AST this needs no pattern at all: a forward declaration of a
 * FunctionDecl is simply the same FunctionDecl with body set to null --
 * CodeGen's emitFunctionDecl already renders a null-body FunctionDecl as
 * "ReturnType name(params);" (no body, trailing semicolon), since that's
 * also the correct rendering for a genuine declaration-without-definition
 * in ordinary C++. No new codegen logic was needed for this -- it already
 * existed for a different reason (the AST always supported a bodyless
 * FunctionDecl, even though nothing produced one until this pass).
 *
 * This lets a class method (e.g. "Agent::update" calling "dirToVec")
 * compile even though the class itself appears earlier in the emitted
 * file than the hoisted function's full definition -- matching the
 * original's exact purpose.
 */
public final class ForwardDeclGenerator {
    private ForwardDeclGenerator() {}

    /**
     * @param hoistedFunctions the FunctionDecl nodes hoisted to namespace
     *                          scope (e.g. via a free-function dependency
     *                          pass analogous to DependencyHoister's
     *                          function-hoisting half)
     * @return one bodyless FunctionDecl per input, in the same order,
     *         suitable for emitting before the hoisted classes that call them
     */
    public static List<FunctionDecl> generate(List<FunctionDecl> hoistedFunctions) {
        List<FunctionDecl> decls = new ArrayList<>(hoistedFunctions.size());
        for (FunctionDecl fd : hoistedFunctions) {
            decls.add(new FunctionDecl(
                fd.returnType(), fd.name(), fd.templateParams(), fd.params(),
                fd.initializerList(), null /* body -- this is what makes it a forward decl */,
                fd.isConstructor(), fd.isDestructor(), fd.isVirtual(), fd.isOverride(),
                fd.isConst(), fd.isStatic(), fd.line(), fd.col(), List.of() /* no comments on the forward decl */
            ));
        }
        return decls;
    }
}
