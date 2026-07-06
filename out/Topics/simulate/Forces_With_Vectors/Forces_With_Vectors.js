/**
 * Forces (Gravity and Fluid Resistance) with Vectors
 * by Daniel Shiffman.
 *
 * Demonstration of multiple forces acting on bodies.
 * Bodies experience gravity continuously and fluid
 * resistance when in "water".
 */

class Mover {
  constructor(m, x, y) {
    this.mass = m;
    this.position = createVector(x, y);
    this.velocity = createVector(0, 0);
    this.acceleration = createVector(0, 0);
  }

  applyForce(force) {
    let f = p5.Vector.div(force, this.mass);
    this.acceleration.add(f);
  }

  update() {
    this.velocity.add(this.acceleration);
    this.position.add(this.velocity);
    this.acceleration.mult(0);
  }

  display() {
    stroke(255);
    strokeWeight(2);
    fill(255, 200);
    ellipse(this.position.x, this.position.y, this.mass * 16, this.mass * 16);
  }

  checkEdges() {
    if (this.position.y > height) {
      this.velocity.y *= -0.9;
      this.position.y = height;
    }
  }
}

class Liquid {
  constructor(x_, y_, w_, h_, c_) {
    this.x = x_;
    this.y = y_;
    this.w = w_;
    this.h = h_;
    this.c = c_;
  }

  contains(m) {
    let l = m.position;
    if (l.x > this.x && l.x < this.x + this.w && l.y > this.y && l.y < this.y + this.h) {
      return true;
    } else {
      return false;
    }
  }

  drag(m) {
    let speed = m.velocity.mag();
    let dragMagnitude = this.c * speed * speed;

    let drag = m.velocity.copy();
    drag.mult(-1);

    drag.setMag(dragMagnitude);
    return drag;
  }

  display() {
    noStroke();
    fill(127);
    rect(this.x, this.y, this.w, this.h);
  }
}

let movers = new Array(10);
let liquid;

function reset() {
  for (let i = 0; i < movers.length; i++) {
    movers[i] = new Mover(random(0.5, 3), 40 + i * 70, 0);
  }
}

function setup() {
  createCanvas(640, 360);
  reset();
  liquid = new Liquid(0, height / 2, width, height / 2, 0.1);
}

function draw() {
  background(0);

  liquid.display();

  for (let mover of movers) {
    if (liquid.contains(mover)) {
      let drag = liquid.drag(mover);
      mover.applyForce(drag);
    }

    let gravity = createVector(0, 0.1 * mover.mass);
    mover.applyForce(gravity);

    mover.update();
    mover.display();
    mover.checkEdges();
  }

  fill(255);
  text("click mouse to reset", 10, 30);
}

function mousePressed() {
  reset();
}
