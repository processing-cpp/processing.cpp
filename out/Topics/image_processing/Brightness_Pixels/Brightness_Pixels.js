/**
 * Brightness Pixels
 * by Daniel Shiffman.
 *
 * This program adjusts the brightness of a part of the image by
 * calculating the distance of each pixel to the mouse.
 */

let img;

function setup() {
  createCanvas(640, 360);
  frameRate(30);
  img = loadImage("moon-wide.jpg");
  img.loadPixels();
  loadPixels();
}

function draw() {
  for (let x = 0; x < img.width; x++) {
    for (let y = 0; y < img.height; y++) {
      let loc = x + y * img.width;
      let r, g, b;
      r = red(img.pixels[loc]);
      let maxdist = 50;
      let d = dist(x, y, mouseX, mouseY);
      let adjustbrightness = 255 * (maxdist - d) / maxdist;
      r += adjustbrightness;
      r = constrain(r, 0, 255);
      let c = color(r);
      pixels[y * width + x] = c;
    }
  }
  updatePixels();
}
