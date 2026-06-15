/**
 * Animated Sprite (Shifty + Teddy)
 * by James Paterson.
 * Translated to C++ Mode.
 *
 * Press the mouse button to change animations.
 */

struct Animation {
    std::vector<PImage*> images;
    int imageCount;
    int frame = 0;

    Animation(std::string prefix, int count) {
        imageCount = count;
        for (int i = 0; i < count; i++) {
            std::string filename = prefix + nf(i, 4) + ".gif";
            images.push_back(loadImage(filename));
        }
    }

    void display(float x, float y) {
        frame = (frame + 1) % imageCount;
        if (images[frame]) image(images[frame], x, y);
    }

    int getWidth() {
        if (images[0]) return images[0]->width;
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
