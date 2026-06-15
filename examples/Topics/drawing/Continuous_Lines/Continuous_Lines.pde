/**
 * Continuous Lines.
 *
 * Click and drag the mouse to draw a line.
 * Uses mouseDX/mouseDY for high-frequency input.
 */

float prevX, prevY;

void setup() {
    size(640, 360);
    background(102);
    prevX = mouseX;
    prevY = mouseY;
}

void draw() {
    if (_mousePressed) {
        stroke(255);
        line(mouseX, mouseY, mouseX - mouseDX, mouseY - mouseDY);
    }
    prevX = mouseX;
    prevY = mouseY;
}
