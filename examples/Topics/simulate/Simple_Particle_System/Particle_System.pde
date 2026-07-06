// A class to describe a group of Particles
// An ArrayList is used to manage the list of Particles

class ParticleSystem {
public:
  ArrayList<Particle> particles;
  PVector origin;

  ParticleSystem(PVector position) {
    origin = position.copy();
  }

  void addParticle() {
    particles.add(new Particle(origin));
  }

  void run() {
    for (int i = particles.size()-1; i >= 0; i--) {
      Particle* p = particles.get(i);
      p->run();
      if (p->isDead()) {
        particles.remove(i);
      }
    }
  }
};
