package cppmode.parser.passes;

import cppmode.parser.ast.decl.FunctionDecl;
import cppmode.parser.ast.decl.TopLevelItem;
import cppmode.parser.ast.decl.TypeDef;
import cppmode.parser.ast.decl.VariableDecl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Replaces the two iterative dependency-hoisting blocks inside
 * CppBuild.java's writeSketch(): the free-function pass and the
 * plain-variable pass that follows it. Both move top-level declarations
 * out to namespace scope when something already hoisted (a class, or in
 * the function pass's case also a previously-hoisted function) references
 * them by name -- since a class hoisted to namespace scope cannot see a
 * Sketch member.
 *
 * Ported from a parallel implementation's CppDependencyHoister (package
 * processing.mode.cpp), retargeted onto this codebase's AST and
 * NameUsageScanner port. See DECISION_two_parser_implementations.md.
 *
 * DEPARTURE from the port source: that implementation deduplicates
 * "already hoisted this exact FunctionDecl" by rendering both candidates
 * through its CppCodeGen and comparing the generated text. This codebase
 * has not ported codegen (not yet needed for anything else), so dedup
 * here instead compares node IDENTITY (reference equality) -- every
 * FunctionDecl considered comes from the SAME immutable `remaining` list
 * this pass is iterating and removing from, so a node is either literally
 * the same object already in hoistedFunctions or it isn't; there is no
 * scenario in this AST (unlike a hypothetical regenerated/rewritten one)
 * where two distinct FunctionDecl objects represent the same source
 * declaration and would need a textual-equality fallback. This is simpler
 * than the original AND correct for this AST's actual usage pattern, not
 * a weaker approximation of it.
 *
 * Preserves two original behaviors exactly, including ones that look like
 * they might be oversights but are deliberately kept as-is rather than
 * "improved," since changing them would change generated output for
 * sketches that currently build correctly:
 *
 *  1. The variable pass's dependency haystack is "hoisted classes plus
 *     hoisted functions" -- it does NOT include previously-hoisted
 *     variables. The function pass's haystack DOES include its own
 *     accumulator. This asymmetry is preserved rather than "fixed."
 *  2. A function reference must look like a CALL to count as a dependency
 *     for the function pass; a bare variable reference (no call) counts
 *     for the variable pass.
 *  3. Lifecycle method/variable names (setup, draw, mousePressed, etc.)
 *     are never hoisted by either pass.
 */
public final class DependencyHoister {
    private DependencyHoister() {}

    public static final class Result {
        public final List<FunctionDecl> hoistedFunctions = new ArrayList<>();
        public final List<VariableDecl> hoistedVariables = new ArrayList<>();
        public final List<TopLevelItem> rest = new ArrayList<>();
    }

    /**
     * @param items the remaining top-level items after class hoisting and
     *              array hoisting have already removed their own pieces
     * @param hoistedClasses the already-hoisted TypeDef nodes (used as the
     *              initial dependency haystack for both sub-passes)
     * @param lifecycleNames names that must never be hoisted by either pass
     */
    public static Result hoist(List<TopLevelItem> items, List<TypeDef> hoistedClasses, Set<String> lifecycleNames) {
        Result result = new Result();
        List<TopLevelItem> remaining = new ArrayList<>(items);

        // =================================================================
        // PASS 1: free functions, called from hoisted classes or from
        // previously-hoisted functions (its own accumulator IS included).
        // =================================================================
        boolean hoistedSomething = true;
        while (hoistedSomething) {
            hoistedSomething = false;

            for (int i = 0; i < remaining.size(); i++) {
                TopLevelItem item = remaining.get(i);
                if (!(item instanceof FunctionDecl fd)) continue;
                if (fd.isConstructor() || fd.isDestructor()) continue;
                if (lifecycleNames.contains(fd.name())) continue;
                if (result.hoistedFunctions.contains(fd)) continue; // identity-based, see class javadoc

                boolean calledFromHoisted = isCalledByAnyTypeDef(fd.name(), hoistedClasses)
                    || isCalledByAnyFunctionDecl(fd.name(), result.hoistedFunctions);
                if (!calledFromHoisted) continue;

                result.hoistedFunctions.add(fd);
                remaining.remove(i);
                hoistedSomething = true;
                break; // restart scan, matching the original's "break after one hoist" restart behavior
            }
        }

        // =================================================================
        // PASS 2: plain variables, referenced (as a bare identifier, not
        // necessarily a call) from hoisted classes or hoisted functions
        // ONLY -- deliberately NOT from other already-hoisted variables.
        // =================================================================
        boolean hoistedVarSomething = true;
        while (hoistedVarSomething) {
            hoistedVarSomething = false;

            for (int i = 0; i < remaining.size(); i++) {
                TopLevelItem item = remaining.get(i);
                if (!(item instanceof VariableDecl vd)) continue;
                if (!vd.arrayDims().isEmpty()) continue; // arrays are ArrayHoister's job, not this pass's
                if (lifecycleNames.contains(vd.name())) continue;
                if (result.hoistedVariables.contains(vd)) continue;

                boolean referencedFromHoisted = isBareIdentifierReferencedByAnyTypeDef(vd.name(), hoistedClasses)
                    || isBareIdentifierReferencedByAnyFunctionDecl(vd.name(), result.hoistedFunctions);
                if (!referencedFromHoisted) continue;

                result.hoistedVariables.add(vd);
                remaining.remove(i);
                hoistedVarSomething = true;
                break;
            }
        }

        result.rest.addAll(remaining);
        return result;
    }

    private static boolean isCalledByAnyTypeDef(String name, List<TypeDef> typeDefs) {
        for (TypeDef td : typeDefs) {
            if (NameUsageScanner.containsCall(td, name)) return true;
        }
        return false;
    }

    private static boolean isCalledByAnyFunctionDecl(String name, List<FunctionDecl> fns) {
        for (FunctionDecl fd : fns) {
            if (NameUsageScanner.containsCall(fd, name)) return true;
        }
        return false;
    }

    private static boolean isBareIdentifierReferencedByAnyTypeDef(String name, List<TypeDef> typeDefs) {
        for (TypeDef td : typeDefs) {
            if (NameUsageScanner.containsIdentifier(td, name)) return true;
        }
        return false;
    }

    private static boolean isBareIdentifierReferencedByAnyFunctionDecl(String name, List<FunctionDecl> fns) {
        for (FunctionDecl fd : fns) {
            if (NameUsageScanner.containsIdentifier(fd, name)) return true;
        }
        return false;
    }
}
