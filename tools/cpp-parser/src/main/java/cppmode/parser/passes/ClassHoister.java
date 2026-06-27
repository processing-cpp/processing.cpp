package cppmode.parser.passes;

import cppmode.parser.ast.decl.TopLevelItem;
import cppmode.parser.ast.decl.TypeDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces CppBuild.java's hoistClassesOnly() + removeHoistedClasses().
 *
 * Ported from a parallel implementation's CppClassHoister (package
 * processing.mode.cpp), retargeted onto this codebase's AST shapes. See
 * DECISION_two_parser_implementations.md for why this is a port of the
 * logic rather than a merge of the two ASTs.
 *
 * The original CppBuild.java pass was two separate character-walking scans
 * over raw text that had to stay in lockstep (one collects class blocks,
 * the other blanks the same ranges from the original source) -- a
 * structural duplication risk: if either scan's class/enum/comment-skipping
 * logic ever drifted from the other's, hoisted classes and "the rest"
 * could silently disagree about where a class started or ended (this is
 * the same bug class as the comment-spanning-newline bug already found and
 * fixed in CppBuild.java's classifyTopLevelDecls).
 *
 * With a real AST this is one operation instead of two: classify
 * cu.items() into "is a TypeDef" vs "isn't," reorder the TypeDefs, and the
 * non-TypeDef items already ARE the correct "rest" with zero risk of the
 * two halves disagreeing, since they're derived from the exact same parsed
 * list rather than two independent re-scans of raw text.
 *
 * Ordering: base classes before derived, matching the original's
 * bubble-style pairwise swap. The original CppBuild.java regex only ever
 * captured a single base-class name per class, so this preserves that same
 * single-base assumption for ordering purposes -- a class with multiple
 * bases (this AST's TypeDef.baseClasses() supports a list, confirmed real
 * by the multiple-inheritance kitchen-sink fixture) is ordered by its
 * FIRST listed base only. This pass doesn't invent new ordering behavior
 * for a case the original CppBuild.java pass never handled either.
 */
public final class ClassHoister {
    private ClassHoister() {}

    public static final class Result {
        /** Hoisted TypeDef nodes, ordered so each class's first base class
         * (if any, and if that base is itself one of the hoisted classes)
         * appears before it. */
        public final List<TypeDef> hoistedClasses;
        /** Every other top-level item, in original order, with the
         * hoisted TypeDefs removed. */
        public final List<TopLevelItem> rest;

        public Result(List<TypeDef> hoistedClasses, List<TopLevelItem> rest) {
            this.hoistedClasses = hoistedClasses;
            this.rest = rest;
        }
    }

    public static Result hoist(List<TopLevelItem> items) {
        List<TypeDef> classBlocks = new ArrayList<>();
        List<TopLevelItem> rest = new ArrayList<>();

        for (TopLevelItem item : items) {
            if (item instanceof TypeDef td) {
                classBlocks.add(td);
            } else {
                rest.add(item);
            }
        }

        boolean changed = true;
        for (int pass = 0; pass < classBlocks.size() * 2 && changed; pass++) {
            changed = false;
            for (int a = 0; a < classBlocks.size(); a++) {
                TypeDef blockA = classBlocks.get(a);
                if (blockA.baseClasses().isEmpty()) continue;
                String aBase = blockA.baseClasses().get(0);
                for (int b = a + 1; b < classBlocks.size(); b++) {
                    TypeDef blockB = classBlocks.get(b);
                    if (blockB.name().equals(aBase)) {
                        classBlocks.set(a, blockB);
                        classBlocks.set(b, blockA);
                        changed = true;
                        break;
                    }
                }
                if (changed) break;
            }
        }

        return new Result(classBlocks, rest);
    }
}
