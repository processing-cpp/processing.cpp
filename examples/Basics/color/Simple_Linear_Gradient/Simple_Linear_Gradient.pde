/**
 * Simple Linear Gradient 
 * 
 * The lerpColor() function is useful for interpolating
 * between two colors.
 */

// Constants
int Y_AXIS = 1;
int X_AXIS = 2;
int b1, b2, c1, c2;

void setup() {
    size(640, 360);

    // Define colors
    b1 = color(255);
    b2 = color(0);
    c1 = color(204, 102, 0);
    c2 = color(0, 102, 153);

    noLoop();
}

void draw() {
    // Background
    setGradient(0, 0, width/2, height, b1, b2, X_AXIS);
    setGradient(width/2, 0, width/2, height, b2, b1, X_AXIS);
    // Foreground
    setGradient(50, 90, 540, 80, c1, c2, Y_AXIS);
    setGradient(50, 190, 540, 80, c2, c1, X_AXIS);
}

void setGradient(int x, int y, float w, float h, int c1, int c2, int axis) {

    noFill();

    if (axis == Y_AXIS) {  // Top to bottom gradient
        for (int i = y; i <= y + (int)h; i++) {
            float inter = map((float)i, (float)y, (float)(y + h), 0.0f, 1.0f);
            int c = lerpColor(c1, c2, inter);
            stroke(c);
            line(x, i, x + (int)w, i);
        }
    }  
    else if (axis == X_AXIS) {  // Left to right gradient
        for (int i = x; i <= x + (int)w; i++) {
            float inter = map((float)i, (float)x, (float)(x + w), 0.0f, 1.0f);
            int c = lerpColor(c1, c2, inter);
            stroke(c);
            line(i, y, i, y + (int)h);
        }
    }
}
