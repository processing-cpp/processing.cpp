/**
 * Sequential
 * by James Paterson.
 * Translated to C++ Mode.
 *
 * Displaying a sequence of images creates the illusion of motion.
 * Twelve images are loaded and each is displayed individually in a loop.
 */
int numFrames = 12;
int currentFrame = 0;
ArrayList<PImage> images;
void setup() {
    size(640, 360);
    frameRate(24);
    images = ArrayList<PImage>();
    for (int i = 0; i < numFrames; i++) {
        String name = "PT_anim" + nf(i, 4) + ".gif";
        images.add(loadImage(name));
    }
}
void draw() {
    background(0);
    currentFrame = (currentFrame + 1) % numFrames;
    int offset = 0;
    for (int x = -100; x < width; x += images.get(0)->width) {
        image(images.get((currentFrame + offset) % numFrames), x, -20);
        offset += 2;
        image(images.get((currentFrame + offset) % numFrames), x, height / 2);
        offset += 2;
    }
}
