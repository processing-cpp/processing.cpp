# stress-cases/

Drop a new `.cpp` file here any time you want to probe whether the
parser handles some C++ construct. No code changes needed anywhere
else -- `StressCasesRunner` (run via `scripts/stress-test.py`, Layer 5)
automatically picks up every `.cpp` file in this folder on every run.

## Format

Every file needs a header comment declaring what SHOULD happen:

```cpp
// EXPECT: PASS
// (optional) CATEGORY: whatever
// (optional) FOUND: where this came from
// (optional) any other notes
//
// A short explanation of what this is testing.
your_actual_test_code_here();
```

`EXPECT` must be either `PASS` or `FAIL`:

- **`PASS`** -- this construct MUST parse and pass through codegen
  without throwing. If it ever stops, that's a real regression, and
  the test run exits non-zero with a clear "REGRESSION" message naming
  the file.

- **`FAIL`** -- this is a KNOWN, currently-unsupported construct.
  Recording an honest "this doesn't work yet" is just as valuable as a
  passing test -- it's evidence-gathering, not failure. If a FAIL case
  ever starts passing (because something else you fixed happened to
  cover it too), the runner reports it as `NOW-PASSES` rather than
  treating it as a problem -- worth updating that file's header to
  `PASS` once you've confirmed it's deliberate, not a fluke.

## What kind of file belongs here

Two kinds, both useful:

1. **Probes** -- you (or anyone) deliberately throwing an unusual,
   adversarial, or just plain weird piece of C++ at the parser to see
   what happens. Doesn't matter if it's something a real Processing
   sketch would ever actually write -- the point is finding the edges
   of what this parser does and doesn't handle, on purpose, before a
   real sketch finds them for you.

2. **Regression guards** -- once a real bug is found and fixed
   (anywhere in this project, not just from this folder), the exact
   shape that broke it belongs here as a permanent `EXPECT: PASS` case,
   so it can never silently break again without this test screaming
   about it immediately.

## Before adding a new case, it's worth checking the real corpus

If you're unsure whether something matters, a quick:

```bash
grep -rn "whatever_construct" tools/cpp-parser/real-corpus-snapshot/
```

...tells you whether any real example sketch actually uses it. Zero
hits doesn't mean don't bother -- it just means note it honestly in the
file's header comment ("CORPUS EVIDENCE: zero, checked directly") rather
than implying it's a confirmed real-world need when it isn't. Both
kinds of finding are useful; just be precise about which one it is.

## Running it standalone

```bash
cd tools/cpp-parser
java -cp out cppmode.parser.StressCasesRunner stress-cases
```

Or just run `scripts/stress-test.py` from the project root -- this is
Layer 5, and it always runs (no GLFW3/GLEW or g++ needed, since this is
pure parser+codegen, no compile-check against the real engine).
