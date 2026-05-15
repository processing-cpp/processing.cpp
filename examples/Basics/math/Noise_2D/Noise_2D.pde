/**
 * Noise2D 
 * by Daniel Shiffman.  
 * 
 * Using 2D noise to create simple texture. 
 */
 
float increment = 0.02f;

void setup() {
    size(640, 360);
}

void draw() {
  
    loadPixels();

    float xoff = 0.0f; // Start xoff at 0

    float detail = map((float)mouseX, 0.0f, (float)width, 0.1f, 0.6f);
    noiseDetail(8, detail);
  
    // For every x,y coordinate in a 2D space, calculate a noise value and produce a brightness value
    for (int x = 0; x < width; x++) {
        xoff += increment;   // Increment xoff 
        float yoff = 0.0f;   // For every xoff, start yoff at 0

        for (int y = 0; y < height; y++) {
            yoff += increment; // Increment yoff
      
            // Calculate noise and scale by 255
            float bright = noise(xoff, yoff) * 255.0f;

            // Try using this line instead
            //float bright = random(0.0f, 255.0f);
      
            // Set each pixel onscreen to a grayscale value
            pixels[x + y * width] = color(bright);
        }
    }
  
    updatePixels();
}
