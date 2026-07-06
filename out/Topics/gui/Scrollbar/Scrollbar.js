/**
 * Scrollbar.
 *
 * Move the scrollbars left and right to change the positions of the images.
 */
//True if a mouse button was pressed while no other button was.
let firstMousePress = false;
let hs1, hs2;  // Two scrollbars
let img1 = null;
let img2 = null;  // Two images to load

function setup() {
  createCanvas(640, 360);
  noStroke();
  hs1 = new HScrollbar(0, height/2-8, width, 16, 16);
  hs2 = new HScrollbar(0, height/2+8, width, 16, 16);
  // Load images
  img1 = loadImage("seedTop.jpg");
  img2 = loadImage("seedBottom.jpg");
}

function draw() {
  background(255);
  // Get the position of the img1 scrollbar
  // and convert to a value to display the img1 image
  let img1Pos = hs1.getPos()-width/2;
  fill(255);
  image(img1, width/2-img1.width/2 + img1Pos*1.5, 0);
  // Get the position of the img2 scrollbar
  // and convert to a value to display the img2 image
  let img2Pos = hs2.getPos()-width/2;
  fill(255);
  image(img2, width/2-img2.width/2 + img2Pos*1.5, height/2);
  hs1.update();
  hs2.update();
  hs1.display();
  hs2.display();
  stroke(0);
  line(0, height/2, width, height/2);
  //After it has been used in the sketch, set it back to false
  if (firstMousePress) {
    firstMousePress = false;
  }
}

function mousePressed() {
  if (!firstMousePress) {
    firstMousePress = true;
  }
}

class HScrollbar {
  constructor(xp, yp, sw, sh, l) {
    this.swidth = sw;
    this.sheight = sh;
    let widthtoheight = sw - sh;
    this.ratio = sw / widthtoheight;
    this.xpos = xp;
    this.ypos = yp-sheight/2;
    this.spos = xp + swidth/2 - sheight/2;
    this.newspos = this.spos;
    this.sposMin = xp;
    this.sposMax = xp + swidth - sheight;
    this.loose = l;
    this.over = false;
    this.locked = false;
  }

  update() {
    if (this.overEvent()) {
      this.over = true;
    } else {
      this.over = false;
    }
    if (firstMousePress && this.over) {
      this.locked = true;
    }
    if (!mouseIsPressed) {
      this.locked = false;
    }
    if (this.locked) {
      this.newspos = this.constrain(mouseX-this.sheight/2, this.sposMin, this.sposMax);
    }
    if (abs(this.newspos - this.spos) > 1) {
      this.spos = this.spos + (this.newspos-this.spos)/this.loose;
    }
  }

  constrain(val, minv, maxv) {
    return min(max(val, minv), maxv);
  }

  overEvent() {
    if (mouseX > this.xpos && mouseX < this.xpos+this.swidth &&
      mouseY > this.ypos && mouseY < this.ypos+this.sheight) {
      return true;
    } else {
      return false;
    }
  }

  display() {
    noStroke();
    fill(204);
    rect(this.xpos, this.ypos, this.swidth, this.sheight);
    if (this.over || this.locked) {
      fill(0, 0, 0);
    } else {
      fill(102, 102, 102);
    }
    rect(this.spos, this.ypos, this.sheight, this.sheight);
  }

  getPos() {
    return this.spos * this.ratio;
  }
}
