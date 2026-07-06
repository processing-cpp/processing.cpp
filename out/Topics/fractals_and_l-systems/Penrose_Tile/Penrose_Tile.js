/**
 * Penrose Tile L-System
 * by Geraldine Sarmiento.
 *
 * This example was based on Patrick Dwyer's L-System class.
 */

class LSystem {
  constructor() {
    this.steps = 0;
    this.axiom = "F";
    this.rule = "F+F-F";
    this.production = "";
    this.startLength = 190.0;
    this.drawLength = 190.0;
    this.theta = radians(120.0);
    this.generations = 0;
    this.reset();
  }

  reset() {
    this.production = this.axiom;
    this.drawLength = this.startLength;
    this.generations = 0;
  }

  getAge() {
    return this.generations;
  }

  render() {
    translate(width / 2, height / 2);
    this.steps += 5;
    if (this.steps > this.production.length) {
      this.steps = this.production.length;
    }
    for (let i = 0; i < this.steps; i++) {
      let step = this.production.charAt(i);
      if (step == 'F') {
        rect(0, 0, -this.drawLength, -this.drawLength);
        noFill();
        translate(0, -this.drawLength);
      } else if (step == '+') {
        rotate(this.theta);
      } else if (step == '-') {
        rotate(-this.theta);
      } else if (step == '[') {
        push();
      } else if (step == ']') {
        pop();
      }
    }
  }

  simulate(gen) {
    while (this.getAge() < gen) {
      this.production = this.iterate(this.production, this.rule);
    }
  }

  iterate(prod_, rule_) {
    this.drawLength = this.drawLength * 0.6;
    this.generations++;
    let result = "";
    for (let i = 0; i < prod_.length; i++) {
      let c = prod_.charAt(i);
      if (c == 'F') result += rule_;
      else result += c;
    }
    return result;
  }
}

class PenroseLSystem extends LSystem {
  constructor() {
    super();
    this.steps = 0;
    this.somestep = 0.1;
    this.axiom = "[X]++[X]++[X]++[X]++[X]";
    this.ruleW = "YF++ZF4-XF[-YF4-WF]++";
    this.ruleX = "+YF--ZF[3-WF--XF]+";
    this.ruleY = "-WF++XF[+++YF++ZF]-";
    this.ruleZ = "--YF++++WF[+ZF++++XF]--XF";
    this.startLength = 460.0;
    this.theta = radians(36);
    this.reset();
  }

  useRule(r_) { this.rule = r_; }
  useAxiom(a_) { this.axiom = a_; }
  useLength(l_) { this.startLength = l_; }
  useTheta(t_) { this.theta = radians(t_); }

  reset() {
    this.production = this.axiom;
    this.drawLength = this.startLength;
    this.generations = 0;
  }

  getAge() { return this.generations; }

  render() {
    translate(width / 2, height / 2);
    let pushes = 0;
    let repeats = 1;
    this.steps += 12;
    if (this.steps > this.production.length) {
      this.steps = this.production.length;
    }
    for (let i = 0; i < this.steps; i++) {
      let step = this.production.charAt(i);
      if (step == 'F') {
        stroke(255, 60);
        for (let j = 0; j < repeats; j++) {
          line(0, 0, 0, -this.drawLength);
          noFill();
          translate(0, -this.drawLength);
        }
        repeats = 1;
      } else if (step == '+') {
        for (let j = 0; j < repeats; j++) { rotate(this.theta); }
        repeats = 1;
      } else if (step == '-') {
        for (let j = 0; j < repeats; j++) { rotate(-this.theta); }
        repeats = 1;
      } else if (step == '[') {
        pushes++;
        push();
      } else if (step == ']') {
        pop();
        pushes--;
      } else if (step >= '0' && step <= '9') {
        repeats = parseInt(step);
      }
    }
    while (pushes > 0) { pop(); pushes--; }
  }

  iterate(prod_, rule_) {
    let newProduction = "";
    for (let i = 0; i < prod_.length; i++) {
      let step = prod_.charAt(i);
      if      (step == 'W') newProduction += this.ruleW;
      else if (step == 'X') newProduction += this.ruleX;
      else if (step == 'Y') newProduction += this.ruleY;
      else if (step == 'Z') newProduction += this.ruleZ;
      else if (step != 'F') newProduction += step;
    }
    this.drawLength = this.drawLength * 0.5;
    this.generations++;
    return newProduction;
  }
}

let ds;

function setup() {
  createCanvas(640, 360);
  ds = new PenroseLSystem();
  ds.simulate(4);
}

function draw() {
  background(0);
  ds.render();
}
