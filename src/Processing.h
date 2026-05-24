#pragma once
#ifndef _WIN32
#include <dirent.h>
#endif
#include <functional>
// =============================================================================
// Processing.h  --  processing-cpp API
// =============================================================================
// processing-cpp is a C++ creative coding framework inspired by Processing (Java).
// It exposes a familiar draw-loop API backed by OpenGL/GLFW/GLEW.
//
// HOW TO USE:
//   1. Include this header in your sketch file.
//   2. Inside  namespace Processing { ... }  define:
//        void setup() { size(640,360); }
//        void draw()  { background(0); ellipse(mouseX,mouseY,40,40); }
//   3. Compile with Processing.cpp and link against GLFW + GLEW + OpenGL.
//
// FILE STRUCTURE:
//   Processing.h    -- This file. API declarations, inline helpers, classes.
//   Processing.cpp  -- Implementation of all declared functions.
//   Platform.h      -- OS abstraction (file dialogs, serial, process, sleep).
//   IDE.cpp         -- The processing-cpp IDE (sketch editor, build, run, terminal).
// =============================================================================

// ---------------------------------------------------------------------------
// Platform shim (must come first; provides termios/glob stubs on Windows)
// ---------------------------------------------------------------------------
#if __has_include("Platform.h")
#  include "Platform.h"
#endif

// ---------------------------------------------------------------------------
// M_PI: Windows (MinGW) only defines this when _USE_MATH_DEFINES is set
// before including <cmath>.  We also provide a fallback just in case.
// ---------------------------------------------------------------------------
#ifndef _USE_MATH_DEFINES
#  define _USE_MATH_DEFINES
#endif
#include <cmath>
#ifndef M_PI
#  define M_PI 3.14159265358979323846
#endif

// ---------------------------------------------------------------------------
// Standard library includes
// ---------------------------------------------------------------------------
#include <iostream>
#include <sstream>
#include <fstream>
#include <regex>
#include <cstdlib>
#include <ctime>
#include <chrono>
#include <string>
#include <vector>
#include <thread>
#include <functional>
#include <algorithm>
#include <memory>
#include <map>
#include <set>
#include <random>
#include <stack>
#include <queue>
#include <list>
#include <deque>
#include <tuple>
#include <optional>
#include <variant>
#include <numeric>
#include <iterator>
#include <memory>
#include <regex>
#include <iomanip>
#include <unordered_map>
#include <unordered_set>
// ---------------------------------------------------------------------------
// OpenGL / GLFW
// ---------------------------------------------------------------------------
// Java-style string + number concatenation
inline std::string operator+(const std::string& s, int n)    { return s + std::to_string(n); }
inline std::string operator+(const std::string& s, long n)   { return s + std::to_string(n); }
inline std::string operator+(const std::string& s, size_t n) { return s + std::to_string(n); }
inline std::string operator+(const std::string& s, float n)  { return s + std::to_string(n); }
inline std::string operator+(const std::string& s, double n) { return s + std::to_string(n); }
inline std::string operator+(const std::string& s, char c)   { return s + std::string(1, c); }
inline std::string operator+(int n,    const std::string& s) { return std::to_string(n) + s; }
inline std::string operator+(long n,   const std::string& s) { return std::to_string(n) + s; }
inline std::string operator+(size_t n, const std::string& s) { return std::to_string(n) + s; }
inline std::string operator+(float n,  const std::string& s) { return std::to_string(n) + s; }
inline std::string operator+(double n, const std::string& s) { return std::to_string(n) + s; }
inline std::string operator+(char c,   const std::string& s) { return std::string(1, c) + s; }
#include <GL/glew.h>
#include <GLFW/glfw3.h>

namespace Processing {

// =============================================================================
// PVECTOR  --  2D/3D vector with all standard Processing operations
// =============================================================================

class PVector {
public:
    float x, y, z;

    // Constructors
    PVector()                        : x(0),  y(0),  z(0)  {}
    PVector(float x, float y)        : x(x),  y(y),  z(0)  {}
    PVector(float x, float y, float z): x(x), y(y),  z(z)  {}

    // Setters
    PVector& set(float _x, float _y, float _z=0) { x=_x; y=_y; z=_z; return *this; }
    PVector& set(const PVector& v)               { x=v.x; y=v.y; z=v.z; return *this; }
    PVector  copy() const { return PVector(x, y, z); }

    // Magnitude
    float mag()   const { return std::sqrt(x*x + y*y + z*z); }
    float magSq() const { return x*x + y*y + z*z; }

    // Arithmetic (in-place)
    PVector& add(float _x, float _y, float _z=0) { x+=_x; y+=_y; z+=_z; return *this; }
    PVector& add(const PVector& v)               { x+=v.x; y+=v.y; z+=v.z; return *this; }
    PVector& sub(float _x, float _y, float _z=0) { x-=_x; y-=_y; z-=_z; return *this; }
    PVector& sub(const PVector& v)               { x-=v.x; y-=v.y; z-=v.z; return *this; }
    PVector& mult(float s)  { x*=s; y*=s; z*=s; return *this; }
    PVector& div(float s)   { x/=s; y/=s; z/=s; return *this; }

    // Arithmetic (static, returns new vector)
    static PVector add(const PVector& a, const PVector& b)  { return PVector(a.x+b.x, a.y+b.y, a.z+b.z); }
    static PVector sub(const PVector& a, const PVector& b)  { return PVector(a.x-b.x, a.y-b.y, a.z-b.z); }
    static PVector mult(const PVector& v, float s)           { return PVector(v.x*s,   v.y*s,   v.z*s);   }
    static PVector div(const PVector& v, float s)            { return PVector(v.x/s,   v.y/s,   v.z/s);   }

    // Operators
    PVector  operator+(const PVector& v) const { return PVector(x+v.x, y+v.y, z+v.z); }
    PVector  operator-(const PVector& v) const { return PVector(x-v.x, y-v.y, z-v.z); }
    PVector  operator*(float s)          const { return PVector(x*s,   y*s,   z*s);   }
    PVector  operator/(float s)          const { return PVector(x/s,   y/s,   z/s);   }
    PVector& operator+=(const PVector& v) { return add(v);  }
    PVector& operator-=(const PVector& v) { return sub(v);  }
    PVector& operator*=(float s)          { return mult(s); }
    PVector& operator/=(float s)          { return div(s);  }
    bool operator==(const PVector& v) const { return x==v.x && y==v.y && z==v.z; }
    bool operator!=(const PVector& v) const { return !(*this==v); }

    // Dot / cross product
    float   dot(const PVector& v)                       const { return x*v.x + y*v.y + z*v.z; }
    float   dot(float _x, float _y, float _z=0)        const { return x*_x  + y*_y  + z*_z;  }
    static float    dot(const PVector& a, const PVector& b)   { return a.dot(b); }
    PVector cross(const PVector& v)                     const { return PVector(y*v.z-z*v.y, z*v.x-x*v.z, x*v.y-y*v.x); }
    static PVector  cross(const PVector& a, const PVector& b) { return a.cross(b); }

    // Normalization / limits
    PVector& normalize()  { float m=mag(); if(m>0) div(m); return *this; }
    PVector  normalized() const { PVector v(*this); return v.normalize(); }
    PVector& limit(float mx)  { if(magSq()>mx*mx){ normalize(); mult(mx); } return *this; }
    PVector& setMag(float m)  { normalize(); mult(m); return *this; }

    // Distance / angle
    float dist(const PVector& v)                     const { float dx=x-v.x,dy=y-v.y,dz=z-v.z; return std::sqrt(dx*dx+dy*dy+dz*dz); }
    static float dist(const PVector& a, const PVector& b) { return a.dist(b); }
    float heading() const { return std::atan2(y, x); }
    float angleBetween(const PVector& v) const {
        float m = mag() * v.mag();
        if (m == 0) return 0;
        float c = dot(v) / m;
        c = c < -1 ? -1 : (c > 1 ? 1 : c);
        return std::acos(c);
    }
    static float angleBetween(const PVector& a, const PVector& b) { return a.angleBetween(b); }

    // Mutation
    PVector& rotate(float t) { float c=std::cos(t),s=std::sin(t),nx=x*c-y*s,ny=x*s+y*c; x=nx; y=ny; return *this; }
    PVector& lerp(const PVector& v, float t) { x+=(v.x-x)*t; y+=(v.y-y)*t; z+=(v.z-z)*t; return *this; }
    PVector& lerp(float _x, float _y, float _z, float t) { x+=(_x-x)*t; y+=(_y-y)*t; z+=(_z-z)*t; return *this; }
    static PVector lerp(const PVector& a, const PVector& b, float t) {
        return PVector(a.x+(b.x-a.x)*t, a.y+(b.y-a.y)*t, a.z+(b.z-a.z)*t);
    }

    // Static constructors
    static PVector fromAngle(float a, float len=1.0f) { return PVector(std::cos(a)*len, std::sin(a)*len); }
    static PVector random2D() {
        float a = static_cast<float>(rand()) / RAND_MAX * 6.28318f;
        return fromAngle(a);
    }
    static PVector random3D() {
        float t = static_cast<float>(rand()) / RAND_MAX * 6.28318f;
        float p = std::acos(2.0f * static_cast<float>(rand()) / RAND_MAX - 1.0f);
        return PVector(std::sin(p)*std::cos(t), std::sin(p)*std::sin(t), std::cos(p));
    }

    std::string toString() const {
        std::ostringstream ss;
        ss << "[ " << x << ", " << y << ", " << z << " ]";
        return ss.str();
    }
};

// =============================================================================
// PCOLOR  --  RGBA color with HSB conversion and blend operations
// =============================================================================

class PColor {
public:
    float r, g, b, a;

    PColor()                             : r(0),   g(0),   b(0),   a(255) {}
    PColor(float gray)                   : r(gray),g(gray),b(gray),a(255) {}
    PColor(float gray, float a)          : r(gray),g(gray),b(gray),a(a)   {}
    PColor(float r, float g, float b)    : r(r),   g(g),   b(b),   a(255) {}
    PColor(float r, float g, float b, float a) : r(r), g(g), b(b), a(a)  {}

    // Construct from packed ARGB integer (0xAARRGGBB)
    explicit PColor(unsigned int argb)
        : r((argb>>16)&0xFF), g((argb>>8)&0xFF), b(argb&0xFF), a((argb>>24)&0xFF) {}

    // Pack to ARGB integer
    unsigned int toARGB() const {
        int ri=(int)std::fmax(0,std::fmin(255,r));
        int gi=(int)std::fmax(0,std::fmin(255,g));
        int bi=(int)std::fmax(0,std::fmin(255,b));
        int ai=(int)std::fmax(0,std::fmin(255,a));
        return (unsigned int)((ai<<24)|(ri<<16)|(gi<<8)|bi);
    }

    // Normalised [0..1] accessors
    float rf() const { return r/255.0f; }
    float gf() const { return g/255.0f; }
    float bf() const { return b/255.0f; }
    float af() const { return a/255.0f; }

    PColor& set(float _r, float _g, float _b, float _a=255) { r=_r; g=_g; b=_b; a=_a; return *this; }
    PColor& set(float gray, float _a=255)                   { r=g=b=gray; a=_a; return *this; }
    PColor  copy() const { return PColor(r, g, b, a); }

    // HSB conversions
    float hue() const {
        float rf_=r/255.f, gf_=g/255.f, bf_=b/255.f;
        float mx=std::fmax(rf_,std::fmax(gf_,bf_));
        float mn=std::fmin(rf_,std::fmin(gf_,bf_));
        float d=mx-mn;
        if (d==0) return 0;
        float h = (mx==rf_) ? (gf_-bf_)/d : (mx==gf_) ? 2.f+(bf_-rf_)/d : 4.f+(rf_-gf_)/d;
        h *= 60.f;
        if (h < 0) h += 360.f;
        return h;
    }
    float saturation() const {
        float mx=std::fmax(r,std::fmax(g,b));
        float mn=std::fmin(r,std::fmin(g,b));
        return mx==0 ? 0 : ((mx-mn)/mx)*100.f;
    }
    float brightness() const { return std::fmax(r,std::fmax(g,b))/255.f*100.f; }

    static PColor fromHSB(float h, float s, float bv, float a=255) {
        s /= 100.f; bv /= 100.f;
        if (s == 0) { float v=bv*255.f; return PColor(v,v,v,a); }
        float hh=std::fmod(h,360.f)/60.f;
        int   i=(int)hh;
        float f=hh-i, p=bv*(1-s), q=bv*(1-s*f), t=bv*(1-s*(1-f));
        float rv,gv,blv;
        switch(i){
            case 0: rv=bv;gv=t; blv=p;  break;
            case 1: rv=q; gv=bv;blv=p;  break;
            case 2: rv=p; gv=bv;blv=t;  break;
            case 3: rv=p; gv=q; blv=bv; break;
            case 4: rv=t; gv=p; blv=bv; break;
            default:rv=bv;gv=p; blv=q;  break;
        }
        return PColor(rv*255,gv*255,blv*255,a);
    }

