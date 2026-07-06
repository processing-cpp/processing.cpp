// Flocking by Daniel Shiffman -- Craig Reynolds Boids
// Tests: multi-class sketch, ArrayList<Boid> pointer storage,
// pass-by-reference boids list, _PSketch injection for width/height,
// all matrix/shape/random functions working bare from non-PApplet class.

class Boid {
public:
  PVector position;
  PVector velocity;
  PVector acceleration;
  float r;
  float maxforce;
  float maxspeed;

  Boid(float x, float y) {
    acceleration = PVector(0, 0);
    float angle = random(TWO_PI);
    velocity = PVector(cos(angle), sin(angle));
    position = PVector(x, y);
    r = 2.0;
    maxspeed = 2;
    maxforce = 0.03;
  }

  void run(ArrayList<Boid>& boids) {
    flock(boids);
    update();
    borders();
    render();
  }

  void applyForce(PVector force) { acceleration.add(force); }

  void flock(ArrayList<Boid>& boids) {
    PVector sep = separate(boids);
    PVector ali = align(boids);
    PVector coh = cohesion(boids);
    sep.mult(1.5); ali.mult(1.0); coh.mult(1.0);
    applyForce(sep); applyForce(ali); applyForce(coh);
  }

  void update() {
    velocity.add(acceleration);
    velocity.limit(maxspeed);
    position.add(velocity);
    acceleration.mult(0);
  }

  PVector seek(PVector target) {
    PVector desired = PVector::sub(target, position);
    desired.normalize();
    desired.mult(maxspeed);
    PVector steer = PVector::sub(desired, velocity);
    steer.limit(maxforce);
    return steer;
  }

  void render() {
    float theta = velocity.heading() + radians(90);
    fill(200, 100);
    stroke(255);
    pushMatrix();
    translate(position.x, position.y);
    rotate(theta);
    beginShape(TRIANGLES);
    vertex(0, -r*2);
    vertex(-r, r*2);
    vertex(r, r*2);
    endShape();
    popMatrix();
  }

  void borders() {
    if (position.x < -r) position.x = width+r;
    if (position.y < -r) position.y = height+r;
    if (position.x > width+r) position.x = -r;
    if (position.y > height+r) position.y = -r;
  }

  PVector separate(ArrayList<Boid>& boids) {
    float desiredseparation = 25.0f;
    PVector steer(0, 0, 0);
    int count = 0;
    for (Boid* other : boids) {
      float d = PVector::dist(position, other->position);
      if ((d > 0) && (d < desiredseparation)) {
        PVector diff = PVector::sub(position, other->position);
        diff.normalize(); diff.div(d);
        steer.add(diff); count++;
      }
    }
    if (count > 0) steer.div((float)count);
    if (steer.mag() > 0) {
      steer.normalize(); steer.mult(maxspeed);
      steer.sub(velocity); steer.limit(maxforce);
    }
    return steer;
  }

  PVector align(ArrayList<Boid>& boids) {
    float neighbordist = 50;
    PVector sum(0, 0);
    int count = 0;
    for (Boid* other : boids) {
      float d = PVector::dist(position, other->position);
      if ((d > 0) && (d < neighbordist)) { sum.add(other->velocity); count++; }
    }
    if (count > 0) {
      sum.div((float)count); sum.normalize(); sum.mult(maxspeed);
      PVector steer = PVector::sub(sum, velocity);
      steer.limit(maxforce); return steer;
    }
    return PVector(0, 0);
  }

  PVector cohesion(ArrayList<Boid>& boids) {
    float neighbordist = 50;
    PVector sum(0, 0);
    int count = 0;
    for (Boid* other : boids) {
      float d = PVector::dist(position, other->position);
      if ((d > 0) && (d < neighbordist)) { sum.add(other->position); count++; }
    }
    if (count > 0) { sum.div(count); return seek(sum); }
    return PVector(0, 0);
  }
};

class Flock {
public:
  ArrayList<Boid> boids;
  Flock() {}
  void run() {
    for (Boid* b : boids) b->run(boids);
  }
  void addBoid(Boid* b) { boids.add(b); }
};

Flock* flock;

void setup() {
  size(640, 360);
  flock = new Flock();
  for (int i = 0; i < 150; i++) {
    flock->addBoid(new Boid(width/2, height/2));
  }
}

void draw() {
  background(50);
  flock->run();
}

void mousePressed() {
  flock->addBoid(new Boid(mouseX, mouseY));
}
