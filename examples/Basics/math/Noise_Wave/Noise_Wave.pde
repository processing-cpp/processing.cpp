float yoff = 0.0f;

void setup() {
    size(640, 360);
}

void draw() {
    background(51);

    fill(255);

    beginShape();

    float xoff = 0.0f;

    for (float x = 0.0f; x <= width; x += 10.0f) {
        float n = noise(xoff, yoff);
        float y = map(n, 0.0f, 1.0f, 200.0f, 300.0f);

        vertex(x, y);

        xoff += 0.05f;
    }

    yoff += 0.01f;

    // close the shape (important for fill to work)
    vertex((float)width, (float)height);
    vertex(0.0f, (float)height);

    endShape(CLOSE);
}
