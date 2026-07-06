/**
 * Convolution
 * by Daniel Shiffman.
 *
 * Applies a convolution matrix to a portion of an image. Move mouse to
 * apply filter to different parts of the image. Click mouse to cycle
 * through different effects (kernels).
 */

PImage* img;
int effect = 0;
int w = 120;

// It's possible to convolve the image with many different
// matrices to produce different effects.  Here are some
// example kernels to try.
float identity[3][3] = { { 0, 0, 0 },
                          { 0, 1, 0 },
                          { 0, 0, 0 } };

float darken[3][3]   = { { 0,   0, 0 },
                          { 0, 0.5, 0 },
                          { 0,   0, 0 } };

float lighten[3][3]  = { { 0, 0, 0 },
                          { 0, 2, 0 },
                          { 0, 0, 0 } };

float sharpen[3][3]  = { {  0, -1,  0 },
                          { -1,  5, -1 },
                          {  0, -1,  0 } };

float sharpen2[3][3] = { { -1, -1, -1 },
                          { -1,  9, -1 },
                          { -1, -1, -1 } };

float box_blur[3][3] = { { 1.0/9.0, 1.0/9.0, 1.0/9.0 },
                          { 1.0/9.0, 1.0/9.0, 1.0/9.0 },
                          { 1.0/9.0, 1.0/9.0, 1.0/9.0 } };

float edge_det[3][3] = { { 0,  1, 0 },
                          { 1, -4, 1 },
                          { 0,  1, 0 } };

float emboss[3][3]   = { { -2, -1, 0 },
                          { -1,  1, 1 },
                          {  0,  1, 2 } };

// Arrays can't hold other arrays by value, so this is an
// array of pointers to 3x3 float arrays. Kept as raw arrays
// (not Array<T>) deliberately: convolution() below is a per-pixel
// hot loop, and this avoids the extra bounds-checked vector
// indirection Array<Array<float>> would add for no real benefit here.
float (*kernels[8])[3] = {
  identity,
  darken,
  lighten,
  sharpen,
  sharpen2,
  box_blur,
  edge_det,
  emboss
};

Array<String> effect_names = {
  String("Identity (no change)"),
  String("Darken"),
  String("Lighten"),
  String("Sharpen"),
  String("Sharpen More"),
  String("Box Blur"),
  String("Edge Detect"),
  String("Emboss")
};

void setup() {
  size(640, 360);
  img = loadImage("moon-wide.jpg");

  noLoop();
}

// Clicking the mouse advances to the next effect
void mousePressed() {
  effect++;
  if (effect >= 8) effect = 0;

  redraw();
}

// Moving the mouse triggers a screen redraw
void mouseMoved() {
  redraw();
}

void mouseDragged() {
  redraw();
}

void draw() {
  // We're only going to process a portion of the image
  // so let's set the whole image as the background first
  image(img, 0, 0);

  // Calculate the small rectangle we will process
  int xstart = constrain(mouseX - w/2, 0, img->width);
  int ystart = constrain(mouseY - w/2, 0, img->height);
  int xend = constrain(mouseX + w/2, 0, img->width);
  int yend = constrain(mouseY + w/2, 0, img->height);
  int matrixsize = 3;
  loadPixels();
  for (int x = xstart; x < xend; x++) {
    for (int y = ystart; y < yend; y++ ) {
      color c = convolution(x, y, kernels[effect], matrixsize, img);
      int loc = x + y*img->width;
      pixels[loc] = c;
    }
  }
  updatePixels();

  textSize(24);
  text(effect_names[effect], 4, 24);
}

color convolution(int x, int y, float matrix[3][3], int matrixsize, PImage* img) {
  float rtotal = 0.0;
  float gtotal = 0.0;
  float btotal = 0.0;
  int offset = matrixsize / 2;
  for (int i = 0; i < matrixsize; i++){
    for (int j= 0; j < matrixsize; j++){
      int xloc = x+i-offset;
      int yloc = y+j-offset;
      int loc = xloc + img->width*yloc;
      // img->pixels is a std::vector<unsigned int>, so .size() gives
      // the real element count -- the faithful translation of Java's
      // img.pixels.length.
      loc = constrain(loc, 0, (int)img->pixels.size() - 1);
      rtotal += (red(img->pixels[loc]) * matrix[i][j]);
      gtotal += (green(img->pixels[loc]) * matrix[i][j]);
      btotal += (blue(img->pixels[loc]) * matrix[i][j]);
    }
  }
  rtotal = constrain(rtotal, 0, 255);
  gtotal = constrain(gtotal, 0, 255);
  btotal = constrain(btotal, 0, 255);
  return color(rtotal, gtotal, btotal);
}
