#include "Processing.h"

namespace Processing {

struct MySketch : public PApplet {
    void setup() override {
        size(400, 400);
    }
    void draw() override {
        background(51);
        fill(255, 0, 0);
        ellipse(mouseX, mouseY, 50, 50);
    }
};

} // namespace Processing

int main() {
    Processing::MySketch sketch;
    sketch.run();
    return 0;
}
