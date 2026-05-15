/**
 * Pie Chart
 * Translated to C++ by Jose Llamas.
 *
 * Uses the arc() function to generate a pie chart from the data
 * stored in an array.
 */

int angles[] = { 30, 10, 45, 35, 60, 38, 75, 67 };

int anglesCount = sizeof(angles) / sizeof(angles[0]);

void pieChart(float diameter, int data[], int length) {
    float lastAngle = 0;

    for (int i = 0; i < length; i++) {

        float gray = map(i, 0, length, 0, 255);

        fill(gray);

        arc(
            width / 2,
            height / 2,
            diameter,
            diameter,
            lastAngle,
            lastAngle + radians(data[i])
        );

        lastAngle += radians(data[i]);
    }
}

void setup() {
    size(640, 360);

    noStroke();

    noLoop();  // Run once and stop
}

void draw() {
    background(100);

    pieChart(300, angles, anglesCount);
}