    // Arithmetic operators
    PColor  operator+(const PColor& o) const { return PColor(r+o.r, g+o.g, b+o.b, a+o.a); }
    PColor  operator-(const PColor& o) const { return PColor(r-o.r, g-o.g, b-o.b, a-o.a); }
    PColor  operator*(float s)         const { return PColor(r*s,   g*s,   b*s,   a*s);   }
    PColor  operator/(float s)         const { return PColor(r/s,   g/s,   b/s,   a/s);   }
    PColor& operator+=(const PColor& o) { r+=o.r; g+=o.g; b+=o.b; a+=o.a; return *this; }
    PColor& operator-=(const PColor& o) { r-=o.r; g-=o.g; b-=o.b; a-=o.a; return *this; }
    PColor& operator*=(float s)         { r*=s;   g*=s;   b*=s;   a*=s;   return *this; }
    PColor& operator/=(float s)         { r/=s;   g/=s;   b/=s;   a/=s;   return *this; }
    bool operator==(const PColor& o) const { return r==o.r && g==o.g && b==o.b && a==o.a; }
    bool operator!=(const PColor& o) const { return !(*this==o); }

    // Utility
    static PColor lerp(const PColor& c1, const PColor& c2, float t) {
        return PColor(c1.r+(c2.r-c1.r)*t, c1.g+(c2.g-c1.g)*t, c1.b+(c2.b-c1.b)*t, c1.a+(c2.a-c1.a)*t);
    }
    PColor& clamp() {
        r=std::fmax(0,std::fmin(255,r)); g=std::fmax(0,std::fmin(255,g));
        b=std::fmax(0,std::fmin(255,b)); a=std::fmax(0,std::fmin(255,a));
        return *this;
    }
    PColor multRGB(float s) const { return PColor(r*s, g*s, b*s, a); }

    // Blend modes (return new color)
    static PColor blend(const PColor& src, const PColor& dst) {
        float sa = src.a/255.f;
        return PColor(src.r*sa+dst.r*(1-sa), src.g*sa+dst.g*(1-sa), src.b*sa+dst.b*(1-sa), 255);
    }
    static PColor add(const PColor& a, const PColor& b) {
        return PColor(std::fmin(255,a.r+b.r), std::fmin(255,a.g+b.g), std::fmin(255,a.b+b.b), a.a);
    }
    static PColor multiply(const PColor& a, const PColor& b) {
        return PColor((a.r/255.f)*b.r, (a.g/255.f)*b.g, (a.b/255.f)*b.b, a.a);
    }
    static PColor screen(const PColor& a, const PColor& b) {
        auto sc=[](float x,float y){ return 255-(255-x)*(255-y)/255.f; };
        return PColor(sc(a.r,b.r), sc(a.g,b.g), sc(a.b,b.b), a.a);
    }

    float brightness255() const { return std::fmax(r, std::fmax(g, b)); }

    std::string toString() const {
        std::ostringstream ss;
        ss << "PColor(" << r << ", " << g << ", " << b << ", " << a << ")";
        return ss.str();
    }
};

// Forward declarations so PColor overloads compile below class definitions
void fill(const PColor& c);
void stroke(const PColor& c);
void background(const PColor& c);
void tint(const PColor& c);

// =============================================================================
// IMAGE FILTER CONSTANTS
// =============================================================================

static constexpr int GRAY        = 1;
static constexpr int INVERT      = 2;
static constexpr int THRESHOLD   = 3;
static constexpr int BLUR_FILTER = 4;
static constexpr int POSTERIZE   = 5;

// =============================================================================
// PIMAGE  --  Pixel buffer backed by an OpenGL texture
// =============================================================================

class PImage {
public:
    int  width  = 0;
    int  height = 0;
    std::vector<unsigned int> pixels;
    GLuint texID = 0;
    bool   dirty = false;

    PImage() = default;
    PImage(int w, int h) {
        // Guard against bad dimensions from corrupted files or failed loads
        if (w > 0 && h > 0 && w < 16384 && h < 16384) {
            width = w; height = h;
            pixels.assign((size_t)w * h, 0xFF000000);
        }
    }

    // Pixel read/write (bounds-checked)
    unsigned int get(int x, int y) const {
        if (x<0||x>=width||y<0||y>=height) return 0;
        return pixels[y*width+x];
    }
    void set(int x, int y, unsigned int c) {
        if (x<0||x>=width||y<0||y>=height) return;
        pixels[y*width+x] = c;
        dirty = true;
    }

    // These mirror the Processing Java API; dirty flag is used by updatePixels()
    void loadPixels()   {}
    void updatePixels() { dirty = true; }

    // Upload CPU pixels to the GPU texture
    void uploadTexture(); // defined in Processing.cpp

    void resize(int w, int h) { width=w; height=h; pixels.assign(w*h, 0xFF000000); dirty=true; }

    // Apply an image filter to all pixels
    void filter(int mode) {
        for (auto& p : pixels) {
            int r=(p>>16)&0xFF, g=(p>>8)&0xFF, b=p&0xFF, a=(p>>24)&0xFF;
            if      (mode == GRAY)      { int gr=(r+g+b)/3; p=(a<<24)|(gr<<16)|(gr<<8)|gr; }
            else if (mode == INVERT)    { p=(a<<24)|((255-r)<<16)|((255-g)<<8)|(255-b); }
            else if (mode == THRESHOLD) { int gr=(r+g+b)/3; int t=gr>127?255:0; p=(a<<24)|(t<<16)|(t<<8)|t; }
        }
        dirty = true;
    }

    // Extract a sub-image
    PImage get(int x, int y, int w, int h) const {
        PImage out(w, h);
        for (int iy=0; iy<h; iy++)
            for (int ix=0; ix<w; ix++)
                out.pixels[iy*w+ix] = get(x+ix, y+iy);
        return out;
    }

    // Copy from another image with scaling
    void copy(const PImage& src, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh) {
        for (int iy=0; iy<dh; iy++)
            for (int ix=0; ix<dw; ix++) {
                int srcX = sx+(int)(ix*(float)sw/dw);
                int srcY = sy+(int)(iy*(float)sh/dh);
                set(dx+ix, dy+iy, src.get(srcX, srcY));
            }
        dirty = true;
    }

    // Apply alpha mask from another grayscale image
    void mask(const PImage& m) {
        for (int i=0; i<width*height && i<(int)m.pixels.size(); i++) {
            int a = (m.pixels[i]>>16)&0xFF;
            pixels[i] = (pixels[i]&0x00FFFFFF)|(a<<24);
        }
        dirty = true;
    }
    void mask(const PImage* m) { if (m) mask(*m); }

    // Destructor frees GPU texture
    virtual ~PImage() { if (texID) glDeleteTextures(1, &texID); }

    // Non-copyable (owns GPU resource -- use PImage* for assignment)
    PImage(const PImage&)            = delete;
    PImage& operator=(const PImage&) = delete;

    // Movable
    PImage(PImage&& o) noexcept
        : width(o.width), height(o.height), pixels(std::move(o.pixels)),
          texID(o.texID), dirty(o.dirty) { o.texID=0; }
};

// =============================================================================
// PGRAPHICS  --  Off-screen render target (framebuffer object)
// =============================================================================

class PGraphics : public PImage {
public:
    GLuint fbo = 0; // framebuffer object
    GLuint rbo = 0; // renderbuffer (depth+stencil)
    bool   active = false;

    PGraphics() = default;

    PGraphics(int w, int h) : PImage(w, h) {
        // Create framebuffer
        glGenFramebuffers(1, &fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        // Colour attachment texture
        if (texID == 0) glGenTextures(1, &texID);
        glBindTexture(GL_TEXTURE_2D, texID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texID, 0);

        // Depth+stencil renderbuffer
        glGenRenderbuffers(1, &rbo);
        glBindRenderbuffer(GL_RENDERBUFFER, rbo);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rbo);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    GLint savedViewport[4] = {};
    void beginDraw() {
        glGetIntegerv(GL_VIEWPORT, savedViewport);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, width, height);
        // Y-up (natural OpenGL/FBO) - content stored right-side-up in texture
        glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity();
        // FBO stores Y flipped vs screen - compensate so Processing Y-down coords work
        glScalef(1.0f, -1.0f, 1.0f);
        glTranslatef(0.0f, -(float)height, 0.0f);
        active = true;
    }
    void endDraw() {
        glMatrixMode(GL_PROJECTION); glPopMatrix();
        glMatrixMode(GL_MODELVIEW);  glPopMatrix();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        active = false;
        // Restore saved main canvas viewport and projection
        extern int logicalW, logicalH;
        glViewport(savedViewport[0],savedViewport[1],savedViewport[2],savedViewport[3]);
        glMatrixMode(GL_PROJECTION);glLoadIdentity();
        glOrtho(0,logicalW,logicalH,0,-1,1);
        glMatrixMode(GL_MODELVIEW);glLoadIdentity();
    }

    // Drawing methods forwarded to Processing -- implemented after full decls
    void background(float g); void background(float r, float g, float b); void background(float r, float g, float b, float a);
    void fill(float g); void fill(float r, float g, float b); void fill(float r, float g, float b, float a);
    void noFill(); void stroke(float g); void stroke(float r, float g, float b); void noStroke();
    void strokeWeight(float w);
    void ellipse(float x, float y, float w, float h);
    void rect(float x, float y, float w, float h);
    void line(float x1, float y1, float x2, float y2);
    void point(float x, float y);
    void triangle(float x1,float y1,float x2,float y2,float x3,float y3);
    void text(const std::string& s, float x, float y);
    void translate(float x, float y); void rotate(float a); void scale(float s);
    void pushMatrix(); void popMatrix();
    void beginShape(); void endShape(int mode=0); void vertex(float x, float y);
    void clear();

    ~PGraphics() {
        if (fbo) glDeleteFramebuffers(1,  &fbo);
        if (rbo) glDeleteRenderbuffers(1, &rbo);
    }

