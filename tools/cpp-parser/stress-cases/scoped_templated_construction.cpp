// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: real corpus, Game_Of_Life.pde -- "cells.resize(cols, std::vector<int>(rows));"
// FIXED: Parser's ::-qualified scoped-name path now checks for a
//        following templated-construction-call suffix, matching the
//        check the bare-identifier path already had.
//
// A "::"-qualified templated type used as a CONSTRUCTION CALL (not just
// a bare type reference). Before the fix, this misparsed as a ScopedName
// followed by an unrelated comparison-chain expression.
void f() {
    int rows = 5;
    auto v = std::vector<int>(rows);
}
