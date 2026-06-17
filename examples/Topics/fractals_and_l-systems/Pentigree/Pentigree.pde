/**
 * Pentigree L-System
 * by Geraldine Sarmiento.
 *
 * This example was based on Patrick Dwyer's L-System class.
 */

class LSystem {
public:
    int steps = 0;

    std::string axiom;
    std::string rule;
    std::string production;

    float startLength;
    float drawLength;
    float theta;

    int generations;

    LSystem() {
        axiom = "F";
        rule = "F+F-F";
        startLength = 90.0;
        theta = radians(120.0);
        reset();
    }

    void reset() {
        production = axiom;
        drawLength = startLength;
        generations = 0;
    }

    int getAge() {
        return generations;
    }

    void render() {
        translate(width / 2, height / 2);
        steps += 5;
        if (steps > (int)production.length()) {
            steps = production.length();
        }
        for (int i = 0; i < steps; i++) {
            char step = production[i];
            if (step == 'F') {
                rect(0, 0, -drawLength, -drawLength);
                noFill();
                translate(0, -drawLength);
            } else if (step == '+') {
                rotate(theta);
            } else if (step == '-') {
                rotate(-theta);
            } else if (step == '[') {
                pushMatrix();
            } else if (step == ']') {
                popMatrix();
            }
        }
    }

    void simulate(int gen) {
        while (getAge() < gen) {
            production = iterate(production, rule);
        }
    }

    virtual std::string iterate(std::string prod_, std::string rule_) {
        drawLength = drawLength * 0.6;
        generations++;
        std::string newProduction = "";
        for (int i = 0; i < (int)prod_.size(); i++) {
            if (prod_[i] == 'F') newProduction += rule_;
            else newProduction += prod_[i];
        }
        return newProduction;
    }

    virtual ~LSystem() {}
};

class PentigreeLSystem : public LSystem {
public:
    int steps = 0;
    float somestep = 0.1;
    float xoff = 0.01;

    PentigreeLSystem() {
        axiom = "F-F-F-F-F";
        rule = "F-F++F+F-F-F";
        startLength = 60.0;
        theta = radians(72);
        reset();
    }

    void useRule(std::string r_) { rule = r_; }
    void useAxiom(std::string a_) { axiom = a_; }
    void useLength(float l_) { startLength = l_; }
    void useTheta(float t_) { theta = radians(t_); }

    void reset() {
        production = axiom;
        drawLength = startLength;
        generations = 0;
    }

    int getAge() {
        return generations;
    }

    void render() {
        translate(width / 4, height / 2);
        steps += 3;
        if (steps > (int)production.length()) {
            steps = production.length();
        }

        for (int i = 0; i < steps; i++) {
            char step = production[i];
            if (step == 'F') {
                noFill();
                stroke(255);
                line(0, 0, 0, -drawLength);
                translate(0, -drawLength);
            } else if (step == '+') {
                rotate(theta);
            } else if (step == '-') {
                rotate(-theta);
            } else if (step == '[') {
                pushMatrix();
            } else if (step == ']') {
                popMatrix();
            }
        }
    }
};

PentigreeLSystem ps;

void setup() {
    size(640, 360);
    ps = PentigreeLSystem();
    ps.simulate(3);
}

void draw() {
    background(0);
    ps.render();
}
