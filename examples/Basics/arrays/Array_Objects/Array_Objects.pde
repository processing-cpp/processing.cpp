/**
 * Array Objects.
 *
 * Demonstrates the syntax for creating an array of custom objects.
 */

struct Module {
    int xOffset, yOffset;
    float x, y;
    int unit;
    int xDirection, yDirection;
    float speed;

    Module() : xOffset(0),yOffset(0),x(0),y(0),unit(0),xDirection(1),yDirection(1),speed(0) {}
    Module(int xOffsetTemp, int yOffsetTemp, int xTemp, int yTemp, float speedTemp, int tempUnit)
        : xOffset(xOffsetTemp), yOffset(yOffsetTemp), x(xTemp), y(yTemp),
          unit(tempUnit), xDirection(1), yDirection(1), speed(speedTemp) {}

    void update() {
        x += speed * xDirection;
        if (x >= unit || x <= 0) {
            xDirection *= -1;
            x += 1 * xDirection;
            y += 1 * yDirection;
        }
        if (y >= unit || y <= 0) {
            yDirection *= -1;
            y += 1 * yDirection;
        }
    }

    void display() {
        fill(255);
        ellipse(xOffset + x, yOffset + y, 6, 6);
    }
};

int unit = 40;
int count;
Module* mods;

void setup() {
    size(640, 360);
    noStroke();
    int wideCount = width / unit;
    int highCount = height / unit;
    count = wideCount * highCount;
    mods = new Module[count];

    int index = 0;
    for (int y = 0; y < highCount; y++) {
        for (int x = 0; x < wideCount; x++) {
            mods[index++] = Module(x*unit, y*unit, unit/2, unit/2, random(0.05f, 0.8f), unit);
        }
    }
}

void draw() {
    background(0);
    for (int i = 0; i < count; i++) {
        mods[i].update();
        mods[i].display();
    }
}
