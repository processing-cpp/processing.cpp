// EXPECT: FAIL
// CATEGORY: parser-gap
//
// User-written class/struct forward declarations ("class B;" / "struct B;")
// crash the parser with "expected '{' but found ';'". This is valid C++
// for mutual pointer dependencies between two classes.
//
// The parser's top-level and class-member dispatch always expects a full
// definition after "class"/"struct", never a bare forward declaration.
// Zero real-corpus evidence so far -- the pipeline's own ForwardDeclGenerator
// handles function forward declarations, but never needed class-level ones
// since ClassHoister reorders classes. Becomes relevant when the user writes
// them explicitly for mutual pointer dependencies.
class B;
class A { public: B* next; };
class B { public: A* prev; };
void setup() {}
void draw() {}
