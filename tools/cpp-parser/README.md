# CppMode Java/C++ Parser -- Status

## What this is

A from-scratch lexer + recursive-descent parser for CppMode's Java-flavored
C++ grammar subset, built in Java (matching `CppBuild.java`'s language) so it
can eventually be integrated directly into the build pipeline as a
replacement for the regex/character-walking passes in `CppBuild.java`
(`classifyTopLevelDecls`, `blankCommentsAndLiterals`,
`checkForUnsupportedJavaArraySyntax`, the various hoisting passes, etc.).

**This has NOT been wired into CppBuild.java or the real build pipeline yet.**
It lives here as a standalone, independently-tested component. See
"Integration plan" below for why, and what's left before that's safe to do.

## Current status (as of this snapshot)

- **Lexer**: complete. Handles all C++ tokens needed by the grammar subset,
  including comments as first-class tokens (not stripped), preprocessor
  directives as opaque pass-through tokens, and the full numeric/string/char
  literal grammar.
- **AST**: complete. `TypeRef` (3 variants), `Expr` (16 variants), `Statement`
  (13 variants), declarations (7 `TopLevelItem` variants including
  `PreprocessorLine` and `TopLevelStatement`).
- **Parser**: complete for the scoped grammar (see original architecture
  notes for what's explicitly out of scope -- full template metaprogramming,
  multiple-inheritance edge cases beyond basic multi-base lists, macro
  expansion, etc).

## Test results

- `fixtures/` -- 6 synthetic stress-test files covering control flow, arrays,
  OOP features (inheritance, operator overloads, ctor init lists), lambdas,
  templates, function pointers, namespaces, and the four original real
  example sketches (LSystem, Mandelbrot, Button, Handles) used to derive the
  initial AST shape. **6/6 passing.**
- `real-corpus-snapshot/` -- all 131 `.pde` files from the real
  `examples/` directory shipped with CppMode. **131/131 passing.**

Run via:
```
javac -d out $(find src/main src/test -name "*.java")
java -cp out cppmode.parser.ParserCorpusSweep fixtures
java -cp out cppmode.parser.ParserCorpusSweep real-corpus-snapshot
```

## Real bugs found and fixed during corpus validation

Worth recording since they're exactly the class of bug this parser exists to
eliminate (comment-blindness, narrow regex assumptions) -- finding and fixing
analogous bugs in itself, via testing, is the whole point:

1. Leading-dot float literals (`.5`) weren't dispatched to numeric lexing.
2. `#include`/preprocessor lines had no token representation at all.
3. `->` was lexed as PUNCTUATION but several parser call sites checked it as
   OPERATOR (copy-paste inconsistency).
4. Bare templated-construction-call syntax (`ArrayList<Handle>()`) wasn't
   parseable as an expression at all -- `<`/`>` were only ever parsed as
   comparison operators in expression context.
5. Local-variable (statement-level) function-pointer declarations
   (`int (*funcPtr)(int,int) = f;`) were only handled at top level, not
   inside function bodies.
6. Destructor dispatch (`virtual ~LSystem() {}`) didn't look past a leading
   `virtual` keyword.
7. Out-of-class static member definitions (`int Counter::count = 0;`) --
   the declarator name itself can be `::`-qualified.
8. **Comments in the middle of a multi-line expression** (e.g. a function
   call with a trailing `//` comment after one argument, before the next)
   broke parsing entirely -- comments were only ever skipped at
   statement/declaration boundaries, not inside expression parsing. Fixed by
   routing comment-skipping through the core token-cursor primitives
   (`peek`/`advance`) so every call site is fixed at once. This was the
   single most structurally important bug found, and was only found by
   testing against real sketches, not synthetic fixtures (none of which
   happened to comment mid-expression).
9. Real top-level Processing "static mode" sketches (no `setup()`/`draw()`
   at all, just bare statements) weren't representable in the `TopLevelItem`
   grammar at all -- added `TopLevelStatement` as a wrapper.
10. `signed`/`unsigned`/`long`/`short` compound integer-type keywords
    (`signed char`, `unsigned long long`) weren't parsed as a single type.
11. C-style array parameters (`int data[]`) weren't handled in `parseParam`.
12. Unary dereference (`*ptr`) was missing from the unary-operator set
    (address-of `&` was added during the original corpus walk, but its
    counterpart was missed).
13. Direct-init with literal constructor arguments at top level
    (`Eye e1(250, 16, 120);`) was being misparsed as a function declaration
    with literal "parameter types".
14. The trailing `;` after a class/struct body, mandatory in strict C++, is
    confirmed OPTIONAL in real `.pde` files and is now optional in the
    grammar too.

## Integration plan

Per the original architecture decision (migrate one regex pass at a time,
validate against the corpus, keep the old pass as fallback -- no flag-day
replacement):

1. **DONE (this step).** Added the parser as an *additional, non-blocking*
   check in `CppBuild.java` that runs alongside (not instead of) the
   existing regex-based `checkForUnsupportedJavaArraySyntax`, logging
   disagreements without changing build behavior. See
   `src/java/CppJavaArrayCheck.java` (the flattened, build-compatible copy
   of this project's `JavaArrayCheck` -- see that file's javadoc and
   `DECISION_two_parser_implementations.md` for why a flattened copy
   exists) and the new `runJavaArrayCheckShadowComparison` method added to
   `CppBuild.java` immediately after the existing check.

   **Honest limitation**: this could not be fully build-verified from
   inside this repo. `CppBuild.java` depends on `processing.app.RunnerListener`
   and `processing.app.Sketch`, which live in the separate `processing4`
   checkout (see `scripts/setup-dev-symlinks.sh`) that wasn't available
   here. What WAS verified: the four new flattened files
   (`CppLexerTokenType`, `CppLexerToken`, `CppLexer`, `CppJavaArrayCheck`)
   compile cleanly together in isolation and produce identical results to
   the original, fully-tested `tools/cpp-parser` source on the same test
   cases. The `CppBuild.java` edit itself is syntactically self-contained
   (no new imports, same package, no dependency on anything outside what
   this repo already has) and was reviewed line-by-line, but the final
   `./gradlew :java:compileJava` needs to run on a machine with the real
   `processing4` checkout before trusting this in practice.

2. Once that's run clean against real user sketches for a while, consider
   replacing `classifyTopLevelDecls` (the hoisting-pass classifier) with a
   real AST-based tree-walk -- this is the actual motivating pain point
   from the original design discussion (the comment-spanning-newline bug
   already found and fixed in `classifyTopLevelDecls` itself is exactly the
   bug class a real parser eliminates structurally). `ClassHoister`,
   `ArrayHoister`, and `DependencyHoister` are already built and tested in
   `tools/cpp-parser/` for this step, whenever it's taken.
3. Only after that's solid, consider the full code-generation stage
   (AST -> C++ source text with `#line` directives) as a replacement for
   the various hoisting/rewriting string-splicing passes. `CodeGen` is
   already built, round-trip-tested, and g++-validated in
   `tools/cpp-parser/` for this step.

This snapshot reflects step 1 of that plan actually applied to
`CppBuild.java`, plus steps 2 and 3's components fully built and tested
ahead of time in `tools/cpp-parser/`, ready to be wired in when their turn
comes.
