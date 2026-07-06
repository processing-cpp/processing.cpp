/**
 * Edge Detection.
 *
 * This program analyzes every pixel in an image and compares it with the
 * neighboring pixels to identify edges.
 *
 * This is an example of an "image convolution" using a kernel (small matrix)
 * to analyze and transform a pixel based on the values of its neighbors.
 *
 * This kernel describes a "Laplacian Edge Detector".  It is effective,
 * but sensitive to noise.  One common enhancement is to add a Gaussian
 * blur to the source image first, as in
 *   grayImg.filter(BLUR);
 * to reduce impact of noise on the output.  The combination is often called
 * "Laplace of Gaussian", or "LoG" for short.
 *
 * For weaker detection effect, try this kernel:     [  0  -1   0 ]
 *                                                    [ -1   4  -1 ]
 *                                                    [  0  -1   0 ]
 */

let kernel = [[ -1, -1, -1],
              [ -1,  8, -1],
              [ -1, -1, -1]];

let img;

function setup() {
  createCanvas(640, 360);
  img = loadImage("moon.jpg");
  noLoop();
}

function draw() {
  image(img, 0, 0);
  img.loadPixels();

  let grayImg = img.copy();
  grayImg.filter(GRAY);

  let edgeImg = createImage(grayImg.width, grayImg.height, RGB);

  for (let y = 1; y < grayImg.height - 1; y++) {
    for (let x = 1; x < grayImg.width - 1; x++) {
      let sum = 128;
      for (let ky = -1; ky <= 1; ky++) {
        for (let kx = -1; kx <= 1; kx++) {
          let pos = (y + ky) * grayImg.width + (x + kx);
          let val = blue(grayImg.pixels[pos]);
          sum += kernel[ky + 1][kx + 1] * val;
        }
      }
      edgeImg.pixels[y * edgeImg.width + x] = color(sum);
    }
  }
  edgeImg.updatePixels();

  image(edgeImg, width / 2, 0);
}
