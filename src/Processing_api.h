// Processing_api.h
// Included in Sketch_run.cpp to make Processing API available to user-defined
// classes inside sketches (which don't inherit from PApplet).
#pragma once
#include "Processing.h"
#include <string>
// Suppress stdlib random() which conflicts with Processing random(float)
#ifdef random
#  undef random
#endif

// ── Math (static, no instance needed) ────────────────────────────────────────
inline float sq(float x)                   { return PApplet::sq(x); }
inline float deltaTime() { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->deltaTime : 0.016f; }

inline float lerp(float a,float b,float t) { return PApplet::lerp(a,b,t); }
inline bool* getKeysDown()  { return PApplet::g_papplet ? PApplet::g_papplet->keysDown  : nullptr; }
inline bool isKeyDown(int keyCode) {
    if(!PApplet::g_papplet) return false;
    if(keyCode<0||keyCode>=256) return false;
    return PApplet::g_papplet->keysDown[keyCode];
}
inline bool* getMouseDown() { return PApplet::g_papplet ? PApplet::g_papplet->mouseDown : nullptr; }
inline float map(float v,float i0,float i1,float o0,float o1) { return PApplet::map(v,i0,i1,o0,o1); }
inline float constrain(float v,float lo,float hi) { return PApplet::constrain(v,lo,hi); }
inline float max(float a,float b)         { return PApplet::max(a,b); }
inline float min(float a,float b)         { return PApplet::min(a,b); }
inline float max(float a,float b,float c) { return PApplet::max(a,b,c); }
inline float min(float a,float b,float c) { return PApplet::min(a,b,c); }
inline float dist(float x1,float y1,float x2,float y2) { return PApplet::dist(x1,y1,x2,y2); }
inline float dist(float x1,float y1,float z1,float x2,float y2,float z2) { return PApplet::dist(x1,y1,z1,x2,y2,z2); }
inline float mag(float x,float y)          { return PApplet::mag(x,y); }
inline float mag(float x,float y,float z)  { return PApplet::mag(x,y,z); }
inline float norm(float v,float lo,float hi){ return PApplet::norm(v,lo,hi); }

