/**
 * Handles.
 *
 * Click and drag the white boxes to change their position.
 */

bool firstMousePress = false;

class Handle {
public:
    int x, y;
    int boxx, boxy;
    int stretch;
    int size;
    bool over;
    bool press;
    bool locked = false;
    bool otherslocked = false;
    std::vector<Handle>* others;

    Handle(int ix, int iy, int il, int is, std::vector<Handle>* o) {
        x = ix;
        y = iy;
        stretch = il;
        size = is;
        boxx = x + stretch - size / 2;
        boxy = y - size / 2;
        others = o;
        over = false;
        press = false;
    }

    void update() {
        boxx = x + stretch;
        boxy = y - size / 2;

        for (int i = 0; i < (int)others->size(); i++) {
            if ((*others)[i].locked == true) {
                otherslocked = true;
                break;
            } else {
                otherslocked = false;
            }
        }

        if (otherslocked == false) {
            overEvent();
            pressEvent();
        }

        if (press) {
            stretch = lock(mouseX - width / 2 - size / 2, 0, width / 2 - size - 1);
        }
    }

    void overEvent() {
        if (overRect(boxx, boxy, size, size)) {
            over = true;
        } else {
            over = false;
        }
    }

    void pressEvent() {
        if ((over && firstMousePress) || locked) {
            press = true;
            locked = true;
        } else {
            press = false;
        }
    }

    void releaseEvent() {
        locked = false;
    }

    void display() {
        line(x, y, x + stretch, y);
        fill(255);
        stroke(0);
        rect(boxx, boxy, size, size);
        if (over || press) {
            line(boxx, boxy, boxx + size, boxy + size);
            line(boxx, boxy + size, boxx + size, boxy);
        }
    }

    bool overRect(int rx, int ry, int rw, int rh) {
        if (mouseX >= rx && mouseX <= rx + rw &&
            mouseY >= ry && mouseY <= ry + rh) {
            return true;
        } else {
            return false;
        }
    }

    int lock(int val, int minv, int maxv) {
        return min(max(val, minv), maxv);
    }
};

std::vector<Handle> handles;

void setup() {
    size(640, 360);
    int num = height / 15;
    int hsize = 10;
    handles.resize(num, Handle(0, 0, 0, 0, &handles));
    for (int i = 0; i < (int)handles.size(); i++) {
        handles[i] = Handle(width / 2, 10 + i * 15, 50 - hsize / 2, 10, &handles);
    }
}

void draw() {
    background(153);

    for (int i = 0; i < (int)handles.size(); i++) {
        handles[i].update();
        handles[i].display();
    }

    fill(0);
    rect(0, 0, width / 2, height);

    if (firstMousePress) {
        firstMousePress = false;
    }
}

void mousePressed() {
    if (!firstMousePress) {
        firstMousePress = true;
    }
}

void mouseReleased() {
    for (int i = 0; i < (int)handles.size(); i++) {
        handles[i].releaseEvent();
    }
}
