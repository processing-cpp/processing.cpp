/**
 * Non-orthogonal Collision with Multiple Ground Segments
 * by Ira Greenberg.
 *
 * Based on Keith Peter's Solution in
 * Foundation Actionscript Animation: Making Things Move!
 */

Orb* orb;

PVector gravity(0, 0.05);
// The ground is an array of "Ground" objects
int segments = 40;
// Array<T> is fixed-size at construction (like Java arrays), and since
// Ground has no default constructor, this stores Ground* rather than
// Ground -- every slot starts null, exactly like Java's "new Ground[segments]"
// array of null references, filled in below.
Array<Ground*> ground(segments);

void setup(){
  size(640, 360);
  // An orb object that will fall and bounce around
  orb = new Orb(50, 50, 3);

  // Calculate ground peak heights
  Array<float> peakHeights(segments+1);
  for (int i=0; i<peakHeights.length(); i++){
    peakHeights[i] = random(height-40, height-30);
  }

  /* Float value required for segment width (segs)
   calculations so the ground spans the entire
   display window, regardless of segment number. */
  float segs = segments;
  for (int i=0; i<segments; i++){
    ground[i]  = new Ground(width/segs*i, peakHeights[i], width/segs*(i+1), peakHeights[i+1]);
  }
}

void draw(){
  // Background
  noStroke();
  fill(0, 15);
  rect(0, 0, width, height);

  // Move and display the orb
  orb->move();
  orb->display();
  // Check walls
  orb->checkWallCollision();

  // Check against all the ground segments
  for (int i=0; i<segments; i++){
    orb->checkGroundCollision(ground[i]);
  }

  // Draw ground
  fill(127);
  beginShape();
  for (int i=0; i<segments; i++){
    vertex(ground[i]->x1, ground[i]->y1);
    vertex(ground[i]->x2, ground[i]->y2);
  }
  vertex(ground[segments-1]->x2, height);
  vertex(ground[0]->x1, height);
  endShape(CLOSE);
}
