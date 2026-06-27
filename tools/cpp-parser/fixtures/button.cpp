/**
 * Button.
 *
 * Click on one of the colored shapes in the
 * center of the image to change the color of
 * the background.
 */
int rectX, rectY;
int circleX, circleY;
int rectSize = 90;
int circleSize = 93;
color rectColor, circleColor, baseColor;
color rectHighlight, circleHighlight;
color currentColor;
bool rectOver = false;
bool circleOver = false;
void setup() {
    size(640, 360);
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
void draw() {
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
void update(int x, int y) {
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
void mousePressed() {
    if (circleOver) {
        currentColor = circleColor;
    }
    if (rectOver) {
        currentColor = rectColor;
    }
}
bool overRect(int x, int y, int w, int h) {
    if (mouseX >= x && mouseX <= x + w &&
        mouseY >= y && mouseY <= y + h) {
        return true;
    } else {
        return false;
    }
}
bool overCircle(int x, int y, int diameter) {
    float disX = x - mouseX;
    float disY = y - mouseY;
    if (sqrt(sq(disX) + sq(disY)) < diameter / 2) {
        return true;
    } else {
        return false;
    }
}
