/**
 * Save One Image
 *
 * The save() function allows you to save an image from the
 * display window. In this example, save() is run when a mouse
 * button is pressed. The image "line.tif" is saved to the
 * same folder as the sketch's program file.
 * Translated to C++ Mode.
 */

function setup() {
    createCanvas(640, 360);
}

function draw() {
    background(204);
    line(0, 0, mouseX, height);
    line(width, 0, 0, mouseY);
}

function mousePressed() {
    save("line.tif");
}
