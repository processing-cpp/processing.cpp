/**
 * The Mandelbrot Set
 * by Daniel Shiffman.
 *
 * Simple rendering of the Mandelbrot set.
 */

void setup() {
    size(640, 360);
    noLoop();
    background(255);

    float w = 4;
    float h = (w * height) / width;

    float xmin = -w / 2;
    float ymin = -h / 2;

    loadPixels();

    int maxiterations = 100;

    float xmax = xmin + w;
    float ymax = ymin + h;

    float dx = (xmax - xmin) / width;
    float dy = (ymax - ymin) / height;

    float y = ymin;
    for (int j = 0; j < height; j++) {
        float x = xmin;
        for (int i = 0; i < width; i++) {

            float a = x;
            float b = y;
            int n = 0;
            float maxAbs = 4.0;
            float absOld = 0.0;
            float convergeNumber = maxiterations;

            while (n < maxiterations) {
                float aa = a * a;
                float bb = b * b;
                float absVal = sqrt(aa + bb);
                if (absVal > maxAbs) {
                    float diffToLast = absVal - absOld;
                    float diffToMax  = maxAbs - absOld;
                    convergeNumber = n + diffToMax / diffToLast;
                    break;
                }
                float twoab = 2.0 * a * b;
                a = aa - bb + x;
                b = twoab + y;
                n++;
                absOld = absVal;
            }

            if (n == maxiterations) {
                pixels[i + j * width] = color(0);
            } else {
                float norm = map(convergeNumber, 0, maxiterations, 0, 1);
                pixels[i + j * width] = color(map(sqrt(norm), 0, 1, 0, 255));
            }
            x += dx;
        }
        y += dy;
    }
    updatePixels();
}

void draw() {}
