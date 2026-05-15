/**
 * Mixture Grid  
 * 
 * Display a 2D grid of boxes with three different kinds of lights. 
 */

void setup() {
    size(640, 360, P3D);
    noStroke();
}

void draw() {
    defineLights();
    background(0);
  
    for (int x = 0; x <= width; x += 60) {
        for (int y = 0; y <= height; y += 60) {
            pushMatrix();
            translate(x, y);

            rotateY(map(mouseX, 0, width, 0, PI));
            rotateX(map(mouseY, 0, height, 0, PI));

            box(90);
            popMatrix();
        }
    }
}

void defineLights() {
    // Orange point light on the right
    pointLight(150, 100, 0,
               200.0f, -150.0f, 0.0f);

    // Blue directional light from the left
    directionalLight(0, 102, 255,
                     1.0f, 0.0f, 0.0f);

    // Yellow spotlight from the front
    spotLight(255, 255, 109,
              0.0f, 40.0f, 200.0f,
              0.0f, -0.5f, -0.5f,
              PI / 2.0f, 2.0f);
}
