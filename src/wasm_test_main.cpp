#include "Processing.h"

namespace Processing {

class TestSketch : public PApplet {
    float t = 0;
public:
    void setup() override { size(900, 700); }
    void draw() override {
        background(15);
        t += 0.015f;

        // 3-point star
        fill(255,100,100,200); stroke(255); strokeWeight(1.5f);
        beginShape();
        for(int i=0;i<6;i++){
            float a=TWO_PI*i/6+t; float r=(i%2==0)?120:50;
            vertex(150+cos(a)*r, 150+sin(a)*r);
        }
        endShape(CLOSE);

        // 5-point star
        fill(255,255,50,200);
        beginShape();
        for(int i=0;i<10;i++){
            float a=TWO_PI*i/10+t; float r=(i%2==0)?100:45;
            vertex(350+cos(a)*r, 150+sin(a)*r);
        }
        endShape(CLOSE);

        // 7-point star
        fill(100,200,255,200);
        beginShape();
        for(int i=0;i<14;i++){
            float a=TWO_PI*i/14+t; float r=(i%2==0)?110:50;
            vertex(580+cos(a)*r, 150+sin(a)*r);
        }
        endShape(CLOSE);

        // 12-point star
        fill(200,100,255,200);
        beginShape();
        for(int i=0;i<24;i++){
            float a=TWO_PI*i/24+t; float r=(i%2==0)?100:60;
            vertex(800+cos(a)*r, 150+sin(a)*r);
        }
        endShape(CLOSE);

        // concave star with very deep indentation
        fill(50,255,150,200);
        beginShape();
        for(int i=0;i<10;i++){
            float a=TWO_PI*i/10+t; float r=(i%2==0)?130:15;
            vertex(150+cos(a)*r, 400+sin(a)*r);
        }
        endShape(CLOSE);

        // asymmetric polygon
        fill(255,150,50,200);
        beginShape();
        vertex(350+cos(t)*20, 300);
        vertex(450, 320+sin(t)*15);
        vertex(480, 430);
        vertex(420, 500);
        vertex(300, 480);
        vertex(260, 380);
        vertex(300, 320);
        endShape(CLOSE);

        // star with noise-perturbed points
        fill(100,200,255,200);
        beginShape();
        for(int i=0;i<16;i++){
            float a=TWO_PI*i/16+t;
            float r=(i%2==0)?120:55;
            float n=noise(i*0.5f,t)*20-10;
            vertex(620+cos(a)*(r+n), 400+sin(a)*(r+n));
        }
        endShape(CLOSE);

        // overlapping star + circle compound shape
        fill(255,80,80,150);
        beginShape();
        for(int i=0;i<20;i++){
            float a=TWO_PI*i/20+t;
            float r=80+sin(a*3+t)*40;
            vertex(830+cos(a)*r, 400+sin(a)*r);
        }
        endShape(CLOSE);

        // no-fill star outline only
        noFill(); stroke(255,200,0); strokeWeight(2);
        beginShape();
        for(int i=0;i<14;i++){
            float a=TWO_PI*i/14-t; float r=(i%2==0)?100:40;
            vertex(150+cos(a)*r, 600+sin(a)*r);
        }
        endShape(CLOSE);

        // star inside PGraphics
        PGraphics* pg = createGraphics(200,200);
        pg->beginDraw();
        pg->background(30);
        pg->fill(255,200,50,200);
        pg->stroke(255);
        pg->strokeWeight(1.5f);
        pg->beginShape();
        for(int i=0;i<10;i++){
            float a=TWO_PI*i/10+t; float r=(i%2==0)?80:35;
            pg->vertex(100+cos(a)*r, 100+sin(a)*r);
        }
        pg->endShape(CLOSE);
        pg->endDraw();
        image(pg, 350, 480);
        delete pg;

        fill(255); textSize(12);
        text("fps:"+str((int)_frameRate), 8, height-8);
    }
};

} // namespace Processing

int main() {
    Processing::TestSketch sketch;
    sketch.run();
    return 0;
}
