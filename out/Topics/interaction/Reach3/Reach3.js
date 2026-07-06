/**
 * Reach 3
 * based on code from Keith Peters.
 *
 * The arm follows the position of the ball by
 * calculating the angles with atan2().
 */

const numSegments = 8;
let x = new Array(numSegments).fill(0);
let y = new Array(numSegments).fill(0);
let angle = new Array(numSegments).fill(0);
let segLength = 26;
let targetX, targetY;

let ballX = 50;
let ballY = 50;
let ballXDirection = 1;
let ballYDirection = -1;

function setup() {
  createCanvas(640, 360);
  strokeWeight(20.0);
  stroke(255, 100);
  noFill();

  x[x.length-1] = width/2;
  y[x.length-1] = height;
}

function draw() {
  background(0);

  strokeWeight(20);
  ballX = ballX + 1.0 * ballXDirection;
  ballY = ballY + 0.8 * ballYDirection;
  if (ballX > width-25 || ballX < 25) {
    ballXDirection *= -1;
  }
  if (ballY > height-25 || ballY < 25) {
    ballYDirection *= -1;
  }
  ellipse(ballX, ballY, 30, 30);

  reachSegment(0, ballX, ballY);
  for (let i = 1; i < numSegments; i++) {
    reachSegment(i, targetX, targetY);
  }
  for (let i = x.length-1; i >= 1; i--) {
    positionSegment(i, i-1);
  }
  for (let i = 0; i < x.length; i++) {
    segment(x[i], y[i], angle[i], (i+1)*2);
  }
}

function positionSegment(a, b) {
  x[b] = x[a] + cos(angle[a]) * segLength;
  y[b] = y[a] + sin(angle[a]) * segLength;
}

function reachSegment(i, xin, yin) {
  let dx = xin - x[i];
  let dy = yin - y[i];
  angle[i] = atan2(dy, dx);

  targetX = xin - cos(angle[i]) * segLength;
  targetY = yin - sin(angle[i]) * segLength;
}

function segment(x, y, a, sw) {
  strokeWeight(sw);
  push();
  translate(x, y);
  rotate(a);
  line(0, 0, segLength, 0);
  pop();
}
