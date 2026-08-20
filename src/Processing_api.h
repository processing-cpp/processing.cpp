// Processing_api.h
// Included in user-defined classes that don't inherit from PApplet.
// Provides sketch state accessors and drawing forwarders via g_papplet.
#pragma once
#include "Processing.h"
#include <string>

using namespace ::Processing;

// ── Sketch state accessors ────────────────────────────────────────────────────
inline int   width()        { return PApplet::g_papplet ? PApplet::g_papplet->logicalW      : 0;    }
inline int   height()       { return PApplet::g_papplet ? PApplet::g_papplet->logicalH      : 0;    }
inline float mouseX()       { return PApplet::g_papplet ? PApplet::g_papplet->mouseX        : 0.f;  }
inline float mouseY()       { return PApplet::g_papplet ? PApplet::g_papplet->mouseY        : 0.f;  }
inline float pmouseX()      { return PApplet::g_papplet ? PApplet::g_papplet->pmouseX       : 0.f;  }
inline float pmouseY()      { return PApplet::g_papplet ? PApplet::g_papplet->pmouseY       : 0.f;  }
inline int   frameCount()   { return PApplet::g_papplet ? PApplet::g_papplet->frameCount    : 0;    }
inline char  key()          { return PApplet::g_papplet ? (char)PApplet::g_papplet->key     : 0;    }
inline int   keyCode()      { return PApplet::g_papplet ? PApplet::g_papplet->keyCode       : 0;    }
inline int   mouseButton()  { return PApplet::g_papplet ? PApplet::g_papplet->mouseButton   : 0;    }
inline bool  mousePressed() { return PApplet::g_papplet ? PApplet::g_papplet->_mousePressed : false; }
inline bool  keyPressed()   { return PApplet::g_papplet ? PApplet::g_papplet->_keyPressed   : false; }
inline float frameRate()    { return PApplet::g_papplet ? PApplet::g_papplet->_frameRate    : 0.f;  }
inline float mouseDX()      { return PApplet::g_papplet ? PApplet::g_papplet->mouseDX       : 0.f;  }
inline float mouseDY()      { return PApplet::g_papplet ? PApplet::g_papplet->mouseDY       : 0.f;  }
inline bool* getKeysDown()  { return PApplet::g_papplet ? PApplet::g_papplet->keysDown  : nullptr; }
inline bool* getMouseDown() { return PApplet::g_papplet ? PApplet::g_papplet->mouseDown : nullptr; }
inline bool  isKeyDown(int k) {
    if(!PApplet::g_papplet||k<0||k>=256) return false;
    return PApplet::g_papplet->keysDown[k];
}

