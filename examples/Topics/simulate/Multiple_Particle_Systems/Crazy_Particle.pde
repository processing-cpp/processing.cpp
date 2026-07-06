// A subclass of Particle

class CrazyParticle : public Particle {
public:
  float theta;

  CrazyParticle(PVector l)
    : Particle(l) {
    theta = 0.0;
  }

  // This update() method overrides the parent class update() method
  void update() override {
    Particle::update();
    // Increment rotation based on horizontal velocity
    float theta_vel = (velocity.x * velocity.mag()) / 10.0f;
    theta += theta_vel;
  }

  // This display() method overrides the parent class display() method
  void display() override {
    // Render the ellipse just like in a regular particle
    Particle::display();
    // Then add a rotating line
    pushMatrix();
    translate(position.x, position.y);
    rotate(theta);
    stroke(255, lifespan);
    line(0, 0, 25, 0);
    popMatrix();
  }
};
