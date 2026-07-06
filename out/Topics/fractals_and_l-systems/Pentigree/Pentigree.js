/**
 * Pentigree L-System
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
    this.startLength = 90.0;
    this.drawLength = 90.0;
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
    let newProduction = "";
    for (let i = 0; i < prod_.length; i++) {
      if (prod_.charAt(i) == 'F') newProduction += rule_;
      else newProduction += prod_.charAt(i);
    }
    return newProduction;
  }
}

class PentigreeLSystem extends LSystem {
  constructor() {
    super();
    this.steps = 0;
    this.somestep = 0.1;
    this.xoff = 0.01;
    this.axiom = "F-F-F-F-F";
    this.rule = "F-F++F+F-F-F";
    this.startLength = 60.0;
    this.theta = radians(72);
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

  getAge() {
    return this.generations;
  }

  render() {
    translate(width / 4, height / 2);
    this.steps += 3;
    if (this.steps > this.production.length) {
      this.steps = this.production.length;
    }

    for (let i = 0; i < this.steps; i++) {
      let step = this.production.charAt(i);
      if (step == 'F') {
        noFill();
        stroke(255);
        line(0, 0, 0, -this.drawLength);
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
}

let ps;

function setup() {
  createCanvas(640, 360);
  ps = new PentigreeLSystem();
  ps.simulate(3);
}

function draw() {
  background(0);
  ps.render();
}
