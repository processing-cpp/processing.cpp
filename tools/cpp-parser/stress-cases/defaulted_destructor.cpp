// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly, no occurrences
//
// "= default;" / "= delete;" on a special member function (constructor,
// destructor, copy/move assignment) is not recognized -- the parser
// expects a function body ("{ ... }") or a bare ";" (a pure declaration)
// after the parameter list, never "= default" or "= delete".
class A {
    virtual ~A() = default;
};
