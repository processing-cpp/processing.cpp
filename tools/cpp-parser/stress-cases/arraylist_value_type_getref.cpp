// EXPECT: FAIL
// CATEGORY: user-convention-gap
//
// ArrayList<PVector> uses POINTER storage (PVector is not in IsJavaValueType)
// so .get(0) returns PVector*, not PVector. The pattern:
//   PVector h = body.get(0);
// fails with "conversion from PVector* to PVector requested".
//
// This is correct C++ behavior for this engine -- PVector is treated as a
// Java-style reference type. The user must write either:
//   PVector* h = body.get(0);   // pointer
//   PVector h = *body.get(0);   // dereference
//
// Not a parser bug. Documents a real user expectation mismatch that will
// recur whenever someone uses ArrayList<PVector> (or any non-primitive
// non-string class) and assigns .get() to a value type.
class Snake {
public:
  ArrayList<PVector> body;
  void update() {
    PVector h = body.get(0);  // wrong: PVector* != PVector
    body.add(0, new PVector(h.x + 5, h.y));
  }
};
Snake* snake;
void setup() { size(640, 360); }
void draw() {}
