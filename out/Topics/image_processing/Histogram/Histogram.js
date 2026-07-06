/**
 * Histogram.
 *
 * Calculates the histogram of an image.
 * A histogram is the frequency distribution
 * of the gray levels with the number of pure black values
 * displayed on the left and number of pure white values on the right.
 *
 * Note that this sketch will behave differently on Android,
 * since most images will no longer be full 24-bit color.
 */

function setup() {
  createCanvas(640, 360);
  noLoop();
}

function draw() {
  let img = loadImage("frontier.jpg");
  image(img, 0, 0);
  let hist = new Array(256).fill(0);

  for (let i = 0; i < img.width; i++) {
    for (let j = 0; j < img.height; j++) {
      let bright = int(brightness(img.get(i, j)));
      hist[bright]++;
    }
  }

  let histMax = Math.max(...hist);

  stroke(255);
  for (let i = 0; i < img.width; i += 2) {
    let which = int(map(i, 0, img.width, 0, 255));
    let y = int(map(hist[which], 0, histMax, img.height, 0));
    line(i, img.height, i, y);
  }
}