    PGraphics(const PGraphics&)            = delete;
    PGraphics& operator=(const PGraphics&) = delete;
    // Allow assignment from pointer (PGraphics pg; pg = createGraphics(w,h))
    PGraphics& operator=(PGraphics* p) {
        if(p && p!=this){
            // Transfer ownership
            if(fbo) glDeleteFramebuffers(1,&fbo);
            if(rbo) glDeleteRenderbuffers(1,&rbo);
            fbo=p->fbo; rbo=p->rbo; texID=p->texID;
            width=p->width; height=p->height;
            active=p->active;
            p->fbo=0; p->rbo=0; p->texID=0;
            delete p;
        }
        return *this;
    }
};

// =============================================================================
// ENVIRONMENT STATE  --  window / display dimensions
// =============================================================================

extern int  winWidth, winHeight;      // window client area in screen pixels
extern int  displayWidth, displayHeight; // full monitor resolution
extern int  pixelWidth, pixelHeight;  // framebuffer size (HiDPI may differ)
extern int  pixelDensityValue;
extern bool isResizable, focused;

// Aliases: 'width' and 'height' are the canonical Processing names
extern int logicalW, logicalH; // sketch's requested size from size()
// width/height always equal what size() set -- never corrupted by WM tile resize.
static inline int& width  = logicalW;
static inline int& height = logicalH;

// =============================================================================
// MOUSE STATE
// =============================================================================

extern float mouseX, mouseY;   // current mouse position (screen coords)
extern float pmouseX, pmouseY; // previous frame mouse position
extern bool  _mousePressed; // true while any mouse button is held
extern int   mouseButton;      // LEFT, RIGHT, or CENTER

// =============================================================================
// EVENT CALLBACKS
// =============================================================================
// Define any of these in your sketch; unimplemented ones are safely skipped.
//
// On Linux/macOS: declared __attribute__((weak)) so undefined ones link as nullptr.
// On Windows (MinGW): weak declarations don't work; instead, _wireCallbacksFn is
//   set at the bottom of IDE.cpp/sketch to point to a function that wires
//   all _on* function pointers.  See the Windows Event Wiring section of IDE.cpp.

// ---------------------------------------------------------------------------
// Processing event callbacks -- define whichever ones your sketch needs.
// Processing.cpp uses _on* function pointers; wireCallbacks() in
// Sketch_run.cpp assigns only the ones the sketch defines.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Internal event function pointers (set by run() via the callbacks above).
// Exposed here so IDE.cpp's wireCallbacks() can assign them.
// ---------------------------------------------------------------------------
#include <functional>
extern std::function<void()>    _onKeyPressed;
extern std::function<void()>    _onKeyReleased;
extern std::function<void()>    _onKeyTyped;
extern std::function<void()>    _onMousePressed;
extern std::function<void()>    _onMouseReleased;
extern std::function<void()>    _onMouseClicked;
extern std::function<void()>    _onMouseMoved;
extern std::function<void()>    _onMouseDragged;
extern std::function<void(int)> _onMouseWheel;
extern std::function<void()>    _onWindowMoved;
extern std::function<void()>    _onWindowResized;

// ---------------------------------------------------------------------------
// Windows-only: raw POD function pointer set by IDE.cpp during static init.
// POD is guaranteed zero-initialized before any constructor runs, so writing
// to it from a static initializer in another translation unit is always safe.
// Processing::run() calls it (if non-null) after setup() to wire all _on* ptrs.
// ---------------------------------------------------------------------------
extern void (*_wireCallbacksFn)(); // set before run() to wire event callbacks
extern void (*_staticSketchSetup)(); // set by static sketches for i3/tiling WM redraw

// =============================================================================
// KEYBOARD STATE
// =============================================================================

extern bool  _keyPressed;   // true while any key is held
extern int  keyCode; // special key code (UP, DOWN, LEFT, RIGHT, ALT, CONTROL, SHIFT, etc.)
extern char key;     // ASCII character, or CODED (0xFF) for special keys

// =============================================================================
// FRAME / LOOP STATE
// =============================================================================

extern int   frameCount;
extern float currentFrameRate;
extern bool  looping;
extern float measuredFrameRate;

// =============================================================================
// DRAWING STATE  --  internal; exposed so IDE.cpp / PGraphics can inspect
// =============================================================================

extern float fillR, fillG, fillB, fillA;
extern float strokeR, strokeG, strokeB, strokeA;
extern float strokeW;
extern bool  doFill, doStroke, smoothing;
extern int   currentRectMode, currentEllipseMode, currentImageMode;
extern float tintR, tintG, tintB, tintA;
extern bool  doTint;
extern int   colorModeVal;
extern float colorMaxH, colorMaxS, colorMaxB, colorMaxA;
extern std::vector<unsigned int> pixels;

// =============================================================================
// CONSTANTS
// =============================================================================

// Mouse buttons
// mouseButton is set to LEFT(37), RIGHT(39), or CENTER(3) when a button is pressed

// ---------------------------------------------------------------------------
// Processing reference constants
// Key codes match Java KeyEvent.VK_* values exactly.
// Mouse button constants match Processing's LEFT/CENTER/RIGHT.
// ---------------------------------------------------------------------------

// key == CODED when a non-ASCII special key is pressed; then check keyCode
static constexpr int CODED     = 0xFF;

// Coded keys (keyCode values, Java KeyEvent.VK_*)
static constexpr int UP        = 38;
static constexpr int DOWN      = 40;
static constexpr int LEFT      = 37;    // arrow key AND left mouse button
static constexpr int RIGHT     = 39;    // arrow key AND right mouse button
static constexpr int ALT       = 18;
static constexpr int CONTROL   = 17;
static constexpr int SHIFT     = 16;
static constexpr int HOME_KEY  = 36;
static constexpr int END_KEY   = 35;
static constexpr int PAGE_UP   = 33;
static constexpr int PAGE_DOWN = 34;
static constexpr int F1_KEY    = 112;  static constexpr int F2_KEY  = 113;
static constexpr int F3_KEY    = 114;  static constexpr int F4_KEY  = 115;
static constexpr int F5_KEY    = 116;  static constexpr int F6_KEY  = 117;
static constexpr int F7_KEY    = 118;  static constexpr int F8_KEY  = 119;
static constexpr int F9_KEY    = 120;  static constexpr int F10_KEY = 121;
static constexpr int F11_KEY   = 122;  static constexpr int F12_KEY = 123;

// Non-coded keys: use `key` directly (not keyCode) for these
static constexpr char BACKSPACE = 8;
static constexpr char TAB       = 9;
static constexpr char ENTER     = 10;   // PC/Unix enter key
// Undefine any system macros that might conflict
#ifdef RETURN
#  undef RETURN
#endif
#ifdef DELETE
#  undef DELETE
#endif
static constexpr int RETURN    = 13;   // Mac return key (same key as ENTER on most systems)
static constexpr int ESC       = 27;
static constexpr int DELETE    = 127;

// Mouse buttons (mouseButton variable, Java MouseEvent values)
static constexpr int CENTER    = 3;     // middle mouse button; also rectMode/ellipseMode CENTER

// Color modes
static constexpr int RGB  = 1;
static constexpr int HSB  = 2;
#define ARGB 3  /* createImage(w,h,ARGB) */

// Shape / rect / ellipse modes
static constexpr int CORNER      = 0;
static constexpr int CORNERS     = 1;
static constexpr int RADIUS      = 2;

// Stroke caps and joins
static constexpr int ROUND   = 10;
static constexpr int SQUARE  = 11;
static constexpr int PROJECT = 12;
static constexpr int MITER   = 13;
static constexpr int BEVEL   = 14;

// beginShape() kinds
static constexpr int POINTS         = 0;
static constexpr int LINES          = 1;
static constexpr int TRIANGLES      = 2;
static constexpr int TRIANGLE_FAN   = 3;
static constexpr int TRIANGLE_STRIP = 4;
static constexpr int QUADS          = 5;
static constexpr int QUAD_STRIP     = 6;
static constexpr int CLOSE          = 7;

// Text alignment
static constexpr int LEFT_ALIGN   = 20;
static constexpr int RIGHT_ALIGN  = 21;
static constexpr int TOP_ALIGN    = 22;
static constexpr int BOTTOM_ALIGN = 23;
static constexpr int BASELINE     = 24;
static constexpr int CENTER_ALIGN = 25;

// Blend modes
static constexpr int BLEND      = 30;
static constexpr int ADD        = 31;
static constexpr int SUBTRACT   = 32;
static constexpr int MULTIPLY   = 33;
static constexpr int SCREEN     = 34;
static constexpr int DARKEST    = 35;
static constexpr int LIGHTEST   = 36;
static constexpr int DIFFERENCE = 37;
static constexpr int EXCLUSION  = 38;

// Math constants (float precision)
static constexpr float PI         = static_cast<float>(M_PI);
static constexpr float TWO_PI     = static_cast<float>(M_PI * 2.0);
static constexpr float HALF_PI    = static_cast<float>(M_PI / 2.0);
static constexpr float QUARTER_PI = static_cast<float>(M_PI / 4.0);
static constexpr float TAU        = TWO_PI; // alias

// Renderer flags for size()
static constexpr int P2D = 2;
static constexpr int P3D = 3;

// Texture / image modes
static constexpr int IMAGE  = 100;
static constexpr int NORMAL = 101;
static constexpr int CLAMP  = 102;
static constexpr int REPEAT = 103;

// hint() flags
static constexpr int ENABLE_DEPTH_TEST         =  1;
static constexpr int DISABLE_DEPTH_TEST        = -1;
static constexpr int ENABLE_DEPTH_SORT         =  2;
static constexpr int DISABLE_DEPTH_SORT        = -2;
static constexpr int ENABLE_OPENGL_ERRORS      =  3;
static constexpr int DISABLE_OPENGL_ERRORS     = -3;
static constexpr int ENABLE_STROKE_PERSPECTIVE =  4;
static constexpr int DISABLE_STROKE_PERSPECTIVE= -4;
static constexpr int ENABLE_TEXTURE_MIPMAPS    =  5;
static constexpr int DISABLE_TEXTURE_MIPMAPS   = -5;

// Cursor shapes (map to GLFW)
static constexpr int ARROW       = GLFW_ARROW_CURSOR;
static constexpr int CROSS       = GLFW_CROSSHAIR_CURSOR;
static constexpr int HAND        = GLFW_HAND_CURSOR;        // GLFW_POINTING_HAND_CURSOR in 3.4+
static constexpr int MOVE        = GLFW_HRESIZE_CURSOR;     // GLFW_RESIZE_ALL_CURSOR in 3.4+
static constexpr int TEXT_CURSOR = GLFW_IBEAM_CURSOR;
static constexpr int WAIT        = GLFW_VRESIZE_CURSOR;     // GLFW_RESIZE_ALL_CURSOR in 3.4+

// =============================================================================
// TIMING  --  inline so they compile anywhere without linking Processing.cpp
// =============================================================================

inline unsigned long millis() {
    using namespace std::chrono;
    static auto start = steady_clock::now();
    return static_cast<unsigned long>(duration_cast<milliseconds>(steady_clock::now()-start).count());
}
inline int second() { std::time_t t=std::time(nullptr); return std::localtime(&t)->tm_sec;      }
inline int minute() { std::time_t t=std::time(nullptr); return std::localtime(&t)->tm_min;      }
inline int hour()   { std::time_t t=std::time(nullptr); return std::localtime(&t)->tm_hour;     }
inline int day()    { std::time_t t=std::time(nullptr); return std::localtime(&t)->tm_mday;     }
inline int month()  { std::time_t t=std::time(nullptr); return std::localtime(&t)->tm_mon+1;    }
inline int year()   { std::time_t t=std::time(nullptr); return std::localtime(&t)->tm_year+1900;}

// =============================================================================
// MATH  --  float wrappers that match Processing Java's function names
// =============================================================================

inline float sin(float x)  { return std::sin(x);  }
inline float cos(float x)  { return std::cos(x);  }
inline float tan(float x)  { return std::tan(x);  }
inline float asin(float x) { return std::asin(x); }
inline float acos(float x) { return std::acos(x); }
inline float atan(float x) { return std::atan(x); }
inline float atan2(float y, float x) { return std::atan2(y, x); }
inline float sqrt(float x)  { return std::sqrt(x);  }
inline float sq(float x)    { return x * x;          }
inline float abs(float x)   { return std::fabs(x);   }
inline float ceil(float x)  { return std::ceil(x);   }
inline float floor(float x) { return std::floor(x);  }
inline float round(float x) { return std::round(x);  }
inline float exp(float x)   { return std::exp(x);    }
inline float log(float x)   { return std::log(x);    }
inline float pow(float b, float e) { return std::pow(b, e); }
inline float mag(float x, float y)         { return std::sqrt(x*x+y*y);       }
inline float mag(float x, float y, float z){ return std::sqrt(x*x+y*y+z*z);   }
inline float norm(float v, float lo, float hi)  { return (v-lo)/(hi-lo);            }
inline float degrees(float r)               { return r * 180.f / PI;               }
inline float radians(float d)               { return d * PI / 180.f;               }
inline float lerp(float a, float b, float t){ return a + t*(b-a);                  }
inline float dist(float x1,float y1,float x2,float y2) {
    float dx=x2-x1, dy=y2-y1;
    return std::sqrt(dx*dx+dy*dy);
}
inline float dist(float x1,float y1,float z1,float x2,float y2,float z2) {
    float dx=x2-x1, dy=y2-y1, dz=z2-z1;
    return std::sqrt(dx*dx+dy*dy+dz*dz);
}
inline float map(float v, float i0, float i1, float o0, float o1) {
    return o0 + (v-i0)*(o1-o0)/(i1-i0);
}
inline float constrain(float v, float lo, float hi) { return v<lo?lo:(v>hi?hi:v); }
inline float max(float a, float b)          { return a>b?a:b; }
inline float min(float a, float b)          { return a<b?a:b; }
inline float max(float a, float b, float c) { return max(a,max(b,c)); }
inline float min(float a, float b, float c) { return min(a,min(b,c)); }
inline bool isNaN(float v)      { return std::isnan(v);  }
inline bool isInfinite(float v) { return std::isinf(v);  }

// =============================================================================
// RANDOM / NOISE
// =============================================================================

void  randomSeed(long s);
float random(float lo, float hi);
float random(float hi);
float  randomGaussian();
void   noiseSeed(int seed);
void   noiseDetail(int octaves, float falloff=0.5f);
float  noise(float x);
float  noise(float x, float y);
float  noise(float x, float y, float z);

// =============================================================================
// ARRAY UTILITIES  --  match Processing Java's array functions
// =============================================================================

template<typename T> inline std::vector<T> append(std::vector<T> arr, T val)                { arr.push_back(val); return arr; }
template<typename T> inline std::vector<T> concat(std::vector<T> a, const std::vector<T>& b){ a.insert(a.end(),b.begin(),b.end()); return a; }
template<typename T> inline std::vector<T> expand(std::vector<T> arr, int n=-1)             { if(n<0)n=(int)arr.size()*2; arr.resize(n); return arr; }
template<typename T> inline std::vector<T> reverse(std::vector<T> arr)                      { std::reverse(arr.begin(),arr.end()); return arr; }
template<typename T> inline std::vector<T> shorten(std::vector<T> arr)                      { if(!arr.empty())arr.pop_back(); return arr; }
template<typename T> inline std::vector<T> sort(std::vector<T> arr)                         { std::sort(arr.begin(),arr.end()); return arr; }
template<typename T> inline std::vector<T> splice(std::vector<T> arr, T val, int i)         { arr.insert(arr.begin()+i,val); return arr; }
template<typename T> inline std::vector<T> splice(std::vector<T> arr, const std::vector<T>& vals, int i){ arr.insert(arr.begin()+i,vals.begin(),vals.end()); return arr; }
template<typename T> inline std::vector<T> subset(const std::vector<T>& arr, int start, int count=-1){
    if(count<0) count=(int)arr.size()-start;
    return std::vector<T>(arr.begin()+start, arr.begin()+start+count);
}
template<typename T> inline void arrayCopy(const std::vector<T>& src, std::vector<T>& dst) { dst=src; }
template<typename T> inline void arrayCopy(const std::vector<T>& src, int sp, std::vector<T>& dst, int dp, int len){
    for(int i=0;i<len;i++) dst[dp+i]=src[sp+i];
}

// =============================================================================
// COLOR TYPE  --  matches Processing Java's 'color' (int) type
// =============================================================================

// 'color' is a packed 32-bit ARGB integer, just like in Processing Java.
// Constructors respect the current colorMode setting (see colorMode()).
struct color {
    unsigned int value;

