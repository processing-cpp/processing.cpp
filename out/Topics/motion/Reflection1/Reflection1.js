/**
 * Non-orthogonal Reflection
 * by Ira Greenberg.
 *
 * Based on the equation (R = 2N(N*L)-L) where R is the
 * reflection vector, N is the normal, and L is the incident
 * vector.
 */

let base1;
let base2;
let baseLength;

let coords = [];

let position;
let velocity;
let r = 6;
let speed = 3.5;

function setup() {
  createCanvas(640, 360);

  fill(128);
  base1 = createVector(0, height - 150);
  base2 = createVector(width, height);
  createGround();

  position = createVector(width / 2, 0);

  velocity = p5.Vector.random2D();
  velocity.mult(speed);
}

function draw() {
  fill(0, 12);
  noStroke();
  rect(0, 0, width, height);

  fill(200);
  quad(base1.x, base1.y, base2.x, base2.y, base2.x, height, 0, height);

  let baseDelta = p5.Vector.sub(base2, base1);
  baseDelta.normalize();
  let normal = createVector(-baseDelta.y, baseDelta.x);

  noStroke();
  fill(255);
  ellipse(position.x, position.y, r * 2, r * 2);

  position.add(velocity);

  let incidence = p5.Vector.mult(velocity, -1);
  incidence.normalize();

  for (let i = 0; i < coords.length; i++) {
    if (p5.Vector.dist(position, coords[i]) < r) {
      let dot = incidence.dot(normal);
      velocity.set(2 * normal.x * dot - incidence.x, 2 * normal.y * dot - incidence.y, 0);
      velocity.mult(speed);

      stroke(255, 128, 0);
      line(position.x, position.y, position.x - normal.x * 100, position.y - normal.y * 100);
    }
  }

  if (position.x > width - r) {
    position.x = width - r;
    velocity.x *= -1;
  }
  if (position.x < r) {
    position.x = r;
    velocity.x *= -1;
  }
  if (position.y < r) {
    position.y = r;
    velocity.y *= -1;
    base1.y = random(height - 100, height);
    base2.y = random(height - 100, height);
    createGround();
  }
}

function createGround() {
  baseLength = p5.Vector.dist(base1, base2);

  coords = new Array(Math.ceil(baseLength));
  for (let i = 0; i < coords.length; i++) {
    coords[i] = createVector(
      base1.x + ((base2.x - base1.x) / baseLength) * i,
      base1.y + ((base2.y - base1.y) / baseLength) * i
    );
  }
}
