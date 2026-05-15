/**
 * Additive Wave
 * by Daniel Shiffman.
 *
 * Create a more complex wave by adding two waves together.
 */

int xspacing = 8;
int w;
int maxwaves = 4;

float theta = 0.0;
float amplitude[4];
float dx[4];
float* yvalues;

void setup() {
    size(640, 360);
    frameRate(30);
    colorMode(RGB, 255, 255, 255, 100);
    w = width + 16;
    for (int i = 0; i < maxwaves; i++) {
        amplitude[i] = random(10, 30);
        float period = random(100, 300);
        dx[i] = (TWO_PI / period) * xspacing;
    }
    yvalues = new float[w / xspacing];
}

void calcWave() {
    theta += 0.02;
    int len = w / xspacing;
    for (int i = 0; i < len; i++) yvalues[i] = 0;
    for (int j = 0; j < maxwaves; j++) {
        float x = theta;
        for (int i = 0; i < len; i++) {
            if (j % 2 == 0) yvalues[i] += sin(x) * amplitude[j];
            else             yvalues[i] += cos(x) * amplitude[j];
            x += dx[j];
        }
    }
}

void renderWave() {
    noStroke();
    fill(255, 50);
    ellipseMode(CENTER);
    int len = w / xspacing;
    for (int x = 0; x < len; x++) {
        ellipse(x * xspacing, height/2 + yvalues[x], 16, 16);
    }
}

void draw() {
    background(0);
    calcWave();
    renderWave();
}
