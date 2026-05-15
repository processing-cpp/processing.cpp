/**
 * Sine. 
 * 
 * Smoothly scaling size with the sin() function. 
 */
 
float diameter; 
float angle = 0.0f;

void setup() {
  size(640, 360);
  diameter = height - 10.0f;
  noStroke();
  fill(255, 204, 0);
}

void draw() {
  
  background(0);

  float d1 = 10.0f + (sin(angle) * diameter/2.0f) + diameter/2.0f;
  float d2 = 10.0f + (sin(angle + PI/2.0f) * diameter/2.0f) + diameter/2.0f;
  float d3 = 10.0f + (sin(angle + PI) * diameter/2.0f) + diameter/2.0f;
  
  ellipse(0.0f, height/2.0f, d1, d1);
  ellipse(width/2.0f, height/2.0f, d2, d2);
  ellipse((float)width, height/2.0f, d3, d3);
  
  angle += 0.02f;
}
