struct Vec2 {
    float x, y;
    Vec2(float x_ = 0, float y_ = 0) : x(x_), y(y_) {}

    Vec2 operator+(const Vec2& o) const { return Vec2(x + o.x, y + o.y); }
    Vec2 operator-(const Vec2& o) const { return Vec2(x - o.x, y - o.y); }
    Vec2 operator*(float s) const { return Vec2(x * s, y * s); }
    Vec2& operator+=(const Vec2& o) { x += o.x; y += o.y; return *this; }
    float dot(const Vec2& o) const { return x * o.x + y * o.y; }
    float length() const { return sqrt(x * x + y * y); }
    Vec2 normalized() const {
        float len = length();
        if (len == 0) return Vec2(0, 0);
        return Vec2(x / len, y / len);
    }
};

class Boid {
public:
    Vec2 pos, vel, acc;
    Boid(Vec2 p) : pos(p), vel(Vec2(random(-1,1), random(-1,1))) {}

    void applyForce(const Vec2& f) { acc += f; }

    void update() {
        vel += acc;
        pos += vel;
        acc = Vec2(0, 0);
    }

    void display() {
        pushMatrix();
        translate(pos.x, pos.y);
        ellipse(0, 0, 6, 6);
        popMatrix();
    }
};

std::vector<Boid*> boids;

void setup() {
    size(640, 360);
    for (int i = 0; i < 20; i++) {
        boids.push_back(new Boid(Vec2(random(width), random(height))));
    }
}

void draw() {
    background(0);
    for (Boid* b : boids) {
        Vec2 toCenter = Vec2(width / 2.0f, height / 2.0f) - b->pos;
        b->applyForce(toCenter.normalized() * 0.05f);
        b->update();
        b->display();
    }
}
