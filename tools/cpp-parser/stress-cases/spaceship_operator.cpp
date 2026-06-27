// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly, no occurrences
//
// C++20's three-way comparison operator "<=>" is lexed as two separate
// tokens ("<=" then ">"), not as a single operator -- the lexer's
// MULTI_CHAR_SYMBOLS list has no 3-character entry for it. CppMode
// targets C++17 per its own build flags, so this may never need fixing,
// but recorded here since it's a real, confirmed gap either way.
void f() {
    int x = 1 <=> 2;
}