// ── Drawing forwarders ────────────────────────────────────────────────────────
inline void background(float g)                        { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->background(g); }
inline void background(float r,float g,float b)        { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->background(r,g,b); }
inline void background(float r,float g,float b,float a){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->background(r,g,b,a); }
inline void fill(float g)                              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->fill(g); }
inline void fill(float r,float g,float b)              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->fill(r,g,b); }
inline void fill(float r,float g,float b,float a)      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->fill(r,g,b,a); }
inline void noFill()                                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->noFill(); }
inline void stroke(float g)                            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->stroke(g); }
inline void stroke(float r,float g,float b)            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->stroke(r,g,b); }
inline void stroke(float r,float g,float b,float a)    { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->stroke(r,g,b,a); }
inline void noStroke()                                 { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->noStroke(); }
inline void strokeWeight(float w)                      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->strokeWeight(w); }
inline void point(float x,float y)                     { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->point(x,y); }
inline void line(float x1,float y1,float x2,float y2)  { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->line(x1,y1,x2,y2); }
inline void line(float x1,float y1,float z1,float x2,float y2,float z2){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->line(x1,y1,z1,x2,y2,z2); }
inline void rect(float x,float y,float w,float h)      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->rect(x,y,w,h); }
inline void ellipse(float x,float y,float w,float h)   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->ellipse(x,y,w,h); }
inline void circle(float x,float y,float d)            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->circle(x,y,d); }
inline void triangle(float x1,float y1,float x2,float y2,float x3,float y3){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->triangle(x1,y1,x2,y2,x3,y3); }
inline void arc(float x,float y,float w,float h,float s,float e){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->arc(x,y,w,h,s,e); }
inline void arc(float x,float y,float w,float h,float s,float e,int m){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->arc(x,y,w,h,s,e,m); }
inline void translate(float x,float y)                 { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->translate(x,y); }
inline void translate(float x,float y,float z)         { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->translate(x,y,z); }
inline void rotate(float a)                            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->rotate(a); }
inline void rotateX(float a)                           { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->rotateX(a); }
inline void rotateY(float a)                           { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->rotateY(a); }
inline void rotateZ(float a)                           { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->rotateZ(a); }
inline void scale(float s)                             { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->scale(s); }
inline void scale(float x,float y)                     { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->scale(x,y); }
inline void pushMatrix()                               { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->pushMatrix(); }
inline void popMatrix()                                { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->popMatrix(); }
inline void push()                                     { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->push(); }
inline void pop()                                      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->pop(); }
inline void pushStyle()                                { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->pushStyle(); }
inline void popStyle()                                 { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->popStyle(); }
inline void resetMatrix()                              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->resetMatrix(); }
inline void beginShape(int k=-1)                       { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->beginShape(k); }
inline void endShape(int m=0)                          { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->endShape(m); }
inline void vertex(float x,float y)                    { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->vertex(x,y); }
inline void vertex(float x,float y,float z)            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->vertex(x,y,z); }
inline void vertex(float x,float y,float u,float v)    { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->vertex(x,y,u,v); }
inline void bezierVertex(float cx1,float cy1,float cx2,float cy2,float x,float y){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->bezierVertex(cx1,cy1,cx2,cy2,x,y); }
inline void curveVertex(float x,float y)               { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->curveVertex(x,y); }
inline void tint(float g)                              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->tint(g); }
inline void tint(float r,float g,float b)              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->tint(r,g,b); }
inline void tint(float r,float g,float b,float a)      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->tint(r,g,b,a); }
inline void noTint()                                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->noTint(); }
inline void smooth()                                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->smooth(); }
inline void noSmooth()                                 { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->noSmooth(); }
inline void colorMode(int m,float mx=255.f)            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->colorMode(m,mx); }
inline void colorMode(int m,float h,float s,float b,float a=255.f){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->colorMode(m,h,s,b,a); }
inline void rectMode(int m)                            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->rectMode(m); }
inline void ellipseMode(int m)                         { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->ellipseMode(m); }
inline void imageMode(int m)                           { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->imageMode(m); }
inline void blendMode(int m)                           { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->blendMode(m); }
inline void clear()                                    { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->clear(); }
inline void noLoop()                                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->noLoop(); }
inline void loop()                                     { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->loop(); }
inline void redraw()                                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->redraw(); }
inline void frameRate(int fps)                         { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->frameRate(fps); }
inline void cursor()                                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->cursor(); }
inline void noCursor()                                 { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->noCursor(); }
inline void lights()                                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->lights(); }
inline void noLights()                                 { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->noLights(); }
inline void ambientLight(float r,float g,float b)      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->ambientLight(r,g,b); }
inline void directionalLight(float r,float g,float b,float nx,float ny,float nz){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->directionalLight(r,g,b,nx,ny,nz); }
inline void pointLight(float r,float g,float b,float x,float y,float z){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->pointLight(r,g,b,x,y,z); }
inline void normal(float nx,float ny,float nz)         { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->normal(nx,ny,nz); }
inline void box(float s)                               { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->box(s); }
inline void box(float w,float h,float d)               { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->box(w,h,d); }
inline void sphere(float r)                            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->sphere(r); }
inline void sphereDetail(int res)                      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->sphereDetail(res); }
inline void camera()                                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->camera(); }
inline void camera(float ex,float ey,float ez,float cx,float cy,float cz,float ux,float uy,float uz){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->camera(ex,ey,ez,cx,cy,cz,ux,uy,uz); }
inline void perspective()                              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->perspective(); }
inline void ortho()                                    { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->ortho(); }
inline void text(const ::std::string& s,float x,float y) { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->text(s,x,y); }
inline void text(int v,float x,float y)                { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->text(v,x,y); }
inline void text(float v,float x,float y)              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->text(v,x,y); }
inline void textSize(float s)                          { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->textSize(s); }
inline void textAlign(int a,int b=-1)                  { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->textAlign(a,b); }
inline void textLeading(float l)                       { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->textLeading(l); }
inline float textWidth(const ::std::string& s)           { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->textWidth(s) : 0; }
inline void loadPixels()                               { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->loadPixels(); }
inline void updatePixels()                             { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->updatePixels(); }
inline void save(const ::std::string& f="")              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->save(f); }
inline void saveFrame(const ::std::string& f="frame-####.png"){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->saveFrame(f); }
inline PImage* loadImage(const ::std::string& p)         { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->loadImage(p) : nullptr; }
inline PImage* createImage(int w,int h,int m=1)        { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->createImage(w,h,m) : nullptr; }
inline PGraphics* createGraphics(int w,int h)          { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->createGraphics(w,h) : nullptr; }
inline void image(PImage* img,float x,float y)         { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->image(img,x,y); }
inline void image(PImage* img,float x,float y,float w,float h){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->image(img,x,y,w,h); }
inline void filter(int m)                              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->filter(m); }
inline float random(float hi)                          { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->random(hi) : 0; }
inline float random(float lo,float hi)                 { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->random(lo,hi) : 0; }
inline float noise(float x)                            { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->noise(x) : 0; }
inline float noise(float x,float y)                    { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->noise(x,y) : 0; }
inline float noise(float x,float y,float z)            { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->noise(x,y,z) : 0; }
inline void noiseSeed(int s)                           { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->noiseSeed(s); }
inline void noiseDetail(int o,float f=0.5f)            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->noiseDetail(o,f); }
inline color makeColor(float a,float b,float c,float d=255){ return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->makeColor(a,b,c,d) : colorVal(0,0,0,255); }
inline void stroke(float g,float a)                    { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->stroke(g,a); }
inline void fill(float g,float a)                      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->fill(g,a); }
inline void tint(float g,float a)                      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->tint(g,a); }
inline void background(color c)                        { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->background(c); }
inline void fill(color c)                              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->fill(c); }
inline void stroke(color c)                            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->stroke(c); }
inline void rect(float x,float y,float w,float h,float r){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->rect(x,y,w,h,r); }
inline void square(float x,float y,float s)            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->square(x,y,s); }
inline void bezier(float x1,float y1,float cx1,float cy1,float cx2,float cy2,float x2,float y2){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->bezier(x1,y1,cx1,cy1,cx2,cy2,x2,y2); }
inline void curve(float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->curve(x0,y0,x1,y1,x2,y2,x3,y3); }
inline void shearX(float a)                            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->shearX(a); }
inline void shearY(float a)                            { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->shearY(a); }
inline void ambient(float r,float g,float b)           { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->ambient(r,g,b); }
inline void specular(float r,float g,float b)          { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->specular(r,g,b); }
inline void emissive(float r,float g,float b)          { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->emissive(r,g,b); }
inline void shininess(float s)                         { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->shininess(s); }
inline void spotLight(float r,float g,float b,float x,float y,float z,float nx,float ny,float nz,float angle,float conc){ if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->spotLight(r,g,b,x,y,z,nx,ny,nz,angle,conc); }
inline void lightFalloff(float c,float l,float q)      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->lightFalloff(c,l,q); }
inline void lightSpecular(float r,float g,float b)     { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->lightSpecular(r,g,b); }
inline void texture(PImage& img)                       { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->texture(img); }
inline void textureMode(int m)                         { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->textureMode(m); }
inline void textureWrap(int m)                         { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->textureWrap(m); }
inline void clip(float x,float y,float w,float h)      { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->clip(x,y,w,h); }
inline void noClip()                                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->noClip(); }
inline void windowTitle(const ::std::string& t)          { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->windowTitle(t); }
inline PFont loadFont(const ::std::string& f)            { static PFont d; return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->loadFont(f) : d; }
inline void textFont(const PFont& f)                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->textFont(f); }
inline void textFont(const PFont& f,float s)           { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->textFont(f,s); }
inline void set(int x,int y,color c)                   { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->set(x,y,c); }
inline color get(int x,int y)                          { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->get(x,y) : color(); }
inline void exit_sketch()                              { if(::Processing::PApplet::g_papplet) ::Processing::PApplet::g_papplet->exit_sketch(); }

