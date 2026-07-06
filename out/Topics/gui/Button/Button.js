/**
 * Button.
 *
 * Click on one of the colored shapes in the
 * center of the image to change the color of
 * the background.
 */

let rectX, rectY;
let circleX, circleY;
let rectSize = 90;
let circleSize = 93;
let rectColor, circleColor, baseColor;
let rectHighlight, circleHighlight;
let currentColor;
let rectOver = false;
let circleOver = false;

function setup() {
    createCanvas(640, 360);
    rectColor = color(0);
    rectHighlight = color(51);
    circleColor = color(255);
    circleHighlight = color(204);
    baseColor = color(102);
    currentColor = baseColor;
    circleX = width / 2 + circleSize / 2 + 10;
    circleY = height / 2;
    rectX = width / 2 - rectSize - 10;
    rectY = height / 2 - rectSize / 2;
    ellipseMode(CENTER);
}

function draw() {
    update(mouseX, mouseY);
    background(currentColor);

    if (rectOver) {
        fill(rectHighlight);
    } else {
        fill(rectColor);
    }
    stroke(255);
    rect(rectX, rectY, rectSize, rectSize);

    if (circleOver) {
        fill(circleHighlight);
    } else {
        fill(circleColor);
    }
    stroke(0);
    ellipse(circleX, circleY, circleSize, circleSize);
}

function update(x, y) {
    if (overCircle(circleX, circleY, circleSize)) {
        circleOver = true;
        rectOver = false;
    } else if (overRect(rectX, rectY, rectSize, rectSize)) {
        rectOver = true;
        circleOver = false;
    } else {
        circleOver = rectOver = false;
    }
}

function mousePressed() {
    if (circleOver) {
        currentColor = circleColor;
    }
    if (rectOver) {
        currentColor = rectColor;
    }
}

function overRect(x, y, w, h) {
    if (mouseX >= x && mouseX <= x + w &&
        mouseY >= y && mouseY <= y + h) {
        return true;
    } else {
        return false;
    }
}

function overCircle(x, y, diameter) {
    let disX = x - mouseX;
    let disY = y - mouseY;
    if (sqrt(sq(disX) + sq(disY)) < diameter / 2) {
        return true;
    } else {
        return false;
    }
}
