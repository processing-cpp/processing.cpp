/**
 * Wolfram Cellular Automata
 * by Daniel Shiffman.
 * Translated to C++ Mode.
 *
 * Simple demonstration of Wolfram's 1-dimensional cellular automata.
 * Restarts with a new ruleset when it reaches the bottom.
 * Click to restart.
 */

class CA {
  constructor(r) {
    this.rules = r;
    this.cells = new Array(width / this.scl).fill(0);
    this.generation = 0;
    this.scl = 1;
    this.restart();
  }

  setRules(r) {
    this.rules = r;
  }

  randomize() {
    for (let i = 0; i < 8; i++) {
      this.rules[i] = int(random(2));
    }
  }

  restart() {
    this.cells.fill(0);
    this.cells[Math.floor(this.cells.length / 2)] = 1;
    this.generation = 0;
  }

  generate() {
    let nextgen = new Array(this.cells.length).fill(0);
    for (let i = 1; i < this.cells.length - 1; i++) {
      let left = this.cells[i - 1];
      let me = this.cells[i];
      let right = this.cells[i + 1];
      nextgen[i] = this.executeRules(left, me, right);
    }
    for (let i = 1; i < this.cells.length - 1; i++) {
      this.cells[i] = nextgen[i];
    }
    this.generation++;
  }

  render() {
    for (let i = 0; i < this.cells.length; i++) {
      fill(this.cells[i] == 1 ? 255 : 0);
      noStroke();
      rect(i * this.scl, this.generation * this.scl, this.scl, this.scl);
    }
  }

  executeRules(a, b, c) {
    if (a == 1 && b == 1 && c == 1) return this.rules[0];
    if (a == 1 && b == 1 && c == 0) return this.rules[1];
    if (a == 1 && b == 0 && c == 1) return this.rules[2];
    if (a == 1 && b == 0 && c == 0) return this.rules[3];
    if (a == 0 && b == 1 && c == 1) return this.rules[4];
    if (a == 0 && b == 1 && c == 0) return this.rules[5];
    if (a == 0 && b == 0 && c == 1) return this.rules[6];
    if (a == 0 && b == 0 && c == 0) return this.rules[7];
    return 0;
  }

  finished() {
    return this.generation > height / this.scl;
  }
}

let ca = null;

function setup() {
  createCanvas(640, 360);
  let ruleset = [0, 1, 0, 1, 1, 0, 1, 0];
  ca = new CA(ruleset);
  background(0);
}

function draw() {
  ca.render();
  ca.generate();

  if (ca.finished()) {
    background(0);
    ca.randomize();
    ca.restart();
  }
}

function mousePressed() {
  background(0);
  ca.randomize();
  ca.restart();
}
