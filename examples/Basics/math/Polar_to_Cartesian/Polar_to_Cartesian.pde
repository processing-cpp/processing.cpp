/**
 * Polar to Cartesian
 * by Daniel Shiffman.  
 * 
 * Convert a polar coordinate (r,theta) to cartesian (x,y).
 * The calculations are x=r*cos(theta) and y=r*sin(theta).  
 */
 
float r;

// Angle and angular velocity, accleration
float theta;
float theta_vel;
float theta_acc;

void setup() {
  size(640, 360);
  
  // Initialize all values
  r = height * 0.45f;
  theta = 0.0f;
  theta_vel = 0.0f;
  theta_acc = 0.0001f;
}

void draw() {
  
  background(0);
  
  // Translate the origin point to the center of the screen
  translate(width/2.0f, height/2.0f);
  
  // Convert polar to cartesian
  float x = r * cos(theta);
  float y = r * sin(theta);
  
  // Draw the ellipse at the cartesian coordinate
  ellipseMode(CENTER);
  noStroke();
  fill(200);
  ellipse(x, y, 32, 32);
  
  // Apply acceleration and velocity to angle 
  theta_vel += theta_acc;
  theta += theta_vel;

}