    color() : value(0xFF000000) {}         // default: opaque black
    color(unsigned int v) : value(v) {}  // allows color pix = img->get(x,y)

    // These constructors call makeColor() which applies colorMode.
    // Defined in Processing.cpp so the colorMode globals are accessible.
    color(int gray);
    color(int gray, int a);
    color(int r, int g, int b);
    color(int r, int g, int b, int a);
    // Use explicit cast: color(0,153,204,(int)a) or color(0.f,153.f,204.f,a)
    color(float gray);
    color(float gray, float a);
    color(float r, float g, float b);
    color(float r, float g, float b, float a);

    explicit operator unsigned int() const { return value; }
    unsigned int toInt() const { return value; }
    // In Processing Java, color IS int. Allow implicit color<->int conversion
    // so sketches can write: int c = color(255); int c = lerpColor(a,b,t);
    operator int() const { return (int)value; }
    // fromRaw: convert raw int (Java color int) directly to color value
    static color fromRaw(int v) { color c; c.value=(unsigned int)v; return c; }
    bool operator==(const color& o) const { return value == o.value; }
    bool operator!=(const color& o) const { return value != o.value; }
};

// Build a color value from components (respects colorMode)
color makeColor(float a, float b, float c, float d=255);
color makeColor(float gray, float alpha=255);

// Pack raw 0-255 RGBA without colorMode (for internal use)
inline color colorVal(int r, int g, int b, int a=255) {
    // Clamp to [0,255] -- don't wrap, which would cause dark artifacts
    // when noise()*255 or other values slightly exceed 255
    auto clamp8=[](int v){return v<0?0:v>255?255:v;};
    return color((unsigned int)(((clamp8(a))<<24)|((clamp8(r))<<16)|((clamp8(g))<<8)|(clamp8(b))));
}

// Color component extractors
float red(color c);
float green(color c);
float blue(color c);
float alpha(color c);
float brightness(color c);
float saturation(color c);
float hue(color c);
color lerpColor(color c1, color c2, float t);

// =============================================================================
// PRINT / OUTPUT
// =============================================================================

template<typename T> inline void print(const T& v)    { std::cout << v; std::cout.flush(); }
template<typename T> inline void println(const T& v)  { std::cout << v << "\n"; std::cout.flush(); }
inline                       void println()             { std::cout << "\n"; std::cout.flush(); }
template<typename T> inline void printArray(const std::vector<T>& a) {
    for (size_t i=0; i<a.size(); i++) std::cout << "[" << i << "] " << a[i] << "\n";
}

// =============================================================================
// STRING UTILITIES
// =============================================================================

inline std::string str(int v)   { return std::to_string(v); }
inline std::string str(float v) { return std::to_string(v); }
inline std::string str(bool v)  { return v ? "true" : "false"; }
inline std::string str(char v)  { return std::string(1, v); }
inline bool        toBoolean(const std::string& s)  { return s=="true"||s=="1"||s=="yes"; }
inline int         toInt(const std::string& s)      { return std::stoi(s); }
inline float       toFloat(const std::string& s)    { return std::stof(s); }
inline char        toChar(int v)                    { return static_cast<char>(v); }
inline std::string trim(const std::string& s) {
    size_t a=s.find_first_not_of(" \t\n\r"), b=s.find_last_not_of(" \t\n\r");
    return a==std::string::npos ? "" : s.substr(a, b-a+1);
}
inline std::vector<std::string> split(const std::string& s, char d) {
    std::vector<std::string> o; std::stringstream ss(s); std::string t;
    while (std::getline(ss, t, d)) o.push_back(t);
    return o;
}
inline std::vector<std::string> splitTokens(const std::string& s, const std::string& delims) {
    std::vector<std::string> o; std::string cur;
    for (char c:s) {
        if (delims.find(c)!=std::string::npos) { if(!cur.empty()){o.push_back(cur);cur.clear();} }
        else cur+=c;
    }
    if (!cur.empty()) o.push_back(cur);
    return o;
}
inline std::string join(const std::vector<std::string>& v, const std::string& sep) {
    std::string o;
    for (size_t i=0; i<v.size(); i++) { if(i) o+=sep; o+=v[i]; }
    return o;
}

// Number formatting
inline std::string nf(float v, int digits)             { std::ostringstream ss; ss.precision(digits); ss<<std::fixed<<v; return ss.str(); }
inline std::string nf(int v, int minDigits)            { std::ostringstream ss; ss<<std::setw(minDigits)<<std::setfill('0')<<v; return ss.str(); }
inline std::string nf(float v, int left, int right)    {
    std::ostringstream ss; ss<<std::fixed<<std::setprecision(right)<<v;
    std::string s=ss.str(); size_t dot=s.find('.');
    size_t intLen=(dot==std::string::npos)?s.size():dot;
    while((int)intLen<left){s="0"+s;intLen++;}
    return s;
}
inline std::string nfc(float v, int digits)  { std::ostringstream ss; ss.precision(digits); ss<<std::fixed<<v; std::string s=ss.str(); int dot=(int)s.find('.'); if(dot<0)dot=(int)s.size(); for(int i=dot-3;i>0;i-=3)s.insert(i,","); return s; }
inline std::string nfp(float v, int digits)  { return (v>=0?"+":"") + nf(v,digits); }
inline std::string nfs(float v, int digits)  { return (v>=0?" ":"") + nf(v,digits); }
inline std::string hex(int v)                { std::ostringstream ss; ss<<std::uppercase<<std::hex<<v; return ss.str(); }
inline std::string hex(int v, int digits)    { std::ostringstream ss; ss<<std::uppercase<<std::hex<<std::setw(digits)<<std::setfill('0')<<v; return ss.str(); }
inline std::string binary(int v)             { std::string s; for(int i=31;i>=0;i--) s+=((v>>i)&1)?'1':'0'; return s; }
inline int         unhex(const std::string& s)   { return std::stoi(s,nullptr,16); }
inline int         unbinary(const std::string& s){ return std::stoi(s,nullptr,2);  }

// Regex helpers
inline std::vector<std::string> match(const std::string& s, const std::string& pat) {
    std::vector<std::string> out; std::smatch m; std::regex re(pat);
    if (std::regex_search(s,m,re)) for (auto& x:m) out.push_back(x.str());
    return out;
}
inline std::vector<std::vector<std::string>> matchAll(const std::string& s, const std::string& pat) {
    std::vector<std::vector<std::string>> out; std::regex re(pat);
    auto it=std::sregex_iterator(s.begin(),s.end(),re), end=std::sregex_iterator();
    for(;it!=end;++it){ std::vector<std::string> row; for(auto& x:*it) row.push_back(x.str()); out.push_back(row); }
    return out;
}

// =============================================================================
// FILE I/O
// =============================================================================

inline std::vector<std::string>     loadStrings(const std::string& path) {
    std::vector<std::string> lines; std::ifstream f(path); std::string l;
    while (std::getline(f,l)) lines.push_back(l);
    return lines;
}
inline bool saveStrings(const std::string& path, const std::vector<std::string>& lines) {
    std::ofstream f(path); if (!f) return false;
    for (auto& l:lines) f<<l<<"\n";
    return true;
}
inline std::vector<unsigned char> loadBytes(const std::string& path) {
    std::ifstream f(path,std::ios::binary);
    return std::vector<unsigned char>((std::istreambuf_iterator<char>(f)),std::istreambuf_iterator<char>());
}
inline bool saveBytes(const std::string& path, const std::vector<unsigned char>& data) {
    std::ofstream f(path,std::ios::binary); if (!f) return false;
    f.write(reinterpret_cast<const char*>(data.data()),data.size());
    return true;
}

// =============================================================================
// USER CALLBACKS  --  the sketch must define at minimum setup() and draw()
// =============================================================================

void setup();  // called once before the draw loop starts
void draw();   // called every frame

// =============================================================================
// ENVIRONMENT FUNCTIONS
// =============================================================================

void size(int w, int h);
void size(int w, int h, int renderer);
void fullScreen();
void frameRate(int fps);
void settings();
void noLoop();
void loop();
void redraw();
void exit_sketch();
void windowTitle(const std::string& t);
void windowMove(int x, int y);
void windowResize(int w, int h);
void windowResizable(bool r);
// ---------------------------------------------------------------------------
// Clipboard, input state, timing, window icon  (IDE-facing helpers)
// ---------------------------------------------------------------------------
void        setClipboard(const std::string& s);  // copy text to system clipboard
std::string getClipboard();                       // paste text from system clipboard
void        setWindowIcon(PImage* img);           // set the window taskbar icon

bool isCtrlDown();   // true while Ctrl is held
bool isShiftDown();  // true while Shift is held
bool isAltDown();    // true while Alt  is held

// Letter key constants -- Java KeyEvent.VK_A=65 .. VK_Z=90
static constexpr int KEY_A=65; static constexpr int KEY_B=66;
static constexpr int KEY_C=67; static constexpr int KEY_D=68;
static constexpr int KEY_E=69; static constexpr int KEY_F=70;
static constexpr int KEY_G=71; static constexpr int KEY_H=72;
static constexpr int KEY_I=73; static constexpr int KEY_J=74;
static constexpr int KEY_K=75; static constexpr int KEY_L=76;
static constexpr int KEY_M=77; static constexpr int KEY_N=78;
static constexpr int KEY_O=79; static constexpr int KEY_P=80;
static constexpr int KEY_Q=81; static constexpr int KEY_R=82;
static constexpr int KEY_S=83; static constexpr int KEY_T=84;
static constexpr int KEY_U=85; static constexpr int KEY_V=86;
static constexpr int KEY_W=87; static constexpr int KEY_X=88;
static constexpr int KEY_Y=89; static constexpr int KEY_Z=90;

static constexpr int PERIOD_KEY = 46;
static constexpr int SLASH_KEY  = 47;
static constexpr int EQUAL_KEY  = 61;
static constexpr int MINUS_KEY  = 45;

void windowRatio(int w, int h);
void pixelDensity(int d);
void smooth();
void noSmooth();
void hint(int which);
void cursor();
void cursor(int type);
void noCursor();
void captureMouse();   // lock cursor to window + raw input (for FPS games)
void releaseMouse();   // unlock cursor

// Convenience aliases that match Processing Java naming
inline int  displayDensity()                { return pixelDensityValue; }
inline void setTitle(const std::string& t)  { windowTitle(t);           }
inline void setLocation(int x, int y)       { windowMove(x, y);         }
inline void setResizable(bool r)            { windowResizable(r);       }
inline void exit()                          { exit_sketch();             }

// =============================================================================
// STYLE STACK  --  save/restore fill, stroke, transform state
// =============================================================================

void push();       // push both style and matrix
void pop();
void pushStyle();  // push only style (fill, stroke, font...)
void popStyle();
void pushMatrix(); // push only the transform matrix
void popMatrix();

// =============================================================================
// COLOR MODE
// =============================================================================

// colorMode(RGB)               -- all channels [0..255]
// colorMode(HSB, 360, 100, 100) -- hue [0..360], sat/bri [0..100]
void colorMode(int mode, float mx=255.f);
void colorMode(int mode, float mH, float mS, float mB, float mA=255.f);

// =============================================================================
// BACKGROUND / CLEAR
// =============================================================================

void background(float gray, float a=255.f);
void background(const PImage& img);   // draw image as background
inline void background(const PImage* img){ if(img) background(*img); }
void background(float r, float g, float b, float a=255.f);
void background(color c);
void clear();

// int overloads forward to float (avoids ambiguity with color constructors)

// =============================================================================
// FILL / STROKE
// =============================================================================

void fill(float gray, float a);
void fill(float gray);
void fill(float r, float g, float b, float a);
void fill(float r, float g, float b);
void fill(color c);
void fill(color c, float a);  // fill with color + override alpha
inline void fill(color c, int a) { fill(c, (float)a); }
void fill(const PColor& c);
void noFill();

void stroke(float gray, float a);
void stroke(float gray);
void stroke(float r, float g, float b, float a);
void stroke(float r, float g, float b);
void stroke(color c);
void stroke(const PColor& c);
void noStroke();
void strokeWeight(float w);
void strokeCap(int cap);
void strokeJoin(int join);

// int overloads
// ---------------------------------------------------------------------------
// Mixed-type overloads for fill, stroke, background, tint.
// These templates accept any arithmetic type (int, float, double, etc.)
// and forward to the canonical float versions, eliminating all ambiguity
// from mixed calls like fill(int, int, float) or stroke(float, int, float).
// ---------------------------------------------------------------------------
template<typename A, typename B,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void fill(A gray, B a)
    { fill((float)gray,(float)a); }

template<typename A, typename B, typename C,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>>>
inline void fill(A r, B g, C b)
    { fill((float)r,(float)g,(float)b); }

template<typename A, typename B, typename C, typename D,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline void fill(A r, B g, C b, D a)
    { fill((float)r,(float)g,(float)b,(float)a); }

template<typename A,
         typename=std::enable_if_t<std::is_arithmetic_v<A>>>
inline void stroke(A gray)
    { stroke((float)gray); }

template<typename A, typename B,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void stroke(A gray, B a)
    { stroke((float)gray,(float)a); }

template<typename A, typename B, typename C,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>>>
inline void stroke(A r, B g, C b)
    { stroke((float)r,(float)g,(float)b); }

template<typename A, typename B, typename C, typename D,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline void stroke(A r, B g, C b, D a)
    { stroke((float)r,(float)g,(float)b,(float)a); }

template<typename A,
         typename=std::enable_if_t<std::is_arithmetic_v<A>>>
inline void strokeWeight(A w)
    { strokeWeight((float)w); }

template<typename A,
         typename=std::enable_if_t<std::is_arithmetic_v<A>>>
inline void fill(A gray)
    { fill((float)gray); }

template<typename A,
         typename=std::enable_if_t<std::is_arithmetic_v<A>>>
inline void background(A gray)
    { background((float)gray); }

template<typename A, typename B,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void background(A gray, B a)
    { background((float)gray,(float)a); }

template<typename A, typename B, typename C,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>>>
inline void background(A r, B g, C b)
    { background((float)r,(float)g,(float)b); }

template<typename A, typename B, typename C, typename D,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline void background(A r, B g, C b, D a)
    { background((float)r,(float)g,(float)b,(float)a); }

void tint(float gray, float a);
void tint(float gray);
void tint(float r, float g, float b, float a);
void tint(float r, float g, float b);

// Integer-only templates: these cast int args to float and call the float overloads.
// Constrained to non-float types so float calls go directly to the float overload
// above and don't recurse back into the template.
template<typename A, typename=std::enable_if_t<std::is_arithmetic_v<A>>>
inline void tint(A gray)
    { tint((float)gray); }

template<typename A, typename B,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void tint(A gray, B a)
    { tint((float)gray,(float)a); }

template<typename A, typename B, typename C,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>>>
inline void tint(A r, B g, C b)
    { tint((float)r,(float)g,(float)b); }

template<typename A, typename B, typename C, typename D,
         typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline void tint(A r, B g, C b, D a)
    { tint((float)r,(float)g,(float)b,(float)a); }

// =============================================================================
// SHAPE ATTRIBUTES
// =============================================================================

void rectMode(int mode);
void ellipseMode(int mode);

// =============================================================================
// 2D PRIMITIVES
// =============================================================================

void point(float x, float y);
void point(float x, float y, float z);
void line(float x1, float y1, float x2, float y2);
void line(float x1, float y1, float z1, float x2, float y2, float z2);
void ellipse(float cx, float cy, float w, float h);
void circle(float cx, float cy, float d);
void rect(float x, float y, float w, float h);
void rect(float x, float y, float w, float h, float r);
void square(float x, float y, float s);
void triangle(float x1, float y1, float x2, float y2, float x3, float y3);
void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4);
void arc(float cx, float cy, float w, float h, float start, float stop);
void arc(float cx, float cy, float w, float h, float start, float stop, int mode);

