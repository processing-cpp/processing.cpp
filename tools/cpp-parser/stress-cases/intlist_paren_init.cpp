// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: Histogram.pde -- "malloc(): unaligned tcache chunk detected" at
//        runtime, then "IntList index 206 out of bounds for length 1"
//        after bounds-checks were added to IntList::operator[].
//
// ROOT CAUSE: CodeGen's brace-init rewrite (which protects against C++'s
// most-vexing-parse for "Type name(args)" that could be a function
// declaration) was missing CppMode's own container types from its
// INITIALIZER_LIST_AMBIGUOUS_TYPES whitelist. All five types (IntList,
// FloatList, StringList, ArrayList, Array) have BOTH a length constructor
// AND an initializer_list constructor, so paren-init and brace-init
// produce completely different results:
//
//   IntList hist(256)  ->  256-element list, all zeros  (what user means)
//   IntList hist{256}  ->  1-element list, hist[0] == 256  (what got emitted)
//
// The crash was silent at the point of construction (no bounds check at
// the time) and only surfaced later when hist[bright]++ with bright in
// [0,255] wrote past a 1-element vector's buffer, corrupting malloc
// metadata. glibc's allocator caught it at the next unrelated allocation.
//
// FIXED: added "IntList", "FloatList", "StringList", "ArrayList", "Array"
// to INITIALIZER_LIST_AMBIGUOUS_TYPES in CodeGen.java (both trees).
// These now stay as paren-init, identical to how std::vector was already
// handled for exactly this reason (see Wolfram.pde in the project history).
void f() {
    IntList hist(256);
    FloatList vals(100);
    StringList names(50);
    ArrayList<int> items(20);
    Array<float> buf(512);
    hist[0]++;
    vals[0] = 3.14f;
}
