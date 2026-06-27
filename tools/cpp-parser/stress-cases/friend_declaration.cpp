// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly, no occurrences
//
// "friend class B;" / "friend void foo(A&);" (friend declarations,
// granting another class/function access to private members) are not
// recognized -- the parser expects an ordinary member declaration after
// an access specifier, never the "friend" keyword.
class A {
    friend class B;
};
