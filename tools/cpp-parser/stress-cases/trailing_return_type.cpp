// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: deliberate probing, zero corpus evidence
// FIXED: "auto f() -> int" failed because "->" is classified as
//        PUNCTUATION (not OPERATOR) in this lexer, so checkOp("->")
//        silently returned false, leaving "->" in the stream for the
//        next rule to reject. Fixed by using checkPunct("->") and
//        consuming-and-discarding the specified return type.
//
// The AST records the declared type as "auto" (not the real return type
// from after "->"), which is a known limitation -- a complete fix would
// replace it, but consuming-and-discarding stops the crash and is
// sufficient for sketches that just use trailing syntax.
auto clamp(float v, float lo, float hi) -> float {
    return v < lo ? lo : v > hi ? hi : v;
}
