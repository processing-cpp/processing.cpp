/**
 * Double Random 
 * by Ira Greenberg.  
 * 
 * Using two random() calls and the point() function 
 * to create an irregular sawtooth line.
 */

int totalPts = 300;
float steps = totalPts + 1;
  
void setup() {
    size(640, 360);
    stroke(255);
    frameRate(1);
} 

void draw() {
    background(0);

    float randVal = 0.0f;

    for (int i = 1; i < steps; i++) {
        point((width / steps) * i,
              (height / 2.0f) + random(-randVal, randVal));

        randVal += random(-5.0f, 5.0f);
    }
}
