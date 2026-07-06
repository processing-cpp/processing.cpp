/**
 * Koch Curve
 * by Daniel Shiffman.
 *
 * Renders a simple fractal, the Koch snowflake.
 * Each recursive level is drawn in sequence.
 */

class KochLine {
  constructor(start, end) {
    this.a = start.copy();
    this.b = end.copy();
  }

  display() {
    stroke(255);
    line(this.a.x, this.a.y, this.b.x, this.b.y);
  }

  start() {
    return this.a.copy();
  }

  end() {
    return this.b.copy();
  }

  kochleft() {
    let v = p5.Vector.sub(this.b, this.a);
    v.div(3);
    v.add(this.a);
    return v;
  }

  kochmiddle() {
    let v = p5.Vector.sub(this.b, this.a);
    v.div(3);

    let p = this.a.copy();
    p.add(v);

    v.rotate(-radians(60));
    p.add(v);

    return p;
  }

  kochright() {
    let v = p5.Vector.sub(this.a, this.b);
    v.div(3);
    v.add(this.b);
    return v;
  }
}

class KochFractal {
  constructor() {
    this.start = createVector(0, height - 20);
    this.end = createVector(width, height - 20);
    this.count = 0;
    this.lines = [];
    this.restart();
  }

  nextLevel() {
    this.lines = this.iterate(this.lines);
    this.count++;
  }

  restart() {
    this.count = 0;
    this.lines = [];
    this.lines.push(new KochLine(this.start, this.end));
  }

  getCount() {
    return this.count;
  }

  render() {
    for (let l of this.lines) {
      l.display();
    }
  }

  iterate(before) {
    let now = [];
    for (let l of before) {
      let a = l.start();
      let b = l.kochleft();
      let c = l.kochmiddle();
      let d = l.kochright();
      let e = l.end();
      now.push(new KochLine(a, b));
      now.push(new KochLine(b, c));
      now.push(new KochLine(c, d));
      now.push(new KochLine(d, e));
    }
    return now;
  }
}

let k;

function setup() {
  createCanvas(640, 360);
  frameRate(1);
  k = new KochFractal();
}

function draw() {
  background(0);
  k.render();
  k.nextLevel();
  if (k.getCount() > 5) {
    k.restart();
  }
}