// Mixed-type templates are in the comprehensive block at end of namespace

// =============================================================================
// 3D PRIMITIVES
// =============================================================================

void box(float size);
void box(float w, float h, float d);
void sphere(float r);
void sphereDetail(int res);
void rotateX(float angle);
void rotateY(float angle);
void rotateZ(float angle);

// =============================================================================
// CUSTOM SHAPES  --  beginShape / vertex / endShape
// =============================================================================

void beginShape(int kind=-1);
void endShape(int mode=0);
void vertex(float x, float y);
void vertex(float x, float y, float z);
void vertex(float x, float y, float u, float v);
void vertex(float x, float y, float z, float u, float v);
void bezierVertex(float cx1, float cy1, float cx2, float cy2, float x, float y);
void quadraticVertex(float cx, float cy, float x, float y);
void curveVertex(float x, float y);
void beginContour();
void endContour();
void bezier(float x1, float y1, float cx1, float cy1, float cx2, float cy2, float x2, float y2);
void curve(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3);
float bezierPoint(float a, float b, float c, float d, float t);
float bezierTangent(float a, float b, float c, float d, float t);
float curvePoint(float a, float b, float c, float d, float t);
float curveTangent(float a, float b, float c, float d, float t);
void curveDetail(int d);
void curveTightness(float t);
void bezierDetail(int d);

// =============================================================================
// MATRIX TRANSFORMS
// =============================================================================

void resetMatrix();
void applyMatrix(float n00,float n01,float n02,float n03,
                 float n10,float n11,float n12,float n13,
                 float n20,float n21,float n22,float n23,
                 float n30,float n31,float n32,float n33);
void translate(float x, float y);
void translate(float x, float y, float z);
void scale(float s);
void scale(float sx, float sy);
void rotate(float angle);
void shearX(float angle);
void shearY(float angle);
void printMatrix();
float screenX(float x, float y, float z=0);
float screenY(float x, float y, float z=0);
float screenZ(float x, float y, float z=0);
float modelX(float x, float y, float z=0);
float modelY(float x, float y, float z=0);
float modelZ(float x, float y, float z=0);

// =============================================================================
// CAMERA  --  3D view projection
// =============================================================================

void camera();
void camera(float eyeX, float eyeY, float eyeZ,
            float centerX, float centerY, float centerZ,
            float upX, float upY, float upZ);
void beginCamera();
void endCamera();
void perspective();
void perspective(float fov, float aspect, float zNear, float zFar);
void ortho();
void ortho(float left, float right, float bottom, float top, float near, float far);
void frustum(float left, float right, float bottom, float top, float near, float far);
void printCamera();
void printProjection();

// =============================================================================
// LIGHTS
// =============================================================================

void lights();
void noLights();
void ambientLight(float r, float g, float b);
void ambientLight(float r, float g, float b, float x, float y, float z);
void directionalLight(float r, float g, float b, float nx, float ny, float nz);
void pointLight(float r, float g, float b, float x, float y, float z);
void spotLight(float r, float g, float b, float x, float y, float z,
               float nx, float ny, float nz, float angle, float concentration);
void lightFalloff(float constant, float linear, float quadratic);
void lightSpecular(float r, float g, float b);
void normal(float nx, float ny, float nz);

// Material properties
void ambient(float r, float g, float b);
void ambient(color c);
void emissive(float r, float g, float b);
void emissive(color c);
void specular(float r, float g, float b);
void specular(color c);
void shininess(float s);

// =============================================================================
// TEXT
// =============================================================================

void text(const std::string& msg, float x, float y);
void text(const std::string& msg, float x, float y, float w, float h); // word-wrapped bounding box
void text(int val,   float x, float y);
void text(float val, float x, float y);
void textSize(float size);
void textAlign(int alignX, int alignY=-1);
void textLeading(float leading);
void textMode(int mode);
float textWidth(const std::string& s);
float textAscent();
float textDescent();

// =============================================================================
// IMAGE FUNCTIONS
// =============================================================================

PImage*    loadImage(const std::string& path);


PImage* createImage(int w, int h, int mode=1);
PGraphics* createGraphics(int w, int h);
void       imageMode(int mode);
void       noTint();
void       filter(int mode);
void       filter(int mode, float param);
void       loadPixels();
void       updatePixels();
color      get(int x, int y);
void       set(int x, int y, color c);
PImage     getRegion(int x, int y, int w, int h);

// =============================================================================
// BLEND / CLIP
// =============================================================================

void blendMode(int mode);
void clip(float x, float y, float w, float h);
void noClip();
void blend(int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh, int mode);
void copy(int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh);

// =============================================================================
// SAVE / THREADING
// =============================================================================

void saveFrame(const std::string& filename="frame-####.png");
void save(const std::string& filename);
inline void thread(std::function<void()> fn) { std::thread(fn).detach(); }
inline void delay(int ms) { std::this_thread::sleep_for(std::chrono::milliseconds(ms)); }

// =============================================================================
// ENTRY POINT
// =============================================================================

void run();

// Opens a console window for stderr output on Windows when --debug flag is passed.
// Call this before run() -- see main.cpp.
void enableDebugConsole();

// =============================================================================
// JSON
// =============================================================================

struct JSONValue;
using JSONObject = std::map<std::string, JSONValue>;
using JSONArray  = std::vector<JSONValue>;

struct JSONValue {
    enum Type { NULL_T,BOOL_T,INT_T,FLOAT_T,STRING_T,ARRAY_T,OBJECT_T } type=NULL_T;
    bool b=false; double n=0; std::string s;
    std::shared_ptr<JSONArray>  arr;
    std::shared_ptr<JSONObject> obj;

    JSONValue() = default;
    JSONValue(bool v)               : type(BOOL_T),   b(v)  {}
    JSONValue(int v)                : type(INT_T),     n(v)  {}
    JSONValue(double v)             : type(FLOAT_T),   n(v)  {}
    JSONValue(const std::string& v) : type(STRING_T),  s(v)  {}
    JSONValue(const char* v)        : type(STRING_T),  s(v)  {}
    JSONValue(JSONArray v)          : type(ARRAY_T),   arr(std::make_shared<JSONArray>(v))  {}
    JSONValue(JSONObject v)         : type(OBJECT_T),  obj(std::make_shared<JSONObject>(v)) {}

    bool isNull()   const { return type==NULL_T;   }
    bool isBool()   const { return type==BOOL_T;   }
    bool isInt()    const { return type==INT_T;     }
    bool isFloat()  const { return type==FLOAT_T || type==INT_T; }
    bool isString() const { return type==STRING_T;  }
    bool isArray()  const { return type==ARRAY_T;   }
    bool isObject() const { return type==OBJECT_T;  }

    bool        getBool()   const { return b;       }
    int         getInt()    const { return (int)n;  }
    float       getFloat()  const { return (float)n;}
    std::string getString() const { return s;       }
    JSONArray&  getArray()        { return *arr;    }
    JSONObject& getObject()       { return *obj;    }
    const JSONArray&  getArray()  const { return *arr; }
    const JSONObject& getObject() const { return *obj; }

    JSONValue& operator[](const std::string& k) { return (*obj)[k]; }
    JSONValue& operator[](int i)                { return (*arr)[i]; }
    int  size()             const { if(isArray())return (int)arr->size(); if(isObject())return (int)obj->size(); return 0; }
    bool hasKey(const std::string& k) const     { return isObject() && obj->count(k); }
};

JSONValue   parseJSON(const std::string& src);
std::string toJSONString(const JSONValue& v, int indent=0);
JSONValue   loadJSONObject(const std::string& path);
JSONValue   loadJSONArray(const std::string& path);
bool        saveJSONObject(const std::string& path, const JSONValue& v, int indent=2);
bool        saveJSONArray(const std::string& path,  const JSONValue& v, int indent=2);
inline JSONValue parseJSONObject(const std::string& s) { return parseJSON(s); }
inline JSONValue parseJSONArray(const std::string& s)  { return parseJSON(s); }

// =============================================================================
// XML
// =============================================================================

struct XML {
    std::string name, content;
    std::map<std::string,std::string> attributes;
    std::vector<XML> children;

    XML() = default;
    explicit XML(const std::string& n) : name(n) {}

    std::string getName()    const { return name;    }
    std::string getContent() const { return content; }

    bool        hasAttribute(const std::string& k)                     const { return attributes.count(k)>0; }
    std::string getAttribute(const std::string& k, const std::string& def="") const {
        auto it = attributes.find(k);
        return it != attributes.end() ? it->second : def;
    }
    int   getAttributeInt(const std::string& k, int def=0)     const { return hasAttribute(k) ? std::stoi(attributes.at(k)) : def; }
    float getAttributeFloat(const std::string& k, float def=0) const { return hasAttribute(k) ? std::stof(attributes.at(k)) : def; }

    void setAttribute(const std::string& k, const std::string& v) { attributes[k]=v; }
    void setContent(const std::string& c) { content=c; }

    XML*              addChild(const std::string& n)  { children.push_back(XML(n)); return &children.back(); }
    XML*              getChild(int i)                  { return i<(int)children.size()?&children[i]:nullptr; }
    XML*              getChild(const std::string& n)   { for(auto& c:children) if(c.name==n) return &c; return nullptr; }
    int               getChildCount()           const  { return (int)children.size(); }
    std::vector<XML*> getChildren(const std::string& n){ std::vector<XML*> r; for(auto& c:children) if(c.name==n) r.push_back(&c); return r; }

    std::string toString(int indent=0) const;
};

XML  loadXML(const std::string& path);
XML  parseXML(const std::string& src);
bool saveXML(const std::string& path, const XML& x);

// =============================================================================
// TABLE  --  CSV-style data with named columns
// =============================================================================

class Table {
public:
    std::vector<std::string>              columns;
    std::vector<std::vector<std::string>> rows;

