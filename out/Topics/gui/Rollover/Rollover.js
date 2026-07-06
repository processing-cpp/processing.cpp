/**
 * Rollover.
 *
 * Roll over the colored squares in the center of the image
 * to change the color of the outside rectangle.
 */


let rectX, rectY;      // Position of square button
let circleX, circleY;  // Position of circle button
let rectSize = 90;     // Diameter of rect
let circleSize = 93;   // Diameter of circle

let rectColor;
let circleColor;
let baseColor;

let rectOver = false;
let circleOver = false;

function setup() {
  createCanvas(640, 360);
  rectColor = color(0);
  circleColor = color(255);
  baseColor = color(102);
  circleX = width/2+circleSize/2+10;
  circleY = height/2;
  rectX = width/2-rectSize-10;
  rectY = height/2-rectSize/2;
  ellipseMode(CENTER);
}

function draw() {
  update(mouseX, mouseY);

  noStroke();
  if (rectOver) {
    background(rectColor);
  } else if (circleOver) {
    background(circleColor);
  } else {
    background(baseColor);
  }

  stroke(255);
  fill(rectColor);
  rect(rectX, rectY, rectSize, rectSize);
  stroke(0);
  fill(circleColor);
  ellipse(circleX, circleY, circleSize, circleSize);
}

function update(x, y) {
  if( overCircle(circleX, circleY, circleSize) ) {
    circleOver = true;
    rectOver = false;
  } else if ( overRect(rectX, rectY, rectSize, rectSize) ) {
    rectOver = true;
    circleOver = false;
  } else {
    circleOver = rectOver = false;
  }
}

function overRect(x, y, width, height) {
  if (mouseX >= x && mouseX <= x+width &&
      mouseY >= y && mouseY <= y+height) {
    return true;
  } else {
    return false;
  }
}

function overCircle(x, y, diameter) {
  let disX = x - mouseX;
  let disY = y - mouseY;
  if(sqrt(sq(disX) + sq(disY)) < diameter/2 ) {
    return true;
  } else {
    return false;
  }
}
