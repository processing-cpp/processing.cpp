/**
 * The Mandelbrot Set
 * by Daniel Shiffman.
 *
 * Simple rendering of the Mandelbrot set.
 */

function setup() {
  createCanvas(640, 360);
  noLoop();
  background(255);

  let w = 4;
  let h = (w * height) / width;

  let xmin = -w / 2;
  let ymin = -h / 2;

  loadPixels();

  let maxiterations = 100;

  let xmax = xmin + w;
  let ymax = ymin + h;

  let dx = (xmax - xmin) / width;
  let dy = (ymax - ymin) / height;

  let y = ymin;
  for (let j = 0; j < height; j++) {
    let x = xmin;
    for (let i = 0; i < width; i++) {

      let a = x;
      let b = y;
      let n = 0;
      let maxAbs = 4.0;
      let absOld = 0.0;
      let convergeNumber = maxiterations;

      while (n < maxiterations) {
        let aa = a * a;
        let bb = b * b;
        let absVal = sqrt(aa + bb);
        if (absVal > maxAbs) {
          let diffToLast = absVal - absOld;
          let diffToMax = maxAbs - absOld;
          convergeNumber = n + diffToMax / diffToLast;
          break;
        }
        let twoab = 2.0 * a * b;
        a = aa - bb + x;
        b = twoab + y;
        n++;
        absOld = absVal;
      }

      if (n == maxiterations) {
        pixels[i + j * width] = color(0);
      } else {
        let norm = map(convergeNumber, 0, maxiterations, 0, 1);
        pixels[i + j * width] = color(map(sqrt(norm), 0, 1, 0, 255));
      }
      x += dx;
    }
    y += dy;
  }
  updatePixels();
}

function draw() {}
