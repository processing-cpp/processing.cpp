// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: real corpus, Scrollbar.pde -- "HScrollbar* hs1, * hs2;"
// FIXED: Parser.parseOneTopLevelDeclarator's comma-continuation loop now
//        consumes a per-declarator leading "*"/"&" instead of assuming
//        every declarator shares the first one's pointer depth exactly.
//
// A multi-declarator line where a LATER declarator repeats its own
// pointer marker. Each declarator's pointer-ness is independent --
// "int* a, b;" only makes 'a' a pointer, "int* a, *b;" makes both
// pointers. This is the exact real bug that broke Scrollbar.pde.
void f() {
    int* a, *b;
    int x, y, z;
}
