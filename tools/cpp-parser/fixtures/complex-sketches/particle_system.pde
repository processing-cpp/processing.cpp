struct Particle {
    PVector pos;
    PVector vel;
    PVector acc;
    float lifespan = 255;

    Particle(PVector p) {
        pos = p;
        vel = PVector(random(-1, 1), random(-2, 0));
        acc = PVector(0, 0.05f);
    }

    void update() {
        vel += acc;
        pos += vel;
        lifespan -= 2.0f;
    }

    bool isDead() {
        return lifespan < 0;
    }

    void display() {
        stroke(255, lifespan);
        fill(255, lifespan);
        ellipse(pos.x, pos.y, 8, 8);
    }
};

std::vector<Particle> particles;

void setup() {
    size(640, 360);
}

void draw() {
    background(0);
    particles.push_back(Particle(PVector(width / 2, height - 10)));

    for (auto it = particles.begin(); it != particles.end();) {
        it->update();
        it->display();
        if (it->isDead()) {
            it = particles.erase(it);
        } else {
            ++it;
        }
    }
}
