/**
 * Embedding Iteration. 
 * 
 * Embedding "for" structures allows repetition in two dimensions. 
 *
 */

size(640, 360); 
background(0); 

int gridSize = 40;

for (int x = gridSize; x <= width - gridSize; x += gridSize) {
    for (int y = gridSize; y <= height - gridSize; y += gridSize) {
        noStroke();
        fill(255);
        rect(x - 1, y - 1, 3, 3);

        // avoid overload ambiguity (gray + alpha)
        stroke(255.0f, 100.0f);

        line(x, y, width / 2, height / 2);
    }
}