    Table() = default;

    void addColumn(const std::string& name) { columns.push_back(name); }
    int  getColumnCount() const { return (int)columns.size(); }
    int  getRowCount()    const { return (int)rows.size();    }
    std::string getColumnTitle(int i) const { return i<(int)columns.size()?columns[i]:""; }
    int  getColumnIndex(const std::string& n) const {
        for (int i=0;i<(int)columns.size();i++) if(columns[i]==n) return i;
        return -1;
    }

    std::vector<std::string>& addRow() { rows.push_back(std::vector<std::string>(columns.size())); return rows.back(); }

    std::string getString(int row, int col)                const { return row<(int)rows.size()&&col<(int)rows[row].size()?rows[row][col]:""; }
    std::string getString(int row, const std::string& col) const { return getString(row,getColumnIndex(col)); }
    int         getInt(int row, int col)                   const { auto s=getString(row,col); return s.empty()?0:std::stoi(s); }
    int         getInt(int row, const std::string& col)    const { return getInt(row,getColumnIndex(col)); }
    float       getFloat(int row, int col)                 const { auto s=getString(row,col); return s.empty()?0:std::stof(s); }
    float       getFloat(int row, const std::string& col)  const { return getFloat(row,getColumnIndex(col)); }

    void setString(int row, int col, const std::string& v) { if(row<(int)rows.size()&&col<(int)rows[row].size()) rows[row][col]=v; }
    void setString(int row, const std::string& col, const std::string& v) { setString(row,getColumnIndex(col),v); }
    void setInt(int row, int col, int v)     { setString(row,col,std::to_string(v)); }
    void setFloat(int row, int col, float v) { setString(row,col,std::to_string(v)); }

    std::vector<int> findRowsWithValue(const std::string& col, const std::string& val) const {
        std::vector<int> r; int c=getColumnIndex(col);
        for (int i=0;i<(int)rows.size();i++) if(getString(i,c)==val) r.push_back(i);
        return r;
    }
    int findFirstRowWithValue(const std::string& col, const std::string& val) const {
        auto r=findRowsWithValue(col,val); return r.empty()?-1:r[0];
    }
    void removeRow(int i) { if(i<(int)rows.size()) rows.erase(rows.begin()+i); }
    void clearRows()      { rows.clear(); }
};

Table* loadTable(const std::string& path, const std::string& options="header");
bool   saveTable(const std::string& path, const Table& t, const std::string& ext="csv");

// =============================================================================
// TYPED LISTS / DICTS  --  match Processing Java's IntList, FloatDict, etc.
// =============================================================================

class IntList {
public:
    std::vector<int> data;
    IntList() = default;
    IntList(std::initializer_list<int> l) : data(l) {}
    // Java-style
    void append(int v)            { data.push_back(v); }
    void add(int v)               { data.push_back(v); }
    void add(int i, int v)        { data.insert(data.begin()+i,v); }
    void set(int i, int v)        { data[i]=v; }
    int  get(int i)         const { return data[i]; }
    int  size()             const { return (int)data.size(); }
    bool isEmpty()          const { return data.empty(); }
    void sort()                   { std::sort(data.begin(),data.end()); }
    void reverse()                { std::reverse(data.begin(),data.end()); }
    bool hasValue(int v)    const { return std::find(data.begin(),data.end(),v)!=data.end(); }
    bool contains(int v)    const { return hasValue(v); }
    void remove(int i)            { data.erase(data.begin()+i); }
    void clear()                  { data.clear(); }
    void shuffle() {
        for(int i=(int)data.size()-1;i>0;i--){
            int j=rand()%(i+1); std::swap(data[i],data[j]);
        }
    }
    int& operator[](int i)        { return data[i]; }
    auto begin() { return data.begin(); }
    auto end()   { return data.end(); }
};

class FloatList {
public:
    std::vector<float> data;
    FloatList() = default;
    FloatList(std::initializer_list<float> l) : data(l) {}
    void  append(float v)         { data.push_back(v); }
    void  add(float v)            { data.push_back(v); }
    void  set(int i, float v)     { data[i]=v; }
    float get(int i)        const { return data[i]; }
    int   size()            const { return (int)data.size(); }
    bool  isEmpty()         const { return data.empty(); }
    void  sort()                  { std::sort(data.begin(),data.end()); }
    void  reverse()               { std::reverse(data.begin(),data.end()); }
    void  remove(int i)           { data.erase(data.begin()+i); }
    void  clear()                 { data.clear(); }
    void  shuffle() {
        for(int i=(int)data.size()-1;i>0;i--){
            int j=rand()%(i+1); std::swap(data[i],data[j]);
        }
    }
    float& operator[](int i)      { return data[i]; }
    auto begin() { return data.begin(); }
    auto end()   { return data.end(); }
};

class StringList {
public:
    std::vector<std::string> data;
    StringList() = default;
    StringList(std::initializer_list<std::string> l) : data(l) {}
    void        append(const std::string& v)    { data.push_back(v); }
    void        set(int i, const std::string& v){ data[i]=v; }
    std::string get(int i)            const     { return data[i]; }
    int         size()                const     { return (int)data.size(); }
    void        sort()                          { std::sort(data.begin(),data.end()); }
    void        reverse()                       { std::reverse(data.begin(),data.end()); }
    bool        hasValue(const std::string& v) const { return std::find(data.begin(),data.end(),v)!=data.end(); }
    void        remove(int i)                   { data.erase(data.begin()+i); }
    void        clear()                         { data.clear(); }
    std::string& operator[](int i)              { return data[i]; }
};

// =============================================================================
// PList<T> -- std::vector wrapper that works inside the Processing namespace
// Use instead of std::vector<T> in sketches to avoid namespace conflicts.
// All Java ArrayList methods supported: add, get, set, remove, size, clear,
// contains, indexOf, sort, shuffle, isEmpty.
// =============================================================================
template<typename T>
class PList {
public:
    ::std::vector<T> _data;

    PList() {}
    PList(::std::initializer_list<T> il) : _data(il) {}

    // Core
    void   add(const T& v)              { _data.push_back(v); }
    void   add(int i, const T& v)       { _data.insert(_data.begin()+i, v); }
    T&     get(int i)                   { return _data[i]; }
    const T& get(int i) const           { return _data[i]; }
    void   set(int i, const T& v)       { _data[i] = v; }
    void   remove(int i)                { _data.erase(_data.begin()+i); }
    bool   remove(const T& v)           { auto it=::std::find(_data.begin(),_data.end(),v); if(it==_data.end()) return false; _data.erase(it); return true; }
    int    size()  const                { return (int)_data.size(); }
    bool   isEmpty() const              { return _data.empty(); }
    void   clear()                      { _data.clear(); }
    bool   contains(const T& v) const   { return ::std::find(_data.begin(),_data.end(),v)!=_data.end(); }
    int    indexOf(const T& v) const    { auto it=::std::find(_data.begin(),_data.end(),v); return it==_data.end()?-1:(int)(it-_data.begin()); }

    // Sort / shuffle
    void sort()    { ::std::sort(_data.begin(), _data.end()); }
    void reverse() { ::std::reverse(_data.begin(), _data.end()); }
    void shuffle() {
        for(int i=size()-1;i>0;i--){
            int j=(int)(::std::rand()%(i+1));
            ::std::swap(_data[i],_data[j]);
        }
    }

    // Operator[] for array-style access
    T&       operator[](int i)       { return _data[i]; }
    const T& operator[](int i) const { return _data[i]; }

    // Range-for support
    auto begin() { return _data.begin(); }
    auto end()   { return _data.end(); }
    auto begin() const { return _data.begin(); }
    auto end()   const { return _data.end(); }

    // Append all from another PList
    void addAll(const PList<T>& other) { for(auto& v:other._data) _data.push_back(v); }
};

// Convenience aliases matching Java Processing names

// PMap<K,V> -- thin std::unordered_map wrapper
template<typename K, typename V>
class PMap {
public:
    ::std::unordered_map<K,V> _data;
    void  put(const K& k, const V& v)        { _data[k]=v; }
    V&    get(const K& k)                     { return _data[k]; }
    bool  containsKey(const K& k) const       { return _data.count(k)>0; }
    void  remove(const K& k)                  { _data.erase(k); }
    int   size() const                        { return (int)_data.size(); }
    void  clear()                             { _data.clear(); }
    auto  begin() { return _data.begin(); }
    auto  end()   { return _data.end(); }
};

// =============================================================================
// PGraphics method implementations (after all Processing function declarations)
// =============================================================================

class IntDict {
public:
    std::map<std::string,int> data;
    void set(const std::string& k, int v)               { data[k]=v; }
    int  get(const std::string& k, int def=0) const     { auto it=data.find(k); return it!=data.end()?it->second:def; }
    bool hasKey(const std::string& k)         const     { return data.count(k)>0; }
    void remove(const std::string& k)                   { data.erase(k); }
    int  size()                               const     { return (int)data.size(); }
    void clear()                                        { data.clear(); }
    std::vector<std::string> keys() const               { std::vector<std::string> r; for(auto& p:data) r.push_back(p.first); return r; }
    int& operator[](const std::string& k)               { return data[k]; }
};

class FloatDict {
public:
    std::map<std::string,float> data;
    void  set(const std::string& k, float v)                { data[k]=v; }
    float get(const std::string& k, float def=0)  const     { auto it=data.find(k); return it!=data.end()?it->second:def; }
    bool  hasKey(const std::string& k)            const     { return data.count(k)>0; }
    void  remove(const std::string& k)                      { data.erase(k); }
    int   size()                                  const     { return (int)data.size(); }
    void  clear()                                           { data.clear(); }
    std::vector<std::string> keys() const                   { std::vector<std::string> r; for(auto& p:data) r.push_back(p.first); return r; }
    float& operator[](const std::string& k)                 { return data[k]; }
};

class StringDict {
public:
    std::map<std::string,std::string> data;
    void        set(const std::string& k, const std::string& v)              { data[k]=v; }
    std::string get(const std::string& k, const std::string& def="") const   { auto it=data.find(k); return it!=data.end()?it->second:def; }
    bool        hasKey(const std::string& k)                          const   { return data.count(k)>0; }
    void        remove(const std::string& k)                                  { data.erase(k); }
    int         size()                                                const   { return (int)data.size(); }
    void        clear()                                                       { data.clear(); }
    std::vector<std::string> keys() const                                     { std::vector<std::string> r; for(auto& p:data) r.push_back(p.first); return r; }
    std::string& operator[](const std::string& k)                            { return data[k]; }
};

// =============================================================================
// PSHAPE  --  reusable geometry (created with createShape / loadShape)
// =============================================================================

class PShape {
public:
    struct Vertex { float x,y,z,u,v; };

    std::vector<Vertex>  verts;
    std::vector<PShape>  children;
    int  kind   = -1;
    bool closed = false;
    bool visible = true;

    float fillR=1,fillG=1,fillB=1,fillA=1;
    float strokeR=0,strokeG=0,strokeB=0,strokeA=1,strokeW=1;
    bool  hasFill=true, hasStroke=false;

    PShape() = default;
    explicit PShape(int k) : kind(k) {}
    // Allow PShape bot = loadShape("file.svg") -- copies from pointer
    PShape(const PShape* p) { if(p) *this = *p; }
    PShape& operator=(const PShape* p) { if(p) *this = *p; return *this; }

    void beginShape(int k=-1)         { kind=k; verts.clear(); }
    void endShape(bool close=false)   { closed=close; }
    void vertex(float x,float y,float z=0,float u=0,float v=0) { verts.push_back({x,y,z,u,v}); }
    void addChild(const PShape& s)    { children.push_back(s); }
    std::string name; // id/name attribute from SVG
    std::vector<int> subpathStarts; // subpath start indices for multi-part fills
    std::vector<Vertex> anchorVerts; // raw anchor points (M/L/C endpoints only) for getVertex()

    PShape* getChild(int i)  { return i<(int)children.size()?&children[i]:nullptr; }
    PShape* getChild(const std::string& n) {
        for(auto& c:children) if(c.name==n) return &c;
        for(auto& c:children){ PShape* r=c.getChild(n); if(r) return r; }
        // Return a static empty shape rather than nullptr to prevent crashes
        static PShape _empty;
        fprintf(stderr,"[PShape] getChild('%s') not found\n", n.c_str());
        return &_empty;
    }
    PShape* getChild(const char* n) { return getChild(std::string(n)); }
    int     getChildCount() const    { return (int)children.size(); }
    PVector getVertex(int i) const   {
        if(i<0||i>=(int)verts.size()) return PVector(0,0,0);
        return PVector(verts[i].x, verts[i].y, verts[i].z);
    }
    void    setVertex(int i, float x, float y) {
        if(i>=0&&i<(int)verts.size()){verts[i].x=x;verts[i].y=y;}
    }
    void    setVertex(int i, float x, float y, float z) {
        if(i>=0&&i<(int)verts.size()){verts[i].x=x;verts[i].y=y;verts[i].z=z;}
    }

