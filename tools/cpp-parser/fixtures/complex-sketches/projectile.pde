#include <math.h>
struct Vec2 {
    float x, y;
    Vec2() : x(0), y(0) {}
    Vec2(float x, float y) : x(x), y(y) {}
    Vec2 operator+(const Vec2& b) const { return Vec2(x + b.x, y + b.y); }
    Vec2 operator-(const Vec2& b) const { return Vec2(x - b.x, y - b.y); }
    Vec2 operator*(float s) const { return Vec2(x * s, y * s); }
    Vec2& operator+=(const Vec2& b) {
        x += b.x; y += b.y;
        return *this;
    }
};
float length(Vec2 v) {
    return sqrt(v.x*v.x + v.y*v.y);
}
Vec2 normalize(Vec2 v) {
    float l = length(v);
    if (l == 0) return Vec2(0,0);
    return Vec2(v.x/l, v.y/l);
}
#define STRF(x) str((float)(x))
Vec2 pos;
Vec2 vel;
Vec2 accel;
float mass = 1.0f;
Vec2 gravity = Vec2(0, 0.4f);
float dragCoeff = 0.02f;
bool quadraticDrag = true;
float dt = 0.016f;
bool paused = false;
Vec2 trail[2000];
int trailSize = 0;
float launchAngle = 45;
float launchSpeed = 20;
void launch() {
    float rad = launchAngle * 3.14159 / 180.0;
    vel.x = cos(rad) * launchSpeed;
    vel.y = -sin(rad) * launchSpeed;
}
void setup() {
    size(1200, 800);
    pos = Vec2(100, 600);
    launch();
}
Vec2 computeDrag(Vec2 v) {
    float speed = length(v);
    if (!quadraticDrag) {
        return v * (-dragCoeff);
    }
    return v * (-dragCoeff * speed);
}
Vec2 computeAcceleration(Vec2 v) {
    Vec2 drag = computeDrag(v);
    Vec2 a;
    a.x = gravity.x + drag.x / mass;
    a.y = gravity.y + drag.y / mass;
    return a;
}
void step() {
    if (paused) return;
    accel = computeAcceleration(vel);
    vel += accel * dt;
    pos += vel * dt;
    if (trailSize < 2000) {
        trail[trailSize++] = pos;
    }
}
void drawHUD() {
    fill(0);
    textSize(14);
    text("Position: (" + STRF(pos.x) + ", " + STRF(pos.y) + ")", 20, 20);
    text("Speed: " + STRF(length(vel)), 20, 60);
    text("Mode: " + String(quadraticDrag ? "Quadratic" : "Linear"), 20, 140);
}
void drawVector(Vec2 origin, Vec2 v, float scale, color c) {
    stroke(c);
    line(origin.x, origin.y, origin.x + v.x * scale, origin.y + v.y * scale);
    Vec2 end(origin.x + v.x * scale, origin.y + v.y * scale);
    line(end.x, end.y, end.x - 5, end.y - 5);
}
void drawTrail() {
    stroke(150);
    for (int i = 1; i < trailSize; i++) {
        line(trail[i-1].x, trail[i-1].y, trail[i].x, trail[i].y);
    }
}
void draw() {
    background(255);
    step();
    drawTrail();
    fill(0);
    ellipse(pos.x, pos.y, 10, 10);
    drawVector(pos, vel, 5, color(0, 0, 255));
    drawHUD();
}
