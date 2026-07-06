class Flock {
public:
  ArrayList<Boid> boids;

  Flock() {}

  void run() {
    for (Boid* b : boids) {
      b->run(boids);
    }
  }

  void addBoid(Boid* b) {
    boids.add(b);
  }
};
