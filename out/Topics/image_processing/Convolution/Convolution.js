/**
 * Convolution
 * by Daniel Shiffman.
 *
 * Applies a convolution matrix to a portion of an image. Move mouse to
 * apply filter to different parts of the image. Click mouse to cycle
 * through different effects (kernels).
 */

let img;
let effect = 0;
let w = 120;

let identity = [ [ 0, 0, 0 ],
                 [ 0, 1, 0 ],
                 [ 0, 0, 0 ] ];

let darken   = [ [ 0,   0, 0 ],
                 [ 0, 0.5, 0 ],
                 [ 0,   0, 0 ] ];

let lighten  = [ [ 0, 0, 0 ],
                 [ 0, 2, 0 ],
                 [ 0, 0, 0 ] ];

let sharpen  = [ [  0, -1,  0 ],
                 [ -1,  5, -1 ],
                 [  0, -1,  0 ] ];

let sharpen2 = [ [ -1, -1, -1 ],
                 [ -1,  9, -1 ],
                 [ -1, -1, -1 ] ];

let box_blur = [ [ 1.0/9.0, 1.0/9.0, 1.0/9.0 ],
                 [ 1.0/9.0, 1.0/9.0, 1.0/9.0 ],
                 [ 1.0/9.0, 1.0/9.0, 1.0/9.0 ] ];

let edge_det = [ [ 0,  1, 0 ],
                 [ 1, -4, 1 ],
                 [ 0,  1, 0 ] ];

let emboss   = [ [ -2, -1, 0 ],
                 [ -1,  1, 1 ],
                 [  0,  1, 2 ] ];

let kernels = [
  identity,
  darken,
  lighten,
  sharpen,
  sharpen2,
  box_blur,
  edge_det,
  emboss
];

let effect_names = [
  "Identity (no change)",
  "Darken",
  "Lighten",
  "Sharpen",
  "Sharpen More",
  "Box Blur",
  "Edge Detect",
  "Emboss"
];

function setup() {
  createCanvas(640, 360);
  img = loadImage("moon-wide.jpg");
  noLoop();
}

function mousePressed() {
  effect++;
  if (effect >= 8) effect = 0;
  redraw();
}

function mouseMoved() {
  redraw();
}

function mouseDragged() {
  redraw();
}

function draw() {
  image(img, 0, 0);

  let xstart = constrain(mouseX - w/2, 0, img.width);
  let ystart = constrain(mouseY - w/2, 0, img.height);
  let xend = constrain(mouseX + w/2, 0, img.width);
  let yend = constrain(mouseY + w/2, 0, img.height);
  let matrixsize = 3;
  loadPixels();
  for (let x = xstart; x < xend; x++) {
    for (let y = ystart; y < yend; y++ ) {
      let c = convolution(x, y, kernels[effect], matrixsize, img);
      let loc = x + y*img.width;
      pixels[loc] = c;
    }
  }
  updatePixels();

  textSize(24);
  text(effect_names[effect], 4, 24);
}

function convolution(x, y, matrix, matrixsize, img) {
  let rtotal = 0.0;
  let gtotal = 0.0;
  let btotal = 0.0;
  let offset = Math.floor(matrixsize / 2);
  for (let i = 0; i < matrixsize; i++){
    for (let j= 0; j < matrixsize; j++){
      let xloc = x+i-offset;
      let yloc = y+j-offset;
      let loc = xloc + img.width*yloc;
      loc = constrain(loc, 0, img.pixels.length - 1);
      rtotal += (red(img.pixels[loc]) * matrix[i][j]);
      gtotal += (green(img.pixels[loc]) * matrix[i][j]);
      btotal += (blue(img.pixels[loc]) * matrix[i][j]);
    }
  }
  rtotal = constrain(rtotal, 0, 255);
  gtotal = constrain(gtotal, 0, 255);
  btotal = constrain(btotal, 0, 255);
  return color(rtotal, gtotal, btotal);
}
