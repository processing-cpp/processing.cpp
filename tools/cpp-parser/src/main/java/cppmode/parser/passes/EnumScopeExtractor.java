package cppmode.parser.passes;

import cppmode.parser.ast.decl.EnumDecl;
import cppmode.parser.ast.decl.TopLevelItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the enum-extraction half of the combined enumScope/
 * constexprScope block in CppBuild.java's writeSketch(). That block does
 * two unrelated things in one character-walking pass: pull out enum
 * declarations (so they can be emitted before classes that use them) and
 * pull out constexpr/static_assert/using-alias declarations. This pass
 * covers only the enum half -- see "constexpr/static_assert/using-alias
 * NOT ported" below for why the other half is intentionally left out for
 * now rather than faked.
 *
 * The original's enum-extraction logic exists almost entirely to solve a
 * problem this AST doesn't have: a naive per-line scan only matches an
 * enum's OPENING line ("enum Direction {"), silently leaving the
 * enumerator list itself behind in the "rest" bucket -- so the original
 * needs a careful character-walking, brace-depth-tracking scan
 * specifically to capture the whole multi-line block as one unit. On
 * this AST, an EnumDecl produced by the parser is ALREADY one complete
 * node (name, isScoped flag, and the full list of enumerator values) --
 * there is no way for "half an enum" to exist in the tree at all, so
 * this pass is just a filter: separate TopLevelItems into "is an
 * EnumDecl" and "isn't," same pattern as ClassHoister/ArrayHoister.
 *
 * NOT PORTED: constexpr declarations, static_assert, and type-alias
 * "using Name = Type;" declarations. Checked directly against the real
 * 131-file example corpus (see tools/cpp-parser/real-corpus-snapshot/):
 * zero files use "constexpr", "static_assert", or a type-alias "using"
 * declaration anywhere. This AST also has no constexpr concept at all
 * (VariableDecl/FunctionDecl only track isConst/isStatic) -- adding
 * speculative support for a construct with zero corpus evidence would
 * mean shipping untested guesswork dressed up as a port. If a future
 * sketch (real or fixture) is found using any of these, that's the
 * trigger to design and test the corresponding AST support properly,
 * not to retrofit it blind now.
 */
public final class EnumScopeExtractor {
    private EnumScopeExtractor() {}

    public static final class Result {
        public final List<EnumDecl> enums = new ArrayList<>();
        public final List<TopLevelItem> rest = new ArrayList<>();
    }

    public static Result extract(List<TopLevelItem> items) {
        Result result = new Result();
        for (TopLevelItem item : items) {
            if (item instanceof EnumDecl ed) {
                result.enums.add(ed);
            } else {
                result.rest.add(item);
            }
        }
        return result;
    }
}
