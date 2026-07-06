/**
 * Blur.
 *
 * This program analyzes every pixel in an image and blends it with the
 * neighboring pixels to blur the image.
 *
 * This is an example of an "image convolution" using a kernel (small matrix)
 * to analyze and transform a pixel based on the values of its neighbors.
 *
 * Image blur is also called a "low-pass filter". Pixels of low frequency
 * change (similar brightness as neighbors) are left mostly unchanged, while
 * those with high frequency change (sharply different values) are smoothed
 * out.
 *
 * The kernel here is a Box Blur, in which all components are equally valued.
 * Another common blur is "Gaussian Blur", in which pixels nearer the center
 * of the kernel have more weight than those further away.
 */

let v = 1.0 / 9.0;
let kernel = [[ v, v, v ],
               [ v, v, v ],
               [ v, v, v ]];

let img;

function setup() {
  createCanvas(640, 360);
  img = loadImage("moon.jpg");
  noLoop();
}

function draw() {
  image(img, 0, 0);
  img.loadPixels();

  let blurImg = createImage(img.width, img.height, RGB);

  for (let y = 1; y < img.height - 1; y++) {
    for (let x = 1; x < img.width - 1; x++) {
      let sumRed = 0;
      let sumGreen = 0;
      let sumBlue = 0;
      for (let ky = -1; ky <= 1; ky++) {
        for (let kx = -1; kx <= 1; kx++) {
          let pos = (y + ky) * img.width + (x + kx);

          let valRed = red(img.pixels[pos]);
          sumRed += kernel[ky + 1][kx + 1] * valRed;

          let valGreen = green(img.pixels[pos]);
          sumGreen += kernel[ky + 1][kx + 1] * valGreen;

          let valBlue = blue(img.pixels[pos]);
          sumBlue += kernel[ky + 1][kx + 1] * valBlue;
        }
      }
      blurImg.pixels[y * blurImg.width + x] = color(sumRed, sumGreen, sumBlue);
    }
  }
  blurImg.updatePixels();

  image(blurImg, width / 2, 0);
}
