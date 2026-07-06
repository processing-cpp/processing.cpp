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
