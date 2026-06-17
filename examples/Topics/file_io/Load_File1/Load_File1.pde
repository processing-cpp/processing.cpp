/**
 * LoadFile 1
 *
 * Loads a text file that contains two numbers separated by a tab.
 * A new pair of numbers is loaded each frame and used to draw a point.
 * Translated to C++ Mode.
 */

std::vector<std::string> lines;
int index = 0;

void setup() {
    size(640, 360);
    background(0);
    stroke(255);
    frameRate(12);
    lines = loadStrings("data/positions.txt");
}

void draw() {
    if (index < (int)lines.size()) {
        std::vector<std::string> pieces = split(lines[index], '\t');
        if (pieces.size() == 2) {
            float x = map(toFloat(pieces[0]), 0, 100, 0, width);
            float y = map(toFloat(pieces[1]), 0, 100, 0, height);
            point(x, y);
        }
        index++;
    }
}
