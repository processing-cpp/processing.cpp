/**
 * Morph.
 *
 * Changing one shape into another by interpolating
 * vertices from one to another
 */

let circle = [];
let square = [];
let morph = [];
let state = false;

function setup() {
  createCanvas(640, 360);

  for (let angle = 0; angle < 360; angle += 9) {
    let v = p5.Vector.fromAngle(radians(angle - 135));
    v.mult(100);
    circle.push(v);
    morph.push(createVector());
  }

  for (let x = -50; x < 50; x += 10) {
    square.push(createVector(x, -50));
  }
  for (let y = -50; y < 50; y += 10) {
    square.push(createVector(50, y));
  }
  for (let x = 50; x > -50; x -= 10) {
    square.push(createVector(x, 50));
  }
  for (let y = 50; y > -50; y -= 10) {
    square.push(createVector(-50, y));
  }
}

function draw() {
  background(51);

  let totalDistance = 0;

  for (let i = 0; i < circle.length; i++) {
    let v1;
    if (state) {
      v1 = circle[i];
    } else {
      v1 = square[i];
    }
    let v2 = morph[i];
    v2.lerp(v1, 0.1);
    totalDistance += p5.Vector.dist(v1, v2);
  }

  if (totalDistance < 0.1) {
    state = !state;
  }

  translate(width / 2, height / 2);
  strokeWeight(4);
  beginShape();
  noFill();
  stroke(255);
  for (let v of morph) {
    vertex(v.x, v.y);
  }
  endShape(CLOSE);
}
