// EXPECT: PASS
// CATEGORY: confirmed-working
// FOUND: deliberate adversarial probing -- confirms this already works correctly
//
// A lambda expression with an explicit trailing return type
// ("-> int") is correctly parsed. Kept here as a positive control: this
// stress-cases folder should catch REGRESSIONS too, not just document
// gaps, so a few confirmed-PASS cases belong here as a tripwire if a
// future change to lambda or function-signature parsing breaks this.
void f() {
    auto adder = [](int a, int b) -> int { return a + b; };
}
