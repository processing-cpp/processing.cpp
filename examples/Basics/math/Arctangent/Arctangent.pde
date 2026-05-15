/**
 * Arctangent.
 *
 * Move the mouse to change the direction of the eyes.
 * The atan2() function computes the angle from each eye to the cursor.
 */

struct Eye {
    int x, y, size;
    float angle = 0.0;

    Eye(int tx, int ty, int ts) : x(tx), y(ty), size(ts) {}

    void update(int mx, int my) {
        angle = atan2(my - y, mx - x);
    }

    void display() {
        pushMatrix();
        translate(x, y);
        fill(255); noStroke();
        ellipse(0, 0, size, size);
        rotate(angle);
        fill(153, 204, 0);
        ellipse(size/4, 0, size/2, size/2);
        popMatrix();
    }
};

Eye e1(250,  16, 120);
Eye e2(164, 185,  80);
Eye e3(420, 230, 220);

void setup() {
    size(640, 360);
    noStroke();
}

void draw() {
    background(102);
    e1.update(mouseX, mouseY); e1.display();
    e2.update(mouseX, mouseY); e2.display();
    e3.update(mouseX, mouseY); e3.display();
}
