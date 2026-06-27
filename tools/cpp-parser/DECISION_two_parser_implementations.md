# Decision: two parallel parser implementations found -- evaluation and resolution

## What happened

Partway through this work, a second, independently-built parser implementation
was discovered already sitting in `/mnt/user-data/outputs/cppmode-parser/`
(package `processing.mode.cpp`: `CppAst.java`, `CppLexer.java`,
`CppParser.java`, `CppCodeGen.java`, plus four hoisting-pass modules:
`CppArrayHoister`, `CppClassHoister`, `CppDependencyHoister`,
`CppLifecycleRewriter`, and `NameUsageScanner`/`CppJavaArrayCheck`). This was
not written in the visible conversation history for this session -- almost
certainly the product of a separate session/conversation with shared output
storage. Rather than guess at provenance or merge two divergent ASTs blindly,
both implementations were evaluated head-to-head on the same ground truth.

## Evaluation method

Both parsers were run against the identical corpus: all 131 real `.pde`
example sketches shipped with CppMode, plus this session's 6 synthetic
stress-test fixtures (covering control flow, OOP, templates, lambdas,
function pointers, namespaces).

## Result

| Implementation                | Pass | Fail | Total |
|--------------------------------|------|------|-------|
| This session's parser          | 137  | 0    | 137   |
| `processing.mode.cpp` parser   | 105  | 26   | 131*  |

(*the other implementation was only tested against the 131 real `.pde`
files, using its own fixture suite separately for the synthetic side -- it
passed its own 6 fixtures, but those were written for its own grammar
assumptions, e.g. it deliberately rejects direct-init-with-args as
"not yet supported," so the comparison there isn't apples-to-apples. The
131-file real-corpus comparison is.)

The other implementation's 26 failures include several bug classes this
session's parser hit and fixed earlier in its own development:
- Processing "static mode" sketches with no `setup()`/`draw()` (bare
  top-level statements) -- unhandled.
- Direct-init with literal constructor arguments at top level
  (`Eye e1(250, 16, 120);`) -- explicitly rejected as unsupported.
- The trailing `;` after a class/struct body being optional in real `.pde`
  files (Scrollbar.pde) -- not handled.
- Several `Unexpected token in expression: 'float'/'int'/'char'` failures
  not yet root-caused, but consistent with cast- or declaration-position
  gaps similar to ones found and fixed in this session's parser.

## Decision

**Keep this session's AST and parser as the foundation.** It is more
correct on the only objective, measurable criterion available (pass rate
against the full real corpus), and that correctness came from an iterative
test-driven process against ground truth, not just design review.

**Port, don't merge, the other implementation's hoisting/codegen modules.**
`CppClassHoister`, `CppArrayHoister`, `CppDependencyHoister`,
`CppLifecycleRewriter`, and `CppCodeGen` are valuable, well-reasoned designs
that this session's parser doesn't have yet -- but they're written against
the OTHER ast's node shapes (`processing.mode.cpp.CppAst.TypeDef` etc.).
They are loosely enough coupled (operating on generic
`CompilationUnit.items` / `TypeDef` shapes, not deep AST internals) that
porting their *logic* onto this session's AST is straightforward and is the
planned next step, rather than attempting a line-level merge of two
divergent type hierarchies, which would produce a system with two
incompatible ASTs pretending to be one.

This is the architecturally clean resolution: no silent overwrites, no
blind merges, a real test-based comparison, and a clear, documented reason
for the path taken.

## Update: porting CppClassHoister surfaced a real bug in this session's parser

While porting the other implementation's `CppClassHoister` logic onto this
session's AST (see `passes/ClassHoister.java`), a genuine, previously-unfound
bug was caught: `bool operator==(const Handle& other) const { ... }` as a
**class member** (as opposed to a free function) was being misrouted into
statement parsing and failing, because the Processing-static-mode
bare-statement fallback added earlier (for files like Coordinates.pde with
no `setup()`/`draw()`) was reachable from inside class bodies too, where it
should never apply -- a class member is always a declaration, never a bare
statement.

Root cause: `parseFunctionOrVariable` is shared between true top-level
dispatch and class-member dispatch, but the static-mode fallback check
inside it had no way to know which context it was being called from.

Fix: added an explicit `isTopLevel` boolean parameter, threaded through both
call sites, gating the fallback so it only ever fires at genuine file scope.

This was caught specifically because evaluating the parallel implementation
prompted writing a real `ClassHoisterTest` against actual multi-inheritance
fixtures (`oop_features.cpp`'s `Sprite : public Movable, public Drawable`,
which also exercises `Handle::operator==`) -- a fixture that had been shown
in conversation earlier but never actually saved to disk or run through the
full parser end-to-end until this point. Re-confirms the project's own
recurring lesson: claiming something parses based on having looked at the
grammar is not the same as having actually run it.

After the fix: 7/7 fixtures (now including the recovered `oop_features.cpp`),
131/131 real corpus, 3/3 ClassHoister tests, all passing.

## Update: most-vexing-parse fix in CodeGen, and a parser bug it surfaced

