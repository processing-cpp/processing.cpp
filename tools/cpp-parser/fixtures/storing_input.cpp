/**
 * Storing Input. 
 * 
 * Move the mouse across the screen to change the position
 * of the circles. The positions of the mouse are recorded
 * into an array and played back every frame. Between each
 * frame, the newest value are added to the end of each array
 * and the oldest value is deleted. 
 */

const int num = 60;
float mx[num];
float my[num];

void setup() {
    size(640, 360);
    noStroke();
    fill(255, 153); 
}

void draw() {
    background(51); 
  
    // Cycle through the array using modulo
    int which = frameCount % num;

    mx[which] = mouseX;
    my[which] = mouseY;
  
    for (int i = 0; i < num; i++) {
        // Oldest to newest ordering
        int index = (which + 1 + i) % num;
        ellipse(mx[index], my[index], i, i);
    }
}
