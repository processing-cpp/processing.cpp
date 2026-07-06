/**
 * Animated Sprite (Shifty + Teddy)
 * by James Paterson.
 * Translated to C++ Mode.
 *
 * Press the mouse button to change animations.
 */
class Animation {
  constructor(prefix, count) {
    this.imageCount = count;
    this.images = [];
    this.frame = 0;
    for (let i = 0; i < count; i++) {
      let filename = prefix + nf(i, 4) + ".gif";
      this.images.push(loadImage(filename));
    }
  }
  display(x, y) {
    this.frame = (this.frame + 1) % this.imageCount;
    let img = this.images[this.frame];
    if (img) image(img, x, y);
  }
  getWidth() {
    let img = this.images[0];
    if (img) return img.width;
    return 0;
  }
}

let animation1 = null;
let animation2 = null;
let xpos;
let ypos;
let drag = 30.0;

function setup() {
  createCanvas(640, 360);
  background(255, 204, 0);
  frameRate(24);
  animation1 = new Animation("PT_Shifty_", 38);
  animation2 = new Animation("PT_Teddy_", 60);
  ypos = height * 0.25;
}

function draw() {
  let dx = mouseX - xpos;
  xpos = xpos + dx / drag;
  if (mouseIsPressed) {
    background(153, 153, 0);
    animation1.display(xpos - animation1.getWidth() / 2, ypos);
  } else {
    background(255, 204, 0);
    animation2.display(xpos - animation2.getWidth() / 2, ypos);
  }
}
