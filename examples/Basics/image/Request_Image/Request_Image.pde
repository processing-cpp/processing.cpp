/**
 * Request Image
 * by Ira Greenberg. Translated to ProcessingGL C++.
 *
 * requestImage() loads images on a background thread so the sketch
 * doesn't freeze. While loading, a bouncing ellipse plays.
 * Width == 0: still loading. Width == -1: load failed.
 */

int imgCount = 12;
PImage* imgs[12];
float imgW;
bool loadStates[12];
float loaderX, loaderY, theta;

bool checkLoadStates() {
    for (int i = 0; i < imgCount; i++) {
        if (!loadStates[i]) return false;
    }
    return true;
}

void drawImages() {
    int y = (height - imgs[0]->height) / 2;
    for (int i = 0; i < imgCount; i++) {
        image(imgs[i], width / imgCount * i, y,
              imgs[i]->height, imgs[i]->height);
    }
}

void runLoaderAni() {
    if (!checkLoadStates()) {
        ellipse(loaderX, loaderY, 10, 10);
        loaderX += 2;
        loaderY = height/2 + sin(theta) * (height/8);
        theta += PI / 22;
        if (loaderX > width + 5) loaderX = -5;
    }
}

void setup() {
    size(640, 360);
    imgW = width / imgCount;
    for (int i = 0; i < imgCount; i++) {
        loadStates[i] = false;
        imgs[i] = requestImage("PT_anim" + nf(i, 4) + ".gif");
    }
}

void draw() {
    background(0);
    runLoaderAni();
    for (int i = 0; i < imgCount; i++) {
        if (imgs[i] != nullptr &&
            imgs[i]->width != 0 &&
            imgs[i]->width != -1) {
            loadStates[i] = true;
        }
    }
    if (checkLoadStates()) {
        drawImages();
    }
}
