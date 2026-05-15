/**
 * On/Off.  
 * 
 * Click the mouse to turn the lights off.
 */
 
float spin = 0.0f;

void setup() {
    size(640, 360, P3D);
    noStroke();
}

void draw() {
    background(51);
  
    if (!_mousePressed) {
        lights();
    }
  
    spin += 0.01f;
  
    pushMatrix();
    translate(width / 2, height / 2, 0);
    rotateX(PI / 9.0f);
    rotateY(PI / 5.0f + spin);
    box(150);
    popMatrix();
}
