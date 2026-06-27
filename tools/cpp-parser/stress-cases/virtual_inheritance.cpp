// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly for
// "virtual public"/"virtual private" base-class specifiers, no occurrences
// (note: PSketchInjector DOES generate "virtual _PSketch" as an
// injected base internally, but that's CodeGen-emitted text, not
// something the PARSER ever needs to read back as user-written source --
// confirmed these are different directions, not a contradiction)
//
// "class A : virtual public B {};" (virtual inheritance, used to solve
// the diamond-inheritance problem) fails -- the base-class-list parser
// expects "public"/"private"/"protected" immediately, with no handling
// for an optional leading "virtual" keyword first.
class B {};
class A : virtual public B {};
