/**
 * Penrose Snowflake L-System
 * by Geraldine Sarmiento.
 *
 * This example was based on Patrick Dwyer's L-System class.
 */

class LSystem {
  constructor() {
    this.steps = 0;
    this.axiom = "F";
    this.rule = "F+F-F";
    this.startLength = 90.0;
    this.theta = radians(120.0);
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
    let newProduction = prod_;
    let result = "";
    for (let i = 0; i < newProduction.length; i++) {
      if (newProduction.charAt(i) == 'F') result += rule_;
      else result += newProduction.charAt(i);
    }
    return result;
  }
}

class PenroseSnowflakeLSystem extends LSystem {
  constructor() {
    super();
    this.axiom = "F3-F3-F3-F3-F";
    this.ruleF = "F3-F3-F45-F++F3-F";
    this.startLength = 450.0;
    this.theta = radians(18);
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
    translate(width, height);
    let repeats = 1;

    this.steps += 3;
    if (this.steps > this.production.length) {
      this.steps = this.production.length;
    }

    for (let i = 0; i < this.steps; i++) {
      let step = this.production.charAt(i);
      if (step == 'F') {
        for (let j = 0; j < repeats; j++) {
          line(0, 0, 0, -this.drawLength);
          translate(0, -this.drawLength);
        }
        repeats = 1;
      } else if (step == '+') {
        for (let j = 0; j < repeats; j++) {
          rotate(this.theta);
        }
        repeats = 1;
      } else if (step == '-') {
        for (let j = 0; j < repeats; j++) {
          rotate(-this.theta);
        }
        repeats = 1;
      } else if (step == '[') {
        push();
      } else if (step == ']') {
        pop();
      } else if (step >= '0' && step <= '9') {
        repeats += parseInt(step);
      }
    }
  }

  iterate(prod_, rule_) {
    let newProduction = "";
    for (let i = 0; i < prod_.length; i++) {
      let step = prod_.charAt(i);
      if (step == 'F') {
        newProduction += this.ruleF;
      } else {
        newProduction += step;
      }
    }
    this.drawLength = this.drawLength * 0.4;
    this.generations++;
    return newProduction;
  }
}

let ps;

function setup() {
  createCanvas(640, 360);
  stroke(255);
  noFill();
  ps = new PenroseSnowflakeLSystem();
  ps.simulate(4);
}

function draw() {
  background(0);
  ps.render();
}
