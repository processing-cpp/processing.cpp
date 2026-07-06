// An ArrayList is used to manage the list of Particles

class ParticleSystem {
public:
  ArrayList<Particle> particles;   // pointer-storage since Particle is not IsJavaValueType
  PVector origin;

  ParticleSystem(int num, PVector v) {
    origin = v.copy();
    for (int i = 0; i < num; i++) {
      particles.add(new Particle(origin));
    }
  }

  void run() {
    // Cycle through the ArrayList backwards, because we are deleting while iterating
    for (int i = particles.size()-1; i >= 0; i--) {
      Particle* p = particles.get(i);
      p->run();
      if (p->isDead()) {
        particles.remove(i);
      }
    }
  }

  void addParticle() {
    Particle* p;
    // Add either a Particle or CrazyParticle to the system
    if (int(random(0, 2)) == 0) {
      p = new Particle(origin);
    }
    else {
      p = new CrazyParticle(origin);
    }
    particles.add(p);
  }

  void addParticle(Particle* p) {
    particles.add(p);
  }

  // A method to test if the particle system still has particles
  bool dead() {
    return particles.isEmpty();
  }
};