Following up on a direct question about whether this project's `CodeGen`
correctly avoids C++'s most-vexing-parse the way the original
`writeSketch()` does (which rewrites direct-init `Type name(args);` to
`Type name{args};` specifically to dodge it): confirmed via a direct,
deliberate g++ test that the risk is real, not theoretical --
`Eye e1(Bar());` (a constructor argument that is itself a bare-type-
constructing expression) is silently parsed by g++ as a FUNCTION
DECLARATION (`e1`'s type becomes `Eye(Bar(*)())`), not an object,
confirmed by trying to access a member on `e1` and getting "request for
member 'member' in 'e1', which is of non-class type 'Eye(Bar (*)())'".

Also confirmed, separately, that the actual real/synthetic corpus's only
example of this pattern (`Eye e1(250, 16, 120);`, plain literal args) does
NOT trigger the ambiguity -- g++ correctly treats it as object
construction either way. This project's `CodeGen` was rendering this
shape with parens, which was therefore correct for everything in the
corpus but not for the general case.

**Fix**: `CodeGen.emitDeclaratorTail` now renders this shape with braces
(`Eye e1{250, 16, 120};`), matching the original's blanket safety margin
rather than only the narrower behavior the corpus happened to require --
brace-init is unambiguous in every case, so there's no reason to keep the
riskier rendering once the failure mode is understood.

**Bug this surfaced**: making this change immediately broke 3 of 9
fixtures in the round-trip test (`handles.cpp`, `oop_features.cpp`,
`batch2_...cpp`) with `expected ';' but found '{'` -- the PARSER had
never been taught to accept brace-direct-init (`Type name{args};`) as
input at all, even though it's valid C++ and is exactly what `CodeGen`
now produces. This meant the parser couldn't read back its own generated
output. Fixed by adding the missing grammar support (symmetric with the
already-supported paren-direct-init form) in both declarator-parsing call
sites (top-level and statement-level) and their corresponding lookahead
checks.

This is a good illustration of why the round-trip test (parse -> generate
-> reparse -> generate again -> compare) earns its keep: a change that
was correct in isolation (the brace-rendering fix) immediately exposed an
unrelated, pre-existing grammar gap (brace-direct-init was never
parseable) that no prior test had reason to exercise, since nothing had
ever produced that exact syntax as input before.

After the fix: 9/9 fixtures, 131/131 real corpus, all other pass test
suites, still passing. Added a dedicated test
(`checkMostVexingParseAvoided` in `CodeGenRoundTripTest`) with a direct
g++ check on the exact ambiguous case, not just the rendered text shape.

- `ClassHoister` (ported from `CppClassHoister`): **done**, tested, passing.
- `ArrayHoister` (ported from `CppArrayHoister`): **done**, tested against
  real corpus data (`storing_input.cpp`'s "const int num; float mx[num];
  float my[num];" pattern, the actual real-world shape this pass exists
  for). Simplified relative to the port source since this AST's
  `VariableDecl` is already single-declarator (no declarator-splitting
  logic needed -- see ArrayHoister's javadoc for why).
- `NameUsageScanner` (ported, same name): **done**, tested indirectly via
  `DependencyHoisterTest`. Rewritten using exhaustive pattern-matching
  switches over this codebase's sealed `Expr`/`Statement` hierarchies
  instead of an if-instanceof chain.
- `DependencyHoister` (ported from `CppDependencyHoister`): **done**,
  tested against `handles.cpp`'s real dependency (`bool firstMousePress`,
  a top-level global referenced as a bare identifier inside
  `Handle::pressEvent()`, correctly hoisted once `Handle` is hoisted to
  namespace scope; `handles`, the `ArrayList<Handle>` global, correctly
  stays behind since nothing inside `Handle`'s own body references it).
  Dedup logic was changed from the port source's codegen-text-equality
  approach to identity-based equality, since this AST's nodes are never
  regenerated/rewritten mid-pipeline the way the port source's model
  apparently anticipated -- see `DependencyHoister`'s javadoc for the
  full reasoning.
- `CodeGen` (ported and EXTENDED from `CppCodeGen`): **done**, tested via
  round-trip (parse -> generate -> reparse -> generate again -> compare)
  against all 9 fixtures, PLUS direct g++ syntax validation on the
  generated output (zero errors, only benign unused-variable warnings --
  same signature as every earlier real-source g++ check in this project).
  Two deliberate extensions beyond the port source: comment re-emission
  (the port source explicitly didn't attempt this; this codebase's AST
  preserves leadingComments on every node specifically so this payoff
  was possible) and optional `#line` directive emission per top-level
  item, which the original architecture doc called for as part of this
  exact stage.

  Two real bugs found and fixed during this port, both via the round-trip
  test and direct output inspection -- NOT caught by a clean compile:
  1. Function-pointer variable declarations rendered with the name in the
     wrong position ("int (*)(int, int) funcPtr" instead of the only
     valid C++ form, "int (*funcPtr)(int, int)") -- the generic "render
     type, then append name" pattern every other declaration shape uses
     doesn't work for this one shape, since the name must be interleaved
     INSIDE the type's parens. Fixed with a dedicated
     `renderTypeAndName` helper used everywhere a declarator is emitted.
  2. The direct-init pattern ("Handle a(5);", represented internally as a
     self-named CallExpr per CallExpr's design notes) rendered as
     "Handle a = a(5);" -- stable across a second round-trip (so the
     round-trip-equality check alone didn't catch it) but wrong: not
     what the user wrote, and borderline-nonsensical to read. Caught by
     reading the generated output directly, not by an automated check --
     a real test for this exact case was added immediately after, so it
     won't regress silently next time.

This completes porting every module from the parallel implementation.
All six analysis/generation passes (`ClassHoister`, `ArrayHoister`,
`NameUsageScanner`, `DependencyHoister`, `JavaArrayCheck`,
`LifecycleRewriter`) plus `CodeGen` are now implemented against this
codebase's AST, each with a real test against actual corpus data (not
just "it compiles"), and the full parser/lexer continues to pass 100% of
the real example corpus (131/131) and all synthetic fixtures (9/9).

## Update: PipelineCompositionTest finds two real bugs, neither caught by any prior test

Every pass up to this point had only ever been tested in ISOLATION --
ClassHoisterTest only calls ClassHoister, ArrayHoisterTest only calls
ArrayHoister, etc. Nothing had confirmed the passes actually COMPOSE
correctly in writeSketch()'s real order, or that a pass behaves the same
way when handed another pass's OUTPUT instead of a freshly-parsed AST.
Added `PipelineCompositionTest`, which runs all eight ported passes in
the real pipeline order on real fixture data and validates the combined
result with an actual g++ compile check (via a small, fixture-specific
stub for `_PSketch` and the handful of Processing API functions each
fixture calls -- intentionally narrow, not a general engine model).

This immediately found two real bugs that NO prior test -- including the
131-file real-corpus parser sweep -- had caught:

**Bug 1: `LifecycleRewriter` incorrectly marked unrelated class methods
as `override`.** The pass recursed into `TypeDef.members()` using the
SAME lifecycle-name-matching rule used for true top-level functions, so
a user-defined class method that happens to share a name with a sketch
lifecycle method (e.g. `class Drawable { void draw() {...} }`, confirmed
real by `oop_features.cpp`) was incorrectly marked `override` -- which
doesn't compile, since `Drawable::draw()` doesn't actually override
anything. The original `CppBuild.java` never has this ambiguity at all,
because of pipeline ORDER: classes are always hoisted out via
`hoistClassesOnly()`/`removeHoistedClasses()` BEFORE
`rewriteAsSketchMethods()` (the method this pass ports) ever runs, so a
class member's body is never even passed to it. This port's first
version recursed into class members anyway and applied the wrong rule
there. Fixed by splitting `rewrite()` into a top-level path (applies both
the override rule and the pointer-defaulting rule) and a class-member
path (applies ONLY pointer-defaulting, never override-marking) --
matching the original's actual scoping, not just its line-by-line logic.

**Bug 2 (more serious): `looksLikeDeclaration()`'s lookahead had no
exclusion for statement-introducing keywords at all**, meaning
`return foo(1, 2);` was silently parsed as a `DeclStatement` --
`parseTypeRef()` happily consumed the keyword `return` itself as if it
were a bogus type name (since `parseTypeRef`/`parseQualifiedTypeName`
accept ANY KEYWORD-or-IDENTIFIER token as a type-name segment, correctly,
for real type keywords -- the bug was that nothing ever excluded the
OTHER kind of keyword), then "foo" became the declared name, and
"(1, 2)" became a direct-init argument list. The resulting AST was a
syntactically-different-but-still-structurally-valid `DeclStatement`,
which round-tripped through `CodeGen` as `return foo{1, 2};` with NO
exception anywhere in the chain -- meaning the existing
`ParserCorpusSweep` (which only checks "did this fail to parse") could
never have caught it, and didn't, despite this exact pattern being
present in real corpus files (`Handles.pde`, `Scrollbar.pde`) and in
this project's own `handles.cpp`/`batch2_...cpp` fixtures the whole
time. Fixed by adding an explicit `STATEMENT_KEYWORDS` exclusion set
(`return`, `if`, `for`, `while`, `do`, `switch`, `break`, `continue`,
`try`, `delete`, `case`, `default`, `else`) checked unconditionally at
the very start of `looksLikeDeclaration()`.

**The real lesson, worth stating plainly**: a corpus sweep that only
checks "did parsing throw an exception" cannot catch a bug that produces
a wrong-but-still-valid AST shape. Both of these bugs are exactly that
class of bug. The pipeline composition test caught them specifically
because it checks something stronger: "does the OUTPUT actually mean
what the INPUT meant," via a real compiler, not "did something happen
without crashing."

After both fixes: 9/9 fixtures, 131/131 real corpus, all 9 other pass
test suites, all still passing. `PipelineCompositionTest` itself: 1 of 4
fixtures (`oop_features.cpp`) now fully passes end-to-end; the other 3
fail only on test-harness stub limitations (no `Sketch : public PApplet`
free-function-forwarding wrapper built yet, `ArrayList<T>` stub lacking
Java-style `.get()`/`.add()`, narrow stub function overload sets) --
confirmed by reading each remaining failure individually, not assumed --
none of which are pass-logic bugs, all of which are exactly the kind of
"context not yet ported" gap already flagged elsewhere in this document.

## Update: PipelineCompositionTest upgraded to use the REAL Processing API, finds a live bug

The previous version of `PipelineCompositionTest` used a small,
hand-written stub of the Processing API (a dozen or so guessed function
signatures) since the real `Processing.h` unconditionally `#include`s
`<GL/glew.h>` and `<GLFW/glfw3.h>`, unavailable in this sandbox with no
network access to fetch them. Several of that version's "failures" were
confirmed to be artifacts of the GUESSED stub being wrong (mismatched
overload arity for `fill()`/`background()`, missing `width`/`height`/
`mouseX` as in-scope names) rather than real pass bugs.

Upgraded the test to use the REAL `src/Processing_api.h` (shipped with
CppMode, included in this upload) as ground truth: mechanically extracted
all 162 real function signatures from it (see
`fixtures/extracted_real_api_stub.h`), and rebuilt the stub's `_PSketch`/
`PApplet` struct shapes and `ArrayList<T>` wrapper to match the real
engine's actual design (Java-reference-semantics `get()` returning `T*`,
not `T`; `Sketch : public PApplet` actually wrapping the rest of the
sketch, not free functions; a real distinct `color` type rather than a
bare `unsigned int` alias, since that alias caused a genuine overload
ambiguity against `background(float)` that a real distinct type
wouldn't have). Still not a full compile -- GL/GLFW themselves remain
out of reach -- but every other part of the real engine's declared
surface is now real, not guessed.

This immediately found a real, currently-LIVE bug in `CodeGen` (already
wired into `CppBuild.java`'s actual build pipeline at this point, not
just the standalone project): **`emitVariableDecl` never emitted the
`const` qualifier at all.** `vd.isStatic()` was checked and rendered;
`vd.isConst()` was simply never referenced anywhere in the method. This
silently dropped `const` from every top-level `const` variable's
generated output -- confirmed concretely by `storing_input.cpp`'s real
`const int num = 60;` followed by `float mx[num];`: the array declaration
needs `num` to be a genuine compile-time constant, and g++ correctly
rejected `float mx[num];` once `num` lost its `const` and became an
ordinary runtime `int`. Fixed in both the standalone project's `CodeGen`
and -- since this bug was already live in the real build -- the actual
`src/java/CodeGen.java` directly.

A related, NOT-yet-fixed gap was found and deliberately left as a
recorded known gap rather than rushed: `DeclStatement` (the
statement/local-scope declaration node, as opposed to `VariableDecl` for
top-level/field declarations) has NO `isConst` field at all -- a local
`const int x = 5;` inside a function body would lose its `const`-ness
the same way, but there's no way to even fix this at the CodeGen level
since the AST itself never captured it in the first place. Checked the
real 131-file corpus directly: zero files have a local/statement-scope
`const` declaration anywhere. Given zero corpus evidence and the larger
scope of a proper fix (new AST field, parser changes, LifecycleRewriter
updates, new tests), this is recorded as a known gap rather than
guessed at -- same treatment as the constexpr/static_assert gap
documented earlier.

After the fix: all 4 PipelineCompositionTest fixtures now pass FULLY
end-to-end against the real API surface (previously only 1 of 4 passed,
and even that one's "pass" was against the guessed stub, not real data).
Also reran the full 9/9 fixture and 131/131 real-corpus parser sweeps
plus all 9 other pass test suites -- all still green, confirming the fix
didn't regress anything else.

Everything below this line is now historical -- it describes the state
BEFORE `writeSketch()` was rewritten to actually call this pipeline. As
of the "wired in" update further down, `JavaArrayCheck`, `LifecycleRewriter`,
`ClassHoister`, `PSketchInjector`, `ArrayHoister`, `DependencyHoister`,
`ForwardDeclGenerator`, and `CodeGen` are all called directly from
`CppBuild.java`'s `writeSketch()` method, replacing the original's
regex/character-walking hoisting and rewriting passes. The "real
discrepancy" noted below (CodeGen's `public:` injection being more
complete than the original's `_PSketch`-conditional version) is now live
build behavior, not a flagged-for-later difference -- confirmed
intentional and kept, since it's a strict improvement (fixes a latent
private-access bug in the original) not a behavior regression.

The historical record below (each module's individual port, the bugs
found while porting each one, and the eventual real-pipeline wiring) is
kept for context on how this was built and validated, not as a
description of current incompleteness.

## Update: consolidated 73 flat src/java/ files down to 21

After flattening every AST/pass file into `src/java/` and rewiring
`writeSketch()` to call them directly (see the "Wired in" update below),
the directory held 73 files -- 11 original, plus 62 new ones, most of the
new ones being individually tiny (the AST node files average ~15-25
lines each, one type per file, matching the organized multi-package
layout in `tools/cpp-parser/` that layout was copied from).

Consolidated into 5 new grouped files plus the existing
standalone-but-substantial ones, with NO logic changes -- purely a file-
count reduction:

- `AstCore.java`: `Node`, `TypeRef` + its 3 variants, `Param` (6 files -> 1)
- `AstExpr.java`: all 18 expression node types (18 files -> 1)
- `AstStmt.java`: all 17 statement node types (17 files -> 1)
- `AstDecl.java`: all 10 declaration node types (10 files -> 1)
- `AstPasses.java`: the 6 smaller analysis passes -- `ClassHoister`,
  `ArrayHoister`, `NameUsageScanner`, `PSketchInjector`,
  `ForwardDeclGenerator`, `EnumScopeExtractor` (6 files -> 1)

`DependencyHoister.java`, `LifecycleRewriter.java`, and `CodeGen.java`
stayed as standalone files since each is substantial enough on its own
(150-630 lines). `Parser.java` and `ParseException.java` also stayed
standalone for the same reason.

Every top-level type that was previously `public` (needed when each type
lived in its own file under a different package, so other packages like
`cppmode.parser.ast.expr` could be referenced from `cppmode.parser`) had
its `public` modifier removed -- now package-private, since everything
that uses these types (`Parser`, `CodeGen`, every pass) lives in the same
flat `processing.mode.cpp` package already. `public static` methods on
otherwise-package-private classes were left as `public` (conventional
and harmless -- visibility is still bounded by the enclosing class).

Verified the consolidation changed nothing behaviorally: recompiled the
full flattened+consolidated set against the same minimal external
stand-ins used to verify the original flatten (clean compile, same one
pre-existing deprecation note), then re-ran the exact same pipeline calls
against `handles.cpp`, `kitchen_sink.cpp`, and `oop_features.cpp` and
confirmed IDENTICAL output (same item counts, same hoisted-class/var/
array/function counts, same codegen output length) to every prior run
before consolidation, plus a full 131/131 real-corpus parse sweep.

`writeSketch()` does considerably more around class hoisting than
`ClassHoister` alone replaces: `_PSketch` injection (ported, see above),
forward-declaration generation for hoisted functions (also now ported,
see `ForwardDeclGenerator` below), and `enumScope`/`constexprScope`
extraction that happens before hoisting even starts (NOT yet ported).
Wiring `ClassHoister`/`ArrayHoister`/`DependencyHoister` in without also
wiring their full surrounding context would silently drop real,
load-bearing behavior -- confirmed by reading `writeSketch()` closely
while porting each piece, not assumed. Each remaining piece should get
the same treatment (read closely, port deliberately, test against real
fixtures AND, where the original behavior is about compile-correctness
rather than just textual shape, validate with a real g++ check, not just
inspect the rendered text) before any of this is wired into the real
build, per the project's staged-integration discipline.
`checkForUnsupportedJavaArraySyntax` remains the only piece actually
swapped into `CppBuild.java` so far, specifically because it's
self-contained and doesn't interact with any of this surrounding
machinery.

- `ForwardDeclGenerator` (new -- ports the inline forward-declaration
  regex in `writeSketch()`, not a separately-named method in the
  original): **done**, tested two ways -- (1) the rendered text shape is
  correct, and (2) a real g++ compile check on the FULL scenario this
  pass exists for: a class whose method calls a free function, with the
  class appearing in the emitted file BEFORE that function's full
  definition, bridged only by the generated forward declaration. Also
  confirmed the negative case directly: the same code WITHOUT the
  forward declaration genuinely fails with g++'s real
  "was not declared in this scope" error, proving the positive test
  wasn't accidentally passing for an unrelated reason.

  Needed essentially no new logic at all -- a forward declaration of a
  `FunctionDecl` is just the same `FunctionDecl` with `body` set to
  `null`, and `CodeGen.emitFunctionDecl` already rendered that shape
  correctly (as plain "ReturnType name(params);") since that's also the
  correct rendering for an ordinary bodyless C++ declaration, a case
  CodeGen needed to support anyway and had already implemented before
  this pass existed to make use of it.

- `EnumScopeExtractor` (new -- ports the enum-extraction half of
  `writeSketch()`'s combined enumScope/constexprScope block): **done**,
  tested against `kitchen_sink.cpp`'s three enums, including a
  deliberately-added multi-line `enum class CardSuit { ... }` spanning
  several lines -- specifically the shape the original needed a careful
  character-walking, brace-depth-tracking scan to handle correctly (a
  naive per-line scan only matches an enum's opening line, silently
  losing every enumerator below it). On this AST that entire problem
  doesn't exist: `EnumDecl` is already one complete node by the time the
  parser produces it, so there's no way for "half an enum" to occur in
  the tree at all. This pass is therefore just a TopLevelItem filter,
  same pattern as ClassHoister/ArrayHoister.

  **NOT PORTED, and explicitly recorded as such rather than faked**: the
  other half of that same original code block -- constexpr declarations,
  static_assert, and type-alias "using Name = Type;" declarations.
  Checked directly: zero files in the real 131-file example corpus use
  any of "constexpr", "static_assert", or a type-alias "using" anywhere.
  This AST also has no constexpr concept at all (VariableDecl/
  FunctionDecl only track isConst/isStatic). Given zero corpus evidence
  either for what real sketches need OR for validating any AST design
  choice here, building speculative support now would be untested
  guesswork dressed up as a port. This is deliberately left as a known
  gap, to be designed and tested properly if and when a real sketch
  (or even just a deliberately-written fixture) is found actually using
  one of these constructs -- not retrofitted blind ahead of any evidence.

## Update: widened PipelineCompositionTest to all 9 fixtures, found and fixed two real structural bugs

Widened `PipelineCompositionTest` from 4 to all 9 fixtures (the
remaining 5 hadn't been run through the full pipeline+g++ check before).
This found two real, structural bugs -- both already LIVE in the actual
wired-in `writeSketch()`, not just the standalone test -- and confirmed
one pre-existing limitation that isn't a regression from this work.

**Bug 1: `#include`/`namespace`/`using namespace` lines were placed
INSIDE `struct Sketch`.** Confirmed by `batch2_..._namespaces.cpp`'s real
top-level `#include <functional>`/`#include <string>` lines: the
pipeline's final assembly unconditionally wrapped everything in
`depResult.rest` inside `struct Sketch { ... }`, including
`PreprocessorLine`/`NamespaceDecl`/`UsingNamespaceDecl` nodes, which are
never valid inside a struct body. This corrupted the ENTIRE rest of the
generated file (a cascade of "expected unqualified-id before 'namespace'"
errors through the entire C++ standard library, since `#include
<functional>` landing mid-struct broke every subsequent system header's
own internal namespace blocks). Fixed by extracting these node types
before the Sketch-wrapping step and emitting them at the very FRONT of
the generated file (ahead of even forward declarations, since a
forward-declared signature could in principle need something from one of
these includes). Fixed in both branches of the real `writeSketch()`
(static-mode and normal-mode) and in `PipelineCompositionTest` itself.

**Bug 2: out-of-class static member definitions were ALSO placed inside
`struct Sketch`.** Confirmed by `kitchen_sink.cpp`'s real
`int Counter::count = 0;` (a `VariableDecl` whose name is itself
`::`-qualified, confirmed real and necessary back when `Counter::count`
was first found requiring qualified-name support in declarator parsing,
much earlier in this project) -- same fundamental problem as bug 1, but
needing DIFFERENT placement: a static member definition must come AFTER
the class it qualifies is declared, so "front of file" (correct for
includes) would be wrong here (referencing `Counter` before it exists).
Fixed by routing `VariableDecl`s with a `::`-qualified name to a separate
list, emitted after the hoisted classes but before `Sketch`.

**Confirmed NOT a bug, a pre-existing structural limitation**: the one
remaining `PipelineCompositionTest` failure
(`batch2_..._namespaces.cpp`'s `int (*funcPtr)(int, int) = someFunc;`)
fails because `someFunc`, staying a `Sketch` member (correctly -- nothing
hoisted references it, so `DependencyHoister` correctly leaves it alone),
becomes a non-static member function once wrapped in `Sketch`, and a
pointer-to-member-function has a different, incompatible type from a
plain function pointer in C++ -- a real language fact, not a pipeline
bug. Confirmed by reading the ORIGINAL CppBuild.java's free-function
hoisting trigger condition directly: it only ever hoisted a function when
a HOISTED CLASS called it, exactly matching DependencyHoister's actual
behavior -- meaning the original regex-based pipeline would have hit this
exact same compile failure for this exact same sketch shape. Not a
regression introduced by this work; a known, pre-existing limitation of
the whole "free functions become Sketch members" design, now explicitly
recorded rather than silently present.

After both fixes: 8 of 9 `PipelineCompositionTest` fixtures pass fully
end-to-end (up from 4 of 9 before this update); the 9th fails only on
the confirmed pre-existing limitation above. Also closed two genuine test-
harness stub gaps found along the way (missing `sqrt`/`<cmath>`, missing
a `pixels` array) -- real gaps in the test's own completeness, not pass
bugs. Reran the full 9/9 fixture and 131/131 real-corpus parser sweeps
plus all 9 other pass test suites -- all still green.

Wired directly into `CppBuild.java`'s `writeSketch()` -- no longer a
standalone, unused component. `JavaArrayCheck`, `LifecycleRewriter`,
`ClassHoister`, `PSketchInjector`, `ArrayHoister`, `DependencyHoister`,
`ForwardDeclGenerator`, and `CodeGen` are all called directly from the
real build pipeline, replacing the original's regex/character-walking
hoisting and rewriting passes. Every literal scaffolding string
`writeSketch()` emits (the `#include`s, `using`-declarations, the
`_PSketch` struct body, free-function forwarding, `main()`) is unchanged
from the original.

Validated as thoroughly as is honestly possible without the real
`processing4` checkout or GLFW/GLEW: a full recompile of the flattened,
consolidated `src/java/` against minimal stand-ins for only the
genuinely-external `processing.app.*`/sibling-IDE classes (clean, one
pre-existing unrelated deprecation note); the full 131-file real-corpus
parser sweep and 9-fixture synthetic sweep (all passing); all 9 isolated
pass-test suites (all passing); and `PipelineCompositionTest`, which
runs the complete pipeline end-to-end against 4 real fixtures and
validates the output with a real g++ syntax check against the REAL
Processing API's 162 extracted function signatures -- all 4 now passing
fully.

Two real, previously-undetected bugs were found and fixed via that last
test specifically (not by inspection, not by the parser's own corpus
sweep): `LifecycleRewriter` incorrectly marking unrelated class methods
as `override`, and `looksLikeDeclaration()` silently misparsing
`return foo(...);` as a declaration. A third, separately found while
upgrading the test to use real API data: `CodeGen` never emitted `const`
for top-level variable declarations at all, which was a LIVE bug in the
already-wired-in build, not just the standalone project -- fixed in both
places.

Known, deliberately-unaddressed gaps, each backed by a real corpus check
rather than assumed: `constexpr`/`static_assert`/type-alias `using`
declarations (zero corpus usage), and local/statement-scope `const`
declarations losing their `const`-ness on output (also zero corpus
usage, and the AST itself doesn't currently have a field to carry that
information for `DeclStatement` the way it does for `VariableDecl`).

What remains unverifiable from this environment: the actual
`./gradlew :java:compileJava` against the real `processing4` checkout,
and a real sketch build/run with actual GLFW/GLEW/OpenGL linked in. Both
require the real machine this was built for.

## Update: considered going further (real GL/GLFW), decided against it with evidence

Considered closing the very last gap -- building minimal GLFW/GLEW
header stubs so `-fsyntax-only` could run against the REAL
`Processing.h`/`Processing_api.h` directly, instead of this project's
own parallel hand-built stub. Counted the actual surface needed: 9
distinct GLFW/GLEW symbols and 43+ distinct raw OpenGL function calls
inside `Processing.h`. Decided against building this stub, for a
concrete reason rather than just effort: hand-writing 40+ GL function
signatures risks getting parameter types subtly wrong (e.g. `GLenum` vs
`GLuint` vs plain `int`) in a way that would still COMPILE but wouldn't
actually validate anything real -- false confidence, the same risk
already flagged when this decision was first considered.

Checked whether this gap actually matters for what this project's
pipeline generates: confirmed directly that ZERO files in the real
131-file corpus call any raw GL function or reference any `GL_*`
constant. `Sketch_run.cpp` (what `writeSketch()` actually generates)
only ever needs to type-check against the `Processing_api.h` wrapper
layer -- already validated with real, mechanically-extracted data -- not
against raw GL, which only matters for `Processing.h`'s own internal
engine implementation, never for generated user-sketch code. The
marginal value of stubbing GL is real GL/GLFW are never something this
project's output depends on type-checking against.

Separately, out of curiosity about how this project's own from-scratch
parser would fare against the real engine source itself (not user
sketches): ran `Processing.h` directly through this project's `Parser`.
It failed -- "expected ';' but found 'operator'" at a free-function
(non-member) `operator+` overload
(`inline std::string operator+(const std::string& s, int n) {...}`).
Checked whether this is a real gap or a confirmed, intentional scope
boundary: zero files in the real corpus use a free-function operator
overload (the corpus's one confirmed operator-overload case,
`oop_features.cpp`'s `Handle::operator==`, is a CLASS MEMBER, the only
form ever exercised or needed). `Processing.h` itself is never run
through `writeSketch()`'s pipeline at all -- only user `.pde` content
is -- so this isn't a real gap in scope, it's confirmation that the
parser's original scope decision (member operator overloads only,
documented from the very start of this project) remains correct and
sufficient for everything the pipeline actually needs to parse.

8 of 9 real/synthetic fixtures now pass FULL end-to-end pipeline
composition (parse through g++ syntax check against the real Processing
API), the 9th failing only on a confirmed pre-existing language
limitation (see above), not a bug in this work. Three real, previously-
undetected structural bugs have been found and fixed entirely through
this testing process, each already live in the real, wired-in
`writeSketch()` at the time it was found: const-qualifier loss on
top-level variables, `#include`/namespace misplacement inside a struct
body, and out-of-class static-member-definition misplacement. All three
are fixed in both the standalone project and the real `src/java/`
directly.

## Update: closed the last untested code path -- writeSketch()'s static-mode branch

Every `PipelineCompositionTest` fixture up to this point had real
`setup()`/`draw()`, meaning `writeSketch()`'s STATIC-MODE branch (no
lifecycle methods at all -- a flat sequence of top-level statements, the
Processing "static mode" feature that originally motivated the
`TopLevelStatement` AST node much earlier in this project) had never
been g++-validated, only the normal-mode branch had. This was a real,
concrete verification gap, not a hypothetical one.

Closed it by adding `Coordinates.pde` (the original real example that
motivated `TopLevelStatement` in the first place) as a fixture and a new
`runStaticModePipeline` test method that mirrors `writeSketch()`'s real
static-mode branch exactly (the settings/body split, the implicit
`setup()`/`draw()` wrapping, the `delay()`/`noLoop()` boilerplate).

Found one more genuine test-harness stub gap along the way (`delay()`
was never stubbed) -- not a pipeline bug, fixed by adding it. After that
fix, the static-mode branch passes fully: real top-level `size()`/
`background()`/`noFill()`/`stroke()`/`point()`/`line()`/`rect()` calls,
correctly split between the generated `setup()`'s settings section and
body, compiling clean against the real API surface.

`PipelineCompositionTest` now covers BOTH of `writeSketch()`'s real
branches (static-mode and normal-mode) against real fixtures and the
real Processing API -- 9 of 10 total checks passing, the 1 failure being
the already-documented pre-existing function-pointer-to-member-function
limitation, not a new finding. Reran the full fixture (10/10) and
real-corpus (131/131) parser sweeps -- still green.

## CURRENT FINAL STATUS (updated again)

9 of 10 pipeline-composition checks pass fully end-to-end against the
real Processing API (both writeSketch() branches now covered: normal-
mode across 9 fixtures, static-mode against the real Coordinates.pde).
The 1 failure is the confirmed pre-existing function-pointer-to-member-
function language limitation, not a bug in this work. Four real,
previously-undetected structural bugs have been found and fixed entirely
through this testing process, each already live in the real, wired-in
writeSketch() at the time found: const-qualifier loss on top-level
variables, #include/namespace misplacement inside a struct body,
out-of-class static-member-definition misplacement, and (earlier in the
project) the override-marking and return-statement-misparsing bugs. All
fixes are applied in both the standalone project and the real
src/java/ directly.

Deliberately decided against building GL/GLFW stubs to chase full
real-header compilation, with concrete evidence backing the decision
(zero real-corpus GL usage) rather than just effort-avoidance. Confirmed
the parser's original member-operator-overload-only scope decision
remains correct and sufficient by testing it against the real engine
source itself, not just user sketches.

Known, deliberately-unaddressed gaps, each backed by a real corpus check:
constexpr/static_assert/type-alias using declarations, and local/
statement-scope const declarations losing their const-ness on output.

What remains unverifiable from this environment: the actual
./gradlew :java:compileJava against the real processing4 checkout, and
a real sketch build/run with actual GLFW/GLEW/OpenGL linked in.

## Update: real processing4 Gradle build attempted -- found and fixed the first actual compile error

First real signal from the actual `processing4` checkout this was built
for: ran `./scripts/setup-dev-symlinks.sh` then `./gradlew :java:compileJava`
for real. Result: every other file compiled clean (`:core:compileJava`,
`:app:compileKotlin`, `:app:compileJava` all succeeded with only
pre-existing, unrelated deprecation warnings) -- `:java:compileJava`
failed with exactly one error:

    AstPasses.java:262: error: patterns in switch statements are a
    preview feature and are disabled by default.
        case Identifier id -> !callOnly && id.name().equals(name);

`NameUsageScanner`'s `visit()` method used a Java 21 pattern-matching
switch expression (`switch (n) { case Identifier id -> ...; ... }`).
This compiled cleanly throughout development against the sandbox's
default `javac` settings, but `processing4`'s real Gradle build doesn't
have switch pattern matching enabled (it's a preview feature on
whatever language level that build targets, even on a JDK where the
feature exists). This is exactly the kind of discrepancy that's only
findable by actually running the real build -- no amount of testing in
an isolated sandbox with different default compiler flags could have
caught it.

**Fix**: rewrote `visit()`'s entire body as a plain if-instanceof chain,
preserving every one of the 31 branches' exact logic -- no behavior
change, purely a syntax-level rewrite to avoid the preview-feature
construct. Considered the alternative (enabling `--enable-preview` in
Gradle) and rejected it as more invasive than just not using the
feature in this one file. Applied the identical fix in both
`src/java/AstPasses.java` (the real, wired-in flattened file) and the
standalone project's `NameUsageScanner.java` (pre-consolidation source
of truth), plus fixed a stale doc comment that referenced the
now-removed pattern-matching switch.

Verified the rewrite preserves behavior exactly, not just "compiles":
reran the full pipeline against `handles.cpp` and confirmed IDENTICAL
hoisting counts (1 class, 1 variable, 0 arrays, 0 functions) to every
prior run before this change, plus the full 131/131 real-corpus parse
sweep, all 9 isolated pass-test suites, and the pipeline composition
test (10/10, the 1 known pre-existing limitation unchanged) -- all still
green.

Searched the rest of `src/java/` for the same pattern-matching-switch
construct to rule out it being a wider, systemic issue: confirmed only
this one file used it (a broad grep flagged `CppBuild.java` too, but
checking directly showed that was a false positive from an unrelated
`case` label coincidentally followed by a capitalized identifier, not
real type-pattern syntax).

## Update: built RealCorpusStressTest -- full pipeline against all 131 real sketches, zero crashes

Built a genuine stress test, requested directly: `RealCorpusStressTest`
runs the COMPLETE pipeline (every pass, dispatching correctly between
static-mode and normal-mode exactly like the real `writeSketch()`) plus
a real g++ syntax check, across all 131 real example sketches -- not
just the 10 hand-picked `PipelineCompositionTest` fixtures. This is a
genuinely larger and more representative surface than anything tested
before.

**Headline result: zero pipeline crashes.** Every one of the 131 real
sketches parses and runs through every pass (EnumScopeExtractor,
LifecycleRewriter, ClassHoister, PSketchInjector, ArrayHoister,
DependencyHoister, ForwardDeclGenerator, CodeGen) without a single Java
exception. This is the strongest evidence yet that the pipeline's LOGIC
is sound across the real corpus, not just on hand-picked fixtures.

One real bug found IN THE STRESS TEST ITSELF while building it (not in
the pipeline): the test's first version only ever ran the normal-mode
pipeline, unconditionally, even for static-mode sketches (no
`setup()`/`draw()`) -- producing the same "bare statement directly
inside struct Sketch" structural error the real `writeSketch()` already
had fixed. Fixed by adding the same `hasSetup`/`hasDraw` dispatch the
real `writeSketch()` uses. After the fix: 60 of 131 g++-clean (up from
51), zero crashes either before or after (the dispatch bug was a
text-shape problem g++ caught, not a pipeline exception).

71 of 131 g++ failures remain, and a closer look shows essentially all
of them are explainable, not real pipeline bugs:

1. **Stub incompleteness** (the large majority): real Processing
   constants/types this test's stub never defined --
   `PI`/`TWO_PI`/`P3D`/`RGB`/`HSB`/`CLOSE`/`TRIANGLE_STRIP`/`ARGB`,
   wrapper classes `PShape`/`PVector`/`String`/`IntList`/`Table`/
   `JSONValue`, and methods on the engine's real wrapper classes this
   test's minimal `PImage`/`PFont`/`ArrayList<T>` stand-ins don't fully
   replicate (`PImage::get`, `PFont::list`, `ArrayList::remove`).
   `RealCorpusStressTest`'s stub was deliberately built narrow (same
   reasoning as `PipelineCompositionTest`'s), and 131 real sketches
   exercise a much wider slice of the real API than 10 chosen fixtures
   did -- this is expected, not a discovery of pipeline bugs.

2. **A genuine stress-test-fidelity gap, NOT a pipeline bug, confirmed by
   reading the real source directly**: `boolean`/`String`-as-bare-type
   failures happen because `RealCorpusStressTest` calls `Parser.parse()`
   directly on raw `.pde` source, but the REAL pipeline
   (`writeSketch()`) runs a separate text-rewriting pass (`javaToC()`,
   handling `boolean`->`bool` among other things) BEFORE parsing.
   `javaToC()` is a private instance method requiring a full `CppBuild`
   object this isolated test can't easily construct, so it wasn't
   replicated -- meaning these specific failures are testing the parser
   against text the real pipeline never actually feeds it in that exact
   form, not a real gap in the parser or any pass.

Net assessment: this stress test's real, trustworthy finding is "zero
pipeline crashes across the full real corpus." The g++ failures are
overwhelmingly test-harness stub-completeness gaps (expected, given how
narrow the stub was built) plus one confirmed pipeline-vs-test-fidelity
gap (`javaToC` not replicated), not new evidence of pipeline bugs.
