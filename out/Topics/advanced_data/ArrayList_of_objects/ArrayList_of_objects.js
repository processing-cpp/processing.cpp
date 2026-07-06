class Ball {
  constructor(tempX, tempY, tempW) {
    this.x = tempX;
    this.y = tempY;
    this.w = tempW;
    this.speed = 0;
    this.gravity = 0.1;
    this.life = 255;
  }
  move() {
    this.speed += this.gravity;
    this.y += this.speed;
    if (this.y > height) {
      this.speed *= -0.8;
      this.y = height;
    }
  }
  finished() {
    this.life--;
    return this.life < 0;
  }
  display() {
    fill(0, this.life);
    ellipse(this.x, this.y, this.w, this.w);
  }
}

let balls = [];
let ballWidth = 48;

function setup() {
  createCanvas(640, 360);
  noStroke();
  balls.push(new Ball(width / 2, 0, ballWidth));
}

function draw() {
  background(255);
  for (let i = balls.length - 1; i >= 0; i--) {
    let b = balls[i];
    b.move();
    b.display();
    if (b.finished()) {
      balls.splice(i, 1);
    }
  }
}

function mousePressed() {
  balls.push(new Ball(mouseX, mouseY, ballWidth));
}
