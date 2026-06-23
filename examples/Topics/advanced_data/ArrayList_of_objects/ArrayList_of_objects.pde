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
ArrayList<Ball> balls;
int ballWidth = 48;
void setup() {
  size(640, 360);
  noStroke();
  balls = ArrayList<Ball>();
  balls.add(new Ball(width / 2, 0, ballWidth));
}
void draw() {
  background(255);
  for (int i = balls.size() - 1; i >= 0; i--) {
    Ball* b = balls.get(i);
    b->move();
    b->display();
    if (b->finished()) {
      balls.remove(i);
    }
  }
}
void mousePressed() {
  balls.add(new Ball(mouseX, mouseY, ballWidth));
}
