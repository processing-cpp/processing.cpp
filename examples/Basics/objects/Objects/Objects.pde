/**
 * Objects
 * by hbarragan.
 *
 * Move the cursor across the image to change the speed and positions
 * of the geometry. The class MRect defines a group of lines.
 */

class MRect {
public:
    int w;
    float xpos, h, ypos, d, t;

    MRect(int iw, float ixp, float ih, float iyp, float id, float it) {
        w = iw; xpos = ixp; h = ih; ypos = iyp; d = id; t = it;
    }

    void move(float posX, float posY, float damping) {
        float dif = ypos - posY;
        if (abs(dif) > 1) ypos -= dif / damping;
        dif = xpos - posX;
        if (abs(dif) > 1) xpos -= dif / damping;
    }

    void display() {
        for (int i = 0; i < (int)t; i++)
            rect(xpos + (i * (d + w)), ypos, w, height * h);
    }
};

MRect* r1;
MRect* r2;
MRect* r3;
MRect* r4;

void setup() {
    size(640, 360);
    fill(255, 204);
    noStroke();
    r1 = new MRect(1, 134.0f, 0.532f, 0.1f*height, 10.0f, 60.0f);
    r2 = new MRect(2,  44.0f, 0.166f, 0.3f*height,  5.0f, 50.0f);
    r3 = new MRect(2,  58.0f, 0.332f, 0.4f*height, 10.0f, 35.0f);
    r4 = new MRect(1, 120.0f, 0.0498f,0.9f*height, 15.0f, 60.0f);
}

void draw() {
    background(0);
    r1->display(); r2->display(); r3->display(); r4->display();
    r1->move(mouseX - (width/2), mouseY + (height*0.1f), 30);
    r2->move((int)(mouseX + (width*0.05f)) % width, mouseY + (height*0.025f), 20);
    r3->move(mouseX/4, mouseY - (height*0.025f), 40);
    r4->move(mouseX - (width/2), (height - mouseY), 50);
}
