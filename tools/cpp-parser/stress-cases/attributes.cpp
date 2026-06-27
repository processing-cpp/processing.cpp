// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly, no occurrences
//
// C++11 attributes ("[[maybe_unused]]", "[[nodiscard]]", "[[deprecated]]",
// etc.) prefixed onto a declaration are not recognized at all -- the
// parser sees a bare "[" where it expects a type or identifier and fails
// immediately. These are rare in Processing-style sketch code but are
// completely ordinary, modern C++.
void f() {
    [[maybe_unused]] int x = 5;
}
