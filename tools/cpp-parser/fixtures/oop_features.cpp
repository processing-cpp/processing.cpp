// oop_features.cpp -- operator overload, multiple inheritance, ctor init list
class Handle {
public:
    int x_, y_;
    Handle(int x) : x_(x), y_(0) { }
    bool operator==(const Handle& other) const {
        return x_ == other.x_ && y_ == other.y_;
    }
};
void useHandleEquality() {
    Handle a(5);
    Handle b(5);
    bool same = (a == b);
}
class Movable {
public:
    void move() { }
};
class Drawable {
public:
    void draw() { }
};
class Sprite : public Movable, public Drawable {
public:
    void update() {
        move();
        draw();
    }
};