    // Bounding box (computed from verts + children)
    float width  = 0;
    float height = 0;
    void computeBounds() {
        float minx=1e9,maxx=-1e9,miny=1e9,maxy=-1e9;
        for(auto& v:verts){minx=std::min(minx,v.x);maxx=std::max(maxx,v.x);miny=std::min(miny,v.y);maxy=std::max(maxy,v.y);}
        for(auto& c:children){const_cast<PShape&>(c).computeBounds();minx=std::min(minx,c.verts.empty()?minx:minx);
            if(!c.verts.empty()){for(auto& v:c.verts){minx=std::min(minx,v.x);maxx=std::max(maxx,v.x);miny=std::min(miny,v.y);maxy=std::max(maxy,v.y);}}}
        width =(maxx>-1e8)?(maxx-minx):0;
        height=(maxy>-1e8)?(maxy-miny):0;
    }
    int     getVertexCount() const    { return (int)verts.size(); }

    void setFill(float r,float g,float b,float a=1)   { fillR=r;fillG=g;fillB=b;fillA=a;hasFill=true; }
    void setStroke(float r,float g,float b,float a=1) { strokeR=r;strokeG=g;strokeB=b;strokeA=a;hasStroke=true; }
    void setStrokeWeight(float w) { strokeW=w; }
    void setVisible(bool v)       { visible=v; }

    void translate(float x,float y,float z=0) { for(auto& v:verts){ v.x+=x;v.y+=y;v.z+=z; } }
    void scale(float s)                        { for(auto& v:verts){ v.x*=s;v.y*=s;v.z*=s; } }
    void scale(float sx, float sy)             { for(auto& v:verts){ v.x*=sx;v.y*=sy; } }

    // Style enable/disable -- controls whether shape uses its own fill/stroke
    // or inherits from the current Processing fill()/stroke() state
    bool styleEnabled = true;
    void disableStyle() { styleEnabled = false; }
    void enableStyle()  { styleEnabled = true;  }
    GLuint texId = 0; // OpenGL texture ID for OBJ material textures
};

PShape  createShape(int kind=-1);
PShape* loadShape(const std::string& path);
PShape  loadShapeVal(const std::string& path); // returns by value for PShape bot = loadShape(...)
void    shape(const PShape& s, float x=0, float y=0);
void    shape(const PShape& s, float x, float y, float w, float h);
inline void shape(const PShape* s, float x=0, float y=0)         { if(s) shape(*s,x,y); }
inline void shape(const PShape* s, float x, float y, float w, float h) { if(s) shape(*s,x,y,w,h); }
void image(PGraphics& pg, float x, float y);
void image(PGraphics& pg, float x, float y, float w, float h);
void    shapeMode(int mode);

// =============================================================================
// PFONT
// =============================================================================

struct PFont {
    static std::vector<std::string> list() {
        std::vector<std::string> fonts;
        std::vector<std::string> dirs = {"data",".","/usr/share/fonts","/usr/local/share/fonts"};
        #ifdef _WIN32
        dirs.push_back("C:/Windows/Fonts");
        #elif defined(__APPLE__)
        dirs.push_back("/Library/Fonts"); dirs.push_back("/System/Library/Fonts");
        #endif
        if(getenv("HOME")){ dirs.push_back(std::string(getenv("HOME"))+"/.fonts"); }
        std::function<void(const std::string&)> scan=[&](const std::string& dir){
            #ifndef _WIN32
            DIR* d=opendir(dir.c_str()); if(!d) return;
            struct dirent* e;
            while((e=readdir(d))!=nullptr){
                std::string nm=e->d_name; if(nm=="."||nm=="..") continue;
                std::string path=dir+"/"+nm;
                if(e->d_type==DT_DIR){ scan(path); continue; }
                if(nm.size()>4){std::string ext=nm.substr(nm.size()-4);
                    if(ext==".ttf"||ext==".otf"||ext==".TTF"||ext==".OTF") fonts.push_back(nm);}
            } closedir(d);
            #else
            WIN32_FIND_DATAA fd; HANDLE h2=FindFirstFileA((dir+"\\*").c_str(),&fd);
            if(h2==INVALID_HANDLE_VALUE) return;
            do { std::string nm=fd.cFileName; if(nm=="."||nm=="..") continue;
                if(fd.dwFileAttributes&FILE_ATTRIBUTE_DIRECTORY){scan(dir+"\\"+nm);continue;}
                if(nm.size()>4){std::string ext=nm.substr(nm.size()-4);
                    if(ext==".ttf"||ext==".otf"||ext==".TTF"||ext==".OTF") fonts.push_back(nm);}
            } while(FindNextFileA(h2,&fd)); FindClose(h2);
            #endif
        };
        for(auto& d:dirs) scan(d);
        std::sort(fonts.begin(),fonts.end());
        fonts.erase(std::unique(fonts.begin(),fonts.end()),fonts.end());
        return fonts;
    }
    std::string name;
    float size = 12;
    bool  loaded = false;

    PFont() = default;
    PFont(const std::string& n, float s) : name(n), size(s), loaded(true) {}
};

PFont loadFont(const std::string& filename);
PFont* createFont(const std::string& name, float size, bool smooth=true);
void  textFont(const PFont& font);
void  textFont(const PFont& font, float size);
inline void textFont(const PFont* font) { if(font) textFont(*font); }
inline void textFont(const PFont* font, float size) { if(font) textFont(*font,size); }

// =============================================================================
// TEXTURE
// =============================================================================

void textureMode(int mode);
void textureWrap(int mode);
void texture(PImage& img);

// =============================================================================
// BUFFERED I/O HELPERS
// =============================================================================

class BufferedReader {
    std::ifstream f;
public:
    explicit BufferedReader(const std::string& path) : f(path) {}
    bool        ready()    const { return f.is_open() && f.good(); }
    std::string readLine()       { std::string l; std::getline(f,l); return f?l:""; }
    void        close()          { f.close(); }
};

class PrintWriter {
    std::ofstream f;
public:
    explicit PrintWriter(const std::string& path) : f(path) {}
    template<typename T> void print(const T& v)   { f << v; }
    template<typename T> void println(const T& v) { f << v << "\n"; }
    void println() { f << "\n"; }
    void flush()   { f.flush(); }
    void close()   { f.close(); }
};

BufferedReader* createReader(const std::string& path);
PrintWriter*    createWriter(const std::string& path);
std::string     selectInput(const std::string& prompt="",  const std::string& filter="");
std::string     selectOutput(const std::string& prompt="", const std::string& filter="");
std::string     selectFolder(const std::string& prompt="");
PImage*         requestImage(const std::string& path);

inline std::ifstream* createInput(const std::string& path)  { return new std::ifstream(path,std::ios::binary); }
inline std::ofstream* createOutput(const std::string& path) { return new std::ofstream(path,std::ios::binary); }
inline bool saveStream(const std::string& path, const std::vector<unsigned char>& data) { return saveBytes(path,data); }
inline void launch(const std::string& path) { system(path.c_str()); }

// Stubs (record/raw are not yet implemented)
inline void beginRecord(const std::string&, const std::string&) {}
inline void endRecord()  {}
inline void beginRaw(const std::string&, const std::string&)    {}
inline void endRaw()     {}

// =============================================================================
// PSHADER  --  GLSL shader wrapper
// =============================================================================

class PShader {
public:
    GLuint program = 0, vert = 0, frag = 0;
    std::string vertSrc, fragSrc;
    bool linked = false;

    PShader() = default;
    PShader(const std::string& v, const std::string& f) : vertSrc(v), fragSrc(f) {}

    static GLuint compileShader(GLenum type, const std::string& src) {
        GLuint s = glCreateShader(type);
        const char* c = src.c_str();
        glShaderSource(s, 1, &c, nullptr);
        glCompileShader(s);
        GLint ok; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
        if (!ok) { char log[512]; glGetShaderInfoLog(s,512,nullptr,log); std::cerr<<"Shader error: "<<log<<"\n"; }
        return s;
    }

    void compile() {
        vert    = compileShader(GL_VERTEX_SHADER,   vertSrc);
        frag    = compileShader(GL_FRAGMENT_SHADER, fragSrc);
        program = glCreateProgram();
        glAttachShader(program,vert); glAttachShader(program,frag);
        glLinkProgram(program);
        GLint ok; glGetProgramiv(program,GL_LINK_STATUS,&ok);
        if (!ok) { char log[512]; glGetProgramInfoLog(program,512,nullptr,log); std::cerr<<"Link error: "<<log<<"\n"; }
        linked = ok;
    }

    void bind()   { if (linked) glUseProgram(program); }
    void unbind() { glUseProgram(0); }

    void set(const std::string& n, float v)                      { glUniform1f(glGetUniformLocation(program,n.c_str()),v); }
    void set(const std::string& n, int v)                        { glUniform1i(glGetUniformLocation(program,n.c_str()),v); }
    void set(const std::string& n, float x, float y)             { glUniform2f(glGetUniformLocation(program,n.c_str()),x,y); }
    void set(const std::string& n, float x, float y, float z)    { glUniform3f(glGetUniformLocation(program,n.c_str()),x,y,z); }
    void set(const std::string& n, float x, float y, float z, float w){ glUniform4f(glGetUniformLocation(program,n.c_str()),x,y,z,w); }

    ~PShader() { if(program)glDeleteProgram(program); if(vert)glDeleteShader(vert); if(frag)glDeleteShader(frag); }
    PShader(const PShader&)            = delete;
    PShader& operator=(const PShader&) = delete;
    PShader(PShader&& o) noexcept
        : program(o.program),vert(o.vert),frag(o.frag),
          vertSrc(o.vertSrc),fragSrc(o.fragSrc),linked(o.linked)
        { o.program=o.vert=o.frag=0; }
};

PShader* loadShader(const std::string& fragPath, const std::string& vertPath="");
void     shader(PShader& s);
void     resetShader();

// =============================================================================
// GENERIC ARRAYLIST / HASHMAP  --  templated Java-style collections
// =============================================================================

template<typename T>
class ArrayList {
public:
    std::vector<T> data;

    ArrayList() = default;

    void add(const T& v)        { data.push_back(v); }
    void add(int i, const T& v) { data.insert(data.begin()+i,v); }
    T&   get(int i)             { return data[i]; }
    const T& get(int i) const   { return data[i]; }
    void set(int i, const T& v) { data[i]=v; }
    void remove(int i)          { data.erase(data.begin()+i); }
    bool remove(const T& v)     { auto it=std::find(data.begin(),data.end(),v); if(it==data.end())return false; data.erase(it); return true; }
    int  size()    const        { return (int)data.size(); }
    bool isEmpty() const        { return data.empty(); }
    void clear()                { data.clear(); }
    bool contains(const T& v) const   { return std::find(data.begin(),data.end(),v)!=data.end(); }
    int  indexOf(const T& v)   const  { auto it=std::find(data.begin(),data.end(),v); return it==data.end()?-1:(int)(it-data.begin()); }
    void sort()                       { std::sort(data.begin(),data.end()); }

    T&       operator[](int i)       { return data[i]; }
    const T& operator[](int i) const { return data[i]; }
    typename std::vector<T>::iterator begin() { return data.begin(); }
    typename std::vector<T>::iterator end()   { return data.end();   }
};

template<typename K, typename V>
class HashMap {
public:
    std::map<K,V> data;

    void put(const K& k, const V& v)         { data[k]=v; }
    V&   get(const K& k)                     { return data[k]; }
    bool containsKey(const K& k)   const     { return data.count(k)>0; }
    bool containsValue(const V& v) const     { for(auto& p:data) if(p.second==v) return true; return false; }
    void remove(const K& k)                  { data.erase(k); }
    int  size()                    const     { return (int)data.size(); }
    bool isEmpty()                 const     { return data.empty(); }
    void clear()                             { data.clear(); }
    std::vector<K> keySet() const            { std::vector<K> r; for(auto& p:data) r.push_back(p.first); return r; }
    std::vector<V> values() const            { std::vector<V> r; for(auto& p:data) r.push_back(p.second); return r; }
    V& operator[](const K& k)               { return data[k]; }
};

// =============================================================================
// TABLEROW  --  single row accessor for Table iteration
// =============================================================================

class TableRow {
public:
    std::vector<std::string>* row  = nullptr;
    std::vector<std::string>* cols = nullptr;

    TableRow() = default;
    TableRow(std::vector<std::string>& r, std::vector<std::string>& c) : row(&r), cols(&c) {}

    std::string getString(int i)                   const { return (row&&i<(int)row->size())?(*row)[i]:""; }
    std::string getString(const std::string& col)  const {
        if (!cols) return "";
        for (int i=0;i<(int)cols->size();i++) if((*cols)[i]==col) return getString(i);
        return "";
    }
    int   getInt(int i)                const { auto s=getString(i);   return s.empty()?0:std::stoi(s); }
    int   getInt(const std::string& c) const { auto s=getString(c);   return s.empty()?0:std::stoi(s); }
    float getFloat(int i)              const { auto s=getString(i);   return s.empty()?0:std::stof(s); }
    float getFloat(const std::string& c)const{ auto s=getString(c);   return s.empty()?0:std::stof(s); }
    void  setString(int i, const std::string& v) { if(row&&i<(int)row->size()) (*row)[i]=v; }
    void  setInt(int i, int v)                   { setString(i, std::to_string(v)); }
    void  setFloat(int i, float v)               { setString(i, std::to_string(v)); }
};

