/**
 * Smoke Particle System
 * by Daniel Shiffman.
 *
 * A basic smoke effect using a particle system. Each particle
 * is rendered as an alpha masked image.
 */

class Particle {
  constructor(l, img_) {
    this.acc = createVector(0, 0);
    let vx = randomGaussian() * 0.3;
    let vy = randomGaussian() * 0.3 - 1.0;
    this.vel = createVector(vx, vy);
    this.loc = l.copy();
    this.lifespan = 100.0;
    this.img = img_;
  }

  run() {
    this.update();
    this.render();
  }

  applyForce(f) {
    this.acc.add(f);
  }

  update() {
    this.vel.add(this.acc);
    this.loc.add(this.vel);
    this.lifespan -= 2.5;
    this.acc.mult(0);
  }

  render() {
    imageMode(CENTER);
    tint(255, this.lifespan);
    image(this.img, this.loc.x, this.loc.y);
  }

  isDead() {
    if (this.lifespan <= 0.0) {
      return true;
    } else {
      return false;
    }
  }
}

class ParticleSystem {
  constructor(num, v, img_) {
    this.origin = v.copy();
    this.img = img_;
    this.particles = [];
    for (let i = 0; i < num; i++) {
      this.particles.push(new Particle(this.origin, this.img));
    }
  }

  run() {
    for (let i = this.particles.length - 1; i >= 0; i--) {
      let p = this.particles[i];
      p.run();
      if (p.isDead()) {
        this.particles.splice(i, 1);
      }
    }
  }

  applyForce(dir) {
    for (let p of this.particles) {
      p.applyForce(dir);
    }
  }

  addParticle() {
    this.particles.push(new Particle(this.origin, this.img));
  }
}

let ps;

function setup() {
  createCanvas(640, 360);
  let img = loadImage("texture.png");
  ps = new ParticleSystem(0, createVector(width / 2, height - 60), img);
}

function draw() {
  background(0);

  let dx = map(mouseX, 0, width, -0.2, 0.2);
  let wind = createVector(dx, 0);
  ps.applyForce(wind);
  ps.run();
  for (let i = 0; i < 2; i++) {
    ps.addParticle();
  }

  drawVector(wind, createVector(width / 2, 50), 500);
}

function drawVector(v, loc, scayl) {
  push();
  let arrowsize = 4;
  translate(loc.x, loc.y);
  stroke(255);
  rotate(v.heading());
  let len = v.mag() * scayl;
  line(0, 0, len, 0);
  line(len, 0, len - arrowsize, +arrowsize / 2);
  line(len, 0, len - arrowsize, -arrowsize / 2);
  pop();
}
