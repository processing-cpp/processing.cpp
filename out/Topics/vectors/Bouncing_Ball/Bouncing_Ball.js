/**
 * Bouncing Ball with Vectors
 * by Daniel Shiffman.
 *
 * Demonstration of using vectors to control motion
 * of a body. This example is not object-oriented
 * See AccelerationWithVectors for an example of how
 * to simulate motion using vectors in an object.
 */

let location;
let velocity;
let gravity;

function setup() {
  createCanvas(640, 360);
  location = createVector(100, 100);
  velocity = createVector(1.5, 2.1);
  gravity = createVector(0, 0.2);
}

function draw() {
  background(0);

  location.add(velocity);
  velocity.add(gravity);

  if ((location.x > width) || (location.x < 0)) {
    velocity.x = velocity.x * -1;
  }
  if (location.y > height) {
    velocity.y = velocity.y * -0.95;
    location.y = height;
  }

  stroke(255);
  strokeWeight(2);
  fill(127);
  ellipse(location.x, location.y, 48, 48);
}