// These let user-defined classes use width/height/mouseX etc. as values.
// Sketch methods use inherited PApplet members directly (no macro expansion).

// Variable macros — defined here (after _PSketch) so user class bodies
// can use width/height/mouseX/mouseY as if they were variables.
// These are NOT active in Processing.cpp or Processing.h.

// IMPORTANT: These macros conflict with struct member access like img->width.
// Use parentheses to disambiguate: img->height works fine since -> suppresses
// macro expansion for the RIGHT side of ->
// Actually in C, "img->height" — the "height" token after -> IS macro-expanded.
// Workaround: use (img)->height or cast. Or just don't define these here.
// 
// For now we define them since most sketches need width/height as globals.
// PImage members can be accessed via the struct directly: (*img).height
// or by temporarily undefining: #undef height ... 
// ── Environment variable accessors ───────────────────────────────────────────
// These let user-defined classes (not inheriting _PSketch) access Processing
// environment variables. Inside _PSketch, member variables shadow these.
// Outside (hoisted classes), these resolve via g_papplet singleton.
inline int   width()        { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->logicalW      : 0;    }
inline int   height()       { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->logicalH      : 0;    }
inline float mouseX()       { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseX        : 0.f;  }
inline float mouseY()       { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseY        : 0.f;  }
inline float pmouseX()      { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->pmouseX       : 0.f;  }
inline float pmouseY()      { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->pmouseY       : 0.f;  }
inline int   frameCount()   { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->frameCount    : 0;    }
inline char  key()          { return ::Processing::PApplet::g_papplet ? (char)::Processing::PApplet::g_papplet->key     : 0;    }
inline int   keyCode()      { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->keyCode       : 0;    }
inline int   mouseButton()  { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseButton   : 0;    }
inline bool  mousePressed() { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->_mousePressed : false; }
inline bool  keyPressed()   { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->_keyPressed   : false; }
inline float frameRate()    { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->_frameRate    : 0.f;  }
inline float mouseDX()      { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseDX       : 0.f;  }
inline float mouseDY()      { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseDY       : 0.f;  }
