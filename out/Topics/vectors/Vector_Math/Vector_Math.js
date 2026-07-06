/**
 * Vector
 * by Daniel Shiffman.
 *
 * Demonstration of some basic vector math: subtraction,
 * normalization, scaling. Normalizing a vector sets
 * its length to 1.
 */

function setup() {
  createCanvas(640, 360);
}

function draw() {
  background(0);

  let mouse = createVector(mouseX, mouseY);
  let center = createVector(width / 2, height / 2);

  mouse.sub(center);

  mouse.normalize();

  mouse.mult(150);

  translate(width / 2, height / 2);
  stroke(255);
  strokeWeight(4);
  line(0, 0, mouse.x, mouse.y);
}
