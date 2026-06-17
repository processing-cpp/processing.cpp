/**
 * Penrose Tile L-System
 * by Geraldine Sarmiento.
 *
 * This example was based on Patrick Dwyer's L-System class.
 */

struct LSystem {
    int steps = 0;
    std::string axiom;
    std::string rule;
    std::string production;
    float startLength;
    float drawLength;
    float theta;
    int generations = 0;

    LSystem() {
        axiom = "F";
        rule = "F+F-F";
        startLength = 190.0;
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
        std::string result = "";
        for (char c : prod_) {
            if (c == 'F') result += rule_;
            else result += c;
        }
        return result;
    }

    virtual ~LSystem() {}
};

struct PenroseLSystem : public LSystem {
    int steps = 0;
    float somestep = 0.1;
    std::string ruleW;
    std::string ruleX;
    std::string ruleY;
    std::string ruleZ;

    PenroseLSystem() {
        axiom = "[X]++[X]++[X]++[X]++[X]";
        ruleW = "YF++ZF4-XF[-YF4-WF]++";
        ruleX = "+YF--ZF[3-WF--XF]+";
        ruleY = "-WF++XF[+++YF++ZF]-";
        ruleZ = "--YF++++WF[+ZF++++XF]--XF";
        startLength = 460.0;
        theta = radians(36);
        reset();
    }

    void useRule(std::string r_)  { rule = r_; }
    void useAxiom(std::string a_) { axiom = a_; }
    void useLength(float l_)      { startLength = l_; }
    void useTheta(float t_)       { theta = radians(t_); }

    void reset() {
        production = axiom;
        drawLength = startLength;
        generations = 0;
    }

    int getAge() { return generations; }

    void render() {
        translate(width / 2, height / 2);
        int pushes = 0;
        int repeats = 1;
        steps += 12;
        if (steps > (int)production.length()) {
            steps = production.length();
        }
        for (int i = 0; i < steps; i++) {
            char step = production[i];
            if (step == 'F') {
                stroke(255, 60);
                for (int j = 0; j < repeats; j++) {
                    line(0, 0, 0, -drawLength);
                    noFill();
                    translate(0, -drawLength);
                }
                repeats = 1;
            } else if (step == '+') {
                for (int j = 0; j < repeats; j++) { rotate(theta); }
                repeats = 1;
            } else if (step == '-') {
                for (int j = 0; j < repeats; j++) { rotate(-theta); }
                repeats = 1;
            } else if (step == '[') {
                pushes++;
                pushMatrix();
            } else if (step == ']') {
                popMatrix();
                pushes--;
            } else if (step >= 48 && step <= 57) {
                repeats = (int)step - 48;
            }
        }
        while (pushes > 0) { popMatrix(); pushes--; }
    }

    std::string iterate(std::string prod_, std::string rule_) override {
        std::string newProduction = "";
        for (int i = 0; i < (int)prod_.size(); i++) {
            char step = prod_[i];
            if      (step == 'W') newProduction += ruleW;
            else if (step == 'X') newProduction += ruleX;
            else if (step == 'Y') newProduction += ruleY;
            else if (step == 'Z') newProduction += ruleZ;
            else if (step != 'F') newProduction += step;
        }
        drawLength = drawLength * 0.5;
        generations++;
        return newProduction;
    }
};

PenroseLSystem ds;

void setup() {
    size(640, 360);
    ds = PenroseLSystem();
    ds.simulate(4);
}

void draw() {
    background(0);
    ds.render();
}