// =============================================================================
// PVECTOR HELPER  --  matches Processing Java's createVector()
// =============================================================================

inline PVector createVector(float x, float y, float z=0) { return PVector(x, y, z); }


// ---------------------------------------------------------------------------
// Mixed-type templates for geometry and math functions.
// Handles calls like rect(int,int,float,float), line(float,int,float,int), etc.
// ---------------------------------------------------------------------------
#include <type_traits>

// line()
template<typename A,typename B,typename C,typename D,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline void line(A x1,B y1,C x2,D y2){ line((float)x1,(float)y1,(float)x2,(float)y2); }
template<typename A,typename B,typename C,typename D,typename E,typename F,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&std::is_arithmetic_v<E>&&std::is_arithmetic_v<F>>>
inline void line(A x1,B y1,C z1,D x2,E y2,F z2){ line((float)x1,(float)y1,(float)z1,(float)x2,(float)y2,(float)z2); }

// rect()
template<typename A,typename B,typename C,typename D,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline void rect(A x,B y,C w,D h2){ rect((float)x,(float)y,(float)w,(float)h2); }
template<typename A,typename B,typename C,typename D,typename E,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&std::is_arithmetic_v<E>>>
inline void rect(A x,B y,C w,D h2,E r){ rect((float)x,(float)y,(float)w,(float)h2,(float)r); }

// ellipse() / circle()
template<typename A,typename B,typename C,typename D,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline void ellipse(A x,B y,C w,D h2){ ellipse((float)x,(float)y,(float)w,(float)h2); }
template<typename A,typename B,typename C,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>>>
inline void circle(A x,B y,C d){ circle((float)x,(float)y,(float)d); }

// point()
template<typename A,typename B,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void point(A x,B y){ point((float)x,(float)y); }
template<typename A,typename B,typename C,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>>>
inline void point(A x,B y,C z){ point((float)x,(float)y,(float)z); }

// triangle()
template<typename A,typename B,typename C,typename D,typename E,typename F,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&std::is_arithmetic_v<E>&&std::is_arithmetic_v<F>>>
inline void triangle(A x1,B y1,C x2,D y2,E x3,F y3){ triangle((float)x1,(float)y1,(float)x2,(float)y2,(float)x3,(float)y3); }

// quad()
template<typename A,typename B,typename C,typename D,typename E,typename F,typename G,typename H,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&std::is_arithmetic_v<E>&&std::is_arithmetic_v<F>&&std::is_arithmetic_v<G>&&std::is_arithmetic_v<H>>>
inline void quad(A x1,B y1,C x2,D y2,E x3,F y3,G x4,H y4){ quad((float)x1,(float)y1,(float)x2,(float)y2,(float)x3,(float)y3,(float)x4,(float)y4); }

// arc()
template<typename A,typename B,typename C,typename D,typename E,typename F,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&std::is_arithmetic_v<E>&&std::is_arithmetic_v<F>>>
inline void arc(A x,B y,C w,D h2,E start,F stop){ arc((float)x,(float)y,(float)w,(float)h2,(float)start,(float)stop); }
template<typename A,typename B,typename C,typename D,typename E,typename F,typename G,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&std::is_arithmetic_v<E>&&std::is_arithmetic_v<F>&&std::is_arithmetic_v<G>>>
inline void arc(A x,B y,C w,D h2,E start,F stop,G mode){ arc((float)x,(float)y,(float)w,(float)h2,(float)start,(float)stop,(int)mode); }

// translate() / rotate() / scale()
template<typename A,typename B,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void translate(A x,B y){ translate((float)x,(float)y); }
template<typename A,typename B,typename C,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>>>
inline void translate(A x,B y,C z){ translate((float)x,(float)y,(float)z); }
template<typename A,
    typename=std::enable_if_t<std::is_arithmetic_v<A>>>
inline void rotate(A a){ rotate((float)a); }
template<typename A,typename B,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void scale(A s1,B s2){ scale((float)s1,(float)s2); }

// vertex()
template<typename A,typename B,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void vertex(A x,B y){ vertex((float)x,(float)y); }
template<typename A,typename B,typename C,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>>>
inline void vertex(A x,B y,C z){ vertex((float)x,(float)y,(float)z); }
template<typename A,typename B,typename C,typename D,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline void vertex(A x,B y,C u,D v2){ vertex((float)x,(float)y,(float)u,(float)v2); }

// text() position
template<typename A,typename B,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void text(const std::string& s,A x,B y){ text(s,(float)x,(float)y); }
template<typename A,typename B,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void text(const char* s,A x,B y){ text(std::string(s),(float)x,(float)y); }

// text with bounding box -- mixed arithmetic types
template<typename A,typename B,typename C,typename D,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline void text(const std::string& s,A x,B y,C w,D h2){ text(s,(float)x,(float)y,(float)w,(float)h2); }
template<typename A,typename B,typename C,typename D,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline void text(const char* s,A x,B y,C w,D h2){ text(std::string(s),(float)x,(float)y,(float)w,(float)h2); }template<typename V,typename A,typename B,
    typename=std::enable_if_t<std::is_arithmetic_v<V>&&std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline void text(V val,A x,B y){ text((float)val,(float)x,(float)y); }
// char overload -- display as character not number
template<typename A,typename B>
inline void text(char c,A x,B y){ text(std::string(1,c),(float)x,(float)y); }
template<typename A,typename B,typename C,typename D>
inline void text(char c,A x,B y,C w,D h2){ text(std::string(1,c),(float)x,(float)y,(float)w,(float)h2); }

// map() -- extremely common source of ambiguity
template<typename V,typename A,typename B,typename C,typename D,
    typename=std::enable_if_t<std::is_arithmetic_v<V>&&std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline float map(V value,A start1,B stop1,C start2,D stop2){
    return map((float)value,(float)start1,(float)stop1,(float)start2,(float)stop2);
}

// constrain()
template<typename V,typename A,typename B,
    typename=std::enable_if_t<std::is_arithmetic_v<V>&&std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>>>
inline float constrain(V val,A lo,B hi){ return constrain((float)val,(float)lo,(float)hi); }

// lerp()
template<typename A,typename B,typename C,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>>>
inline float lerp(A a,B b2,C t){ return lerp((float)a,(float)b2,(float)t); }

// bezier() -- 8 arithmetic params
template<typename A,typename B,typename C,typename D,
         typename E,typename F,typename G,typename H,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&
                               std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&
                               std::is_arithmetic_v<E>&&std::is_arithmetic_v<F>&&
                               std::is_arithmetic_v<G>&&std::is_arithmetic_v<H>>>
inline void bezier(A x1,B y1,C cx1,D cy1,E cx2,F cy2,G x2,H y2){
    bezier((float)x1,(float)y1,(float)cx1,(float)cy1,
           (float)cx2,(float)cy2,(float)x2,(float)y2);
}

// bezierPoint / bezierTangent / curvePoint / curveTangent -- mixed types
template<typename A,typename B,typename C,typename D,typename T,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&
                               std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&
                               std::is_arithmetic_v<T>>>
inline float bezierPoint(A a,B b,C c,D d,T t){
    return bezierPoint((float)a,(float)b,(float)c,(float)d,(float)t);
}
template<typename A,typename B,typename C,typename D,typename T,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&
                               std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&
                               std::is_arithmetic_v<T>>>
inline float bezierTangent(A a,B b,C c,D d,T t){
    return bezierTangent((float)a,(float)b,(float)c,(float)d,(float)t);
}

// curve() -- 8 params
template<typename A,typename B,typename C,typename D,
         typename E,typename F,typename G,typename H,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&
                               std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&
                               std::is_arithmetic_v<E>&&std::is_arithmetic_v<F>&&
                               std::is_arithmetic_v<G>&&std::is_arithmetic_v<H>>>
inline void curve(A x0,B y0,C x1,D y1,E x2,F y2,G x3,H y3){
    curve((float)x0,(float)y0,(float)x1,(float)y1,
          (float)x2,(float)y2,(float)x3,(float)y3);
}

// dist()
template<typename A,typename B,typename C,typename D,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>>>
inline float dist(A x1,B y1,C x2,D y2){ return dist((float)x1,(float)y1,(float)x2,(float)y2); }
template<typename A,typename B,typename C,typename D,typename E,typename F,
    typename=std::enable_if_t<std::is_arithmetic_v<A>&&std::is_arithmetic_v<B>&&std::is_arithmetic_v<C>&&std::is_arithmetic_v<D>&&std::is_arithmetic_v<E>&&std::is_arithmetic_v<F>>>
inline float dist(A x1,B y1,C z1,D x2,E y2,F z2){ return dist((float)x1,(float)y1,(float)z1,(float)x2,(float)y2,(float)z2); }

// image() -- mixed types, value and pointer variants
// image() -- draw a PImage to screen
// All user-facing overloads below; implementation in Processing.cpp
void image(PImage* img, float x, float y);
void image(PImage* img, float x, float y, float w, float h);
inline void image(const PImage& img, float x, float y) {
    // Check if this is actually a PGraphics (needs V-flip)
    const PGraphics* pg2 = dynamic_cast<const PGraphics*>(&img);
    if(pg2) image(const_cast<PGraphics&>(*pg2), x, y);
    else image(const_cast<PImage*>(&img), x, y);
}
inline void image(const PImage& img, float x, float y, float w, float h) {
    const PGraphics* pg2 = dynamic_cast<const PGraphics*>(&img);
    if(pg2) image(const_cast<PGraphics&>(*pg2), x, y, w, h);
    else image(const_cast<PImage*>(&img), x, y, w, h);
}
inline void image(const PImage* img, float x, float y)
    { if(img) image(const_cast<PImage*>(img), x, y); }
inline void image(const PImage* img, float x, float y, float w, float h)
    { if(img) image(const_cast<PImage*>(img), x, y, w, h); }

} // namespace Processing


namespace Processing {
inline void PGraphics::background(float g)                          { ::Processing::background(g); }
inline void PGraphics::background(float r, float g2, float b)       { ::Processing::background(r,g2,b,255); }
inline void PGraphics::background(float r, float g2, float b, float a){ ::Processing::background(r,g2,b,a); }
inline void PGraphics::fill(float g)                                { ::Processing::fill(g); }
inline void PGraphics::fill(float r, float g2, float b)             { ::Processing::fill(r,g2,b); }
inline void PGraphics::fill(float r, float g2, float b, float a)    { ::Processing::fill(r,g2,b,a); }
inline void PGraphics::noFill()                                     { ::Processing::noFill(); }
inline void PGraphics::stroke(float g)                              { ::Processing::stroke(g); }
inline void PGraphics::stroke(float r, float g2, float b)           { ::Processing::stroke(r,g2,b); }
inline void PGraphics::noStroke()                                   { ::Processing::noStroke(); }
inline void PGraphics::strokeWeight(float w)                        { ::Processing::strokeWeight(w); }
inline void PGraphics::ellipse(float x, float y, float w2, float h2){ ::Processing::ellipse(x,y,w2,h2); }
inline void PGraphics::rect(float x, float y, float w2, float h2)   { ::Processing::rect(x,y,w2,h2); }
inline void PGraphics::line(float x1, float y1, float x2, float y2) { ::Processing::line(x1,y1,x2,y2); }
inline void PGraphics::point(float x, float y)                      { ::Processing::point(x,y); }
inline void PGraphics::triangle(float x1,float y1,float x2,float y2,float x3,float y3){ ::Processing::triangle(x1,y1,x2,y2,x3,y3); }
inline void PGraphics::text(const std::string& s, float x, float y) { ::Processing::text(s,x,y); }
inline void PGraphics::translate(float x, float y)                  { ::Processing::translate(x,y); }
inline void PGraphics::rotate(float a)                              { ::Processing::rotate(a); }
inline void PGraphics::scale(float s)                               { ::Processing::scale(s); }
inline void PGraphics::pushMatrix()                                 { ::Processing::pushMatrix(); }
inline void PGraphics::popMatrix()                                  { ::Processing::popMatrix(); }
inline void PGraphics::beginShape()                                 { ::Processing::beginShape(); }
inline void PGraphics::endShape(int mode)                           { ::Processing::endShape(mode); }
inline void PGraphics::vertex(float x, float y)                     { ::Processing::vertex(x,y); }
inline void PGraphics::clear()                                      { ::Processing::clear(); }
} // namespace Processing


inline void link(const std::string& url) {
#ifdef _WIN32
    ShellExecuteA(nullptr,"open",url.c_str(),nullptr,nullptr,SW_SHOWNORMAL);
#elif defined(__APPLE__)
    system(("open "+url).c_str());
#else
    system(("xdg-open "+url+" &").c_str());
#endif
}
inline void link(const char* url){link(std::string(url));}

extern std::string g_sketchName;
