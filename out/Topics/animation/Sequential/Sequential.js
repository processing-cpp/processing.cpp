/**
 * Sequential
 * by James Paterson.
 * Translated to C++ Mode.
 *
 * Displaying a sequence of images creates the illusion of motion.
 * Twelve images are loaded and each is displayed individually in a loop.
 */
let numFrames = 12;
let currentFrame = 0;
let images = [];

function setup() {
  createCanvas(640, 360);
  frameRate(24);
  for (let i = 0; i < numFrames; i++) {
    let name = "PT_anim" + nf(i, 4) + ".gif";
    images.push(loadImage(name));
  }
}

function draw() {
  background(0);
  currentFrame = (currentFrame + 1) % numFrames;
  let offset = 0;
  for (let x = -100; x < width; x += images[0].width) {
    image(images[(currentFrame + offset) % numFrames], x, -20);
    offset += 2;
    image(images[(currentFrame + offset) % numFrames], x, height / 2);
    offset += 2;
  }
}
