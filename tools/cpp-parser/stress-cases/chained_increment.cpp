// EXPECT: PASS
// CATEGORY: confirmed-working
// FOUND: deliberate adversarial probing -- confirms this already works correctly
//
// Chained pre/post increment in one expression (real, if questionable,
// C++ -- technically undefined-behavior-adjacent in real compilers but
// syntactically valid and something a sketch author could plausibly
// write). Kept as a positive control / regression tripwire.
void f() {
    int x;
    x = x++ + ++x;
}
