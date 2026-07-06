/**
 * Non-orthogonal Collision with Multiple Ground Segments
 * by Ira Greenberg.
 *
 * Based on Keith Peter's Solution in
 * Foundation Actionscript Animation: Making Things Move!
 */

class Ground {
  constructor(x1, y1, x2, y2) {
    this.x1 = x1;
    this.y1 = y1;
    this.x2 = x2;
    this.y2 = y2;
    this.x = (x1 + x2) / 2;
    this.y = (y1 + y2) / 2;
    this.len = dist(x1, y1, x2, y2);
    this.rot = atan2((y2 - y1), (x2 - x1));
  }
}

class Orb {
  constructor(x, y, r_) {
    this.position = createVector(x, y);
    this.velocity = createVector(0.5, 0);
    this.r = r_;
    this.damping = 0.8;
  }

  move() {
    this.velocity.add(gravity);
    this.position.add(this.velocity);
  }

  display() {
    noStroke();
    fill(200);
    ellipse(this.position.x, this.position.y, this.r * 2, this.r * 2);
  }

  checkWallCollision() {
    if (this.position.x > width - this.r) {
      this.position.x = width - this.r;
      this.velocity.x *= -this.damping;
    } else if (this.position.x < this.r) {
      this.position.x = this.r;
      this.velocity.x *= -this.damping;
    }
  }

  checkGroundCollision(groundSegment) {
    let deltaX = this.position.x - groundSegment.x;
    let deltaY = this.position.y - groundSegment.y;

    let cosine = cos(groundSegment.rot);
    let sine = sin(groundSegment.rot);

    let groundXTemp = cosine * deltaX + sine * deltaY;
    let groundYTemp = cosine * deltaY - sine * deltaX;
    let velocityXTemp = cosine * this.velocity.x + sine * this.velocity.y;
    let velocityYTemp = cosine * this.velocity.y - sine * this.velocity.x;

    if (groundYTemp > -this.r &&
      this.position.x > groundSegment.x1 &&
      this.position.x < groundSegment.x2 ) {
      groundYTemp = -this.r;
      velocityYTemp *= -1.0;
      velocityYTemp *= this.damping;
    }

    deltaX = cosine * groundXTemp - sine * groundYTemp;
    deltaY = cosine * groundYTemp + sine * groundXTemp;
    this.velocity.x = cosine * velocityXTemp - sine * velocityYTemp;
    this.velocity.y = cosine * velocityYTemp + sine * velocityXTemp;
    this.position.x = groundSegment.x + deltaX;
    this.position.y = groundSegment.y + deltaY;
  }
}

let orb;
let gravity = createVector(0, 0.05);
let segments = 40;
let ground = [];

function setup() {
  createCanvas(640, 360);
  orb = new Orb(50, 50, 3);

  let peakHeights = new Array(segments + 1);
  for (let i = 0; i < peakHeights.length; i++) {
    peakHeights[i] = random(height - 40, height - 30);
  }

  let segs = segments;
  for (let i = 0; i < segments; i++) {
    ground.push(new Ground(width / segs * i, peakHeights[i], width / segs * (i + 1), peakHeights[i + 1]));
  }
}

function draw() {
  noStroke();
  fill(0, 15);
  rect(0, 0, width, height);

  orb.move();
  orb.display();
  orb.checkWallCollision();

  for (let i = 0; i < segments; i++) {
    orb.checkGroundCollision(ground[i]);
  }

  fill(127);
  beginShape();
  for (let i = 0; i < segments; i++) {
    vertex(ground[i].x1, ground[i].y1);
    vertex(ground[i].x2, ground[i].y2);
  }
  vertex(ground[segments - 1].x2, height);
  vertex(ground[0].x1, height);
  endShape(CLOSE);
}
