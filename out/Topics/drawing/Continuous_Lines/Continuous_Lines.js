/**
 * Continuous Lines.
 *
 * Click and drag the mouse to draw a line.
 * Uses mouseDX/mouseDY for high-frequency input.
 */

let prevX, prevY;

function setup() {
    createCanvas(640, 360);
    background(102);
    prevX = mouseX;
    prevY = mouseY;
}

function draw() {
    if (mouseIsPressed) {
        stroke(255);
        line(mouseX, mouseY, mouseX - mouseDX, mouseY - mouseDY);
    }
    prevX = mouseX;
    prevY = mouseY;
}
