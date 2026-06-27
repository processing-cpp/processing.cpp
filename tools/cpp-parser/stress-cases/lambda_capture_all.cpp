// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: deliberate adversarial probing -- a real bug
// FIXED: parseCapture() unconditionally called expectIdentifier(),
//        assuming every capture is a named identifier, with no check
//        for a bare "=" or a lone "&" (capture-ALL, not a named
//        by-reference capture) first. Fixed by checking for both forms
//        before falling through to the named-capture path.
//
// Lambda capture-all syntax ("[=]" -- capture everything by value,
// "[&]" -- capture everything by reference) is extremely common,
// idiomatic C++ -- arguably MORE common in everyday code than the
// explicit named-capture form this parser already supported. Both
// forms previously failed to parse at all.
void f() {
    int x = 5;
    auto byValue = [=]() { return x; };
    auto byRef = [&]() { x++; };
}
