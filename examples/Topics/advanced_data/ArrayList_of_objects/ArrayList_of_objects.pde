#include <vector>

class Ball {
public:
  float x;
  float y;
  float speed;
  float gravity;
  float w;
  float life;

  Ball(float tempX, float tempY, float tempW) {
    x = tempX;
    y = tempY;
    w = tempW;
    speed = 0;
    gravity = 0.1;
    life = 255;
  }

  void move() {
    speed += gravity;
    y += speed;

    if (y > height) {
      speed *= -0.8;
      y = height;
    }
  }

  bool finished() {
    life--;
    return life < 0;
  }

  void display() {
    fill(0, life);
    ellipse(x, y, w, w);
  }
};

std::vector<Ball> balls;
int ballWidth = 48;

void setup() {
  size(640, 360);
  noStroke();

  balls.push_back(Ball(width / 2, 0, ballWidth));
}

void draw() {
  background(255);

  for (int i = (int)balls.size() - 1; i >= 0; i--) {
    balls[i].move();
    balls[i].display();

    if (balls[i].finished()) {
      balls.erase(balls.begin() + i);
    }
  }
}

void mousePressed() {
  balls.push_back(Ball(mouseX, mouseY, ballWidth));
}
