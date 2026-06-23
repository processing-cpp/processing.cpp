/**
 * Animated Sprite (Shifty + Teddy)
 * by James Paterson.
 * Translated to C++ Mode.
 *
 * Press the mouse button to change animations.
 */
struct Animation {
    ArrayList<PImage> images;
    int imageCount;
    int frame = 0;
    Animation(String prefix, int count) {
        imageCount = count;
        images = ArrayList<PImage>();
        for (int i = 0; i < count; i++) {
            String filename = prefix + nf(i, 4) + ".gif";
            images.add(loadImage(filename));
        }
    }
    void display(float x, float y) {
        frame = (frame + 1) % imageCount;
        PImage* img = images.get(frame);
        if (img) image(img, x, y);
    }
    int getWidth() {
        PImage* img = images.get(0);
        if (img) return img->width;
        return 0;
    }
};
Animation* animation1 = nullptr;
Animation* animation2 = nullptr;
float xpos;
float ypos;
float drag = 30.0;
void setup() {
    size(640, 360);
    background(255, 204, 0);
    frameRate(24);
    animation1 = new Animation("PT_Shifty_", 38);
    animation2 = new Animation("PT_Teddy_", 60);
    ypos = height * 0.25;
}
void draw() {
    float dx = mouseX - xpos;
    xpos = xpos + dx / drag;
    if (_mousePressed) {
        background(153, 153, 0);
        animation1->display(xpos - animation1->getWidth() / 2, ypos);
    } else {
        background(255, 204, 0);
        animation2->display(xpos - animation2->getWidth() / 2, ypos);
    }
}
