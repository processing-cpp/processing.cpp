/**
 * Koch Curve
 * by Daniel Shiffman.
 *
 * Renders a simple fractal, the Koch snowflake.
 * Each recursive level is drawn in sequence.
 */

// Koch Curve
// A class to describe one line segment in the fractal
// Includes methods to calculate midPVectors along the line according to the Koch algorithm
struct KochLine {
    // Two PVectors,
    // a is the "left" PVector and
    // b is the "right" PVector
    PVector a;
    PVector b;

    KochLine(PVector start, PVector end) {
        a = start.copy();
        b = end.copy();
    }

    void display() {
        stroke(255);
        line(a.x, a.y, b.x, b.y);
    }

    PVector start() {
        return a.copy();
    }

    PVector end() {
        return b.copy();
    }

    // This is easy, just 1/3 of the way
    PVector kochleft() {
        PVector v = PVector::sub(b, a);
        v.div(3);
        v.add(a);
        return v;
    }

    // More complicated, have to use a little trig to figure out where this PVector is!
    PVector kochmiddle() {
        PVector v = PVector::sub(b, a);
        v.div(3);

        PVector p = a.copy();
        p.add(v);

        v.rotate(-radians(60));
        p.add(v);

        return p;
    }

    // Easy, just 2/3 of the way
    PVector kochright() {
        PVector v = PVector::sub(a, b);
        v.div(3);
        v.add(b);
        return v;
    }
};

// Koch Curve
// A class to manage the list of line segments in the snowflake pattern
struct KochFractal {
    PVector start;                    // A PVector for the start
    PVector end;                      // A PVector for the end
    std::vector<KochLine> lines;      // A list to keep track of all the lines
    int count;

    KochFractal() {
        start = PVector(0, height - 20);
        end   = PVector(width, height - 20);
        count = 0;
        restart();
    }

    void nextLevel() {
        // For every line that is in the vector
        // create 4 more lines in a new vector
        lines = iterate(lines);
        count++;
    }

    void restart() {
        count = 0;           // Reset count
        lines.clear();       // Empty the vector
        lines.push_back(KochLine(start, end));  // Add the initial line
    }

    int getCount() {
        return count;
    }

    // This is easy, just draw all the lines
    void render() {
        for (KochLine& l : lines) {
            l.display();
        }
    }

    // This is where the **MAGIC** happens
    // Step 1: Create an empty vector
    // Step 2: For every line currently in the vector
    //   - calculate 4 line segments based on Koch algorithm
    //   - add all 4 line segments into the new vector
    // Step 3: Return the new vector and it becomes the list of line segments for the structure

    // As we do this over and over again, each line gets broken into 4 lines,
    // which gets broken into 4 lines, and so on. . .
    std::vector<KochLine> iterate(std::vector<KochLine>& before) {
        std::vector<KochLine> now;   // Create empty list
        for (KochLine& l : before) {
            // Calculate 5 koch PVectors (done for us by the line object)
            PVector a = l.start();
            PVector b = l.kochleft();
            PVector c = l.kochmiddle();
            PVector d = l.kochright();
            PVector e = l.end();
            // Make line segments between all the PVectors and add them
            now.push_back(KochLine(a, b));
            now.push_back(KochLine(b, c));
            now.push_back(KochLine(c, d));
            now.push_back(KochLine(d, e));
        }
        return now;
    }
};

KochFractal k;

void setup() {
    size(640, 360);
    frameRate(1);  // Animate slowly
    k = KochFractal();
}

void draw() {
    background(0);
    // Draws the snowflake!
    k.render();
    // Iterate
    k.nextLevel();
    // Let's not do it more than 5 times. . .
    if (k.getCount() > 5) {
        k.restart();
    }
}
