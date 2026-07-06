// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: a realistic quadtree sketch using "new QuadNode(Rect<float>{x,y,w,h})"
// FIXED: looksLikeTemplatedConstructionCallee now recognizes a following
//        "{" as well as "(" (previously only "("); parsePostfix now
//        consumes a "{...}" after a templated-identifier callee and
//        parses it as a brace-init CallExpr (isBraceInit=true).
//
// "Name<Args>{...}" -- brace-init of a templated type -- is real,
// ordinary modern C++ for aggregate initialization. This is the SAME
// disambiguation gap already fixed for "std::vector<int>(...)" and
// "obj.method<int>(...)", just for brace syntax instead of parens. This
// case deliberately uses a class with NO methods at all (so
// PSketchInjector never injects a _PSketch base, avoiding the SEPARATE,
// deeper, still-open issue tracked in
// templated_brace_init_aggregate.cpp) -- isolating just the parsing fix
// this case is meant to guard.
template<typename T>
struct Box {
    T x, y, w, h;
};
void f() {
    Box<float> b = Box<float>{1.0f, 2.0f, 3.0f, 4.0f};
}
