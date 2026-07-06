template<typename T>
struct Rect {
    float x, y, w, h;
    bool contains(T px, T py) const {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
};

class QuadNode {
public:
    Rect<float> bounds;
    std::vector<PVector*> points;
    QuadNode* children[4] = {nullptr, nullptr, nullptr, nullptr};
    static const int CAPACITY = 4;

    QuadNode(Rect<float> b) : bounds(b) {}

    ~QuadNode() {
        for (int i = 0; i < 4; i++) {
            delete children[i];
        }
    }

    bool insert(PVector* p) {
        if (!bounds.contains(p->x, p->y)) return false;
        if (points.size() < CAPACITY) {
            points.push_back(p);
            return true;
        }
        if (children[0] == nullptr) subdivide();
        for (int i = 0; i < 4; i++) {
            if (children[i]->insert(p)) return true;
        }
        return false;
    }

    void subdivide() {
        float hw = bounds.w / 2, hh = bounds.h / 2;
        children[0] = new QuadNode(Rect<float>{bounds.x, bounds.y, hw, hh});
        children[1] = new QuadNode(Rect<float>{bounds.x + hw, bounds.y, hw, hh});
        children[2] = new QuadNode(Rect<float>{bounds.x, bounds.y + hh, hw, hh});
        children[3] = new QuadNode(Rect<float>{bounds.x + hw, bounds.y + hh, hw, hh});
    }
};

QuadNode* root = nullptr;
std::vector<PVector> allPoints;

void setup() {
    size(640, 360);
    root = new QuadNode(Rect<float>{0, 0, (float)width, (float)height});
    for (int i = 0; i < 50; i++) {
        allPoints.push_back(PVector(random(width), random(height)));
    }
    for (auto& p : allPoints) {
        root->insert(&p);
    }
}

void draw() {
    background(0);
    for (auto& p : allPoints) {
        ellipse(p.x, p.y, 4, 4);
    }
}
