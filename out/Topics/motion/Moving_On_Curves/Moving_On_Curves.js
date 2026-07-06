/**
 * Moving On Curves.
 *
 * In this example, the circles moves along the curve y = x^4.
 * Click the mouse to have it move to a new position.
 */

let beginX = 20.0;
let beginY = 10.0;
let endX = 570.0;
let endY = 320.0;
let distX;
let distY;
let exponent = 4;
let x = 0.0;
let y = 0.0;
let step = 0.01;
let pct = 0.0;

function setup() {
  createCanvas(640, 360);
  noStroke();
  distX = endX - beginX;
  distY = endY - beginY;
}

function draw() {
  fill(0, 2);
  rect(0, 0, width, height);
  pct += step;
  if (pct < 1.0) {
    x = beginX + (pct * distX);
    y = beginY + (pow(pct, exponent) * distY);
  }
  fill(255);
  ellipse(x, y, 20, 20);
}

function mousePressed() {
  pct = 0.0;
  beginX = x;
  beginY = y;
  endX = mouseX;
  endY = mouseY;
  distX = endX - beginX;
  distY = endY - beginY;
}
