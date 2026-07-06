/**
 * Bouncy Bubbles
 * based on code from Keith Peters.
 *
 * Multiple-object collision.
 */

let numBalls = 12;
let spring = 0.05;
let gravity = 0.03;
let friction = -0.9;
let balls = [];

class Ball {
  constructor(xin, yin, din, idin, oin) {
    this.x = xin;
    this.y = yin;
    this.diameter = din;
    this.id = idin;
    this.others = oin;
    this.vx = 0;
    this.vy = 0;
  }

  collide() {
    for (let i = this.id + 1; i < numBalls; i++) {
      let other = this.others[i];
      let dx = other.x - this.x;
      let dy = other.y - this.y;
      let distance = sqrt(dx*dx + dy*dy);
      let minDist = other.diameter/2 + this.diameter/2;
      if (distance < minDist) {
        let angle = atan2(dy, dx);
        let targetX = this.x + cos(angle) * minDist;
        let targetY = this.y + sin(angle) * minDist;
        let ax = (targetX - other.x) * spring;
        let ay = (targetY - other.y) * spring;
        this.vx -= ax;
        this.vy -= ay;
        other.vx += ax;
        other.vy += ay;
      }
    }
  }

  move() {
    this.vy += gravity;
    this.x += this.vx;
    this.y += this.vy;
    if (this.x + this.diameter/2 > width) {
      this.x = width - this.diameter/2;
      this.vx *= friction;
    }
    else if (this.x - this.diameter/2 < 0) {
      this.x = this.diameter/2;
      this.vx *= friction;
    }
    if (this.y + this.diameter/2 > height) {
      this.y = height - this.diameter/2;
      this.vy *= friction;
    }
    else if (this.y - this.diameter/2 < 0) {
      this.y = this.diameter/2;
      this.vy *= friction;
    }
  }

  display() {
    ellipse(this.x, this.y, this.diameter, this.diameter);
  }
}

function setup() {
  createCanvas(640, 360);
  for (let i = 0; i < numBalls; i++) {
    balls.push(new Ball(random(width), random(height), random(30, 70), i, balls));
  }
  noStroke();
  fill(255, 204);
}

function draw() {
  background(0);
  for (let ball of balls) {
    ball.collide();
    ball.move();
    ball.display();
  }
}
