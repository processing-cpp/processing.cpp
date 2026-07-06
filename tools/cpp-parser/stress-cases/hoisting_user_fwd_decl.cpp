// EXPECT: PASS
// CATEGORY: regression-guard
// SKETCH: mutual_recursion_forward_decl.pde
//
// PREVIOUSLY FAILING: user-written forward declarations were duplicated
// inside Sketch alongside their full definition, causing "cannot be
// overloaded with itself" in g++. Found via Follow1.pde.
//
// FIXED: before emitting sketchMembers into the Sketch struct, strip any
// bodyless FunctionDecl whose name+param-count matches a bodied one in
// the same set. Class members have full mutual visibility in C++, so the
// forward declaration is not only redundant but actively illegal as a
// member redeclaration.
//
// When the USER writes an explicit forward declaration ("float g(float x);")
// for mutual recursion and the full definition also appears at top level,
// the pipeline puts BOTH inside Sketch as members. C++ does not allow a
// member function to be re-declared with the same signature inside the
// same class -- "cannot be overloaded with itself".
//
// Root cause: the pipeline treats a bodyless FunctionDecl (forward decl)
// identically to a bodied one -- both end up in depResult.rest and both
// get emitted as Sketch members. Inside a struct, C++ class members have
// full mutual visibility, so the forward declaration is not only redundant
// but actively illegal as a redeclaration.
//
// Fix: before emitting into Sketch, strip any bodyless FunctionDecl whose
// name also has a bodied FunctionDecl in the same emission set.
float g(float x);
float f(float x) { return x <= 0 ? 0.0f : g(x - 1); }
float g(float x) { return x <= 0 ? 1.0f : f(x - 1); }
void setup() {}
void draw() {}
