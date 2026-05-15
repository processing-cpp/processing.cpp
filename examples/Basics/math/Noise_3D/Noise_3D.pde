float increment = 0.01f;
float zoff = 0.0f;
float zincrement = 0.02f;

void setup() {
    size(640, 360);
    frameRate(30);
}

void draw() {
    loadPixels();

    float xoff = 0.0f;

    for (int x = 0; x < width; x++) {
        xoff += increment;
        float yoff = 0.0f;

        for (int y = 0; y < height; y++) {
            yoff += increment;

            float bright = noise(xoff, yoff, zoff) * 255.0f;

            pixels[x + y * width] = color(bright, bright, bright);
        }
    }

    updatePixels();

    zoff += zincrement;
}
