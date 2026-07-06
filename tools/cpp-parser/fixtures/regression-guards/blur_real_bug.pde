float v = 1.0 / 9.0;
float kernel[3][3] = {{ v, v, v },
                       { v, v, v },
                       { v, v, v }};
PImage* img;
void setup() {
  size(640, 360);
  img = loadImage("moon.jpg");
  noLoop();
}
void draw() {
  image(img, 0, 0);
  img->loadPixels();
  PImage* blurImg = createImage(img->width, img->height, RGB);
  for (int y = 1; y < img->height - 1; y++) {
    for (int x = 1; x < img->width - 1; x++) {
      float sumRed = 0;
      float sumGreen = 0;
      float sumBlue = 0;
      for (int ky = -1; ky <= 1; ky++) {
        for (int kx = -1; kx <= 1; kx++) {
          int pos = (y + ky) * img->width + (x + kx);
          float valRed = red(img->pixels[pos]);
          sumRed += kernel[ky + 1][kx + 1] * valRed;
          float valGreen = green(img->pixels[pos]);
          sumGreen += kernel[ky + 1][kx + 1] * valGreen;
          float valBlue = blue(img->pixels[pos]);
          sumBlue += kernel[ky + 1][kx + 1] * valBlue;
        }
      }
      blurImg->pixels[y * blurImg->width + x] = color(sumRed, sumGreen, sumBlue);
    }
  }
  blurImg->updatePixels();
  image(blurImg, width / 2, 0);
}
