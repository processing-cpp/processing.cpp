void setup() {
    size(640, 360);
    background(102);
}

void draw() {
    float fromX = mouseX - mouseDX;
    float fromY = mouseY - mouseDY;
    variableEllipse(mouseX, mouseY, fromX, fromY);
}

void variableEllipse(float x, float y, float px, float py) {
    float speed = abs(x - px) + abs(y - py);
    stroke(speed);
    ellipse(x, y, speed, speed);
}
