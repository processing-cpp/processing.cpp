// A class to describe a group of Particles
// An ArrayList is used to manage the list of Particles

class ParticleSystem {
public:
  ArrayList<Particle> particles;   // pointer-storage
  PVector origin;
  PImage* img;

  ParticleSystem(int num, PVector v, PImage* img_) {
    origin = v.copy();
    img = img_;
    for (int i = 0; i < num; i++) {
      particles.add(new Particle(origin, img));
    }
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

  // Method to add a force vector to all particles currently in the system
  void applyForce(PVector dir) {
    for (Particle* p : particles) {
      p->applyForce(dir);
    }
  }

  void addParticle() {
    particles.add(new Particle(origin, img));
  }
};
