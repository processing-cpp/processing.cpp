/**
 * Bouncy Bubbles
 * based on code from Keith Peters.
 *
 * Multiple-object collision.
 */

class Ball;

int numBalls = 12;
float spring = 0.05;
float gravity = 0.03;
float friction = -0.9;
ArrayList<Ball> balls;

class Ball {
public:
  float x, y;
  float diameter;
  float vx = 0;
  float vy = 0;
  int id;
  ArrayList<Ball>* others;

  Ball(float xin, float yin, float din, int idin, ArrayList<Ball>* oin) {
    x = xin;
    y = yin;
    diameter = din;
    id = idin;
    others = oin;
  }

  void collide() {
    for (int i = id + 1; i < numBalls; i++) {
      Ball* other = others->get(i);
      float dx = other->x - x;
      float dy = other->y - y;
      float distance = sqrt(dx*dx + dy*dy);
      float minDist = other->diameter/2 + diameter/2;
      if (distance < minDist) {
        float angle = atan2(dy, dx);
        float targetX = x + cos(angle) * minDist;
        float targetY = y + sin(angle) * minDist;
        float ax = (targetX - other->x) * spring;
        float ay = (targetY - other->y) * spring;
        vx -= ax;
        vy -= ay;
        other->vx += ax;
        other->vy += ay;
      }
    }
  }

  void move() {
    vy += gravity;
    x += vx;
    y += vy;
    if (x + diameter/2 > width) {
      x = width - diameter/2;
      vx *= friction;
    }
    else if (x - diameter/2 < 0) {
      x = diameter/2;
      vx *= friction;
    }
    if (y + diameter/2 > height) {
      y = height - diameter/2;
      vy *= friction;
    }
    else if (y - diameter/2 < 0) {
      y = diameter/2;
      vy *= friction;
    }
  }

  void display() {
    ellipse(x, y, diameter, diameter);
  }
};

void setup() {
  size(640, 360);
  for (int i = 0; i < numBalls; i++) balls.add(nullptr); // pre-size, like Java's "new Ball[numBalls]"
  for (int i = 0; i < numBalls; i++) {
    balls[i] = new Ball(random(width), random(height), random(30, 70), i, &balls);
  }
  noStroke();
  fill(255, 204);
}

void draw() {
  background(0);
  for (Ball* ball : balls) {
    ball->collide();
    ball->move();
    ball->display();
  }
}
