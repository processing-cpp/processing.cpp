/**
 * Linear Interpolation. 
 * 
 * Move the mouse across the screen and the symbol will follow.  
 * Between drawing each frame of the animation, the ellipse moves 
 * part of the distance (0.05) from its current position toward 
 * the cursor using the lerp() function.
 */
 
float x = 0.0f;
float y = 0.0f;

void setup() {
    size(640, 360); 
    noStroke();  
}

void draw() { 
    background(51);
  
    // lerp() calculates a number between two numbers at a specific increment. 
    // The amt parameter is the amount to interpolate between the two values 
    // where 0.0 equal to the first point, 0.1 is very near the first point, 0.5 
    // is half-way in between, etc.  
  
    // Here we are moving 5% of the way to the mouse location each frame
    x = lerp(x, (float)mouseX, 0.05f);
    y = lerp(y, (float)mouseY, 0.05f);
  
    fill(255);
    stroke(255);
    ellipse(x, y, 66, 66);
}
