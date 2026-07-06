// Two mutually recursive functions where the user writes
// an explicit forward declaration for one of them.
// The user-written "float g(float x);" should not result in
// a duplicate member declaration inside Sketch alongside the
// full definition -- C++ does not allow this.
float g(float x);
float f(float x) {
  if (x <= 0) return 0;
  return g(x - 1);
}
float g(float x) {
  if (x <= 0) return 1;
  return f(x - 1);
}
void setup() { size(640, 360); }
void draw() { background(0); text(f(5), 20, 20); }
