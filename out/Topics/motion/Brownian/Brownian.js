/**
 * Brownian motion.
 *
 * Recording random movement as a continuous line.
 */

const num = 2000;
let range = 6;

let ax = new Array(num).fill(0);
let ay = new Array(num).fill(0);

function setup()
{
  createCanvas(640, 360);
  for (let i = 0; i < num; i++) {
    ax[i] = width/2;
    ay[i] = height/2;
  }
  frameRate(30);
}

function draw()
{
  background(51);

  for (let i = 1; i < num; i++) {
    ax[i-1] = ax[i];
    ay[i-1] = ay[i];
  }

  ax[num-1] += random(-range, range);
  ay[num-1] += random(-range, range);

  ax[num-1] = constrain(ax[num-1], 0, width);
  ay[num-1] = constrain(ay[num-1], 0, height);

  for (let i = 1; i < num; i++) {
    let val = i/num * 204.0 + 51;
    stroke(val);
    line(ax[i-1], ay[i-1], ax[i], ay[i]);
  }
}
