// EXPECT: PASS
// CATEGORY: regression-guard
//
// Multi-tab sketches where one class depends on another (Flock depends on
// Boid) previously failed with "Flock does not name a type" because:
// 1. Tab merge used insert(0) which REVERSED non-lifecycle tab order,
//    putting Flock.pde before Boid.pde even though B < F alphabetically.
// 2. hoistedVariables (e.g. "Flock* flock") were emitted before finalClasses,
//    so the type appeared before its definition.
//
// Both fixed. This single-file version tests the hoisting order fix (2).
class Boid {
public:
  PVector pos;
  Boid(float x, float y) : pos(x, y) {}
  void update() { pos.x += 1; }
};

class Flock {
public:
  ArrayList<Boid> boids;
  void add(Boid* b) { boids.add(b); }
  void run() { for (Boid* b : boids) b->update(); }
};

Flock* flock;

void setup() {
  size(640, 360);
  flock = new Flock();
  flock->add(new Boid(100, 100));
}

void draw() {
  background(0);
  flock->run();
}
