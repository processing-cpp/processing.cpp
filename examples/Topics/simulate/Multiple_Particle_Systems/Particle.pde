class Particle {
public:
  PVector position;
  PVector velocity;
  PVector acceleration;
  float lifespan;

  Particle(PVector l) {
    acceleration = PVector(0, 0.05);
    velocity = PVector(random(-1, 1), random(-2, 0));
    position = l.copy();
    lifespan = 255.0;
  }

  virtual ~Particle() {}

  void run() {
    update();
    display();
  }

  // Method to update position
  virtual void update() {
    velocity.add(acceleration);
    position.add(velocity);
    lifespan -= 2.0;
  }

  // Method to display
  virtual void display() {
    stroke(255, lifespan);
    fill(255, lifespan);
    ellipse(position.x, position.y, 8, 8);
  }

  // Is the particle still useful?
  bool isDead() {
    return (lifespan < 0.0);
  }
};
