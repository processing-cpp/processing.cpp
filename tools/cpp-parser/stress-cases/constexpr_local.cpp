// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: deliberate probing, zero corpus evidence
// FIXED: "constexpr" was not in the qualifier set tolerated by
//        looksLikeDeclaration() or consumed by parseDeclStatementsDesugared(),
//        so "constexpr int N = 5;" crashed identically to how "static int x"
//        did before the static-local fix. Treated as equivalent to "const"
//        for AST purposes (constexpr implies const).
void f() {
    constexpr int N = 100;
    constexpr float PI = 3.14159f;
    float arr[N];
}
