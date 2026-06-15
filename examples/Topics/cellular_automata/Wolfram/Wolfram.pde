/**
 * Wolfram Cellular Automata
 * by Daniel Shiffman.
 * Translated to C++ Mode.
 *
 * Simple demonstration of Wolfram's 1-dimensional cellular automata.
 * Restarts with a new ruleset when it reaches the bottom.
 * Click to restart.
 */

struct CA {
    std::vector<int> cells;
    std::vector<int> rules;
    int generation = 0;
    int scl = 1;

    CA(std::vector<int> r) {
        rules = r;
        cells.resize(width / scl, 0);
        restart();
    }

    void setRules(std::vector<int> r) {
        rules = r;
    }

    void randomize() {
        for (int i = 0; i < 8; i++) {
            rules[i] = (int)random(2);
        }
    }

    void restart() {
        std::fill(cells.begin(), cells.end(), 0);
        cells[cells.size() / 2] = 1;
        generation = 0;
    }

    void generate() {
        std::vector<int> nextgen(cells.size(), 0);
        for (int i = 1; i < (int)cells.size() - 1; i++) {
            int left  = cells[i - 1];
            int me    = cells[i];
            int right = cells[i + 1];
            nextgen[i] = executeRules(left, me, right);
        }
        for (int i = 1; i < (int)cells.size() - 1; i++) {
            cells[i] = nextgen[i];
        }
        generation++;
    }

    void render() {
        for (int i = 0; i < (int)cells.size(); i++) {
            fill(cells[i] == 1 ? 255 : 0);
            noStroke();
            rect(i * scl, generation * scl, scl, scl);
        }
    }

    int executeRules(int a, int b, int c) {
        if (a == 1 && b == 1 && c == 1) return rules[0];
        if (a == 1 && b == 1 && c == 0) return rules[1];
        if (a == 1 && b == 0 && c == 1) return rules[2];
        if (a == 1 && b == 0 && c == 0) return rules[3];
        if (a == 0 && b == 1 && c == 1) return rules[4];
        if (a == 0 && b == 1 && c == 0) return rules[5];
        if (a == 0 && b == 0 && c == 1) return rules[6];
        if (a == 0 && b == 0 && c == 0) return rules[7];
        return 0;
    }

    bool finished() {
        return generation > height / scl;
    }
};

CA* ca = nullptr;

void setup() {
    size(640, 360);
    std::vector<int> ruleset = {0, 1, 0, 1, 1, 0, 1, 0};
    ca = new CA(ruleset);
    background(0);
}

void draw() {
    ca->render();
    ca->generate();

    if (ca->finished()) {
        background(0);
        ca->randomize();
        ca->restart();
    }
}

void mousePressed() {
    background(0);
    ca->randomize();
    ca->restart();
}