// ── Drawing forwarders ────────────────────────────────────────────────────────
// These are needed because Processing.h free functions are in namespace
// Processing, but hoisted classes need unqualified access.
// Note: random() is NOT forwarded here to avoid shadowing stdlib random().
//       Use ::Processing::random() or PApplet::g_papplet->random() directly.
inline void pushMatrix()    { if(PApplet::g_papplet) PApplet::g_papplet->pushMatrix(); }
inline void popMatrix()     { if(PApplet::g_papplet) PApplet::g_papplet->popMatrix(); }
inline void push()          { if(PApplet::g_papplet) PApplet::g_papplet->push(); }
inline void pop()           { if(PApplet::g_papplet) PApplet::g_papplet->pop(); }
inline void pushStyle()     { if(PApplet::g_papplet) PApplet::g_papplet->pushStyle(); }
inline void popStyle()      { if(PApplet::g_papplet) PApplet::g_papplet->popStyle(); }
inline void beginShape(int k=-1){ if(PApplet::g_papplet) PApplet::g_papplet->beginShape(k); }
inline void endShape(int m=0)   { if(PApplet::g_papplet) PApplet::g_papplet->endShape(m); }
inline void vertex(float x,float y)              { if(PApplet::g_papplet) PApplet::g_papplet->vertex(x,y); }
inline void vertex(float x,float y,float z)      { if(PApplet::g_papplet) PApplet::g_papplet->vertex(x,y,z); }
inline void translate(float x,float y)           { if(PApplet::g_papplet) PApplet::g_papplet->translate(x,y); }
inline void translate(float x,float y,float z)   { if(PApplet::g_papplet) PApplet::g_papplet->translate(x,y,z); }
inline void rotate(float a)                      { if(PApplet::g_papplet) PApplet::g_papplet->rotate(a); }
inline void rotateX(float a)                     { if(PApplet::g_papplet) PApplet::g_papplet->rotateX(a); }
inline void rotateY(float a)                     { if(PApplet::g_papplet) PApplet::g_papplet->rotateY(a); }
inline void rotateZ(float a)                     { if(PApplet::g_papplet) PApplet::g_papplet->rotateZ(a); }
inline void scale(float s)                       { if(PApplet::g_papplet) PApplet::g_papplet->scale(s); }
inline void scale(float x,float y)               { if(PApplet::g_papplet) PApplet::g_papplet->scale(x,y); }
inline void fill(float g)                        { if(PApplet::g_papplet) PApplet::g_papplet->fill(g); }
inline void fill(float r,float g,float b)        { if(PApplet::g_papplet) PApplet::g_papplet->fill(r,g,b); }
inline void fill(float r,float g,float b,float a){ if(PApplet::g_papplet) PApplet::g_papplet->fill(r,g,b,a); }
inline void fill(float g,float a)                { if(PApplet::g_papplet) PApplet::g_papplet->fill(g,a); }
inline void fill(color c)                        { if(PApplet::g_papplet) PApplet::g_papplet->fill(c); }
inline void noFill()                             { if(PApplet::g_papplet) PApplet::g_papplet->noFill(); }
inline void stroke(float g)                      { if(PApplet::g_papplet) PApplet::g_papplet->stroke(g); }
inline void stroke(float r,float g,float b)      { if(PApplet::g_papplet) PApplet::g_papplet->stroke(r,g,b); }
inline void stroke(float r,float g,float b,float a){ if(PApplet::g_papplet) PApplet::g_papplet->stroke(r,g,b,a); }
inline void stroke(float g,float a)              { if(PApplet::g_papplet) PApplet::g_papplet->stroke(g,a); }
inline void stroke(color c)                      { if(PApplet::g_papplet) PApplet::g_papplet->stroke(c); }
inline void noStroke()                           { if(PApplet::g_papplet) PApplet::g_papplet->noStroke(); }
inline void strokeWeight(float w)                { if(PApplet::g_papplet) PApplet::g_papplet->strokeWeight(w); }
inline void background(float g)                  { if(PApplet::g_papplet) PApplet::g_papplet->background(g); }
inline void background(float r,float g,float b)  { if(PApplet::g_papplet) PApplet::g_papplet->background(r,g,b); }
inline void background(float r,float g,float b,float a){ if(PApplet::g_papplet) PApplet::g_papplet->background(r,g,b,a); }
inline void background(color c)                  { if(PApplet::g_papplet) PApplet::g_papplet->background(c); }
inline void point(float x,float y)               { if(PApplet::g_papplet) PApplet::g_papplet->point(x,y); }
inline void line(float x1,float y1,float x2,float y2){ if(PApplet::g_papplet) PApplet::g_papplet->line(x1,y1,x2,y2); }
inline void line(float x1,float y1,float z1,float x2,float y2,float z2){ if(PApplet::g_papplet) PApplet::g_papplet->line(x1,y1,z1,x2,y2,z2); }
inline void rect(float x,float y,float w,float h){ if(PApplet::g_papplet) PApplet::g_papplet->rect(x,y,w,h); }
inline void rect(float x,float y,float w,float h,float r){ if(PApplet::g_papplet) PApplet::g_papplet->rect(x,y,w,h,r); }
inline void ellipse(float x,float y,float w,float h){ if(PApplet::g_papplet) PApplet::g_papplet->ellipse(x,y,w,h); }
inline void circle(float x,float y,float d)      { if(PApplet::g_papplet) PApplet::g_papplet->circle(x,y,d); }
inline void triangle(float x1,float y1,float x2,float y2,float x3,float y3){ if(PApplet::g_papplet) PApplet::g_papplet->triangle(x1,y1,x2,y2,x3,y3); }
inline void arc(float x,float y,float w,float h,float s,float e){ if(PApplet::g_papplet) PApplet::g_papplet->arc(x,y,w,h,s,e); }
inline void arc(float x,float y,float w,float h,float s,float e,int m){ if(PApplet::g_papplet) PApplet::g_papplet->arc(x,y,w,h,s,e,m); }
inline void image(PImage* img,float x,float y)   { if(PApplet::g_papplet) PApplet::g_papplet->image(img,x,y); }
inline void image(PImage* img,float x,float y,float w,float h){ if(PApplet::g_papplet) PApplet::g_papplet->image(img,x,y,w,h); }
inline void text(const ::std::string& s,float x,float y){ if(PApplet::g_papplet) PApplet::g_papplet->text(s,x,y); }
inline void text(int v,float x,float y)          { if(PApplet::g_papplet) PApplet::g_papplet->text(v,x,y); }
inline void text(float v,float x,float y)        { if(PApplet::g_papplet) PApplet::g_papplet->text(v,x,y); }
inline void textSize(float s)                    { if(PApplet::g_papplet) PApplet::g_papplet->textSize(s); }
inline void textAlign(int a,int b=-1)            { if(PApplet::g_papplet) PApplet::g_papplet->textAlign(a,b); }
inline float textWidth(const ::std::string& s)   { return PApplet::g_papplet ? PApplet::g_papplet->textWidth(s) : 0; }
inline void tint(float r,float g,float b,float a){ if(PApplet::g_papplet) PApplet::g_papplet->tint(r,g,b,a); }
inline void noTint()                             { if(PApplet::g_papplet) PApplet::g_papplet->noTint(); }
inline void colorMode(int m,float mx=255.f)      { if(PApplet::g_papplet) PApplet::g_papplet->colorMode(m,mx); }
inline void blendMode(int m)                     { if(PApplet::g_papplet) PApplet::g_papplet->blendMode(m); }
inline void noLoop()                             { if(PApplet::g_papplet) PApplet::g_papplet->noLoop(); }
inline void loop()                               { if(PApplet::g_papplet) PApplet::g_papplet->loop(); }
inline void redraw()                             { if(PApplet::g_papplet) PApplet::g_papplet->redraw(); }
inline PImage* loadImage(const ::std::string& p) { return PApplet::g_papplet ? PApplet::g_papplet->loadImage(p) : nullptr; }
inline PImage* createImage(int w,int h,int m=1)  { return PApplet::g_papplet ? PApplet::g_papplet->createImage(w,h,m) : nullptr; }
inline float noise(float x)                      { return PApplet::g_papplet ? PApplet::g_papplet->noise(x) : 0; }
inline float noise(float x,float y)              { return PApplet::g_papplet ? PApplet::g_papplet->noise(x,y) : 0; }
inline float noise(float x,float y,float z)      { return PApplet::g_papplet ? PApplet::g_papplet->noise(x,y,z) : 0; }
inline float random(float hi)                    { return PApplet::g_papplet ? PApplet::g_papplet->random(hi) : 0; }
inline float random(float lo,float hi)           { return PApplet::g_papplet ? PApplet::g_papplet->random(lo,hi) : 0; }
inline void lights()                             { if(PApplet::g_papplet) PApplet::g_papplet->lights(); }
inline void noLights()                           { if(PApplet::g_papplet) PApplet::g_papplet->noLights(); }
inline void box(float s)                         { if(PApplet::g_papplet) PApplet::g_papplet->box(s); }
inline void box(float w,float h,float d)         { if(PApplet::g_papplet) PApplet::g_papplet->box(w,h,d); }
inline void sphere(float r)                      { if(PApplet::g_papplet) PApplet::g_papplet->sphere(r); }
inline void camera(float ex,float ey,float ez,float cx,float cy,float cz,float ux,float uy,float uz){ if(PApplet::g_papplet) PApplet::g_papplet->camera(ex,ey,ez,cx,cy,cz,ux,uy,uz); }
inline color lerpColor(color a,color b,float t)  { return PApplet::g_papplet ? PApplet::g_papplet->lerpColor(a,b,t) : a; }
inline bool isKeyDown(int k) {
    if(!PApplet::g_papplet||k<0||k>=256) return false;
    return PApplet::g_papplet->keysDown[k];
}
