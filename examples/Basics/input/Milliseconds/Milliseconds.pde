/**
 * Milliseconds. 
 * 
 * A millisecond is 1/1000 of a second. 
 * Processing keeps track of the number of milliseconds a program has run.
 * By modifying this number with the modulo(%) operator, 
 * different patterns in time are created.  
 */
 
float _scale;

void setup() {
    size(640, 360);
    noStroke();
    _scale = width / 20.0f;
}

void draw() { 
    for (int i = 0; i < (int)_scale; i++) {

        colorMode(RGB, (i + 1) * _scale * 10.0f);

        fill(millis() % (int)((i + 1) * _scale * 10.0f));

        rect(i * _scale, 0, _scale, height);
    }
}
