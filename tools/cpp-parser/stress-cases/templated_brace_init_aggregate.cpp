// EXPECT: FAIL
// CATEGORY: parser-gap (deeper, structural -- two real fixes layered, one real gap remains)
// FOUND: a deliberately complex, realistic quadtree sketch (not adversarial
// one-liner probing) -- "new QuadNode(Rect<float>{x, y, w, h})"
// CORPUS EVIDENCE: zero confirmed real-corpus impact -- checked directly;
// the one real corpus file with a similar shape (Load_File2.pde's
// Record struct) has an explicit constructor already, so it was never
// an aggregate to begin with and is unaffected by this specific gap.
//
// This file's history, in order:
// 1. Originally found: "Rect<float>{...}" (brace-init of a templated
//    type) misparsed as a comparison chain, the same disambiguation gap
//    already fixed for "std::vector<int>(...)" and "obj.method<int>(...)"
//    -- just for brace syntax instead of parens. FIXED: parsePostfix now
//    recognizes a templated-identifier callee followed by "{" and parses
//    it as a brace-init CallExpr (a new isBraceInit flag added to the
//    existing CallExpr record, consistent with this project's own
//    documented precedent of preferring a flag over a new node type for
//    closely-related call shapes -- see CallExpr's own javadoc on
//    ConstructExpr).
// 2. Fixing #1 surfaced a SECOND, DEEPER, genuinely structural issue:
//    PSketchInjector injects a "virtual _PSketch" base class into ANY
//    hoisted class/struct with at least one method (hasAnyMethod), on
//    the theory that a class with behavior likely needs Processing API
//    access. "Rect<T>" has a "contains(...)" method, so it gets the
//    base injected -- but once ANY class inherits from anything, it is
//    NO LONGER AN AGGREGATE in C++, which means it loses its implicit
//    brace-init-as-member-list-init constructor entirely. Confirmed
//    directly via the real g++ error: "no matching function for call to
//    'Rect<float>::Rect(<brace-enclosed initializer list>)'" -- the
//    injected base class is the actual reason, not anything about the
//    brace-init parsing fix itself (which is confirmed correct and
//    unrelated to this second issue).
//
// A complete fix for #2 would need PSketchInjector's heuristic to
// distinguish "this method genuinely references something from
// _PSketch" from "this class merely has a method" -- a real, deeper
// analysis (checking method bodies for actual _PSketch member usage)
// that the current heuristic was never built to do. Deliberately NOT
// built here, given zero confirmed real-corpus impact -- the one
// candidate real file is unaffected for an unrelated reason (it already
// has an explicit constructor, so was never an aggregate regardless of
// this issue).
struct Rect {
    float x, y, w, h;
    bool contains(float px, float py) const {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
};
class Holder {
    Rect bounds;
    Holder() : bounds(Rect{0, 0, 10, 10}) {}
};
