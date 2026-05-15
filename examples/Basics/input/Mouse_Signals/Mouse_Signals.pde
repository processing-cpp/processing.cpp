/**
 * Mouse Signals 
 * 
 * Move and click the mouse to generate signals. 
 * The top row is the signal from "mouseX", 
 * the middle row is the signal from "mouseY",
 * and the bottom row is the signal from "mousePressed". 
 */
 
int xvals[640];
int yvals[640];
int bvals[640];

void setup() {
    size(640, 360);
    noSmooth();
}

void draw() {
    background(102);
  
    // Shift values left
    for (int i = 1; i < width; i++) { 
        xvals[i - 1] = xvals[i]; 
        yvals[i - 1] = yvals[i];
        bvals[i - 1] = bvals[i];
    } 

    // Add new values
    xvals[width - 1] = mouseX; 
    yvals[width - 1] = mouseY;
  
    if (_mousePressed == true) {
        bvals[width - 1] = 0;
    } else {
        bvals[width - 1] = height / 3;
    }
  
    fill(255);
    noStroke();
    rect(0, height / 3, width, height / 3 + 1);

    for (int i = 1; i < width; i++) {

        // Draw the x-values
        stroke(255);
        point(i, map(xvals[i], 0, width, 0, height / 3 - 1));
    
        // Draw the y-values
        stroke(0);
        point(i, height / 3 + yvals[i] / 3);
    
        // Draw the mouse presses
        stroke(255);
        line(i,
             (2 * height / 3) + bvals[i],
             i,
             (2 * height / 3) + bvals[i - 1]);
    }
}
