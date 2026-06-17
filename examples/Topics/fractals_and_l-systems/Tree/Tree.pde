/**
 * Recursive Tree
 * by Daniel Shiffman.
 *
 * Renders a simple tree-like structure via recursion.
 * The branching angle is calculated as a function of
 * the horizontal mouse location. Move the mouse left
 * and right to change the angle.
 */

float theta;

void setup() {
    size(640, 360);
}

void draw() {
    background(0);
    frameRate(30);
    stroke(255);
    float a = (mouseX / (float)width) * 90;
    theta = radians(a);
    translate(width / 2, height);
    line(0, 0, 0, -120);
    translate(0, -120);
    branch(120);
}

void branch(float h) {
    h *= 0.66;
    if (h > 2) {
        pushMatrix();
        rotate(theta);
        line(0, 0, 0, -h);
        translate(0, -h);
        branch(h);
        popMatrix();

        pushMatrix();
        rotate(-theta);
        line(0, 0, 0, -h);
        translate(0, -h);
        branch(h);
        popMatrix();
    }
}
