// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly, no occurrences
//
// C++17 structured bindings ("auto [a, b] = pair;" / "auto& [a, b] = pair;")
// are not supported by parseOneDeclarator's declarator-name grammar, which
// only ever expects a single identifier after the type, never a
// "[name, name, ...]" destructuring pattern.
void f() {
    auto& [a, b] = pair;
}
