/**
 * Multiple Particle Systems
 * by Daniel Shiffman.
 *
 * Click the mouse to generate a burst of particles
 * at mouse position.
 *
 * Each burst is one instance of a particle system
 * with Particles and CrazyParticles (a subclass of Particle).
 * Note use of Inheritance and Polymorphism.
 */

class Particle {
  constructor(l) {
    this.acceleration = createVector(0, 0.05);
    this.velocity = createVector(random(-1, 1), random(-2, 0));
    this.position = l.copy();
    this.lifespan = 255.0;
  }

  run() {
    this.update();
    this.display();
  }

  update() {
    this.velocity.add(this.acceleration);
    this.position.add(this.velocity);
    this.lifespan -= 2.0;
  }

  display() {
    stroke(255, this.lifespan);
    fill(255, this.lifespan);
    ellipse(this.position.x, this.position.y, 8, 8);
  }

  isDead() {
    return (this.lifespan < 0.0);
  }
}

class CrazyParticle extends Particle {
  constructor(l) {
    super(l);
    this.theta = 0.0;
  }

  update() {
    super.update();
    let theta_vel = (this.velocity.x * this.velocity.mag()) / 10.0;
    this.theta += theta_vel;
  }

  display() {
    super.display();
    push();
    translate(this.position.x, this.position.y);
    rotate(this.theta);
    stroke(255, this.lifespan);
    line(0, 0, 25, 0);
    pop();
  }
}

class ParticleSystem {
  constructor(num, v) {
    this.origin = v.copy();
    this.particles = [];
    for (let i = 0; i < num; i++) {
      this.particles.push(new Particle(this.origin));
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

  addParticle() {
    let p;
    if (int(random(0, 2)) == 0) {
      p = new Particle(this.origin);
    } else {
      p = new CrazyParticle(this.origin);
    }
    this.particles.push(p);
  }

  addParticle(p) {
    this.particles.push(p);
  }

  dead() {
    return this.particles.length == 0;
  }
}

let systems = [];

function setup() {
  createCanvas(640, 360);
}

function draw() {
  background(0);
  for (let ps of systems) {
    ps.run();
    ps.addParticle();
  }
  if (systems.length == 0) {
    fill(255);
    textAlign(CENTER);
    text("click mouse to add particle systems", width / 2, height / 2);
  }
}

function mousePressed() {
  systems.push(new ParticleSystem(1, createVector(mouseX, mouseY)));
}
