#pragma once
#if __has_include("stb_truetype.h") && !defined(PROCESSING_HAS_STB_TRUETYPE)
#  define PROCESSING_HAS_STB_TRUETYPE 1
#endif
#if PROCESSING_HAS_STB_TRUETYPE
// Include stb_truetype header-only (no implementation) for type definitions
#  ifndef STB_TRUETYPE_IMPLEMENTATION
#    include "stb_truetype.h"
#  endif
#endif
// On Windows, include <windows.h> explicitly before anything else that needs
// Win32 APIs (FindFirstFileA, MessageBoxA, AllocConsole, Sleep, etc.).
// <GL/glew.h> pulls it in transitively but only after GLEW's own includes --
// explicit include here guarantees it arrives before any Win32 API usage.
// NOTE: do NOT define WIN32_LEAN_AND_MEAN here -- GLEW needs wingdi.h which
// WIN32_LEAN_AND_MEAN strips, causing GL type definition failures.
#ifdef _WIN32
#  ifndef NOMINMAX
#    define NOMINMAX
#  endif
#  include <windows.h>
#  include <shellapi.h>
#endif
#ifndef _WIN32
#include <dirent.h>
#endif
#include <functional>
// C++23/26 headers -- guarded by __has_include for maximum portability
#if __has_include(<expected>)
#  include <expected>
#endif
#if __has_include(<flat_map>)
#  include <flat_map>
#endif
#if __has_include(<flat_set>)
#  include <flat_set>
#endif
#if __has_include(<print>)
#  include <print>
#endif
#if __has_include(<inplace_vector>)
#  include <inplace_vector>
#endif
// <coroutine> requires -fcoroutines on GCC; guard it so the header compiles
// without that flag when coroutines aren't needed by the user's sketch.
#if defined(__cpp_impl_coroutine) || defined(__clang__) || defined(_MSC_VER) ||     (defined(__GNUC__) && defined(_GLIBCXX_COROUTINE))
#include <coroutine>
#endif
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
#include <climits>
#include <cstdarg>
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
inline ::std::string operator+(const ::std::string& s, int n)    { return s + ::std::to_string(n); }
inline ::std::string operator+(const ::std::string& s, long n)   { return s + ::std::to_string(n); }
inline ::std::string operator+(const ::std::string& s, size_t n) { return s + ::std::to_string(n); }
inline ::std::string operator+(const ::std::string& s, float n)  { return s + ::std::to_string(n); }
inline ::std::string operator+(const ::std::string& s, double n) { return s + ::std::to_string(n); }
inline ::std::string operator+(const ::std::string& s, char c)   { return s + ::std::string(1, c); }
inline ::std::string operator+(int n,    const ::std::string& s) { return ::std::to_string(n) + s; }
inline ::std::string operator+(long n,   const ::std::string& s) { return ::std::to_string(n) + s; }
inline ::std::string operator+(size_t n, const ::std::string& s) { return ::std::to_string(n) + s; }
inline ::std::string operator+(float n,  const ::std::string& s) { return ::std::to_string(n) + s; }
inline ::std::string operator+(double n, const ::std::string& s) { return ::std::to_string(n) + s; }
inline ::std::string operator+(char c,   const ::std::string& s) { return ::std::string(1, c) + s; }
#include <GL/glew.h>
#include <GLFW/glfw3.h>

// =============================================================================
// WINDOWS MACRO CLEANUP
// =============================================================================
// Root cause: on Windows/MSYS2/MinGW, <GL/glew.h> includes <windows.h> which
// pulls in <wingdi.h>. That header defines macros like OPAQUE, TRANSPARENT,
// DELETE, CLOSE, DIFFERENCE, BLEND, ADD, MULTIPLY, SCREEN, GRAY, INVERT, etc.
// as plain integer preprocessor macros. Later in this file we define Processing
// constants with those same names as "static constexpr int OPAQUE = 3;" -- but
// the macro fires first and turns that into "static constexpr int 2 = 3;" which
// is a syntax error ("expected unqualified-id before numeric constant").
//
// WIN32_LEAN_AND_MEAN doesn't help because GLEW needs wingdi.h for its own GL
// type definitions. The only correct fix is to #undef the offending macros
// after the includes that caused them, before our own definitions use the names.
// Each undef is inside #ifdef so it is a complete no-op on Linux and macOS.
#ifdef OPAQUE
#  undef OPAQUE
#endif
#ifdef TRANSPARENT
#  undef TRANSPARENT
#endif
#ifdef ALTERNATE
#  undef ALTERNATE
#endif
#ifdef WINDING
#  undef WINDING
#endif
#ifdef RELATIVE
#  undef RELATIVE
#endif
#ifdef ABSOLUTE
#  undef ABSOLUTE
#endif
#ifdef CLOSE
#  undef CLOSE
#endif
#ifdef DELETE
#  undef DELETE
#endif
#ifdef DIFFERENCE
#  undef DIFFERENCE
#endif
#ifdef BLEND
#  undef BLEND
#endif
#ifdef ADD
#  undef ADD
#endif
#ifdef SUBTRACT
#  undef SUBTRACT
#endif
#ifdef MULTIPLY
#  undef MULTIPLY
#endif
#ifdef SCREEN
#  undef SCREEN
#endif
#ifdef OVERLAY
#  undef OVERLAY
#endif
#ifdef DARKEST
#  undef DARKEST
#endif
#ifdef LIGHTEST
#  undef LIGHTEST
#endif
#ifdef INVERT
#  undef INVERT
#endif
#ifdef GRAY
#  undef GRAY
#endif
#ifdef CROSS
#  undef CROSS
#endif
#ifdef ARROW
#  undef ARROW
#endif
#ifdef HAND
#  undef HAND
#endif
#ifdef MOVE
#  undef MOVE
#endif
#ifdef WAIT
#  undef WAIT
#endif
#ifdef ERROR
#  undef ERROR
#endif
#ifdef NEAR
#  undef NEAR
#endif
#ifdef FAR
#  undef FAR
#endif

// =============================================================================
// DEBUG OUTPUT -- toggle with -DPROCESSING_DEBUG at compile time
// =============================================================================
// Use PDEBUG(...) anywhere you'd normally reach for a raw fprintf(stderr,...)
// call while investigating something. It's a no-op (compiles to nothing,
// zero runtime cost) unless PROCESSING_DEBUG is defined, so debug prints
// can be left in the source permanently without ever reaching a normal
// build or a user's console -- no more hunting down and deleting stray
// fprintf calls by hand once an investigation is done.
//
// Usage (same argument style as fprintf, always include the trailing \n):
//   PDEBUG("beginDraw: width=%d height=%d\n", width, height);
//
// To actually see the output during local debugging, rebuild with:
//   g++ -DPROCESSING_DEBUG ... (alongside the other -D flags already used)
#ifndef PROCESSING_BUILD_STAMP
    // Fallback for any compile that doesn't go through rebuild-engine.sh
    // (e.g. the IDE's own per-sketch compile, which links the pre-built
    // Processing.o but never defines this itself). Seeing "UNKNOWN" at
    // runtime is itself a useful signal that something bypassed the
    // normal engine-build script.
    #define PROCESSING_BUILD_STAMP "UNKNOWN"
#endif
#ifndef PROCESSING_WEBSITE_URL
    // Fallback only -- the REAL value always comes from
    // config/cppmode.properties's website.base.url, read fresh by
    // rebuild-engine.sh and passed in via -DPROCESSING_WEBSITE_URL at
    // build time. Nothing in this source file ever hardcodes the actual
    // URL string itself; this fallback only exists so a compile that
    // bypasses the script entirely still produces a valid (if generic)
    // message instead of a broken one.
    #define PROCESSING_WEBSITE_URL "https://processing-cpp.github.io"
#endif

#ifdef PROCESSING_DEBUG
    #define PDEBUG(...) fprintf(stderr, "[PDEBUG] " __VA_ARGS__)
#else
    #define PDEBUG(...) do {} while (0)
#endif


namespace Processing {
using namespace std;

// =============================================================================
// PVECTOR  --  2D/3D vector with all standard Processing operations
// =============================================================================

class PVector {
public:
    float x, y, z;

    // Constructors
    PVector()                        : x(0),  y(0),  z(0)  {}
    // Accept any arithmetic type (int, float, double) to match Java's implicit
    // widening -- eliminates narrowing-conversion warnings from expressions like
    // PVector(width/2, height/2) where width/height are int.
    template<typename A, typename B,
        typename = ::std::enable_if_t<::std::is_convertible_v<A,float> && ::std::is_convertible_v<B,float>>>
    PVector(A x, B y)          : x((float)x), y((float)y), z(0)   {}
    template<typename A, typename B, typename C,
        typename = ::std::enable_if_t<::std::is_convertible_v<A,float> && ::std::is_convertible_v<B,float> && ::std::is_convertible_v<C,float>>>
    PVector(A x, B y, C z)     : x((float)x), y((float)y), z((float)z) {}

    // Setters
    PVector& set(float _x, float _y, float _z=0) { x=_x; y=_y; z=_z; return *this; }
    PVector& set(const PVector& v)               { x=v.x; y=v.y; z=v.z; return *this; }
    PVector  copy() const { return PVector(x, y, z); }

    // Magnitude
    float mag()   const { return ::std::sqrt(x*x + y*y + z*z); }
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
    float dist(const PVector& v)                     const { float dx=x-v.x,dy=y-v.y,dz=z-v.z; return ::std::sqrt(dx*dx+dy*dy+dz*dz); }
    static float dist(const PVector& a, const PVector& b) { return a.dist(b); }
    float heading() const { return ::std::atan2(y, x); }
    // heading2D() is @Deprecated in Processing 4 Java but still present as an
    // alias -- keep it here so sketches copied from old examples just work.
    float heading2D() const { return heading(); }
    float angleBetween(const PVector& v) const {
        float m = mag() * v.mag();
        if (m == 0) return 0;
        float c = dot(v) / m;
        c = c < -1 ? -1 : (c > 1 ? 1 : c);
        return ::std::acos(c);
    }
    static float angleBetween(const PVector& a, const PVector& b) { return a.angleBetween(b); }

    // Mutation
    PVector& rotate(float t) { float c=::std::cos(t),s=::std::sin(t),nx=x*c-y*s,ny=x*s+y*c; x=nx; y=ny; return *this; }
    PVector& lerp(const PVector& v, float t) { x+=(v.x-x)*t; y+=(v.y-y)*t; z+=(v.z-z)*t; return *this; }
    PVector& lerp(float _x, float _y, float _z, float t) { x+=(_x-x)*t; y+=(_y-y)*t; z+=(_z-z)*t; return *this; }
    static PVector lerp(const PVector& a, const PVector& b, float t) {
        return PVector(a.x+(b.x-a.x)*t, a.y+(b.y-a.y)*t, a.z+(b.z-a.z)*t);
    }

    // Static constructors
    static PVector fromAngle(float a, float len=1.0f) { return PVector(::std::cos(a)*len, ::std::sin(a)*len); }
    static PVector random2D() {
        float a = static_cast<float>(rand()) / (float)RAND_MAX * 6.28318f;
        return fromAngle(a);
    }
    static PVector random3D() {
        float t = static_cast<float>(rand()) / (float)RAND_MAX * 6.28318f;
        float p = ::std::acos(2.0f * static_cast<float>(rand()) / (float)RAND_MAX - 1.0f);
        return PVector(::std::sin(p)*::std::cos(t), ::std::sin(p)*::std::sin(t), ::std::cos(p));
    }

    ::std::string toString() const {
        ::std::ostringstream ss;
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
        int ri=(int)::std::fmax(0,::std::fmin(255,r));
        int gi=(int)::std::fmax(0,::std::fmin(255,g));
        int bi=(int)::std::fmax(0,::std::fmin(255,b));
        int ai=(int)::std::fmax(0,::std::fmin(255,a));
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
        float mx=::std::fmax(rf_,::std::fmax(gf_,bf_));
        float mn=::std::fmin(rf_,::std::fmin(gf_,bf_));
        float d=mx-mn;
        if (d==0) return 0;
        float h = (mx==rf_) ? (gf_-bf_)/d : (mx==gf_) ? 2.f+(bf_-rf_)/d : 4.f+(rf_-gf_)/d;
        h *= 60.f;
        if (h < 0) h += 360.f;
        return h;
    }
    float saturation() const {
        float mx=::std::fmax(r,::std::fmax(g,b));
        float mn=::std::fmin(r,::std::fmin(g,b));
        return mx==0 ? 0 : ((mx-mn)/mx)*100.f;
    }
    float brightness() const { return ::std::fmax(r,::std::fmax(g,b))/255.f*100.f; }

    static PColor fromHSB(float h, float s, float bv, float a=255) {
        s /= 100.f; bv /= 100.f;
        if (s == 0) { float v=bv*255.f; return PColor(v,v,v,a); }
        float hh=::std::fmod(h,360.f)/60.f;
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
        r=::std::fmax(0,::std::fmin(255,r)); g=::std::fmax(0,::std::fmin(255,g));
        b=::std::fmax(0,::std::fmin(255,b)); a=::std::fmax(0,::std::fmin(255,a));
        return *this;
    }
    PColor multRGB(float s) const { return PColor(r*s, g*s, b*s, a); }

    // Blend modes (return new color)
    static PColor blend(const PColor& src, const PColor& dst) {
        float sa = src.a/255.f;
        return PColor(src.r*sa+dst.r*(1-sa), src.g*sa+dst.g*(1-sa), src.b*sa+dst.b*(1-sa), 255);
    }
    static PColor add(const PColor& a, const PColor& b) {
        return PColor(::std::fmin(255,a.r+b.r), ::std::fmin(255,a.g+b.g), ::std::fmin(255,a.b+b.b), a.a);
    }
    static PColor multiply(const PColor& a, const PColor& b) {
        return PColor((a.r/255.f)*b.r, (a.g/255.f)*b.g, (a.b/255.f)*b.b, a.a);
    }
    static PColor screen(const PColor& a, const PColor& b) {
        auto sc=[](float x,float y){ return 255-(255-x)*(255-y)/255.f; };
        return PColor(sc(a.r,b.r), sc(a.g,b.g), sc(a.b,b.b), a.a);
    }

    float brightness255() const { return ::std::fmax(r, ::std::fmax(g, b)); }

    ::std::string toString() const {
        ::std::ostringstream ss;
        ss << "PColor(" << r << ", " << g << ", " << b << ", " << a << ")";
        return ss.str();
    }
};

// Forward declarations so PColor overloads compile below class definitions
void fill(const PColor& c);
void stroke(const PColor& c);
void background(const PColor& c);
void tint(const PColor& c);

// IMAGE FILTER CONSTANTS
// =============================================================================

static constexpr int THRESHOLD   = 16;
static constexpr int GRAY        = 12;
static constexpr int OPAQUE      = 14;
static constexpr int INVERT      = 13;
static constexpr int POSTERIZE   = 15;
static constexpr int BLUR        = 11;
static constexpr int ERODE       = 17;
static constexpr int DILATE      = 18;

// =============================================================================
// PIMAGE  --  Pixel buffer backed by an OpenGL texture
// =============================================================================

class PImage {
public:
    int  width  = 0;
    int  height = 0;
    ::std::vector<unsigned int> pixels;
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

    // Apply an image filter to all pixels. Mirrors Java's filter(int kind)
    // -- fills in the same per-mode defaults Java uses when no level is
    // given (THRESHOLD: 0.5, BLUR: radius 1; everything else ignores the
    // level entirely). POSTERIZE has no documented Java default for the
    // no-level call; 4 is a reasonable stand-in, not a spec'd value.
    void filter(int mode) {
        switch (mode) {
            case THRESHOLD: filter(mode, 0.5f); return;
            case POSTERIZE: filter(mode, 4.0f); return;
            case BLUR:      filter(mode, 1.0f); return;
            default:        filter(mode, 0.0f); return; // GRAY/OPAQUE/INVERT/ERODE/DILATE take no level
        }
    }

    // Mirrors Java's filter(int kind, float param).
    void filter(int mode, float param) {
        // Luminance conversion (matches the weighting Processing itself
        // uses for GRAY/THRESHOLD, credited in its docs to toxi) --
        // closer to real Processing than a flat (r+g+b)/3 average.
        auto luminance = [](int r, int g, int b) {
            int v = (int)(0.299f*r + 0.587f*g + 0.114f*b);
            return v<0 ? 0 : (v>255 ? 255 : v);
        };

        if (mode == GRAY) {
            for (auto& p : pixels) {
                int r=(p>>16)&0xFF, g=(p>>8)&0xFF, b=p&0xFF, a=(p>>24)&0xFF;
                int gr = luminance(r,g,b);
                p = ((unsigned)a<<24)|((unsigned)gr<<16)|((unsigned)gr<<8)|(unsigned)gr;
            }
        } else if (mode == INVERT) {
            for (auto& p : pixels) {
                int r=(p>>16)&0xFF, g=(p>>8)&0xFF, b=p&0xFF, a=(p>>24)&0xFF;
                p = ((unsigned)a<<24)|((unsigned)(255-r)<<16)|((unsigned)(255-g)<<8)|(unsigned)(255-b);
            }
        } else if (mode == THRESHOLD) {
            int t = (int)(param * 255.0f);
            for (auto& p : pixels) {
                int r=(p>>16)&0xFF, g=(p>>8)&0xFF, b=p&0xFF, a=(p>>24)&0xFF;
                int v = luminance(r,g,b) > t ? 255 : 0;
                p = ((unsigned)a<<24)|((unsigned)v<<16)|((unsigned)v<<8)|(unsigned)v;
            }
        } else if (mode == OPAQUE) {
            for (auto& p : pixels) p |= 0xFF000000u;
        } else if (mode == POSTERIZE) {
            int steps = (int)param;
            if (steps < 2) steps = 2;
            if (steps > 255) steps = 255;
            auto quantize = [steps](int c) {
                int lvl = (c * steps) / 256;
                int out = lvl * 255 / (steps - 1);
                return out<0 ? 0 : (out>255 ? 255 : out);
            };
            for (auto& p : pixels) {
                int r=(p>>16)&0xFF, g=(p>>8)&0xFF, b=p&0xFF, a=(p>>24)&0xFF;
                r=quantize(r); g=quantize(g); b=quantize(b);
                p = ((unsigned)a<<24)|((unsigned)r<<16)|((unsigned)g<<8)|(unsigned)b;
            }
        } else if (mode == BLUR) {
            applyBoxBlurApprox(param > 0 ? param : 1.0f);
        } else if (mode == ERODE) {
            applyMorphology(false);
        } else if (mode == DILATE) {
            applyMorphology(true);
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

    // Duplicate the whole image into a new, independent PImage -- mirrors
    // Java's PImage.copy() (equivalent to Java's now-deprecated no-arg
    // get(), itself defined as get(0,0,width,height)). Returned via the
    // move constructor below (NRVO, or implicit move-on-return for a
    // named local going out of scope) -- the deleted copy constructor
    // just above is never invoked here.
    PImage copy() const {
        PImage out(width, height);
        out.pixels = pixels;
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

private:
    // 3 passes of separable box blur closely approximates a true Gaussian
    // blur (a well-known equivalence) -- this is NOT a port of Processing's
    // own specific stack-blur implementation (credited to Mario Klingemann
    // in Java's docs), so results won't be bit-identical to real Processing,
    // but filter(BLUR) / filter(BLUR, level) match Java's API and produce
    // the same kind of visual softening, scaling with level the same way.
    void applyBoxBlurApprox(float radiusParam) {
        int r = (int)(radiusParam + 0.5f);
        if (r < 1) r = 1;
        for (int pass = 0; pass < 3; pass++) {
            boxBlurPass(true, r);
            boxBlurPass(false, r);
        }
    }

    void boxBlurPass(bool horizontal, int r) {
        ::std::vector<unsigned int> out(pixels.size());
        int w = width, h = height;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                long sa=0, sr=0, sg=0, sb=0; int count=0;
                for (int k = -r; k <= r; k++) {
                    int sx = horizontal ? x+k : x;
                    int sy = horizontal ? y   : y+k;
                    if (sx < 0) sx = 0;
                    if (sx >= w) sx = w-1;
                    if (sy < 0) sy = 0;
                    if (sy >= h) sy = h-1;
                    unsigned int c = pixels[sy*w+sx];
                    sa += (c>>24)&0xFF; sr += (c>>16)&0xFF; sg += (c>>8)&0xFF; sb += c&0xFF;
                    count++;
                }
                unsigned int a=(unsigned)(sa/count), rr=(unsigned)(sr/count),
                             g=(unsigned)(sg/count), b=(unsigned)(sb/count);
                out[y*w+x] = (a<<24)|(rr<<16)|(g<<8)|b;
            }
        }
        pixels = ::std::move(out);
    }

    // ERODE (shrink light areas) / DILATE (grow light areas): replaces each
    // pixel with the min- (erode) or max- (dilate) luminance color among
    // itself and its 4-connected neighbors, using 77/151/28-weighted
    // luminance (the standard 0.299/0.587/0.114 coefficients scaled to
    // 256), matching the structure of real Processing's own dilate()/
    // erode() implementation. Edge pixels clamp to themselves for any
    // missing neighbor rather than wrapping or reading out of bounds.
    void applyMorphology(bool isDilate) {
        ::std::vector<unsigned int> out(pixels.size());
        auto lum = [](unsigned int c) {
            int r=(c>>16)&0xFF, g=(c>>8)&0xFF, b=c&0xFF;
            return 77*r + 151*g + 28*b;
        };
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y*width + x;
                unsigned int best = pixels[idx];
                int bestLum = lum(best);
                const int nx[4] = {x-1, x+1, x,   x};
                const int ny[4] = {y,   y,   y-1, y+1};
                for (int k = 0; k < 4; k++) {
                    if (nx[k]<0 || nx[k]>=width || ny[k]<0 || ny[k]>=height) continue;
                    unsigned int c = pixels[ny[k]*width + nx[k]];
                    int l = lum(c);
                    if (isDilate ? (l > bestLum) : (l < bestLum)) { best = c; bestLum = l; }
                }
                out[idx] = best;
            }
        }
        pixels = ::std::move(out);
    }

public:

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
    PImage(const PImage&) __attribute__((error(
        "E0002: PImage value-style copying is not supported. "
        "Declare PImage* instead of PImage. "
        "See " PROCESSING_WEBSITE_URL "/error/E0002.html"
    )));
    // Prevent PImage img = loadImage(...) -- must use PImage*
    PImage(PImage*) __attribute__((error(
        "E0002: Use PImage* not PImage. Write: PImage* img = loadImage(...);"
    )));
    PImage& operator=(const PImage&) __attribute__((error(
        "E0002: PImage value-style assignment is not supported. "
        "Declare PImage* instead of PImage. "
        "See " PROCESSING_WEBSITE_URL "/error/E0002.html"
    )));

    // Movable
    PImage(PImage&& o) noexcept
        : width(o.width), height(o.height), pixels(::std::move(o.pixels)),
          texID(o.texID), dirty(o.dirty) { o.texID=0; }
};

// =============================================================================
// PGRAPHICS  --  Off-screen render target (framebuffer object)
// =============================================================================

struct color;  // forward declaration -- full definition follows PApplet

class PGraphics : public PImage {
public:
    GLuint fbo = 0; // framebuffer object
    GLuint rbo = 0; // renderbuffer (depth+stencil)
    bool   active = false;

    // Independent per-buffer style state. Real Processing's PGraphics has
    // its OWN fill/stroke/text/etc. settings, completely separate from the
    // main canvas's -- setting fill() on the main canvas must never affect
    // a PGraphics buffer, and vice versa. Previously every PGraphics method
    // forwarded directly to the single global PApplet::g_papplet singleton,
    // meaning style state silently bled between the main canvas and every
    // buffer (e.g. a thick green main-canvas stroke would incorrectly show
    // up on an ellipse drawn inside a buffer that never set its own
    // stroke). beginDraw()/endDraw() now swap PApplet's current style out
    // for this buffer's OWN remembered style, and swap it back after,
    // exactly mirroring how Java's PGraphics keeps independent state.
    struct StyleSnapshot {
        // Defaults match PApplet's own real defaults (white fill, black
        // stroke) -- these are also real Processing's documented
        // beginDraw() defaults ("Sets the default properties"), NOT an
        // arbitrary choice. The earlier version of this struct had
        // fillR=0 (black fill), which is backwards -- every fresh
        // PGraphics buffer with no explicit fill()/stroke() calls should
        // look exactly like a freshly created Processing sketch: white
        // fill, black stroke, weight 1.
        float fillR=1, fillG=1, fillB=1, fillA=1;
        float strokeR=0, strokeG=0, strokeB=0, strokeA=1;
        float strokeW=1;
        bool  doFill=true, doStroke=true, smoothing=true;
        // BUG FIX: these were raw 0/0/0 literals, which silently meant
        // CORNER mode (CORNER=0) for ellipseMode specifically, when real
        // Processing's actual default is CENTER (=3). That made every
        // fresh PGraphics buffer's ellipse() calls interpret their first
        // two arguments as the bounding box's top-left corner instead of
        // its center, shifting every default-mode ellipse by half its
        // width/height toward the bottom-right. rectMode's and
        // imageMode's real defaults ARE actually CORNER (=0), so those
        // two were correct by coincidence -- only currentEllipseMode
        // needed the real CENTER constant.
        // Using literal values, not the CORNER/CENTER named constants:
        // those constants are declared later in this file, after
        // PGraphics's own definition, so they're not in scope yet here.
        // CORNER=0, CENTER=3 (see the static constexpr declarations
        // further down in this file).
        int   currentRectMode=0 /*CORNER*/, currentEllipseMode=3 /*CENTER*/, currentImageMode=0 /*CORNER*/;
        float tintR=1, tintG=1, tintB=1, tintA=1;
        bool  doTint=false;
        int   colorModeVal=0;
        float colorMaxH=255.f, colorMaxS=255.f, colorMaxB=255.f, colorMaxA=255.f;
        float g_textSize=14.0f;
        int   g_textAlignX=0, g_textAlignY=0;
        float g_textLeading=0.0f;
        bool  initialized=false; // false until beginDraw() runs once and sets real Processing defaults
    };
    StyleSnapshot myStyle;       // this buffer's OWN persistent style
    StyleSnapshot _savedMainStyle; // main canvas's style, stashed during beginDraw()..endDraw()

    // Multisampled render target: PGraphics now matches real Processing's
    // default antialiasing (smooth(2) on P2D/P3D) by rendering into a
    // multisample renderbuffer-backed FBO, then resolving (blitting) down
    // into the plain texture-backed FBO that drawPGraphicsRect samples
    // from. Without this, the main canvas's window-level MSAA never
    // applied to off-screen buffers at all.
    GLuint msaaFbo = 0;
    GLuint msaaColorRbo = 0;
    GLuint msaaDepthRbo = 0;
    int    samples = 0; // 0 = no multisampling
    bool   is3D = false; // true if created via createGraphics(w,h,P3D)

    PGraphics() = default;

    PGraphics(int w, int h) : PImage(w, h) {
        // Can't reach PApplet::g_papplet here -- PApplet's complete type
        // isn't available yet at this point in the header (PGraphics is
        // defined before it). Default directly to real Processing's own
        // P2D/P3D default (smooth(2)) rather than reaching across that
        // forward-reference gap. A sketch wanting a different level for
        // its buffers can extend this later if needed.
        samples = 0; // TEMPORARY: forced off to test if MSAA itself is the bug

        // Resolve target: plain, non-multisampled FBO + texture --
        // unchanged from before, just filled via a blit-resolve now.
        glGenFramebuffers(1, &fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        if (texID == 0) glGenTextures(1, &texID);
        glBindTexture(GL_TEXTURE_2D, texID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texID, 0);

        glGenRenderbuffers(1, &rbo);
        glBindRenderbuffer(GL_RENDERBUFFER, rbo);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rbo);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Multisample render target: only created if antialiasing was
        // actually requested. beginDraw() binds THIS one; endDraw() blits
        // it down into the resolve target above.
        if (samples > 0) {
            glGenFramebuffers(1, &msaaFbo);
            glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
            glGenRenderbuffers(1, &msaaColorRbo);
            glBindRenderbuffer(GL_RENDERBUFFER, msaaColorRbo);
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA8, w, h);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, msaaColorRbo);
            glGenRenderbuffers(1, &msaaDepthRbo);
            glBindRenderbuffer(GL_RENDERBUFFER, msaaDepthRbo);
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH24_STENCIL8, w, h);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, msaaDepthRbo);
            GLenum msaaStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (msaaStatus != GL_FRAMEBUFFER_COMPLETE) {
                glDeleteFramebuffers(1, &msaaFbo); msaaFbo = 0;
                glDeleteRenderbuffers(1, &msaaColorRbo); msaaColorRbo = 0;
                glDeleteRenderbuffers(1, &msaaDepthRbo); msaaDepthRbo = 0;
                samples = 0;
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
    }

    PGraphics(int w, int h, bool threeD) : PGraphics(w, h) {
        is3D = threeD;
    }

    GLint savedViewport[4] = {};
    void beginDraw(); // defined after PApplet (needs its complete type for style swap)
    void _endDrawImpl(); // body of endDraw(), out-of-line for the same reason
    void endDraw() { _endDrawImpl(); }

    // Drawing methods forwarded to Processing -- implemented after full decls
    void background(float g); void background(float r, float g, float b); void background(float r, float g, float b, float a);
    void fill(float g); void fill(float r, float g, float b); void fill(float r, float g, float b, float a);
    void noFill(); void stroke(float g); void stroke(float r, float g, float b); void noStroke();
    void strokeWeight(float w);
    void ellipse(float x, float y, float w, float h);
    void rect(float x, float y, float w, float h);
    void rect(float x, float y, float w, float h, float r);
    void line(float x1, float y1, float x2, float y2);
    void point(float x, float y);
    void triangle(float x1,float y1,float x2,float y2,float x3,float y3);
    void text(const ::std::string& s, float x, float y);
    void textSize(float size);
    void textAlign(int alignX);
    void textAlign(int alignX, int alignY);
    void translate(float x, float y, float z);
    void rotateX(float angle);
    void rotateY(float angle);
    void rotateZ(float angle);
    void box(float size);
    void box(float w, float h, float d);
    void sphere(float r);
    void lights();
    void noLights();
    void ambientLight(float r, float g, float b);
    void ambientLight(float r, float g, float b, float x, float y, float z);
    void directionalLight(float r, float g, float b, float nx, float ny, float nz);
    void pointLight(float r, float g, float b, float x, float y, float z);
    void spotLight(float r, float g, float b, float x, float y, float z,
                   float nx, float ny, float nz, float angle, float conc);
    void lightFalloff(float c, float l, float q);
    void lightSpecular(float r, float g, float b);
    void translate(float x, float y); void rotate(float a); void scale(float s);
    void pushMatrix(); void popMatrix();
    void beginShape(); void endShape(int mode=0); void vertex(float x, float y);
    void clear();

    // ── Additional PGraphics methods matching Java Processing ──────────────
    void stroke(float g, float a);
    void stroke(float r, float g, float b, float a);
    void fill(float g, float a);
    void beginShape(int kind);
    void vertex(float x, float y, float z);
    void camera();
    void camera(float ex,float ey,float ez,float cx,float cy,float cz,float ux,float uy,float uz);
    void perspective();
    void perspective(float fov, float aspect, float zNear, float zFar);
    void ortho();
    void ortho(float l, float r, float b, float t, float n, float f);
    void bezier(float x1,float y1,float cx1,float cy1,float cx2,float cy2,float x2,float y2);
    void curve(float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3);
    void bezierVertex(float cx1,float cy1,float cx2,float cy2,float x,float y);
    void curveVertex(float x, float y);
    void image(PImage* img, float x, float y);
    void image(PImage* img, float x, float y, float w, float h);
    void image(PImage* img, float dx1,float dy1,float dx2,float dy2,float sx1,float sy1,float sx2,float sy2);
    void tint(float gray);
    void tint(float gray, float a);
    void tint(float r, float g, float b, float a);
    void noTint();
    void colorMode(int mode, float mx=255);
    void colorMode(int mode, float mH, float mS, float mB, float mA);
    void textLeading(float v);
    float textWidth(const ::std::string& s);
    void push(); void pop();
    void scale(float sx, float sy);
    void resetMatrix();
    void shearX(float a); void shearY(float a);
    void normal(float nx, float ny, float nz);
    void shininess(float s);
    void specular(float r, float g, float b);
    void emissive(float r, float g, float b);
    void ambient(float r, float g, float b);
    void rectMode(int m); void ellipseMode(int m); void imageMode(int m);
    void noSmooth(); void smooth();
    void circle(float x, float y, float d);
    void square(float x, float y, float s);
    void quad(float x1,float y1,float x2,float y2,float x3,float y3,float x4,float y4);
    void arc(float cx,float cy,float w,float h,float sa,float ea);
    void arc(float cx,float cy,float w,float h,float sa,float ea,int mode);
    void blendMode(int mode);
    void clip(float x, float y, float w, float h); void noClip();
    void loadPixels(); void updatePixels();
    void stroke(color c);
    void fill(color c);
    void background(color c);
    color get(int x, int y);
    void set(int x, int y, color c);



    ~PGraphics() {
        // Defensive cleanup: a PGraphics can be destroyed (via delete, or
        // by going out of scope) while its beginDraw() was never matched
        // with an endDraw() -- e.g. "pg = createGraphics(...)" reassigns
        // a pointer, leaking the OLD PGraphics it pointed to if nothing
        // explicitly deleted it first; if something DOES eventually
        // delete it (or CppBuild auto-inserts a delete for exactly this
        // case), the destructor running mid-beginDraw() needs to
        // gracefully unwind that state rather than leaving the matrix
        // stack unbalanced or GL bindings dangling on whatever context
        // outlives this object.
        if (active) {
            PDEBUG("PGraphics::~PGraphics: destroying while still active "
                   "(beginDraw() never matched with endDraw()) -- "
                   "auto-closing now. this=%p\n", (void*)this);
            _endDrawImpl();
        }
        if (msaaFbo)       glDeleteFramebuffers(1, &msaaFbo);
        if (msaaColorRbo)  glDeleteRenderbuffers(1, &msaaColorRbo);
        if (msaaDepthRbo)  glDeleteRenderbuffers(1, &msaaDepthRbo);
        if (fbo) glDeleteFramebuffers(1,  &fbo);
        if (rbo) glDeleteRenderbuffers(1, &rbo);
    }

    PGraphics(const PGraphics&) __attribute__((error(
        "E0001: PGraphics value-style copying is not supported. "
        "Declare PGraphics* instead of PGraphics. "
        "See " PROCESSING_WEBSITE_URL "/error/E0001.html"
    )));
    PGraphics& operator=(const PGraphics&) __attribute__((error(
        "E0001: PGraphics value-style assignment is not supported. "
        "Declare PGraphics* instead of PGraphics. "
        "See " PROCESSING_WEBSITE_URL "/error/E0001.html"
    )));
    // Allow assignment from pointer (PGraphics pg; pg = createGraphics(w,h))
    // [E0001] REMOVED: the legacy "PGraphics pg; pg = createGraphics(...);"
    // value-style assignment is no longer supported. PGraphics owns
    // unique GPU resources (FBO, renderbuffers, texture) -- unlike
    // PShape/PFont, which hold only plain CPU-side data and are safely
    // copyable, copying or reassigning a PGraphics VALUE has no safe
    // meaning. Declare it as a pointer instead:
    //
    //   PGraphics* pg;
    //   pg = createGraphics(w, h);
    //   pg->beginDraw();
    //   ...
    //   pg->endDraw();
    //
    // This explicit compile error is intentional: it tells you exactly
    // what to fix, rather than silently compiling against a value-style
    // declaration that would behave incorrectly or unsafely. The actual
    // URL in the error message below comes from PROCESSING_WEBSITE_URL
    // (ultimately config/cppmode.properties), never hardcoded here.
    PGraphics& operator=(PGraphics* p) __attribute__((error(
        "E0001: PGraphics value-style assignment is not supported. "
        "Declare PGraphics* instead of PGraphics. "
        "See " PROCESSING_WEBSITE_URL "/error/E0001"
    )));
};

// =============================================================================
// =============================================================================


// Aliases: 'width' and 'height' are the canonical Processing names
// width/height always equal what size() set -- never corrupted by WM tile resize.

// =============================================================================
// =============================================================================


// =============================================================================
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

// ---------------------------------------------------------------------------
// Windows-only: raw POD function pointer set by IDE.cpp during static init.
// POD is guaranteed zero-initialized before any constructor runs, so writing
// to it from a static initializer in another translation unit is always safe.
// Processing::run() calls it (if non-null) after setup() to wire all _on* ptrs.
// ---------------------------------------------------------------------------

// =============================================================================
// =============================================================================


// =============================================================================
// =============================================================================


// =============================================================================
// =============================================================================


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
static constexpr int CODED     = 0xFFFF; // matches real Processing's PConstants.CODED exactly (was incorrectly 0xFF, off by a factor of 256)

// Coded keys (keyCode values, Java KeyEvent.VK_*)
static constexpr int UP        = 38;
static constexpr int DOWN      = 40;
static constexpr int LEFT      = 37;    // arrow key AND left mouse button
static constexpr int RIGHT     = 39;    // arrow key AND right mouse button
// Helper trait: matches arithmetic types AND implicit-conversion proxy types
// (e.g. _PSketch::_W for width/height). Used by all API function templates.
template<typename T>
constexpr bool _is_numeric_v =
    ::std::is_arithmetic_v<T> || ::std::is_convertible_v<T, float>;

static constexpr int ALT       = 18;
static constexpr int CONTROL   = 17;
static constexpr int SHIFT     = 16;
static constexpr int HOME_KEY  = 36;  static constexpr int HOME     = 36;
static constexpr int END_KEY   = 35;  static constexpr int END      = 35;
static constexpr int PAGE_UP   = 33;
static constexpr int PAGE_DOWN = 34;
static constexpr int F1_KEY    = 112; static constexpr int F1  = 112;
static constexpr int F2_KEY    = 113; static constexpr int F2  = 113;
static constexpr int F3_KEY    = 114; static constexpr int F3  = 114;
static constexpr int F4_KEY    = 115; static constexpr int F4  = 115;
static constexpr int F5_KEY    = 116; static constexpr int F5  = 116;
static constexpr int F6_KEY    = 117; static constexpr int F6  = 117;
static constexpr int F7_KEY    = 118; static constexpr int F7  = 118;
static constexpr int F8_KEY    = 119; static constexpr int F8  = 119;
static constexpr int F9_KEY    = 120; static constexpr int F9  = 120;
static constexpr int F10_KEY   = 121; static constexpr int F10 = 121;
static constexpr int F11_KEY   = 122; static constexpr int F11 = 122;
static constexpr int F12_KEY   = 123; static constexpr int F12 = 123;

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
static constexpr int HSB  = 3;
#define ARGB 3  /* createImage(w,h,ARGB) */

// Shape / rect / ellipse modes
static constexpr int CORNER      = 0;
static constexpr int CORNERS     = 1;
static constexpr int RADIUS      = 2;

// Stroke caps and joins
static constexpr int ROUND   = 2;
static constexpr int SQUARE  = 1;
static constexpr int PROJECT = 4;
static constexpr int MITER   = 8;
static constexpr int BEVEL   = 32;

// beginShape() kinds
static constexpr int POINTS         = 3;
static constexpr int LINES          = 5;
static constexpr int TRIANGLES      = 9;
static constexpr int TRIANGLE_FAN   = 11;
static constexpr int TRIANGLE_STRIP = 10;
static constexpr int QUADS          = 17;
static constexpr int QUAD_STRIP     = 18;
static constexpr int CLOSE          = 2;
// Arc modes
static constexpr int OPEN           = 1;
static constexpr int CHORD          = 2;
static constexpr int PIE            = 3;

// Text alignment
// Text alignment internal constants
static constexpr int LEFT_ALIGN   = 20;
static constexpr int RIGHT_ALIGN  = 21;
static constexpr int TOP_ALIGN    = 22;
static constexpr int BOTTOM_ALIGN = 23;
static constexpr int BASELINE     = 0;     // Processing Java value
static constexpr int CENTER_ALIGN = 25;
// Processing Java textAlign vertical aliases
static constexpr int TOP    = 101;
static constexpr int BOTTOM = 102;

// Blend modes
static constexpr int BLEND      = 1;
static constexpr int ADD        = 2;
static constexpr int SUBTRACT   = 4;
static constexpr int MULTIPLY   = 128;
static constexpr int SCREEN     = 256;
static constexpr int DARKEST    = 8;
static constexpr int LIGHTEST   = 16;
static constexpr int DIFFERENCE = 32;
static constexpr int EXCLUSION  = 64;
static constexpr int OVERLAY    = 512;
static constexpr int HARD_LIGHT = 1024;
static constexpr int SOFT_LIGHT = 2048;
static constexpr int DODGE      = 4096;
static constexpr int BURN       = 8192;
static constexpr int REPLACE    = 0;
// Boolean aliases
#ifndef TRUE
#define TRUE  true
#define FALSE false
#endif

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
    using namespace ::std::chrono;
    static auto start = steady_clock::now();
    return static_cast<unsigned long>(duration_cast<milliseconds>(steady_clock::now()-start).count());
}
inline int second() { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_sec;      }
inline int minute() { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_min;      }
inline int hour()   { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_hour;     }
inline int day()    { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_mday;     }
inline int month()  { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_mon+1;    }
inline int year()   { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_year+1900;}

// 'color' is a packed 32-bit ARGB integer, just like in Processing Java.
// Constructors respect the current colorMode setting (see colorMode()).
struct color {
    unsigned int value;

    color() : value(0xFF000000) {}         // default: opaque black
    color(unsigned int v) : value(v) {}  // allows color pix = img->get(x,y)

    // Defined in Processing.cpp so the colorMode globals are accessible.
    //
    // 3-arg and 4-arg int overloads were intentionally removed: they were
    // pure pass-throughs that cast to float and called the exact same
    // _makeColor() the float overloads call directly, so removing them
    // changes no runtime behavior -- it only removes the possibility of
    // ambiguous overload resolution on mixed-type calls like
    // color(map(...), map(...), 50).
    //
    // 1-arg and 2-arg int overloads are KEPT: color(int) would otherwise be
    // ambiguous between color(float) (grayscale) and color(unsigned int)
    // (raw packed ARGB pixel value, e.g. color pix = img->get(x,y)) -- two
    // different, equally-valid implicit conversions for the same int
    // literal. color(int gray) as an EXACT match resolves that in favor
    // of "grayscale", matching real Processing semantics.
    color(int gray);
    color(int gray, int a);
    // int 3/4-arg overloads: these were previously removed to avoid overload
    // resolution ambiguity, but are restored because:
    // (a) int->float promotion makes int args exact-match int overloads rather
    //     than ambiguously matching float ones, so no ambiguity occurs in practice
    // (b) without them, "color c(r, g, b)" with int r,g,b stays as paren-init
    //     (color is in INITIALIZER_LIST_AMBIGUOUS_TYPES) but then fails to compile
    //     since color(float,float,float) requires narrowing conversion of int->float
    //     in direct-init context.
    color(int r, int g, int b);
    color(int r, int g, int b, int a);
    color(float gray);
    color(float gray, float a);
    color(float r, float g, float b);
    color(float r, float g, float b, float a);
    // Mixed numeric args: any combination of int/float
    template<typename R, typename G, typename B>
    color(R r, G g, B b) : color((float)r, (float)g, (float)b) {}
    template<typename R, typename G, typename B, typename A>
    color(R r, G g, B b, A a) : color((float)r, (float)g, (float)b, (float)a) {}
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

// Pack raw 0-255 RGBA without colorMode (for internal use)

inline color colorVal(int r, int g, int b, int a=255) {
    // Clamp to [0,255] -- don't wrap, which would cause dark artifacts
    // when noise()*255 or other values slightly exceed 255
    auto clamp8=[](int v){return v<0?0:v>255?255:v;};
    return color((unsigned int)(((clamp8(a))<<24)|((clamp8(r))<<16)|((clamp8(g))<<8)|(clamp8(b))));
}
// color() free functions -- match Processing Java
inline color color_(int gray)                    { return colorVal(gray,gray,gray,255); }
inline color color_(int gray, int a)             { return colorVal(gray,gray,gray,a); }
inline color color_(int r, int g, int b)         { return colorVal(r,g,b,255); }
inline color color_(int r, int g, int b, int a)  { return colorVal(r,g,b,a); }
inline color color_(float gray)                  { return colorVal((int)gray,(int)gray,(int)gray,255); }
inline color color_(float gray, float a)         { return colorVal((int)gray,(int)gray,(int)gray,(int)a); }
inline color color_(float r, float g, float b)   { return colorVal((int)r,(int)g,(int)b,255); }
inline color color_(float r, float g, float b, float a){ return colorVal((int)r,(int)g,(int)b,(int)a); }


// Color component extractors

// =============================================================================
// PRINT / OUTPUT
// =============================================================================

template<typename T> inline void print(const T& v)    { ::std::cout << v; ::std::cout.flush(); }
template<typename T> inline void println(const T& v)  { ::std::cout << v << "\n"; ::std::cout.flush(); }
inline                       void println()             { ::std::cout << "\n"; ::std::cout.flush(); }
template<typename T> inline void printArray(const ::std::vector<T>& a) {
    for (size_t i=0; i<a.size(); i++) ::std::cout << "[" << i << "] " << a[i] << "\n";
}

// =============================================================================
// STRING UTILITIES
// =============================================================================

inline ::std::string str(int v)   { return ::std::to_string(v); }
inline ::std::string str(float v) { return ::std::to_string(v); }
// double overload -- without this, any expression that promotes to double
// (e.g. a literal like 180.0 or 3.14159 anywhere in the expression, or a
// <cmath> function like atan2()/sqrt() that returns double) makes str(...)
// ambiguous: double doesn't exactly match int/float/bool/char, and more
// than one of those is an equally-good implicit conversion target, so
// overload resolution can't pick one. ::std::to_string(double) uses the
// same default 6-decimal-place formatting as ::std::to_string(float), so
// this doesn't introduce any visible precision/formatting mismatch with
// str(float).
inline ::std::string str(double v) { return ::std::to_string(v); }
inline ::std::string str(bool v)  { return v ? "true" : "false"; }
inline ::std::string str(char v)  { return ::std::string(1, v); }
// char16_t overload -- matches Java's actual "char" type for key/keyTyped
// etc., which is 16-bit (UTF-16). For values within the basic ASCII/
// Latin-1 range (which covers everything our engine's keyboard handling
// actually produces), this prints the single character exactly like
// str(char) does. CODED (0xFFFF) itself isn't really meant to be
// printed as text at all (matching real Processing -- see PConstants.
// CODED's own doc comment, "key will be CODED"), so this just produces
// SOME single-character output for it rather than special-casing it.
inline ::std::string str(char16_t v)  { return ::std::string(1, (char)v); }
inline bool        toBoolean(const ::std::string& s)  { return s=="true"||s=="1"||s=="yes"; }
inline int         toInt(const ::std::string& s)      { return ::std::stoi(s); }
inline float       toFloat(const ::std::string& s)    { try { return ::std::stof(s); } catch (...) { return 0.0f; } }
inline char        toChar(int v)                    { return static_cast<char>(v); }
// randomGaussian -- Box-Muller transform
inline float randomGaussian() {
    static bool hasSpare = false;
    static float spare;
    if (hasSpare) { hasSpare = false; return spare; }
    float u, v, s;
    do { u = (rand()/(float)RAND_MAX)*2.f-1.f; v = (rand()/(float)RAND_MAX)*2.f-1.f; s=u*u+v*v; } while(s>=1.f||s==0.f);
    float mul = ::std::sqrt(-2.f*::std::log(s)/s);
    spare = v*mul; hasSpare = true;
    return u*mul;
}
inline int   parseInt(const ::std::string& s)   { try { return ::std::stoi(s); } catch(...) { return 0; } }
inline float parseFloat(const ::std::string& s) { try { return ::std::stof(s); } catch(...) { return 0.f; } }
inline bool  parseBoolean(const ::std::string& s){ return s=="true"||s=="True"||s=="TRUE"||s=="1"; }
inline ::std::string toUpperCase(::std::string s) { for(auto& c:s) c=::std::toupper((unsigned char)c); return s; }
inline ::std::string toLowerCase(::std::string s) { for(auto& c:s) c=::std::tolower((unsigned char)c); return s; }
inline ::std::string trim(const ::std::string& s) {
    size_t a=s.find_first_not_of(" \t\n\r"), b=s.find_last_not_of(" \t\n\r");
    return a==::std::string::npos ? "" : s.substr(a, b-a+1);
}
inline ::std::vector<::std::string> split(const ::std::string& s, char d) {
    ::std::vector<::std::string> o; ::std::stringstream ss(s); ::std::string t;
    while (::std::getline(ss, t, d)) o.push_back(t);
    return o;
}
inline ::std::vector<::std::string> splitTokens(const ::std::string& s, const ::std::string& delims) {
    ::std::vector<::std::string> o; ::std::string cur;
    for (char c:s) {
        if (delims.find(c)!=::std::string::npos) { if(!cur.empty()){o.push_back(cur);cur.clear();} }
        else cur+=c;
    }
    if (!cur.empty()) o.push_back(cur);
    return o;
}
inline ::std::vector<::std::string> splitTokens(const ::std::string& s) {
    return splitTokens(s, " \t\n\r\f");
}
inline ::std::string join(const ::std::vector<::std::string>& v, const ::std::string& sep) {
    ::std::string o;
    for (size_t i=0; i<v.size(); i++) { if(i) o+=sep; o+=v[i]; }
    return o;
}

// Number formatting -- mirrors Processing (Java)'s nf()/nfc()/nfp()/nfs() family,
// including the int[]/float[] array overloads. Sign is always kept outside any
// zero-padding (Processing pads the magnitude, not the raw formatted string).

// -- nf() ---------------------------------------------------------------
inline ::std::string nf(int v) { return ::std::to_string(v); }
inline ::std::string nf(int v, int minDigits) {
    bool neg = v < 0;
    long mag = neg ? -static_cast<long>(v) : static_cast<long>(v);
    ::std::string s = ::std::to_string(mag);
    while ((int)s.size() < minDigits) s = "0" + s;
    return neg ? ("-" + s) : s;
}
inline ::std::string nf(float v, int digits) { ::std::ostringstream ss; ss.precision(digits); ss<<::std::fixed<<v; return ss.str(); } // non-standard convenience overload (kept for back-compat)
inline ::std::string nf(float v, int left, int right) {
    bool neg = v < 0;
    float mag = neg ? -v : v;
    ::std::ostringstream ss; ss<<::std::fixed<<::std::setprecision(right)<<mag;
    ::std::string s=ss.str(); size_t dot=s.find('.');
    size_t intLen=(dot==::std::string::npos)?s.size():dot;
    while((int)intLen<left){s="0"+s;intLen++;}
    return neg ? ("-" + s) : s;
}
inline ::std::vector<::std::string> nf(const ::std::vector<int>& nums) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (int n : nums) out.push_back(nf(n));
    return out;
}
inline ::std::vector<::std::string> nf(const ::std::vector<int>& nums, int digits) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (int n : nums) out.push_back(nf(n, digits));
    return out;
}
inline ::std::vector<::std::string> nf(const ::std::vector<float>& nums, int left, int right) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (float n : nums) out.push_back(nf(n, left, right));
    return out;
}

// -- nfc() : comma-grouped ------------------------------------------------
inline ::std::string nfc(int v) {
    bool neg = v < 0;
    long mag = neg ? -static_cast<long>(v) : static_cast<long>(v);
    ::std::string s = ::std::to_string(mag);
    for (int i = (int)s.size() - 3; i > 0; i -= 3) s.insert(i, ",");
    return neg ? ("-" + s) : s;
}
inline ::std::string nfc(float v, int right) {
    bool neg = v < 0;
    float mag = neg ? -v : v;
    ::std::ostringstream ss; ss.precision(right); ss<<::std::fixed<<mag;
    ::std::string s=ss.str(); int dot=(int)s.find('.'); if(dot<0)dot=(int)s.size();
    for(int i=dot-3;i>0;i-=3)s.insert(i,",");
    return neg ? ("-" + s) : s;
}
inline ::std::vector<::std::string> nfc(const ::std::vector<int>& nums) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (int n : nums) out.push_back(nfc(n));
    return out;
}
inline ::std::vector<::std::string> nfc(const ::std::vector<float>& nums, int right) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (float n : nums) out.push_back(nfc(n, right));
    return out;
}

// -- nfp() : '+' prefix for non-negative ----------------------------------
inline ::std::string nfp(int v)                       { return (v>=0?"+":"") + nf(v); }
inline ::std::string nfp(int v, int digits)            { return (v>=0?"+":"") + nf(v,digits); }
inline ::std::string nfp(float v, int left, int right) { return (v>=0?"+":"") + nf(v,left,right); }
inline ::std::vector<::std::string> nfp(const ::std::vector<int>& nums) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (int n : nums) out.push_back(nfp(n));
    return out;
}
inline ::std::vector<::std::string> nfp(const ::std::vector<int>& nums, int digits) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (int n : nums) out.push_back(nfp(n, digits));
    return out;
}
inline ::std::vector<::std::string> nfp(const ::std::vector<float>& nums, int left, int right) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (float n : nums) out.push_back(nfp(n, left, right));
    return out;
}

// -- nfs() : ' ' prefix for non-negative (aligns with '-' of negatives) ---
inline ::std::string nfs(int v)                       { return (v>=0?" ":"") + nf(v); }
inline ::std::string nfs(int v, int digits)            { return (v>=0?" ":"") + nf(v,digits); }
inline ::std::string nfs(float v, int left, int right) { return (v>=0?" ":"") + nf(v,left,right); }
inline ::std::vector<::std::string> nfs(const ::std::vector<int>& nums) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (int n : nums) out.push_back(nfs(n));
    return out;
}
inline ::std::vector<::std::string> nfs(const ::std::vector<int>& nums, int digits) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (int n : nums) out.push_back(nfs(n, digits));
    return out;
}
inline ::std::vector<::std::string> nfs(const ::std::vector<float>& nums, int left, int right) {
    ::std::vector<::std::string> out; out.reserve(nums.size());
    for (float n : nums) out.push_back(nfs(n, left, right));
    return out;
}
inline ::std::string hex(int v)                { ::std::ostringstream ss; ss<<::std::uppercase<<::std::hex<<v; return ss.str(); }
inline ::std::string hex(int v, int digits)    { ::std::ostringstream ss; ss<<::std::uppercase<<::std::hex<<::std::setw(digits)<<::std::setfill('0')<<v; return ss.str(); }
inline ::std::string binary(int v)             { ::std::string s; for(int i=31;i>=0;i--) s+=((v>>i)&1)?'1':'0'; return s; }
inline int         unhex(const ::std::string& s)   { return ::std::stoi(s,nullptr,16); }
inline int         unbinary(const ::std::string& s){ return ::std::stoi(s,nullptr,2);  }

// Regex helpers
inline ::std::vector<::std::string> match(const ::std::string& s, const ::std::string& pat) {
    ::std::vector<::std::string> out; ::std::smatch m; ::std::regex re(pat);
    if (::std::regex_search(s,m,re)) for (auto& x:m) out.push_back(x.str());
    return out;
}
inline ::std::vector<::std::vector<::std::string>> matchAll(const ::std::string& s, const ::std::string& pat) {
    ::std::vector<::std::vector<::std::string>> out; ::std::regex re(pat);
    auto it=::std::sregex_iterator(s.begin(),s.end(),re), end=::std::sregex_iterator();
    for(;it!=end;++it){ ::std::vector<::std::string> row; for(auto& x:*it) row.push_back(x.str()); out.push_back(row); }
    return out;
}

// =============================================================================
// FILE I/O
// =============================================================================

inline ::std::vector<::std::string>     loadStrings(const ::std::string& path) {
    ::std::vector<::std::string> lines; ::std::ifstream f(path); ::std::string l;
    while (::std::getline(f,l)) lines.push_back(l);
    return lines;
}
inline bool saveStrings(const ::std::string& path, const ::std::vector<::std::string>& lines) {
    ::std::ofstream f(path); if (!f) return false;
    for (auto& l:lines) f<<l<<"\n";
    return true;
}
inline ::std::vector<unsigned char> loadBytes(const ::std::string& path) {
    ::std::ifstream f(path,::std::ios::binary);
    return ::std::vector<unsigned char>((::std::istreambuf_iterator<char>(f)),::std::istreambuf_iterator<char>());
}
inline bool saveBytes(const ::std::string& path, const ::std::vector<unsigned char>& data) {
    ::std::ofstream f(path,::std::ios::binary); if (!f) return false;
    f.write(reinterpret_cast<const char*>(data.data()),data.size());
    return true;
}

// =============================================================================
// USER CALLBACKS  --  the sketch must define at minimum setup() and draw()
// =============================================================================


// =============================================================================
// ENVIRONMENT FUNCTIONS
// =============================================================================

// ---------------------------------------------------------------------------
// Clipboard, input state, timing, window icon  (IDE-facing helpers)
// ---------------------------------------------------------------------------


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
static constexpr int KEY_0=48; static constexpr int KEY_1=49; static constexpr int KEY_2=50; static constexpr int KEY_3=51; static constexpr int KEY_4=52;
static constexpr int KEY_5=53; static constexpr int KEY_6=54; static constexpr int KEY_7=55; static constexpr int KEY_8=56; static constexpr int KEY_9=57;
static constexpr int SPACE=32;

static constexpr int PERIOD_KEY = 46;
static constexpr int SLASH_KEY  = 47;
static constexpr int EQUAL_KEY  = 61;
static constexpr int MINUS_KEY  = 45;



// =============================================================================
// STYLE STACK  --  save/restore fill, stroke, transform state
// =============================================================================


// =============================================================================
// COLOR MODE
// =============================================================================

// colorMode(RGB)               -- all channels [0..255]
// colorMode(HSB, 360, 100, 100) -- hue [0..360], sat/bri [0..100]

// =============================================================================
// BACKGROUND / CLEAR
// =============================================================================



// =============================================================================
// FILL / STROKE
// =============================================================================

struct PApplet; // forward decl for template bodies
inline void fill(color c, int a) { fill(c, (float)a); }



// ── Forward declarations for template helpers ─────────────────────────────────
// These break infinite recursion in the arithmetic-overload templates below.
// Implementations are after struct PApplet (which defines g_papplet).
struct PApplet;
namespace _api {
    void size(int,int);
    void size(int,int,int);
    void fullScreen();
    void fullScreen(int);
    void line(float,float,float,float);
    void line(float,float,float,float,float,float);
    void rect(float,float,float,float);
    void rect(float,float,float,float,float);
    void ellipse(float,float,float,float);
    void circle(float,float,float);
    void point(float,float);
    void point(float,float,float);
    void triangle(float,float,float,float,float,float);
    void quad(float,float,float,float,float,float,float,float);
    void arc(float,float,float,float,float,float);
    void arc(float,float,float,float,float,float,int);
    void translate(float,float);
    void translate(float,float,float);
    void scale(float,float);
    void vertex(float,float);
    void vertex(float,float,float);
    void vertex(float,float,float,float);
    void bezier(float,float,float,float,float,float,float,float);
    void curve(float,float,float,float,float,float,float,float);
    void text(float,float,float);
    void text(const ::std::string&,float,float);
    void text(const ::std::string&,float,float,float,float);
    float map(float,float,float,float,float);
    float constrain(float,float,float);
    float lerp(float,float,float);
    void fill(float);
    void fill(float,float);
    void fill(float,float,float);
    void fill(float,float,float,float);
    void stroke(float);
    void stroke(float,float);
    void stroke(float,float,float);
    void stroke(float,float,float,float);
    void background(float);
    void background(float,float);
    void background(float,float,float);
    void background(float,float,float,float);
    void tint(float);
    void tint(float,float);
    void tint(float,float,float);
    void tint(float,float,float,float);
    void strokeWeight(float);
    void rotate(float);
}

// int overloads
// ---------------------------------------------------------------------------
// Mixed-type overloads for fill, stroke, background, tint.
// These templates accept any arithmetic type (int, float, double, etc.)
// and forward to the canonical float versions, eliminating all ambiguity
// from mixed calls like fill(int, int, float) or stroke(float, int, float).
// ---------------------------------------------------------------------------
template<typename A, typename B,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void fill(A gray, B a)
    { _api::fill((float)gray,(float)a); }

template<typename A, typename B, typename C,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
inline void fill(A r, B g, C b)
    { _api::fill((float)r,(float)g,(float)b); }

template<typename A, typename B, typename C, typename D,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void fill(A r, B g, C b, D a)
    { _api::fill((float)r,(float)g,(float)b,(float)a); }

template<typename A,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
inline void stroke(A gray)
    { _api::stroke((float)gray); }

template<typename A, typename B,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void stroke(A gray, B a)
    { _api::stroke((float)gray,(float)a); }

template<typename A, typename B, typename C,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
inline void stroke(A r, B g, C b)
    { _api::stroke((float)r,(float)g,(float)b); }

template<typename A, typename B, typename C, typename D,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void stroke(A r, B g, C b, D a)
    { _api::stroke((float)r,(float)g,(float)b,(float)a); }

template<typename A,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
inline void strokeWeight(A w)
    { _api::strokeWeight((float)w); }

template<typename A,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
inline void fill(A gray)
    { _api::fill((float)gray); }

template<typename A,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
inline void background(A gray)
    { _api::background((float)gray); }

template<typename A, typename B,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void background(A gray, B a)
    { _api::background((float)gray,(float)a); }

template<typename A, typename B, typename C,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
inline void background(A r, B g, C b)
    { _api::background((float)r,(float)g,(float)b); }

template<typename A, typename B, typename C, typename D,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void background(A r, B g, C b, D a)
    { _api::background((float)r,(float)g,(float)b,(float)a); }


// Integer-only templates: these cast int args to float and call the float overloads.
// Constrained to non-float types so float calls go directly to the float overload
// above and don't recurse back into the template.
template<typename A, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
inline void tint(A gray)
    { _api::tint((float)gray); }

template<typename A, typename B,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void tint(A gray, B a)
    { _api::tint((float)gray,(float)a); }

template<typename A, typename B, typename C,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
inline void tint(A r, B g, C b)
    { _api::tint((float)r,(float)g,(float)b); }

template<typename A, typename B, typename C, typename D,
         typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void tint(A r, B g, C b, D a)
    { _api::tint((float)r,(float)g,(float)b,(float)a); }

// =============================================================================
// SHAPE ATTRIBUTES
// =============================================================================


// =============================================================================
// 2D PRIMITIVES
// =============================================================================


// Mixed-type templates are in the comprehensive block at end of namespace

// =============================================================================
// 3D PRIMITIVES
// =============================================================================


// =============================================================================
// CUSTOM SHAPES  --  beginShape / vertex / endShape
// =============================================================================


// =============================================================================
// MATRIX TRANSFORMS
// =============================================================================


// =============================================================================
// CAMERA  --  3D view projection
// =============================================================================


// =============================================================================
// LIGHTS
// =============================================================================


// Material properties

// =============================================================================
// TEXT
// =============================================================================


// =============================================================================
// IMAGE FUNCTIONS
// =============================================================================



PImage     getRegion(int x, int y, int w, int h);

// =============================================================================
// BLEND / CLIP
// =============================================================================


// =============================================================================
// SAVE / THREADING
// =============================================================================

inline void thread(::std::function<void()> fn) { ::std::thread(fn).detach(); }
inline void delay(int ms) { ::std::this_thread::sleep_for(::std::chrono::milliseconds(ms)); }

// =============================================================================
// ENTRY POINT
// =============================================================================


// Opens a console window for stderr output on Windows when --debug flag is passed.
// Call this before run() -- see main.cpp.

// =============================================================================
// JSON
// =============================================================================

struct JSONValue;
using JSONObject = ::std::map<::std::string, JSONValue>;
using JSONArray  = ::std::vector<JSONValue>;

struct JSONValue {
    enum Type { NULL_T,BOOL_T,INT_T,FLOAT_T,STRING_T,ARRAY_T,OBJECT_T } type=NULL_T;
    bool b=false; double n=0; ::std::string s;
    ::std::shared_ptr<JSONArray>  arr;
    ::std::shared_ptr<JSONObject> obj;

    JSONValue() = default;
    JSONValue(bool v)               : type(BOOL_T),   b(v)  {}
    JSONValue(int v)                : type(INT_T),     n(v)  {}
    JSONValue(double v)             : type(FLOAT_T),   n(v)  {}
    JSONValue(const ::std::string& v) : type(STRING_T),  s(v)  {}
    JSONValue(const char* v)        : type(STRING_T),  s(v)  {}
    JSONValue(JSONArray v)          : type(ARRAY_T),   arr(::std::make_shared<JSONArray>(v))  {}
    JSONValue(JSONObject v)         : type(OBJECT_T),  obj(::std::make_shared<JSONObject>(v)) {}

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
    ::std::string getString() const { return s;       }
    JSONArray&  getArray()        { return *arr;    }
    JSONObject& getObject()       { return *obj;    }
    const JSONArray&  getArray()  const { return *arr; }
    const JSONObject& getObject() const { return *obj; }

    JSONValue& operator[](const ::std::string& k) { return (*obj)[k]; }
    JSONValue& operator[](int i)                { return (*arr)[i]; }
    int  size()             const { if(isArray())return (int)arr->size(); if(isObject())return (int)obj->size(); return 0; }
    bool hasKey(const ::std::string& k) const     { return isObject() && obj->count(k); }
};


// =============================================================================
// XML
// =============================================================================

struct XML {
    ::std::string name, content;
    ::std::map<::std::string,::std::string> attributes;
    ::std::vector<XML> children;

    XML() = default;
    explicit XML(const ::std::string& n) : name(n) {}

    ::std::string getName()    const { return name;    }
    ::std::string getContent() const { return content; }

    bool        hasAttribute(const ::std::string& k)                     const { return attributes.count(k)>0; }
    ::std::string getAttribute(const ::std::string& k, const ::std::string& def="") const {
        auto it = attributes.find(k);
        return it != attributes.end() ? it->second : def;
    }
    int   getAttributeInt(const ::std::string& k, int def=0)     const { return hasAttribute(k) ? ::std::stoi(attributes.at(k)) : def; }
    float getAttributeFloat(const ::std::string& k, float def=0) const { return hasAttribute(k) ? ::std::stof(attributes.at(k)) : def; }

    void setAttribute(const ::std::string& k, const ::std::string& v) { attributes[k]=v; }
    void setContent(const ::std::string& c) { content=c; }

    XML*              addChild(const ::std::string& n)  { children.push_back(XML(n)); return &children.back(); }
    XML*              getChild(int i)                  { return i<(int)children.size()?&children[i]:nullptr; }
    XML*              getChild(const ::std::string& n)   { for(auto& c:children) if(c.name==n) return &c; return nullptr; }
    int               getChildCount()           const  { return (int)children.size(); }
    ::std::vector<XML*> getChildren(const ::std::string& n){ ::std::vector<XML*> r; for(auto& c:children) if(c.name==n) r.push_back(&c); return r; }

    ::std::string toString(int indent=0) const;
};


// =============================================================================
// TABLE  --  CSV-style data with named columns
// =============================================================================

class Table {
public:
    ::std::vector<::std::string>              columns;
    ::std::vector<::std::vector<::std::string>> rows;

    Table() = default;

    void addColumn(const ::std::string& name) { columns.push_back(name); }
    int  getColumnCount() const { return (int)columns.size(); }
    int  getRowCount()    const { return (int)rows.size();    }
    ::std::string getColumnTitle(int i) const { return i<(int)columns.size()?columns[i]:""; }
    int  getColumnIndex(const ::std::string& n) const {
        for (int i=0;i<(int)columns.size();i++) if(columns[i]==n) return i;
        return -1;
    }

    ::std::vector<::std::string>& addRow() { rows.push_back(::std::vector<::std::string>(columns.size())); return rows.back(); }

    ::std::string getString(int row, int col)                const { return row<(int)rows.size()&&col<(int)rows[row].size()?rows[row][col]:""; }
    ::std::string getString(int row, const ::std::string& col) const { return getString(row,getColumnIndex(col)); }
    int         getInt(int row, int col)                   const { auto s=getString(row,col); return s.empty()?0:std::stoi(s); }
    int         getInt(int row, const ::std::string& col)    const { return getInt(row,getColumnIndex(col)); }
    float       getFloat(int row, int col)                 const { auto s=getString(row,col); return s.empty()?0:std::stof(s); }
    float       getFloat(int row, const ::std::string& col)  const { return getFloat(row,getColumnIndex(col)); }

    void setString(int row, int col, const ::std::string& v) { if(row<(int)rows.size()&&col<(int)rows[row].size()) rows[row][col]=v; }
    void setString(int row, const ::std::string& col, const ::std::string& v) { setString(row,getColumnIndex(col),v); }
    void setInt(int row, int col, int v)     { setString(row,col,::std::to_string(v)); }
    void setFloat(int row, int col, float v) { setString(row,col,::std::to_string(v)); }

    ::std::vector<int> findRowsWithValue(const ::std::string& col, const ::std::string& val) const {
        ::std::vector<int> r; int c=getColumnIndex(col);
        for (int i=0;i<(int)rows.size();i++) if(getString(i,c)==val) r.push_back(i);
        return r;
    }
    int findFirstRowWithValue(const ::std::string& col, const ::std::string& val) const {
        auto r=findRowsWithValue(col,val); return r.empty()?-1:r[0];
    }
    void removeRow(int i) { if(i<(int)rows.size()) rows.erase(rows.begin()+i); }
    void clearRows()      { rows.clear(); }
};


// =============================================================================
// TYPED LISTS / DICTS  --  match Processing Java's IntList, FloatDict, etc.
// =============================================================================

// =============================================================================
// String -- real wrapper class with Java's String API, NOT a textual
// rename to ::std::string. Inherits ::std::string for storage/operators
// (+, ==, <<, etc. all keep working), and adds Java-named methods so
// sketch authors can transfer their Java/Processing knowledge directly:
// length(), charAt(), equals(), equalsIgnoreCase(), substring(),
// indexOf(), lastIndexOf(), toLowerCase(), toUpperCase(), trim(),
// contains(), startsWith(), endsWith(), replace(), isEmpty(), concat(),
// compareTo(). Regex-based methods (matches/replaceAll/split with regex)
// are intentionally NOT implemented -- real Processing sketches rarely
// use them, and a correct regex engine is a much bigger addition.
// =============================================================================
class String : public ::std::string {
public:
    String() : ::std::string() {}
    String(const ::std::string& s) : ::std::string(s) {}
    String(const char* s) : ::std::string(s) {}
    String(char c) : ::std::string(1, c) {}
    String(const ::std::string& s, size_t pos, size_t len = npos) : ::std::string(s, pos, len) {}

    int length() const { return (int)size(); }
    bool isEmpty() const { return empty(); }

    char charAt(int index) const { return at((size_t)index); }

    bool equals(const ::std::string& other) const { return *this == other; }
    bool equalsIgnoreCase(const ::std::string& other) const {
        if (size() != other.size()) return false;
        for (size_t i = 0; i < size(); i++)
            if (tolower((unsigned char)(*this)[i]) != tolower((unsigned char)other[i])) return false;
        return true;
    }

    String substring(int beginIndex) const {
        if (beginIndex < 0) beginIndex = 0;
        if ((size_t)beginIndex > size()) beginIndex = (int)size();
        return String(substr((size_t)beginIndex));
    }
    String substring(int beginIndex, int endIndex) const {
        if (beginIndex < 0) beginIndex = 0;
        if (endIndex > (int)size()) endIndex = (int)size();
        if (endIndex < beginIndex) endIndex = beginIndex;
        return String(substr((size_t)beginIndex, (size_t)(endIndex - beginIndex)));
    }

    int indexOf(const ::std::string& needle) const {
        size_t p = find(needle);
        return p == npos ? -1 : (int)p;
    }
    int indexOf(const ::std::string& needle, int fromIndex) const {
        size_t p = find(needle, (size_t)::std::max(0, fromIndex));
        return p == npos ? -1 : (int)p;
    }
    int lastIndexOf(const ::std::string& needle) const {
        size_t p = rfind(needle);
        return p == npos ? -1 : (int)p;
    }

    String toLowerCase() const {
        ::std::string r = *this;
        for (auto& c : r) c = (char)tolower((unsigned char)c);
        return String(r);
    }
    String toUpperCase() const {
        ::std::string r = *this;
        for (auto& c : r) c = (char)toupper((unsigned char)c);
        return String(r);
    }

    String trim() const {
        size_t start = find_first_not_of(" \t\n\r\f\v");
        if (start == npos) return String("");
        size_t end = find_last_not_of(" \t\n\r\f\v");
        return String(substr(start, end - start + 1));
    }

    bool contains(const ::std::string& needle) const { return find(needle) != npos; }
    bool startsWith(const ::std::string& prefix) const {
        return size() >= prefix.size() && compare(0, prefix.size(), prefix) == 0;
    }
    bool endsWith(const ::std::string& suffix) const {
        return size() >= suffix.size() && compare(size() - suffix.size(), suffix.size(), suffix) == 0;
    }

    String replace(char oldChar, char newChar) const {
        ::std::string r = *this;
        for (auto& c : r) if (c == oldChar) c = newChar;
        return String(r);
    }
    String replace(const ::std::string& oldStr, const ::std::string& newStr) const {
        ::std::string r = *this;
        size_t pos = 0;
        while ((pos = r.find(oldStr, pos)) != npos) {
            r.replace(pos, oldStr.size(), newStr);
            pos += newStr.size();
        }
        return String(r);
    }

    String concat(const ::std::string& other) const { return String(*this + other); }

    int compareTo(const ::std::string& other) const { return compare(other); }

    // split(delim) -- one of the most commonly used String methods in
    // real Processing sketches (parsing CSV/delimited text). Matches
    // Java's String.split(String regex) for the simple, non-regex,
    // single-character-or-literal-delimiter case. Returns
    // ::std::vector<String> rather than ArrayList<String> -- ArrayList<T>
    // is declared LATER in this file, so referencing it here would be a
    // forward-reference compile error; ::std::vector works identically
    // for a simple for-loop over the results and has no ordering
    // dependency.
    // Edge cases handled to match Java's actual behavior:
    //   - empty input string -> single-element vector containing ""
    //   - delimiter not found -> whole string as the only element
    //   - consecutive delimiters -> empty-string elements between them
    //     (Java does NOT collapse them, and neither do we)
    //   - empty delimiter -> returns the original string unsplit
    ::std::vector<String> split(const ::std::string& delim) const {
        ::std::vector<String> result;
        if (delim.empty()) {
            result.push_back(String(*this));
            return result;
        }
        size_t start = 0, pos;
        while ((pos = find(delim, start)) != npos) {
            result.push_back(String(substr(start, pos - start)));
            start = pos + delim.size();
        }
        result.push_back(String(substr(start)));
        return result;
    }

    // toCharArray() -- matches Java's String.toCharArray(). An empty
    // string correctly returns an empty vector, not a vector containing
    // one null char.
    ::std::vector<char> toCharArray() const {
        return ::std::vector<char>(begin(), end());
    }

    // ===== Java API additions (added by apply_java_additions.py) =====

    // String(char[]) -- round-trip with toCharArray()
    String(const ::std::vector<char>& chars) : ::std::string(chars.begin(), chars.end()) {}
    String(const char* chars, size_t count) : ::std::string(chars, count) {}

    // compareToIgnoreCase -- case-insensitive lexicographic compare
    int compareToIgnoreCase(const ::std::string& other) const {
        size_t n = ::std::min(size(), other.size());
        for (size_t i = 0; i < n; i++) {
            char a = (char)tolower((unsigned char)(*this)[i]);
            char b = (char)tolower((unsigned char)other[i]);
            if (a != b) return (int)(unsigned char)a - (int)(unsigned char)b;
        }
        return (int)size() - (int)other.size();
    }

    // matches()/replaceAll() with regex intentionally NOT implemented --
    // same rationale as split(): real Processing sketches rarely use
    // them, and a correct regex engine is a much bigger addition. Use
    // ::std::regex directly in sketch code if needed.

    // ---- static methods ----

    // String.valueOf(...) -- Java's universal "stringify a primitive".
    static String valueOf(int v)         { return String(::std::to_string(v)); }
    static String valueOf(long v)        { return String(::std::to_string(v)); }
    static String valueOf(float v)       { return String(::std::to_string(v)); }
    static String valueOf(double v)      { return String(::std::to_string(v)); }
    static String valueOf(bool v)        { return String(v ? "true" : "false"); }
    static String valueOf(char v)        { return String(v); }
    static String valueOf(const ::std::vector<char>& chars) { return String(chars); }

    // String.join(delim, ...) -- Java 8+.
    static String join(const ::std::string& delim, ::std::initializer_list<::std::string> parts) {
        String result;
        bool first = true;
        for (const auto& p : parts) {
            if (!first) result += delim;
            result += p;
            first = false;
        }
        return result;
    }
    template<typename Container>
    static String join(const ::std::string& delim, const Container& parts) {
        String result;
        bool first = true;
        for (const auto& p : parts) {
            if (!first) result += delim;
            result += p;
            first = false;
        }
        return result;
    }

    // String.format(...) -- printf-style formatting matching Java's
    // String.format(String, Object...) for the common specifiers real
    // Processing sketches use: %d %f %s %c %x %o %% with width/precision
    // flags (e.g. "%05.2f", "%-10s"). Built on vsnprintf.
    static String format(const char* fmt, ...) {
        va_list args;
        va_start(args, fmt);
        va_list args_copy;
        va_copy(args_copy, args);
        int needed = ::std::vsnprintf(nullptr, 0, fmt, args_copy);
        va_end(args_copy);
        if (needed < 0) { va_end(args); return String(""); }
        ::std::vector<char> buf((size_t)needed + 1);
        ::std::vsnprintf(buf.data(), buf.size(), fmt, args);
        va_end(args);
        return String(::std::string(buf.data(), (size_t)needed));
    }
};

// =============================================================================
// PRIMITIVE WRAPPER CLASSES -- Integer, Float, Double, Long, Byte, Character
// =============================================================================
// Real Java wrapper classes, matching real semantics: a constructor
// taking the primitive (or a String, parsed via the matching parseXxx
// logic), an xxxValue() accessor, a static valueOf() factory, a static
// parseXxx() parser, an implicit conversion operator back to the
// primitive (covers Java's autoboxing/unboxing convenience without
// needing the user to call xxxValue() everywhere), and toString()/
// compareTo() for parity with String's own wrapper-class methods.
//
// Previously these six types were rewritten as plain text to their bare
// primitive equivalents (Integer->int, Float->float, etc.) -- the same
// category of bug fixed for String->::std::string: it silently changed
// the user's declared type, breaking anything relying on real wrapper-
// object behavior (valueOf(), parseXxx(), nullability via a sentinel,
// etc.), even though most everyday Processing code never notices since
// implicit conversion makes these usable almost everywhere a bare
// primitive would be.
template<typename PrimT>
class NumberWrapperBase {
protected:
    PrimT v;
public:
    NumberWrapperBase() : v(PrimT()) {}
    NumberWrapperBase(PrimT val) : v(val) {}
    operator PrimT() const { return v; }
    int compareTo(const NumberWrapperBase& other) const {
        if (v < other.v) return -1;
        if (v > other.v) return 1;
        return 0;
    }
    bool equals(const NumberWrapperBase& other) const { return v == other.v; }
    String toString() const { return String(::std::to_string(v)); }
};

class Integer : public NumberWrapperBase<int> {
public:
    Integer() : NumberWrapperBase<int>() {}
    Integer(int val) : NumberWrapperBase<int>(val) {}
    explicit Integer(const ::std::string& s) : NumberWrapperBase<int>(::std::stoi(s)) {}
    int intValue() const { return v; }
    static Integer valueOf(int val) { return Integer(val); }
    static Integer valueOf(const ::std::string& s) { return Integer(::std::stoi(s)); }
    static int parseInt(const ::std::string& s) { return ::std::stoi(s); }
};

class Float : public NumberWrapperBase<float> {
public:
    Float() : NumberWrapperBase<float>() {}
    Float(float val) : NumberWrapperBase<float>(val) {}
    explicit Float(const ::std::string& s) : NumberWrapperBase<float>(::std::stof(s)) {}
    float floatValue() const { return v; }
    static Float valueOf(float val) { return Float(val); }
    static Float valueOf(const ::std::string& s) { return Float(::std::stof(s)); }
    static float parseFloat(const ::std::string& s) { return ::std::stof(s); }
};

class Double : public NumberWrapperBase<double> {
public:
    Double() : NumberWrapperBase<double>() {}
    Double(double val) : NumberWrapperBase<double>(val) {}
    explicit Double(const ::std::string& s) : NumberWrapperBase<double>(::std::stod(s)) {}
    double doubleValue() const { return v; }
    static Double valueOf(double val) { return Double(val); }
    static Double valueOf(const ::std::string& s) { return Double(::std::stod(s)); }
    static double parseDouble(const ::std::string& s) { return ::std::stod(s); }
};

class Long : public NumberWrapperBase<long> {
public:
    Long() : NumberWrapperBase<long>() {}
    Long(long val) : NumberWrapperBase<long>(val) {}
    explicit Long(const ::std::string& s) : NumberWrapperBase<long>(::std::stol(s)) {}
    long longValue() const { return v; }
    static Long valueOf(long val) { return Long(val); }
    static Long valueOf(const ::std::string& s) { return Long(::std::stol(s)); }
    static long parseLong(const ::std::string& s) { return ::std::stol(s); }
};

class Byte : public NumberWrapperBase<signed char> {
public:
    Byte() : NumberWrapperBase<signed char>() {}
    Byte(signed char val) : NumberWrapperBase<signed char>(val) {}
    explicit Byte(const ::std::string& s) : NumberWrapperBase<signed char>((signed char)::std::stoi(s)) {}
    signed char byteValue() const { return v; }
    static Byte valueOf(signed char val) { return Byte(val); }
    static Byte valueOf(const ::std::string& s) { return Byte((signed char)::std::stoi(s)); }
    static signed char parseByte(const ::std::string& s) { return (signed char)::std::stoi(s); }
};

// Character is NOT a Number subclass in real Java (it extends Object
// directly), so it doesn't share NumberWrapperBase -- no compareTo via
// numeric ordering makes sense to inherit from that template here,
// though char does have a natural ordering, so compareTo is still
// provided directly.
class Character {
    char v;
public:
    Character() : v('\0') {}
    Character(char val) : v(val) {}
    operator char() const { return v; }
    char charValue() const { return v; }
    static Character valueOf(char val) { return Character(val); }
    int compareTo(const Character& other) const {
        if (v < other.v) return -1;
        if (v > other.v) return 1;
        return 0;
    }
    bool equals(const Character& other) const { return v == other.v; }
    String toString() const { return String(::std::string(1, v)); }
    static bool isDigit(char c) { return c >= '0' && c <= '9'; }
    static bool isLetter(char c) { return ::std::isalpha((unsigned char)c) != 0; }
    static bool isUpperCase(char c) { return ::std::isupper((unsigned char)c) != 0; }
    static bool isLowerCase(char c) { return ::std::islower((unsigned char)c) != 0; }
    static char toUpperCase(char c) { return (char)::std::toupper((unsigned char)c); }
    static char toLowerCase(char c) { return (char)::std::tolower((unsigned char)c); }
};


class IntList {
public:
    ::std::vector<int> data;
    IntList() = default;
    IntList(::std::initializer_list<int> l) : data(l) {}
    // Java-style
    void append(int v)            { data.push_back(v); }
    void add(int v)               { data.push_back(v); }
    void add(int i, int v)        { data.insert(data.begin()+i,v); }
    void set(int i, int v)        { data[i]=v; }
    int  get(int i)         const { return data[i]; }
    int  size()             const { return (int)data.size(); }
    bool isEmpty()          const { return data.empty(); }
    void sort()                   { ::std::sort(data.begin(),data.end()); }
    void reverse()                { ::std::reverse(data.begin(),data.end()); }
    bool hasValue(int v)    const { return ::std::find(data.begin(),data.end(),v)!=data.end(); }
    bool contains(int v)    const { return hasValue(v); }
    void remove(int i)            { data.erase(data.begin()+i); }
    void clear()                  { data.clear(); }
    void shuffle() {
        for(int i=(int)data.size()-1;i>0;i--){
            int j=rand()%(i+1); ::std::swap(data[i],data[j]);
        }
    }
    // Bounds-checked access -- matches Java's ArrayIndexOutOfBoundsException
    // semantics (a clean, catchable error) rather than C++'s usual
    // undefined-behavior-on-out-of-range for operator[]. Without this, an
    // out-of-range index silently corrupts the heap instead of failing
    // loudly at the actual bad access -- the corruption then surfaces
    // later, at an unrelated allocation, as a cryptic allocator error.
    int& operator[](int i) {
        if (i < 0 || i >= (int)data.size())
            throw ::std::out_of_range(
                "IntList index " + ::std::to_string(i) +
                " out of bounds for length " + ::std::to_string(data.size()));
        return data[(size_t)i];
    }
    auto begin() { return data.begin(); }
    auto end()   { return data.end(); }

    // ===== Java/Processing API additions (apply_java_additions.py) =====
    explicit IntList(int length) : data((size_t)::std::max(0, length), 0) {}

    static IntList fromRange(int stop) { return fromRange(0, stop); }
    static IntList fromRange(int start, int stop) {
        IntList r;
        for (int i = start; i < stop; i++) r.append(i);
        return r;
    }

    void resize(int length) { data.resize((size_t)::std::max(0, length), 0); }

    void push(int v) { append(v); }
    int  pop() {
        if (data.empty()) throw ::std::runtime_error("Can't call pop() on an empty list");
        int v = data.back();
        data.pop_back();
        return v;
    }

    int index(int value) const {
        for (int i = 0; i < (int)data.size(); i++) if (data[i] == value) return i;
        return -1;
    }
    int removeValue(int value) {
        int idx = index(value);
        if (idx != -1) remove(idx);
        return idx;
    }
    int removeValues(int value) {
        int before = (int)data.size();
        data.erase(::std::remove(data.begin(), data.end(), value), data.end());
        return before - (int)data.size();
    }
    void appendUnique(int value) { if (!hasValue(value)) append(value); }

    void append(const ::std::vector<int>& values) { for (int v : values) append(v); }
    void append(const IntList& list) {
        // Snapshot first: if 'list' is THIS SAME object (e.g. self-aliasing
        // call list.append(list)), iterating list.data directly while
        // append(v) grows this->data via push_back can reallocate the
        // underlying buffer mid-loop, invalidating the range-for's
        // captured begin()/end() iterators -- a use-after-free. Copying
        // values out first makes append(self) safe and correct.
        // (Found by stress-testing; confirmed via AddressSanitizer.)
        ::std::vector<int> snapshot = list.data;
        for (int v : snapshot) append(v);
    }

    void increment(int idx) {
        if ((int)data.size() <= idx) resize(idx + 1);
        data[idx]++;
    }
    // addAt/subAt/multAt/divAt -- real Java IntList overloads add() etc.
    // for in-place arithmetic on one element using the SAME name as
    // insert (add(index,value)); we use distinct names here since our
    // add(int,int) already means "insert v at i".
    void addAt(int idx, int amount)  { data.at(idx) += amount; }
    void subAt(int idx, int amount)  { data.at(idx) -= amount; }
    void multAt(int idx, int amount) { data.at(idx) *= amount; }
    void divAt(int idx, int amount)  { data.at(idx) /= amount; }

    int min() const {
        if (data.empty()) throw ::std::runtime_error("Cannot use min() on an empty IntList.");
        return *::std::min_element(data.begin(), data.end());
    }
    int max() const {
        if (data.empty()) throw ::std::runtime_error("Cannot use max() on an empty IntList.");
        return *::std::max_element(data.begin(), data.end());
    }
    int minIndex() const {
        if (data.empty()) throw ::std::runtime_error("Cannot use minIndex() on an empty IntList.");
        return (int)::std::distance(data.begin(), ::std::min_element(data.begin(), data.end()));
    }
    int maxIndex() const {
        if (data.empty()) throw ::std::runtime_error("Cannot use maxIndex() on an empty IntList.");
        return (int)::std::distance(data.begin(), ::std::max_element(data.begin(), data.end()));
    }
    long sumLong() const { long s = 0; for (int v : data) s += v; return s; }
    int  sum()     const { return (int)sumLong(); }

    void sortReverse() { ::std::sort(data.begin(), data.end(), ::std::greater<int>()); }

    IntList copy() const { IntList r; r.data = data; return r; }

    IntList getSubset(int start) const { return getSubset(start, (int)data.size() - start); }
    IntList getSubset(int start, int num) const {
        // Real Java IntList.getSubset() relies on System.arraycopy, which
        // throws for an out-of-range start/num. Our begin()+start+num
        // iterator arithmetic is UB if out of range rather than a safe
        // throw -- confirmed by AddressSanitizer -- so we validate first.
        if (start < 0 || num < 0 || start + num > (int)data.size()) {
            throw ::std::out_of_range("IntList::getSubset() index out of range");
        }
        IntList r;
        r.data.assign(data.begin() + start, data.begin() + start + num);
        return r;
    }

    ::std::string join(const ::std::string& separator) const {
        if (data.empty()) return "";
        ::std::string r = ::std::to_string(data[0]);
        for (size_t i = 1; i < data.size(); i++) { r += separator; r += ::std::to_string(data[i]); }
        return r;
    }
    void print() const {
        for (int i = 0; i < (int)data.size(); i++) printf("[%d] %d\n", i, data[i]);
    }
    ::std::string toString() const {
        return "IntList size=" + ::std::to_string(size()) + " [ " + join(", ") + " ]";
    }
};

class FloatList {
public:
    ::std::vector<float> data;
    FloatList() = default;
    FloatList(::std::initializer_list<float> l) : data(l) {}
    void  append(float v)         { data.push_back(v); }
    void  add(float v)            { data.push_back(v); }
    void  set(int i, float v)     { data[i]=v; }
    float get(int i)        const { return data[i]; }
    int   size()            const { return (int)data.size(); }
    bool  isEmpty()         const { return data.empty(); }
    void  sort()                  { ::std::sort(data.begin(),data.end()); }
    void  reverse()               { ::std::reverse(data.begin(),data.end()); }
    void  remove(int i)           { data.erase(data.begin()+i); }
    void  clear()                 { data.clear(); }
    void  shuffle() {
        for(int i=(int)data.size()-1;i>0;i--){
            int j=rand()%(i+1); ::std::swap(data[i],data[j]);
        }
    }
    // Bounds-checked access -- see IntList::operator[] for rationale.
    float& operator[](int i) {
        if (i < 0 || i >= (int)data.size())
            throw ::std::out_of_range(
                "FloatList index " + ::std::to_string(i) +
                " out of bounds for length " + ::std::to_string(data.size()));
        return data[(size_t)i];
    }
    auto begin() { return data.begin(); }
    auto end()   { return data.end(); }

    // ===== Java/Processing API additions (apply_java_additions.py) =====
    explicit FloatList(int length) : data((size_t)::std::max(0, length), 0.0f) {}

    void resize(int length) { data.resize((size_t)::std::max(0, length), 0.0f); }

    void add(int i, float v) { data.insert(data.begin()+i, v); }

    bool hasValue(float v) const { return ::std::find(data.begin(),data.end(),v)!=data.end(); }
    bool contains(float v)  const { return hasValue(v); }

    void push(float v) { append(v); }
    float pop() {
        if (data.empty()) throw ::std::runtime_error("Can't call pop() on an empty list");
        float v = data.back();
        data.pop_back();
        return v;
    }

    int index(float value) const {
        for (int i = 0; i < (int)data.size(); i++) if (data[i] == value) return i;
        return -1;
    }
    int removeValue(float value) {
        int idx = index(value);
        if (idx != -1) remove(idx);
        return idx;
    }
    int removeValues(float value) {
        int before = (int)data.size();
        data.erase(::std::remove(data.begin(), data.end(), value), data.end());
        return before - (int)data.size();
    }
    void appendUnique(float value) { if (!hasValue(value)) append(value); }

    void append(const ::std::vector<float>& values) { for (float v : values) append(v); }
    void append(const FloatList& list) {
        // Snapshot first -- see IntList::append(const IntList&) comment;
        // protects against self-aliasing (list.append(list)) reallocating
        // mid-iteration and invalidating the iterators we're reading from.
        ::std::vector<float> snapshot = list.data;
        for (float v : snapshot) append(v);
    }

    void addAt(int idx, float amount)  { data.at(idx) += amount; }
    void subAt(int idx, float amount)  { data.at(idx) -= amount; }
    void multAt(int idx, float amount) { data.at(idx) *= amount; }
    void divAt(int idx, float amount)  { data.at(idx) /= amount; }

    float min() const {
        if (data.empty()) throw ::std::runtime_error("Cannot use min() on an empty FloatList.");
        return *::std::min_element(data.begin(), data.end());
    }
    float max() const {
        if (data.empty()) throw ::std::runtime_error("Cannot use max() on an empty FloatList.");
        return *::std::max_element(data.begin(), data.end());
    }
    int minIndex() const {
        if (data.empty()) throw ::std::runtime_error("Cannot use minIndex() on an empty FloatList.");
        return (int)::std::distance(data.begin(), ::std::min_element(data.begin(), data.end()));
    }
    int maxIndex() const {
        if (data.empty()) throw ::std::runtime_error("Cannot use maxIndex() on an empty FloatList.");
        return (int)::std::distance(data.begin(), ::std::max_element(data.begin(), data.end()));
    }
    double sum() const { double s = 0; for (float v : data) s += v; return s; }

    void sortReverse() { ::std::sort(data.begin(), data.end(), ::std::greater<float>()); }

    FloatList copy() const { FloatList r; r.data = data; return r; }

    FloatList getSubset(int start) const { return getSubset(start, (int)data.size() - start); }
    FloatList getSubset(int start, int num) const {
        // See IntList::getSubset() comment -- same UB risk, same fix.
        if (start < 0 || num < 0 || start + num > (int)data.size()) {
            throw ::std::out_of_range("FloatList::getSubset() index out of range");
        }
        FloatList r;
        r.data.assign(data.begin() + start, data.begin() + start + num);
        return r;
    }

    ::std::string join(const ::std::string& separator) const {
        if (data.empty()) return "";
        ::std::string r = ::std::to_string(data[0]);
        for (size_t i = 1; i < data.size(); i++) { r += separator; r += ::std::to_string(data[i]); }
        return r;
    }
    void print() const {
        for (int i = 0; i < (int)data.size(); i++) printf("[%d] %f\n", i, data[i]);
    }
    ::std::string toString() const {
        return "FloatList size=" + ::std::to_string(size()) + " [ " + join(", ") + " ]";
    }
};

class StringList {
public:
    ::std::vector<::std::string> data;
    StringList() = default;
    StringList(::std::initializer_list<::std::string> l) : data(l) {}
    void        append(const ::std::string& v)    { data.push_back(v); }
    void        set(int i, const ::std::string& v){ data[i]=v; }
    ::std::string get(int i)            const     { return data[i]; }
    int         size()                const     { return (int)data.size(); }
    void        sort()                          { ::std::sort(data.begin(),data.end()); }
    void        reverse()                       { ::std::reverse(data.begin(),data.end()); }
    bool        hasValue(const ::std::string& v) const { return ::std::find(data.begin(),data.end(),v)!=data.end(); }
    void        remove(int i)                   { data.erase(data.begin()+i); }
    void        clear()                         { data.clear(); }
    // Bounds-checked access -- see IntList::operator[] for rationale.
    ::std::string& operator[](int i) {
        if (i < 0 || i >= (int)data.size())
            throw ::std::out_of_range(
                "StringList index " + ::std::to_string(i) +
                " out of bounds for length " + ::std::to_string(data.size()));
        return data[(size_t)i];
    }

    // ===== Java/Processing API additions (apply_java_additions.py) =====
    explicit StringList(int length) : data((size_t)::std::max(0, length)) {}

    bool isEmpty() const { return data.empty(); }
    void resize(int length) { data.resize((size_t)::std::max(0, length)); }

    void add(const ::std::string& v)            { data.push_back(v); }
    void add(int i, const ::std::string& v)     { data.insert(data.begin()+i, v); }
    bool contains(const ::std::string& v) const { return hasValue(v); }

    void push(const ::std::string& v) { append(v); }
    ::std::string pop() {
        if (data.empty()) throw ::std::runtime_error("Can't call pop() on an empty list");
        ::std::string v = data.back();
        data.pop_back();
        return v;
    }

    int index(const ::std::string& value) const {
        for (int i = 0; i < (int)data.size(); i++) if (data[i] == value) return i;
        return -1;
    }
    int removeValue(const ::std::string& value) {
        int idx = index(value);
        if (idx != -1) remove(idx);
        return idx;
    }
    int removeValues(const ::std::string& value) {
        int before = (int)data.size();
        data.erase(::std::remove(data.begin(), data.end(), value), data.end());
        return before - (int)data.size();
    }
    void appendUnique(const ::std::string& value) { if (!hasValue(value)) append(value); }

    void append(const ::std::vector<::std::string>& values) { for (auto& v : values) append(v); }
    void append(const StringList& list) {
        // Snapshot first -- see IntList::append(const IntList&) comment;
        // protects against self-aliasing (list.append(list)) reallocating
        // mid-iteration and invalidating the iterators we're reading from.
        ::std::vector<::std::string> snapshot = list.data;
        for (auto& v : snapshot) append(v);
    }

    void shuffle() {
        for (int i = (int)data.size()-1; i > 0; i--) {
            int j = rand() % (i+1);
            ::std::swap(data[i], data[j]);
        }
    }

    StringList copy() const { StringList r; r.data = data; return r; }

    StringList getSubset(int start) const { return getSubset(start, (int)data.size() - start); }
    StringList getSubset(int start, int num) const {
        // See IntList::getSubset() comment -- same UB risk, same fix.
        if (start < 0 || num < 0 || start + num > (int)data.size()) {
            throw ::std::out_of_range("StringList::getSubset() index out of range");
        }
        StringList r;
        r.data.assign(data.begin() + start, data.begin() + start + num);
        return r;
    }

    ::std::string join(const ::std::string& separator) const {
        if (data.empty()) return "";
        ::std::string r = data[0];
        for (size_t i = 1; i < data.size(); i++) { r += separator; r += data[i]; }
        return r;
    }
    void print() const {
        for (int i = 0; i < (int)data.size(); i++) printf("[%d] %s\n", i, data[i].c_str());
    }
    ::std::string toString() const {
        return "StringList size=" + ::std::to_string(size()) + " [ " + join(", ") + " ]";
    }
};

// =============================================================================
// ArrayList<T> -- Java-style generic list, backed by ::std::vector. Real
// Processing/Java method names (add/get/remove/size/isEmpty/contains),
// NOT a textual rewrite to ::std::vector, since ::std::vector's own method
// names (push_back/operator[]/erase) don't match what sketch source
// written against Java's ArrayList API actually calls.
//
// In Java, ArrayList<T> ALWAYS stores references for object types --
// there are no value-type objects in Java at all, so this was never a
// choice the sketch author made, it's just how every Java object type
// behaves. PImage/PFont/PShape/PGraphics specifically are non-copyable in
// our C++ port (they own unique GPU resources -- copying one would either
// crash via double-free or silently alias the same resource from two
// "different" objects), which is the correct C++ translation of "this is
// reference-like in Java." Rather than forcing sketch authors to notice
// and write ArrayList<PGraphics*> for exactly these four types, ArrayList
// detects non-copy-constructible T automatically and stores T* internally
// -- the PUBLIC API (add/get/etc.) still looks and behaves like Java's,
// the indirection is an implementation detail, exactly mirroring how
// "PGraphics pg; pg = createGraphics(...);" already becomes a pointer
// under the hood without the sketch author needing to write one.
// Default rule: T is reference-like (stored as T*) UNLESS it's one of
// Java's true value types -- primitives (int/float/bool/char/etc.) or
// ::std::string/String. This matches Java's ACTUAL semantics exactly:
// every object/class type in Java is reference-like, full stop --
// primitives are the only exception. Our earlier version used
// "!::std::is_copy_constructible<T>" as the trigger, which correctly
// caught PImage/PFont/PShape/PGraphics (non-copyable in C++, so they
// were forced to be pointer-stored) but WRONGLY left ordinary,
// copyable user-defined classes (e.g. a sketch's own "Particle" class)
// as value-stored -- meaning ArrayList<Particle>.get(i) returned a
// COPY, so calling p.update() on that copy never mutated what was
// actually stored in the list. In Java, Particle is reference-like
// =============================================================================
// Array<T> -- fixed-size array matching real Java array semantics
// =============================================================================
// Java's "int[] a = new int[10];" creates a FIXED-SIZE array: zero/false/
// null-initialized by default, and bounds-checked at runtime (throwing
// ArrayIndexOutOfBoundsException on an invalid index) -- this is
// DIFFERENT from ArrayList<T> (growable, add()/remove()) and different
// from a raw C-style array (no bounds checking at all in C++, undefined
// behavior on out-of-range access rather than a clean, catchable error).
//
// Array<T> exists to be the faithful, safe translation of THIS specific
// Java construct: fixed length (set once, at construction, matching
// Java's own "can't resize an array" rule), default-initialized
// elements, and a bounds-checked [] operator that throws ::std::out_of_range
// (the closest C++ equivalent to Java's ArrayIndexOutOfBoundsException)
// rather than silently reading/writing out-of-bounds memory.
//
// Real Java syntax ("int[] a = new int[10];") is intentionally NOT
// supported directly -- see the E0004 compiler error attached to the
// blocked overload below. CppBuild does not attempt to silently rewrite
// this syntax, for the same reason pointerizeNewAssignedVars was
// removed earlier: guessing at the user's intent via text rewriting is
// exactly the category of fragile, comment-blind, scope-unaware
// mechanism this whole session has been moving away from. Sketch
// authors write Array<T> explicitly instead.
template<typename T>
class Array {
    ::std::vector<T> data;
public:
    Array() {}
    explicit Array(int size) : data(size > 0 ? (size_t)size : 0, T()) {}
    Array(int size, const T& fillValue) : data(size > 0 ? (size_t)size : 0, fillValue) {}

    int length() const { return (int)data.size(); }

    // Bounds-checked access -- matches Java's ArrayIndexOutOfBoundsException
    // semantics (a clean, catchable error) rather than C++'s usual
    // undefined-behavior-on-out-of-range for operator[].
    T& operator[](int index) {
        if (index < 0 || index >= (int)data.size())
            throw ::std::out_of_range(
                "Array index " + ::std::to_string(index) +
                " out of bounds for length " + ::std::to_string(data.size()));
        return data[(size_t)index];
    }
    const T& operator[](int index) const {
        if (index < 0 || index >= (int)data.size())
            throw ::std::out_of_range(
                "Array index " + ::std::to_string(index) +
                " out of bounds for length " + ::std::to_string(data.size()));
        return data[(size_t)index];
    }

    // get/set methods for Java-style access and bool[] compatibility
    T get(int index) const {
        if (index < 0 || index >= (int)data.size())
            throw ::std::out_of_range("Array index " + ::std::to_string(index) + " out of bounds");
        return data[(size_t)index];
    }
    void set(int index, const T& val) {
        if (index < 0 || index >= (int)data.size())
            throw ::std::out_of_range("Array index " + ::std::to_string(index) + " out of bounds");
        data[(size_t)index] = val;
    }
    auto begin()       { return data.begin(); }
    auto end()         { return data.end(); }
    auto begin() const { return data.begin(); }
    auto end()   const { return data.end(); }

    // Matches Java's array-literal syntax: int[] a = {1, 2, 3};
    Array(::std::initializer_list<T> init) : data(init) {}

    // Exposed so the free functions below (append/arrayCopy/concat/
    // expand/reverse/shorten/sort/splice/subset) can build new Array<T>
    // instances directly from a ::std::vector<T>.
    static Array<T> fromVector(::std::vector<T> v) {
        Array<T> a(0);
        a.data = ::std::move(v);
        return a;
    }
    const ::std::vector<T>& rawData() const { return data; }
    ::std::vector<T>&       rawData()       { return data; }
};

// =============================================================================
// Array<T> utility functions -- matching real Processing's free-function
// call style exactly: "arr = append(arr, val);" not "arr.append(val);",
// since every one of these (except reverse/sort) returns a NEW array
// rather than mutating in place, mirroring Java's own fixed-length-array
// constraint.
// =============================================================================

template<typename T>
Array<T> append(const Array<T>& arr, const T& value) {
    ::std::vector<T> v = arr.rawData();
    v.push_back(value);
    return Array<T>::fromVector(::std::move(v));
}

template<typename T>
void arrayCopy(const Array<T>& src, Array<T>& dst) {
    int n = ::std::min(src.length(), dst.length());
    for (int i = 0; i < n; i++) dst[i] = src[i];
}
template<typename T>
void arrayCopy(const Array<T>& src, int srcPos, Array<T>& dst, int dstPos, int length) {
    for (int i = 0; i < length; i++) dst[dstPos + i] = src[srcPos + i];
}
// arrayCopy for std::vector
template<class T> inline void arrayCopy(const ::std::vector<T>& src, ::std::vector<T>& dst) {
    dst = src;
}
template<class T> inline void arrayCopy(const ::std::vector<T>& src, int srcPos, ::std::vector<T>& dst, int dstPos, int length) {
    for (int i=0;i<length;i++) dst[dstPos+i]=src[srcPos+i];
}
// arrayCopy for raw arrays
template<class T> inline void arrayCopy(const T* src, T* dst, int length) {
    ::std::copy(src, src+length, dst);
}

// arrayCopy for std::vector
template<class T> inline void arrayCopy(const ::std::vector<T>& src, ::std::vector<T>& dst) {
    dst = src;
}
template<class T> inline void arrayCopy(const ::std::vector<T>& src, int srcPos, ::std::vector<T>& dst, int dstPos, int length) {
    for (int i=0;i<length;i++) dst[dstPos+i]=src[srcPos+i];
}
// arrayCopy for raw arrays
template<class T> inline void arrayCopy(const T* src, T* dst, int length) {
    ::std::copy(src, src+length, dst);
}


template<typename T>
Array<T> concat(const Array<T>& a, const Array<T>& b) {
    ::std::vector<T> v = a.rawData();
    const auto& bv = b.rawData();
    v.insert(v.end(), bv.begin(), bv.end());
    return Array<T>::fromVector(::std::move(v));
}

template<typename T>
Array<T> expand(const Array<T>& arr) {
    int newSize = arr.length() == 0 ? 1 : arr.length() * 2;
    ::std::vector<T> v = arr.rawData();
    v.resize((size_t)newSize, T());
    return Array<T>::fromVector(::std::move(v));
}
template<typename T>
Array<T> expand(const Array<T>& arr, int newSize) {
    ::std::vector<T> v = arr.rawData();
    v.resize((size_t)::std::max(newSize, arr.length()), T());
    return Array<T>::fromVector(::std::move(v));
}

template<typename T>
void reverse(Array<T>& arr) {
    ::std::reverse(arr.rawData().begin(), arr.rawData().end());
}

template<typename T>
Array<T> shorten(const Array<T>& arr) {
    ::std::vector<T> v = arr.rawData();
    if (!v.empty()) v.pop_back();
    return Array<T>::fromVector(::std::move(v));
}

template<typename T>
void sort(Array<T>& arr) {
    ::std::sort(arr.rawData().begin(), arr.rawData().end());
}
template<typename T>
void sort(Array<T>& arr, int count) {
    ::std::sort(arr.rawData().begin(), arr.rawData().begin() + ::std::min(count, arr.length()));
}

template<typename T>
Array<T> splice(const Array<T>& arr, const T& value, int index) {
    ::std::vector<T> v = arr.rawData();
    v.insert(v.begin() + index, value);
    return Array<T>::fromVector(::std::move(v));
}
template<typename T>
Array<T> splice(const Array<T>& arr, const Array<T>& values, int index) {
    ::std::vector<T> v = arr.rawData();
    const auto& vv = values.rawData();
    v.insert(v.begin() + index, vv.begin(), vv.end());
    return Array<T>::fromVector(::std::move(v));
}

template<typename T>
Array<T> subset(const Array<T>& arr, int start) {
    const auto& v = arr.rawData();
    return Array<T>::fromVector(::std::vector<T>(v.begin() + start, v.end()));
}
template<typename T>
Array<T> subset(const Array<T>& arr, int start, int count) {
    const auto& v = arr.rawData();
    return Array<T>::fromVector(::std::vector<T>(v.begin() + start, v.begin() + start + count));
}

class Integer; class Float; class Double; class Long; class Byte; class Character;
template<typename T>
struct IsJavaValueType : ::std::integral_constant<bool,
    ::std::is_arithmetic<T>::value ||      // int, float, double, bool, char, etc.
    ::std::is_same<T, ::std::string>::value ||
    ::std::is_same<T, String>::value ||
    // BUG FIX: the new Integer/Float/Double/Long/Byte/Character wrapper
    // classes are NOT ::std::is_arithmetic (they're classes wrapping a
    // primitive, not primitives themselves), so without this explicit
    // list, ArrayList<Integer> etc. would silently become reference-
    // storage (Integer*) -- breaking "nums.add(10);" (a plain int
    // literal can't implicitly become an Integer*) even though these
    // wrapper classes are specifically designed to be lightweight,
    // copyable stand-ins for primitives (unlike PImage/PGraphics, which
    // genuinely own unique GPU resources and must stay reference-like).
    ::std::is_same<T, Integer>::value ||
    ::std::is_same<T, Float>::value ||
    ::std::is_same<T, Double>::value ||
    ::std::is_same<T, Long>::value ||
    ::std::is_same<T, Byte>::value ||
    ::std::is_same<T, Character>::value
> {};

template<typename T, bool IsRefLike = !IsJavaValueType<T>::value>
class ArrayList;

// Value-storage specialization: used for ordinary copyable types
// (int, float, String, user structs without unique GPU resources, etc.)
template<typename T>
class ArrayList<T, false> {
public:
    ::std::vector<T> data;
    ArrayList() = default;
    ArrayList(::std::initializer_list<T> l) : data(l) {}
    void  add(const T& v)           { data.push_back(v); }
    void  add(int i, const T& v)    { data.insert(data.begin()+i, v); }
    void  set(int i, const T& v)    { data[i]=v; }
    T     get(int i)            const{ return data[i]; }
    int   size()                const{ return (int)data.size(); }
    bool  isEmpty()              const{ return data.empty(); }
    bool  contains(const T& v)  const{ return ::std::find(data.begin(),data.end(),v)!=data.end(); }
    void  remove(int i)              { data.erase(data.begin()+i); }
    void  clear()                    { data.clear(); }
    // Bounds-checked access -- see IntList::operator[] for rationale.
    T&    operator[](int i) {
        if (i < 0 || i >= (int)data.size())
            throw ::std::out_of_range(
                "ArrayList index " + ::std::to_string(i) +
                " out of bounds for length " + ::std::to_string(data.size()));
        return data[(size_t)i];
    }
    auto  begin() { return data.begin(); }
    auto  end()   { return data.end(); }

    // ===== java.util.ArrayList<T> API additions (apply_java_additions.py) =====
    bool removeElement(const T& v) {
        auto it = ::std::find(data.begin(), data.end(), v);
        if (it == data.end()) return false;
        data.erase(it);
        return true;
    }
    int indexOf(const T& v) const {
        auto it = ::std::find(data.begin(), data.end(), v);
        return it == data.end() ? -1 : (int)::std::distance(data.begin(), it);
    }
    int lastIndexOf(const T& v) const {
        auto it = ::std::find(data.rbegin(), data.rend(), v);
        return it == data.rend() ? -1 : (int)(data.size() - 1 - ::std::distance(data.rbegin(), it));
    }
    void addAll(const ArrayList<T,false>& other) {
        // Snapshot first: protects against self-aliasing (list.addAll(list)).
        // vector::insert with a source range that overlaps the destination
        // vector is not guaranteed safe by the standard if the insert
        // triggers reallocation -- copying out first avoids relying on
        // implementation-specific behavior. (Found by stress-testing.)
        ::std::vector<T> snapshot = other.data;
        data.insert(data.end(), snapshot.begin(), snapshot.end());
    }
    void addAll(int idx, const ArrayList<T,false>& other) {
        ::std::vector<T> snapshot = other.data;
        data.insert(data.begin()+idx, snapshot.begin(), snapshot.end());
    }
    ArrayList<T,false> subList(int from, int to) const {
        // Real java.util.ArrayList.subList() throws IndexOutOfBoundsException
        // for fromIndex<0, toIndex>size(), or fromIndex>toIndex. Without this
        // check, data.begin()+from or data.begin()+to with an out-of-range
        // offset is undefined behavior in the STL (not a safe throw) --
        // confirmed by AddressSanitizer during stress-testing.
        if (from < 0 || to > (int)data.size() || from > to) {
            throw ::std::out_of_range("ArrayList::subList() index out of range");
        }
        ArrayList<T,false> r;
        r.data.assign(data.begin()+from, data.begin()+to);
        return r;
    }
    void ensureCapacity(int cap) { data.reserve((size_t)cap); }
    void trimToSize() { data.shrink_to_fit(); }
};

// Reference-storage specialization: used automatically for non-copyable
// T (PImage, PFont, PShape, PGraphics). Stores T* internally; the public
// API still takes/returns in terms that match how Java code calls it --
// add() takes a T* (matching what createGraphics()/loadImage() etc.
// already return), get() returns T* (matching Java's reference
// semantics: "PGraphics pg = list.get(i);" should give you the SAME
// object, not a copy).
template<typename T>
class ArrayList<T, true> {
public:
    ::std::vector<T*> data;
    ArrayList() = default;
    void  add(T* v)                  { data.push_back(v); }
    void  add(T  v)                  { data.push_back(new T(::std::move(v))); }  // accept by value, heap-allocate
    void  add(int i, T* v)            { data.insert(data.begin()+i, v); }
    void  add(int i, T  v)            { data.insert(data.begin()+i, new T(::std::move(v))); }
    void  set(int i, T* v)            { data[i]=v; }
    T*    get(int i)             const{ return data[i]; }
    int   size()                 const{ return (int)data.size(); }
    bool  isEmpty()               const{ return data.empty(); }
    bool  contains(T* v)         const{ return ::std::find(data.begin(),data.end(),v)!=data.end(); }
    void  remove(int i)               { data.erase(data.begin()+i); }
    void  clear()                     { data.clear(); }
    // Bounds-checked access -- see IntList::operator[] for rationale.
    T*&   operator[](int i) {
        if (i < 0 || i >= (int)data.size())
            throw ::std::out_of_range(
                "ArrayList index " + ::std::to_string(i) +
                " out of bounds for length " + ::std::to_string(data.size()));
        return data[(size_t)i];
    }
    auto  begin() { return data.begin(); }
    auto  end()   { return data.end(); }

    // ===== java.util.ArrayList<T> API additions (apply_java_additions.py) =====
    bool removeElement(T* v) {
        auto it = ::std::find(data.begin(), data.end(), v);
        if (it == data.end()) return false;
        data.erase(it);
        return true;
    }
    int indexOf(T* v) const {
        auto it = ::std::find(data.begin(), data.end(), v);
        return it == data.end() ? -1 : (int)::std::distance(data.begin(), it);
    }
    int lastIndexOf(T* v) const {
        auto it = ::std::find(data.rbegin(), data.rend(), v);
        return it == data.rend() ? -1 : (int)(data.size() - 1 - ::std::distance(data.rbegin(), it));
    }
    void addAll(const ArrayList<T,true>& other) {
        // Snapshot first -- see ArrayList<T,false>::addAll comment.
        ::std::vector<T*> snapshot = other.data;
        data.insert(data.end(), snapshot.begin(), snapshot.end());
    }
    void addAll(int idx, const ArrayList<T,true>& other) {
        ::std::vector<T*> snapshot = other.data;
        data.insert(data.begin()+idx, snapshot.begin(), snapshot.end());
    }
    ArrayList<T,true> subList(int from, int to) const {
        // See ArrayList<T,false>::subList() comment -- same UB risk,
        // same fix.
        if (from < 0 || to > (int)data.size() || from > to) {
            throw ::std::out_of_range("ArrayList::subList() index out of range");
        }
        ArrayList<T,true> r;
        r.data.assign(data.begin()+from, data.begin()+to);
        return r;
    }
    void ensureCapacity(int cap) { data.reserve((size_t)cap); }
    void trimToSize() { data.shrink_to_fit(); }
};

// =============================================================================
// PMap<K,V> -- thin ::std::unordered_map wrapper
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
    ::std::map<::std::string,int> data;
    void set(const ::std::string& k, int v)               { data[k]=v; }
    int  get(const ::std::string& k, int def=0) const     { auto it=data.find(k); return it!=data.end()?it->second:def; }
    bool hasKey(const ::std::string& k)         const     { return data.count(k)>0; }
    void remove(const ::std::string& k)                   { data.erase(k); }
    int  size()                               const     { return (int)data.size(); }
    void clear()                                        { data.clear(); }
    ::std::vector<::std::string> keys() const               { ::std::vector<::std::string> r; for(auto& p:data) r.push_back(p.first); return r; }
    int& operator[](const ::std::string& k)               { return data[k]; }
};

class FloatDict {
public:
    ::std::map<::std::string,float> data;
    void  set(const ::std::string& k, float v)                { data[k]=v; }
    float get(const ::std::string& k, float def=0)  const     { auto it=data.find(k); return it!=data.end()?it->second:def; }
    bool  hasKey(const ::std::string& k)            const     { return data.count(k)>0; }
    void  remove(const ::std::string& k)                      { data.erase(k); }
    int   size()                                  const     { return (int)data.size(); }
    void  clear()                                           { data.clear(); }
    ::std::vector<::std::string> keys() const                   { ::std::vector<::std::string> r; for(auto& p:data) r.push_back(p.first); return r; }
    float& operator[](const ::std::string& k)                 { return data[k]; }
};

class StringDict {
public:
    ::std::map<::std::string,::std::string> data;
    void        set(const ::std::string& k, const ::std::string& v)              { data[k]=v; }
    ::std::string get(const ::std::string& k, const ::std::string& def="") const   { auto it=data.find(k); return it!=data.end()?it->second:def; }
    bool        hasKey(const ::std::string& k)                          const   { return data.count(k)>0; }
    void        remove(const ::std::string& k)                                  { data.erase(k); }
    int         size()                                                const   { return (int)data.size(); }
    void        clear()                                                       { data.clear(); }
    ::std::vector<::std::string> keys() const                                     { ::std::vector<::std::string> r; for(auto& p:data) r.push_back(p.first); return r; }
    ::std::string& operator[](const ::std::string& k)                            { return data[k]; }
};

// =============================================================================
// PSHAPE  --  reusable geometry (created with createShape / loadShape)
// =============================================================================

class PShape {
public:
    struct Vertex { float x,y,z,u,v; };

    ::std::vector<Vertex>  verts;
    ::std::vector<PShape>  children;
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
    void addChild(const PShape* s)    { if (s) addChild(*s); }
    ::std::string name; // id/name attribute from SVG
    ::std::vector<int> subpathStarts; // subpath start indices for multi-part fills
    ::std::vector<Vertex> anchorVerts; // raw anchor points (M/L/C endpoints only) for getVertex()

    PShape* getChild(int i)  { return i<(int)children.size()?&children[i]:nullptr; }
    PShape* getChild(const ::std::string& n) {
        for(auto& c:children) if(c.name==n) return &c;
        for(auto& c:children){ PShape* r=c.getChild(n); if(r) return r; }
        // Return a static empty shape rather than nullptr to prevent crashes
        static PShape _empty;
        fprintf(stderr,"[PShape] getChild('%s') not found\n", n.c_str());
        return &_empty;
    }
    PShape* getChild(const char* n) { return getChild(::std::string(n)); }
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
        for(auto& v:verts){minx=::std::min(minx,v.x);maxx=::std::max(maxx,v.x);miny=::std::min(miny,v.y);maxy=::std::max(maxy,v.y);}
        for(auto& c:children){const_cast<PShape&>(c).computeBounds();minx=::std::min(minx,c.verts.empty()?minx:minx);
            if(!c.verts.empty()){for(auto& v:c.verts){minx=::std::min(minx,v.x);maxx=::std::max(maxx,v.x);miny=::std::min(miny,v.y);maxy=::std::max(maxy,v.y);}}}
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


// =============================================================================
// PFONT
// =============================================================================

struct PFont {
    static ::std::vector<::std::string> list() {
        ::std::vector<::std::string> fonts;
        ::std::vector<::std::string> dirs = {"data",".","/usr/share/fonts","/usr/local/share/fonts"};
        #ifdef _WIN32
        dirs.push_back("C:/Windows/Fonts");
        #elif defined(__APPLE__)
        dirs.push_back("/Library/Fonts"); dirs.push_back("/System/Library/Fonts");
        #endif
        if(getenv("HOME")){ dirs.push_back(::std::string(getenv("HOME"))+"/.fonts"); }
        ::std::function<void(const ::std::string&)> scan=[&](const ::std::string& dir){
            #ifndef _WIN32
            DIR* d=opendir(dir.c_str()); if(!d) return;
            struct dirent* e;
            while((e=readdir(d))!=nullptr){
                ::std::string nm=e->d_name; if(nm=="."||nm=="..") continue;
                ::std::string path=dir+"/"+nm;
                if(e->d_type==DT_DIR){ scan(path); continue; }
                if(nm.size()>4){::std::string ext=nm.substr(nm.size()-4);
                    if(ext==".ttf"||ext==".otf"||ext==".TTF"||ext==".OTF") fonts.push_back(nm);}
            } closedir(d);
            #else
            WIN32_FIND_DATAA fd; HANDLE h2=FindFirstFileA((dir+"\\*").c_str(),&fd);
            if(h2==INVALID_HANDLE_VALUE) return;
            do { ::std::string nm=fd.cFileName; if(nm=="."||nm=="..") continue;
                if(fd.dwFileAttributes&FILE_ATTRIBUTE_DIRECTORY){scan(dir+"\\"+nm);continue;}
                if(nm.size()>4){::std::string ext=nm.substr(nm.size()-4);
                    if(ext==".ttf"||ext==".otf"||ext==".TTF"||ext==".OTF") fonts.push_back(nm);}
            } while(FindNextFileA(h2,&fd)); FindClose(h2);
            #endif
        };
        for(auto& d:dirs) scan(d);
        ::std::sort(fonts.begin(),fonts.end());
        fonts.erase(::std::unique(fonts.begin(),fonts.end()),fonts.end());
        return fonts;
    }
    ::std::string name;
    float size = 12;
    bool  loaded = false;

    PFont() = default;
    PFont(const ::std::string& n, float s) : name(n), size(s), loaded(true) {}
};


// =============================================================================
// TEXTURE
// =============================================================================


// =============================================================================
// BUFFERED I/O HELPERS
// =============================================================================

class BufferedReader {
    ::std::ifstream f;
public:
    explicit BufferedReader(const ::std::string& path) : f(path) {}
    bool        ready()    const { return f.is_open() && f.good(); }
    ::std::string readLine()       { ::std::string l; ::std::getline(f,l); return f?l:""; }
    void        close()          { f.close(); }
};

class PrintWriter {
    ::std::ofstream f;
public:
    explicit PrintWriter(const ::std::string& path) : f(path) {}
    template<typename T> void print(const T& v)   { f << v; }
    template<typename T> void println(const T& v) { f << v << "\n"; }
    void println() { f << "\n"; }
    void flush()   { f.flush(); }
    void close()   { f.close(); }
};


inline ::std::ifstream* createInput(const ::std::string& path)  { return new ::std::ifstream(path,::std::ios::binary); }
inline ::std::ofstream* createOutput(const ::std::string& path) { return new ::std::ofstream(path,::std::ios::binary); }
inline bool saveStream(const ::std::string& path, const ::std::vector<unsigned char>& data) { return saveBytes(path,data); }
inline void launch(const ::std::string& path) { system(path.c_str()); }

// Stubs (record/raw are not yet implemented)
inline void beginRecord(const ::std::string&, const ::std::string&) {}
inline void endRecord()  {}
inline void beginRaw(const ::std::string&, const ::std::string&)    {}
inline void endRaw()     {}

// =============================================================================
// PSHADER  --  GLSL shader wrapper
// =============================================================================

class PShader {
public:
    GLuint program = 0, vert = 0, frag = 0;
    ::std::string vertSrc, fragSrc;
    bool linked = false;

    PShader() = default;
    PShader(const ::std::string& v, const ::std::string& f) : vertSrc(v), fragSrc(f) {}

    static GLuint compileShader(GLenum type, const ::std::string& src) {
        GLuint s = glCreateShader(type);
        const char* c = src.c_str();
        glShaderSource(s, 1, &c, nullptr);
        glCompileShader(s);
        GLint ok; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
        if (!ok) { char log[512]; glGetShaderInfoLog(s,512,nullptr,log); ::std::cerr<<"Shader error: "<<log<<"\n"; }
        return s;
    }

    void compile() {
        vert    = compileShader(GL_VERTEX_SHADER,   vertSrc);
        frag    = compileShader(GL_FRAGMENT_SHADER, fragSrc);
        program = glCreateProgram();
        glAttachShader(program,vert); glAttachShader(program,frag);
        glLinkProgram(program);
        GLint ok; glGetProgramiv(program,GL_LINK_STATUS,&ok);
        if (!ok) { char log[512]; glGetProgramInfoLog(program,512,nullptr,log); ::std::cerr<<"Link error: "<<log<<"\n"; }
        linked = ok;
    }

    void bind()   { if (linked) glUseProgram(program); }
    void unbind() { glUseProgram(0); }

    void set(const ::std::string& n, float v)                      { glUniform1f(glGetUniformLocation(program,n.c_str()),v); }
    void set(const ::std::string& n, int v)                        { glUniform1i(glGetUniformLocation(program,n.c_str()),v); }
    void set(const ::std::string& n, double v)                     { set(n,(float)v); }
    void set(const ::std::string& n, float x, float y)             { glUniform2f(glGetUniformLocation(program,n.c_str()),x,y); }
    void set(const ::std::string& n, double x, double y)           { set(n,(float)x,(float)y); }
    void set(const ::std::string& n, float x, float y, float z)    { glUniform3f(glGetUniformLocation(program,n.c_str()),x,y,z); }
    void set(const ::std::string& n, double x, double y, double z) { set(n,(float)x,(float)y,(float)z); }
    void set(const ::std::string& n, float x, float y, float z, float w){ glUniform4f(glGetUniformLocation(program,n.c_str()),x,y,z,w); }
    void set(const ::std::string& n, double x, double y, double z, double w){ set(n,(float)x,(float)y,(float)z,(float)w); }

    ~PShader() { if(program)glDeleteProgram(program); if(vert)glDeleteShader(vert); if(frag)glDeleteShader(frag); }
    PShader(const PShader&) __attribute__((error(
        "E0003: PShader value-style copying is not supported. "
        "Declare PShader* instead of PShader. "
        "See " PROCESSING_WEBSITE_URL "/error/E0003.html"
    )));
    PShader& operator=(const PShader&) __attribute__((error(
        "E0003: PShader value-style assignment is not supported. "
        "Declare PShader* instead of PShader. "
        "See " PROCESSING_WEBSITE_URL "/error/E0003.html"
    )));
    PShader(PShader&& o) noexcept
        : program(o.program),vert(o.vert),frag(o.frag),
          vertSrc(o.vertSrc),fragSrc(o.fragSrc),linked(o.linked)
        { o.program=o.vert=o.frag=0; }
};


// =============================================================================
// GENERIC ARRAYLIST / HASHMAP  --  templated Java-style collections
// =============================================================================



template<typename K, typename V>
class HashMap {
public:
    ::std::map<K,V> data;

    void put(const K& k, const V& v)         { data[k]=v; }
    V&   get(const K& k)                     { return data[k]; }
    bool containsKey(const K& k)   const     { return data.count(k)>0; }
    bool containsValue(const V& v) const     { for(auto& p:data) if(p.second==v) return true; return false; }
    void remove(const K& k)                  { data.erase(k); }
    int  size()                    const     { return (int)data.size(); }
    bool isEmpty()                 const     { return data.empty(); }
    void clear()                             { data.clear(); }
    ::std::vector<K> keySet() const            { ::std::vector<K> r; for(auto& p:data) r.push_back(p.first); return r; }
    ::std::vector<V> values() const            { ::std::vector<V> r; for(auto& p:data) r.push_back(p.second); return r; }
    V& operator[](const K& k)               { return data[k]; }
};

// =============================================================================
// TABLEROW  --  single row accessor for Table iteration
// =============================================================================

class TableRow {
public:
    ::std::vector<::std::string>* row  = nullptr;
    ::std::vector<::std::string>* cols = nullptr;

    TableRow() = default;
    TableRow(::std::vector<::std::string>& r, ::std::vector<::std::string>& c) : row(&r), cols(&c) {}

    ::std::string getString(int i)                   const { return (row&&i<(int)row->size())?(*row)[i]:""; }
    ::std::string getString(const ::std::string& col)  const {
        if (!cols) return "";
        for (int i=0;i<(int)cols->size();i++) if((*cols)[i]==col) return getString(i);
        return "";
    }
    int   getInt(int i)                const { auto s=getString(i);   return s.empty()?0:std::stoi(s); }
    int   getInt(const ::std::string& c) const { auto s=getString(c);   return s.empty()?0:std::stoi(s); }
    float getFloat(int i)              const { auto s=getString(i);   return s.empty()?0:std::stof(s); }
    float getFloat(const ::std::string& c)const{ auto s=getString(c);   return s.empty()?0:std::stof(s); }
    void  setString(int i, const ::std::string& v) { if(row&&i<(int)row->size()) (*row)[i]=v; }
    void  setInt(int i, int v)                   { setString(i, ::std::to_string(v)); }
    void  setFloat(int i, float v)               { setString(i, ::std::to_string(v)); }
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
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void size(int w, int h){ _api::size(w,h); }
inline void size(int w, int h, int mode){ _api::size(w,h,mode); }
inline void fullScreen(){ _api::fullScreen(); }
inline void fullScreen(int mode){ _api::fullScreen(mode); }
template<typename A,typename B,typename C,typename D,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void line(A x1,B y1,C x2,D y2){ _api::line((float)x1,(float)y1,(float)x2,(float)y2); }
template<typename A,typename B,typename C,typename D,typename E,typename F,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&::std::is_arithmetic_v<E>&&::std::is_arithmetic_v<F>>>
inline void line(A x1,B y1,C z1,D x2,E y2,F z2){ _api::line((float)x1,(float)y1,(float)z1,(float)x2,(float)y2,(float)z2); }

// rect()
template<typename A,typename B,typename C,typename D,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void rect(A x,B y,C w,D h2){ _api::rect((float)x,(float)y,(float)w,(float)h2); }
template<typename A,typename B,typename C,typename D,typename E,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&::std::is_arithmetic_v<E>>>
inline void rect(A x,B y,C w,D h2,E r){ _api::rect((float)x,(float)y,(float)w,(float)h2,(float)r); }

// ellipse() / circle()
template<typename A,typename B,typename C,typename D,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void ellipse(A x,B y,C w,D h2){ _api::ellipse((float)x,(float)y,(float)w,(float)h2); }
template<typename A,typename B,typename C,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
inline void circle(A x,B y,C d){ _api::circle((float)x,(float)y,(float)d); }

// point()
template<typename A,typename B,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void point(A x,B y){ _api::point((float)x,(float)y); }
template<typename A,typename B,typename C,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
inline void point(A x,B y,C z){ _api::point((float)x,(float)y,(float)z); }

// triangle()
template<typename A,typename B,typename C,typename D,typename E,typename F,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&::std::is_arithmetic_v<E>&&::std::is_arithmetic_v<F>>>
inline void triangle(A x1,B y1,C x2,D y2,E x3,F y3){ _api::triangle((float)x1,(float)y1,(float)x2,(float)y2,(float)x3,(float)y3); }

// quad()
template<typename A,typename B,typename C,typename D,typename E,typename F,typename G,typename H,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&::std::is_arithmetic_v<E>&&::std::is_arithmetic_v<F>&&::std::is_arithmetic_v<G>&&::std::is_arithmetic_v<H>>>
inline void quad(A x1,B y1,C x2,D y2,E x3,F y3,G x4,H y4){ _api::quad((float)x1,(float)y1,(float)x2,(float)y2,(float)x3,(float)y3,(float)x4,(float)y4); }

// arc()
template<typename A,typename B,typename C,typename D,typename E,typename F,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&::std::is_arithmetic_v<E>&&::std::is_arithmetic_v<F>>>
inline void arc(A x,B y,C w,D h2,E start,F stop){ _api::arc((float)x,(float)y,(float)w,(float)h2,(float)start,(float)stop); }
template<typename A,typename B,typename C,typename D,typename E,typename F,typename G,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&::std::is_arithmetic_v<E>&&::std::is_arithmetic_v<F>&&::std::is_arithmetic_v<G>>>
inline void arc(A x,B y,C w,D h2,E start,F stop,G mode){ _api::arc((float)x,(float)y,(float)w,(float)h2,(float)start,(float)stop,(int)mode); }

// translate() / rotate() / scale()
template<typename A,typename B,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void translate(A x,B y){ _api::translate((float)x,(float)y); }
template<typename A,typename B,typename C,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
inline void translate(A x,B y,C z){ _api::translate((float)x,(float)y,(float)z); }
template<typename A,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
inline void rotate(A a){ _api::rotate((float)a); }
template<typename A,typename B,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void scale(A s1,B s2){ _api::scale((float)s1,(float)s2); }

// vertex()
template<typename A,typename B,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void vertex(A x,B y){ _api::vertex((float)x,(float)y); }
template<typename A,typename B,typename C,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
inline void vertex(A x,B y,C z){ _api::vertex((float)x,(float)y,(float)z); }
template<typename A,typename B,typename C,typename D,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void vertex(A x,B y,C u,D v2){ _api::vertex((float)x,(float)y,(float)u,(float)v2); }

// text() position
template<typename A,typename B,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void text(const ::std::string& s,A x,B y){ text(s,(float)x,(float)y); }
template<typename A,typename B,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void text(const char* s,A x,B y){ text(::std::string(s),(float)x,(float)y); }

// text with bounding box -- mixed arithmetic types
template<typename A,typename B,typename C,typename D,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void text(const ::std::string& s,A x,B y,C w,D h2){ text(s,(float)x,(float)y,(float)w,(float)h2); }
template<typename A,typename B,typename C,typename D,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline void text(const char* s,A x,B y,C w,D h2){ text(::std::string(s),(float)x,(float)y,(float)w,(float)h2); }template<typename V,typename A,typename B,
    typename=::std::enable_if_t<::std::is_arithmetic_v<V>&&::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline void text(V val,A x,B y){ text((float)val,(float)x,(float)y); }
// char overload -- display as character not number
template<typename A,typename B>
inline void text(char c,A x,B y){ text(::std::string(1,c),(float)x,(float)y); }
template<typename A,typename B,typename C,typename D>
inline void text(char c,A x,B y,C w,D h2){ text(::std::string(1,c),(float)x,(float)y,(float)w,(float)h2); }

// map() -- extremely common source of ambiguity
template<typename V,typename A,typename B,typename C,typename D,
    typename=::std::enable_if_t<::std::is_arithmetic_v<V>&&::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline float map(V value,A start1,B stop1,C start2,D stop2){
    return _api::map((float)value,(float)start1,(float)stop1,(float)start2,(float)stop2);
}

// constrain()
template<typename V,typename A,typename B,
    typename=::std::enable_if_t<::std::is_arithmetic_v<V>&&::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
inline float constrain(V val,A lo,B hi){ return _api::constrain((float)val,(float)lo,(float)hi); }

// lerp()
template<typename A,typename B,typename C,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
inline float lerp(A a,B b2,C t){ return _api::lerp((float)a,(float)b2,(float)t); }
template<class V,class A,class B>
inline float norm(V value,A start,B stop){ return map((float)value,(float)start,(float)stop,0.0f,1.0f); }

// bezier() -- 8 arithmetic params
template<typename A,typename B,typename C,typename D,
         typename E,typename F,typename G,typename H,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&
                               ::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&
                               ::std::is_arithmetic_v<E>&&::std::is_arithmetic_v<F>&&
                               ::std::is_arithmetic_v<G>&&::std::is_arithmetic_v<H>>>
inline void bezier(A x1,B y1,C cx1,D cy1,E cx2,F cy2,G x2,H y2){
    bezier((float)x1,(float)y1,(float)cx1,(float)cy1,
           (float)cx2,(float)cy2,(float)x2,(float)y2);
}

// bezierPoint / bezierTangent / curvePoint / curveTangent -- mixed types
template<typename A,typename B,typename C,typename D,typename T,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&
                               ::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&
                               ::Processing::_is_numeric_v<T>>>
inline float bezierPoint(A a,B b,C c,D d,T t){
    return bezierPoint((float)a,(float)b,(float)c,(float)d,(float)t);
}
template<typename A,typename B,typename C,typename D,typename T,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&
                               ::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&
                               ::Processing::_is_numeric_v<T>>>
inline float bezierTangent(A a,B b,C c,D d,T t){
    return bezierTangent((float)a,(float)b,(float)c,(float)d,(float)t);
}

// curve() -- 8 params
template<typename A,typename B,typename C,typename D,
         typename E,typename F,typename G,typename H,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&
                               ::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&
                               ::std::is_arithmetic_v<E>&&::std::is_arithmetic_v<F>&&
                               ::std::is_arithmetic_v<G>&&::std::is_arithmetic_v<H>>>
inline void curve(A x0,B y0,C x1,D y1,E x2,F y2,G x3,H y3){
    curve((float)x0,(float)y0,(float)x1,(float)y1,
          (float)x2,(float)y2,(float)x3,(float)y3);
}

// dist()
// NOTE: bodies compute directly with ::std::sqrt rather than delegating to
// dist((float)x,...) -- the cast-to-float form re-matched this same template
// (no non-template dist(float,float,float,float) exists at global scope),
// causing infinite recursion / stack-overflow segfault when called from a
// non-PApplet class (confirmed via compile-and-run: segfault in Ground's
// constructor in NonOrthogonalCollisionGroundSegments.pde).
// PApplet::dist is incomplete at this point in the header so can't be used
// here; inlining the computation directly is the cleanest fix.
template<typename A,typename B,typename C,typename D,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
inline float dist(A x1,B y1,C x2,D y2){ float dx=(float)x2-(float)x1,dy=(float)y2-(float)y1; return ::std::sqrt(dx*dx+dy*dy); }
template<typename A,typename B,typename C,typename D,typename E,typename F,
    typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&::std::is_arithmetic_v<E>&&::std::is_arithmetic_v<F>>>
inline float dist(A x1,B y1,C z1,D x2,E y2,F z2){ float dx=(float)x2-(float)x1,dy=(float)y2-(float)y1,dz=(float)z2-(float)z1; return ::std::sqrt(dx*dx+dy*dy+dz*dz); }
template<class A> inline float sq(A v){ return (float)v*(float)v; }
template<class A,class B> inline float mag(A x,B y){ return ::std::sqrt((float)x*(float)x+(float)y*(float)y); }
template<class A,class B,class C> inline float mag(A x,B y,C z){ return ::std::sqrt((float)x*(float)x+(float)y*(float)y+(float)z*(float)z); }
template<class A> inline float abs(A v){ return ::std::abs((float)v); }
template<class A> inline float floor(A v){ return ::std::floor((float)v); }
template<class A> inline float ceil(A v){ return ::std::ceil((float)v); }
template<class A> inline float round(A v){ return ::std::round((float)v); }
template<class A> inline float sqrt(A v){ return ::std::sqrt((float)v); }
template<class A,class B> inline float pow(A base,B exp){ return ::std::pow((float)base,(float)exp); }
template<class A> inline float exp(A v){ return ::std::exp((float)v); }
template<class A> inline float log(A v){ return ::std::log((float)v); }
// Trig
inline float sin(float x)     { return ::std::sin(x); }
inline float cos(float x)     { return ::std::cos(x); }
inline float tan(float x)     { return ::std::tan(x); }
inline float asin(float x)    { return ::std::asin(x); }
inline float acos(float x)    { return ::std::acos(x); }
inline float atan(float x)    { return ::std::atan(x); }
template<class A,class B> inline float atan2(A y,B x){ return ::std::atan2((float)y,(float)x); }
inline float degrees(float r) { return r*180.f/PI; }
inline float radians(float d) { return d*PI/180.f; }
// Color channel accessors
inline float alpha(color c)      { return (float)((c.value>>24)&0xFF); }
inline float red(color c)        { return (float)((c.value>>16)&0xFF); }
inline float green(color c)      { return (float)((c.value>>8)&0xFF); }
inline float blue(color c)       { return (float)(c.value&0xFF); }
inline float brightness(color c) { float r=red(c)/255.f,g=green(c)/255.f,b=blue(c)/255.f; return ::std::max({r,g,b})*255.f; }
inline float hue(color c)        { float r=red(c)/255.f,g=green(c)/255.f,b=blue(c)/255.f; float mx=::std::max({r,g,b}),mn=::std::min({r,g,b}); if(mx==mn) return 0; float h=0; if(mx==r) h=(g-b)/(mx-mn); else if(mx==g) h=2+(b-r)/(mx-mn); else h=4+(r-g)/(mx-mn); h*=60; if(h<0) h+=360; return h/360.f*255.f; }
inline float saturation(color c) { float r=red(c)/255.f,g=green(c)/255.f,b=blue(c)/255.f; float mx=::std::max({r,g,b}),mn=::std::min({r,g,b}); return mx==0?0:(mx-mn)/mx*255.f; }
// min/max -- 2 and 3 argument versions like Processing Java
template<class A,class B> inline auto min(A a,B b)->decltype((float)a){ return (float)a<(float)b?(float)a:(float)b; }
template<class A,class B,class C> inline auto min(A a,B b,C c)->decltype((float)a){ float fa=(float)a,fb=(float)b,fc=(float)c; return fa<fb?(fa<fc?fa:fc):(fb<fc?fb:fc); }
template<class A,class B> inline auto max(A a,B b)->decltype((float)a){ return (float)a>(float)b?(float)a:(float)b; }
template<class A,class B,class C> inline auto max(A a,B b,C c)->decltype((float)a){ float fa=(float)a,fb=(float)b,fc=(float)c; return fa>fb?(fa>fc?fa:fc):(fb>fc?fb:fc); }
// lerpColor and blendColor as free functions
inline color lerpColor(color c1, color c2, float t) {
    int r1=(c1.value>>16)&0xFF, g1=(c1.value>>8)&0xFF, b1=c1.value&0xFF, a1=(c1.value>>24)&0xFF;
    int r2=(c2.value>>16)&0xFF, g2=(c2.value>>8)&0xFF, b2=c2.value&0xFF, a2=(c2.value>>24)&0xFF;
    int r=(int)(r1+(r2-r1)*t), g=(int)(g1+(g2-g1)*t), b=(int)(b1+(b2-b1)*t), a=(int)(a1+(a2-a1)*t);
    return colorVal(r,g,b,a);
}
inline color blendColor(color c1, color c2, int mode) {
    if (mode == REPLACE) return c2;
    if (mode == BLEND) {
        float a=((c2.value>>24)&0xFF)/255.f;
        int r=(int)(((c1.value>>16)&0xFF)*(1-a)+((c2.value>>16)&0xFF)*a);
        int g=(int)(((c1.value>>8)&0xFF)*(1-a)+((c2.value>>8)&0xFF)*a);
        int b=(int)((c1.value&0xFF)*(1-a)+(c2.value&0xFF)*a);
        return colorVal(r,g,b,255);
    }
    if (mode == ADD) {
        int r=::std::min((int)(((c1.value>>16)&0xFF)+((c2.value>>16)&0xFF)),255);
        int g=::std::min((int)(((c1.value>>8)&0xFF)+((c2.value>>8)&0xFF)),255);
        int b=::std::min((int)((c1.value&0xFF)+(c2.value&0xFF)),255);
        return colorVal(r,g,b,255);
    }
    return c2; // fallback
}
// Array utility functions matching Processing Java
template<class T> inline ::std::vector<T> append(::std::vector<T> arr, T val) { arr.push_back(val); return arr; }
template<class T> inline ::std::vector<T> expand(::std::vector<T> arr) { arr.resize(arr.size()*2); return arr; }
template<class T> inline ::std::vector<T> expand(::std::vector<T> arr, int n) { arr.resize(n); return arr; }
template<class T> inline ::std::vector<T> shorten(::std::vector<T> arr) { if(!arr.empty()) arr.pop_back(); return arr; }
template<class T> inline ::std::vector<T> subset(const ::std::vector<T>& arr, int start) { return ::std::vector<T>(arr.begin()+start,arr.end()); }
template<class T> inline ::std::vector<T> subset(const ::std::vector<T>& arr, int start, int count) { return ::std::vector<T>(arr.begin()+start,arr.begin()+start+count); }
template<class T> inline ::std::vector<T> concat(::std::vector<T> a, const ::std::vector<T>& b) { a.insert(a.end(),b.begin(),b.end()); return a; }
template<class T> inline ::std::vector<T> reverse(::std::vector<T> arr) { ::std::reverse(arr.begin(),arr.end()); return arr; }
template<class T> inline ::std::vector<T> sort(::std::vector<T> arr) { ::std::sort(arr.begin(),arr.end()); return arr; }
template<class T> inline ::std::vector<T> sort(::std::vector<T> arr, int count) { ::std::sort(arr.begin(),arr.begin()+::std::min((int)arr.size(),count)); return arr; }


// image() -- mixed types, value and pointer variants
// image() -- draw a PImage to screen
// All user-facing overloads below; implementation in Processing.cpp

// =============================================================================
// PAPPLET  --  base class for user sketches
//
// All Processing state (mouseX, width, frameCount, ...) lives as member fields.
// All API functions (background, ellipse, fill, ...) are member methods.
// Processing.cpp defines PApplet::<method>(...) bodies.
//
// Usage:
//   struct Sketch : public PApplet {
//       bool firstMousePress = false;
//       void setup() override { size(640,360); }
//       void draw()  override { background(0); }
//       void mousePressed() override { firstMousePress = true; }
//   };
//   int main() { Sketch s; s.run(); return 0; }
// =============================================================================

// ── Event objects (mirrors Java Processing's MouseEvent / KeyEvent) ──────────
struct MouseEvent {
    float x, y;           // cursor position at time of event
    int   button;         // LEFT, CENTER, RIGHT, or -1
    int   count;          // click count (press/release/click) or wheel steps
    bool  shiftDown;
    bool  controlDown;
    bool  altDown;
    bool  metaDown;
    int   action;         // PRESS, RELEASE, CLICK, MOVE, DRAG, WHEEL
    // action constants
    static constexpr int PRESS   = 1;
    static constexpr int RELEASE = 2;
    static constexpr int CLICK   = 3;
    static constexpr int MOVE    = 4;
    static constexpr int DRAG    = 5;
    static constexpr int WHEEL   = 6;
    // convenience
    bool isShiftDown()   const { return shiftDown;   }
    bool isControlDown() const { return controlDown; }
    bool isAltDown()     const { return altDown;     }
    bool isMetaDown()    const { return metaDown;    }
    int  getButton()     const { return button;      }
    int  getCount()      const { return count;       }
    float getX()         const { return x;           }
    float getY()         const { return y;           }
    int  getAction()     const { return action;      }
};

struct KeyEvent {
    char16_t key;         // the character (matches PApplet::key)
    int      keyCode;     // VK_* code (matches PApplet::keyCode)
    bool     shiftDown;
    bool     controlDown;
    bool     altDown;
    bool     metaDown;
    int      action;      // PRESS, RELEASE, TYPE
    static constexpr int PRESS   = 1;
    static constexpr int RELEASE = 2;
    static constexpr int TYPE    = 3;
    bool isShiftDown()   const { return shiftDown;   }
    bool isControlDown() const { return controlDown; }
    bool isAltDown()     const { return altDown;     }
    bool isMetaDown()    const { return metaDown;    }
    char16_t getKey()    const { return key;         }
    int  getKeyCode()    const { return keyCode;     }
    int  getAction()     const { return action;      }
};

struct PApplet {
    // ── Public state (directly accessible in sketch code) ───────────────────
    int   winWidth = 640, winHeight = 480;
    int   logicalW = 640, logicalH  = 480;
    int   displayWidth = 0, displayHeight = 0;
    int   pixelWidth = 0, pixelHeight = 0;
    int   pixelDensityValue = 1;
    bool  isResizable = false;
    bool  focused = false;

    // width/height: direct accessors (no reference members — they break copy ctor)
    int& width  = logicalW;
    int& height = logicalH;

    float mouseX = 0, mouseY = 0, pmouseX = 0, pmouseY = 0;
    float mouseDX = 0, mouseDY = 0;
    bool  _mousePressed = false;
    int   mouseButton = -1;

    bool  _keyPressed = false;
    int   keyCode = 0;
    // char16_t, not char -- Java's "char" is genuinely a 16-bit type
    // (a UTF-16 code unit), wide enough to hold CODED's real value
    // (0xFFFF) without truncation. A C++ "char" is only 8 bits, so
    // "(char)CODED" always truncated down to 0xFF regardless of what
    // CODED's declared value was. char16_t is the real, character-
    // semantic C++ type for exactly this situation -- str()/String
    // concatenation get dedicated overloads (see below) so key still
    // displays/concatenates as a character, matching Java's actual
    // behavior, rather than falling back to int's numeric formatting.
    char16_t key = 0;
    bool  keys[349] = {};        // all currently held GLFW keycodes (AAA-style flat array)
    bool  mouseButtons[8] = {};  // all currently held mouse buttons (GLFW_MOUSE_BUTTON_*)
    bool  keysDown[256] = {};    // Processing-keycode-indexed, mirrors upper/lower letters
    bool  mouseDown[128] = {};   // Processing mouse-button-indexed (LEFT/RIGHT/CENTER)
    bool  _eventDrewSomething = false;        // set true if keyPressed()/mousePressed() drew
    bool  _backgroundCalledThisFrame = false; // set true if background() was called this frame

    int   frameCount = 1;
    float _frameRate = 60.0f;
    bool  looping = true;


    float fillR = 1, fillG = 1, fillB = 1, fillA = 1;
    float strokeR = 0, strokeG = 0, strokeB = 0, strokeA = 1;
    float strokeW = 1;
    bool  doFill = true, doStroke = true, smoothing = true;

    int   currentRectMode = CORNER;
    int   currentEllipseMode = CENTER;
    int   currentImageMode = CORNER;

    float tintR = 1, tintG = 1, tintB = 1, tintA = 1;
    bool  doTint = false;

    int   colorModeVal = RGB;
    float colorMaxH = 255.f, colorMaxS = 255.f, colorMaxB = 255.f, colorMaxA = 255.f;

    ::std::vector<unsigned int> pixels;

    ::std::string g_sketchName = "Sketch";

    // Event callback function pointers (wired by PApplet::run())
    ::std::function<void()>    _onKeyPressed;
    ::std::function<void()>    _onKeyReleased;
    ::std::function<void()>    _onKeyTyped;
    ::std::function<void()>    _onMousePressed;
    ::std::function<void()>    _onMouseReleased;
    ::std::function<void()>    _onMouseClicked;
    ::std::function<void()>    _onMouseMoved;
    ::std::function<void()>    _onMouseDragged;
    ::std::function<void(int)> _onMouseWheel;
    ::std::function<void()>    _onWindowMoved;
    ::std::function<void()>    _onWindowResized;

    void (*_wireCallbacksFn)() = nullptr;
    void (*_staticSketchSetup)() = nullptr;

    // ── Lifecycle (user overrides these) ────────────────────────────────────
    virtual void setup()         {}
    virtual void draw()          {}
    virtual void settings()      {}
    virtual void mousePressed()              {}
    virtual void mousePressed(MouseEvent e)  {}
    virtual void mouseReleased()             {}
    virtual void mouseReleased(MouseEvent e) {}
    virtual void mouseClicked()              {}
    virtual void mouseClicked(MouseEvent e)  {}
    virtual void mouseMoved()                {}
    virtual void mouseMoved(MouseEvent e)    {}
    virtual void mouseDragged()              {}
    virtual void mouseDragged(MouseEvent e)  {}
    virtual void mouseWheel(int delta)       {}
    virtual void mouseWheel(MouseEvent e)    {}
    virtual void keyPressed()                {}
    virtual void keyPressed(KeyEvent e)      {}
    virtual void keyReleased()               {}
    virtual void keyReleased(KeyEvent e)     {}
    virtual void keyTyped()                  {}
    virtual void keyTyped(KeyEvent e)        {}
    virtual void windowMoved()   {}
    virtual void windowResized() {}

    bool isMousePressed() const { return _mousePressed; }
    bool isKeyPressed()   const { return _keyPressed; }

    // ── run() ────────────────────────────────────────────────────────────────
    void run();

    // ── Static instance pointer ──────────────────────────────────────────────
    static PApplet* g_papplet;

    // ── Environment ─────────────────────────────────────────────────────────
    void size(int w, int h);
    void size(int w, int h, int renderer);
    void fullScreen();
    void fullScreen(int mode);
    void frameRate(int fps);
    void noLoop();
    void loop();
    void redraw();
    void exit_sketch();
    void exit() { exit_sketch(); }
    void windowTitle(const ::std::string& t);
    void windowMove(int x, int y);
    void windowResize(int w, int h);
    void windowResizable(bool r);
    void windowRatio(int w, int h);
    void pixelDensity(int d);
    void smooth(int level = 2); // real Processing's P2D/P3D default is smooth(2)
    void noSmooth();
    int  smoothLevel = 2; // current level, used by PGraphics to match the main canvas's AA quality
    void hint(int which);
    void cursor();
    void cursor(int type);
    void noCursor();
    void captureMouse();
    void releaseMouse();
    void setTitle(const ::std::string& t) { windowTitle(t); }
    void setLocation(int x, int y)      { windowMove(x,y); }
    void setResizable(bool r)           { windowResizable(r); }
    void setClipboard(const ::std::string& s);
    ::std::string getClipboard();
    void setWindowIcon(PImage* img);
    bool isCtrlDown();
    bool isShiftDown();
    bool isAltDown();
    int  displayDensity() { return pixelDensityValue; }
    void setProjection(int w, int h);  // also called from resize callback
    void enableDebugConsole();

    static unsigned long millis() {
        static auto _start = ::std::chrono::steady_clock::now();
        return (unsigned long)::std::chrono::duration_cast<::std::chrono::milliseconds>(
            ::std::chrono::steady_clock::now() - _start).count();
    }
    static int  second() { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_sec;      }
    static int  minute() { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_min;      }
    static int  hour()   { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_hour;     }
    static int  day()    { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_mday;     }
    static int  month()  { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_mon+1;    }
    static int  year()   { ::std::time_t t=::std::time(nullptr); return ::std::localtime(&t)->tm_year+1900;}
    static void delay(int ms) { ::std::this_thread::sleep_for(::std::chrono::milliseconds(ms)); }
    void thread(::std::function<void()> fn) { ::std::thread(fn).detach(); }

    // ── Style stack ──────────────────────────────────────────────────────────
    void push();
    void pop();
    void pushStyle();
    void popStyle();
    void pushMatrix();
    void popMatrix();

    // ── Color mode ───────────────────────────────────────────────────────────
    void  colorMode(int mode, float mx=255.f);
    void  colorMode(int mode, float mH, float mS, float mB, float mA=255.f);
    color makeColor(float a, float b, float c, float d=255);
    color makeColor(float gray, float alpha=255);
    float red(color c);
    float green(color c);
    float blue(color c);
    float alpha(color c);
    float brightness(color c);
    float saturation(color c);
    float hue(color c);
    color lerpColor(color c1, color c2, float t);

    // ── Background / clear ───────────────────────────────────────────────────
    void background(float gray, float a=255.f);
    void background(float r, float g, float b, float a=255.f);
    void background(color c);
    void background(const PImage& img);
    void background(const PImage* img) { if(img) background(*img); }
    void background(const PColor& c);
    template<typename A, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
    void background(A gray) { background((float)gray); }
    template<typename A, typename B, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
    void background(A gray, B a) { background((float)gray,(float)a); }
    template<typename A, typename B, typename C, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
    void background(A r, B g, C b) { background((float)r,(float)g,(float)b); }
    template<typename A, typename B, typename C, typename D, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
    void background(A r, B g, C b, D a) { background((float)r,(float)g,(float)b,(float)a); }
    void clear();


    // ── Fill ─────────────────────────────────────────────────────────────────
    void fill(float gray, float a);
    void fill(float gray);
    void fill(float r, float g, float b, float a);
    void fill(float r, float g, float b);
    void fill(color c);
    void fill(color c, float a);
    void fill(color c, int a) { fill(c,(float)a); }
    void fill(const PColor& c);
    void noFill();
    template<typename A, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
    void fill(A gray) { fill((float)gray); }
    template<typename A, typename B, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
    void fill(A gray, B a) { fill((float)gray,(float)a); }
    template<typename A, typename B, typename C, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
    void fill(A r, B g, C b) { fill((float)r,(float)g,(float)b); }
    template<typename A, typename B, typename C, typename D, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
    void fill(A r, B g, C b, D a) { fill((float)r,(float)g,(float)b,(float)a); }

    // ── Stroke ───────────────────────────────────────────────────────────────
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
    template<typename A, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
    void stroke(A gray) { stroke((float)gray); }
    template<typename A, typename B, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
    void stroke(A gray, B a) { stroke((float)gray,(float)a); }
    template<typename A, typename B, typename C, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
    void stroke(A r, B g, C b) { stroke((float)r,(float)g,(float)b); }
    template<typename A, typename B, typename C, typename D, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
    void stroke(A r, B g, C b, D a) { stroke((float)r,(float)g,(float)b,(float)a); }
    template<typename A, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
    void strokeWeight(A w) { strokeWeight((float)w); }

    // ── Tint ─────────────────────────────────────────────────────────────────
    void tint(float gray, float a);
    void tint(float gray);
    void tint(float r, float g, float b, float a);
    void tint(float r, float g, float b);
    void tint(const PColor& c);
    void noTint();
    template<typename A, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
    void tint(A gray) { tint((float)gray); }
    template<typename A, typename B, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
    void tint(A gray, B a) { tint((float)gray,(float)a); }
    template<typename A, typename B, typename C, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
    void tint(A r, B g, C b) { tint((float)r,(float)g,(float)b); }
    template<typename A, typename B, typename C, typename D, typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
    void tint(A r, B g, C b, D a) { tint((float)r,(float)g,(float)b,(float)a); }

    // ── Shape attributes ─────────────────────────────────────────────────────
    void rectMode(int mode);
    void ellipseMode(int mode);

    // ── 2D primitives ────────────────────────────────────────────────────────
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
    template<typename A,typename B,typename C,typename D,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
    void ellipse(A cx,B cy,C w,D h){ ellipse((float)cx,(float)cy,(float)w,(float)h); }
    template<typename A,typename B,typename C,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
    void circle(A cx,B cy,C d){ circle((float)cx,(float)cy,(float)d); }
    template<typename A,typename B,typename C,typename D,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
    void rect(A x,B y,C w,D h){ rect((float)x,(float)y,(float)w,(float)h); }
    template<typename A,typename B,typename C,typename D,typename E,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&::std::is_arithmetic_v<E>>>
    void rect(A x,B y,C w,D h,E r){ rect((float)x,(float)y,(float)w,(float)h,(float)r); }
    template<typename A,typename B,typename C,typename D,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
    void line(A x1,B y1,C x2,D y2){ line((float)x1,(float)y1,(float)x2,(float)y2); }
    template<typename A,typename B,typename C,typename D,typename E,typename F,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>&&::std::is_arithmetic_v<E>&&::std::is_arithmetic_v<F>>>
    void line(A x1,B y1,C z1,D x2,E y2,F z2){ line((float)x1,(float)y1,(float)z1,(float)x2,(float)y2,(float)z2); }
    template<typename A,typename B,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
    void point(A x,B y){ point((float)x,(float)y); }

    // ── 3D primitives ────────────────────────────────────────────────────────
    void box(float size);
    void box(float w, float h, float d);
    void sphere(float r);
    void sphereDetail(int res);
    void rotateX(float angle);
    void rotateY(float angle);
    void rotateZ(float angle);
    template<typename A,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
    void rotateX(A a){ rotateX((float)a); }
    template<typename A,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
    void rotateY(A a){ rotateY((float)a); }
    template<typename A,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
    void rotateZ(A a){ rotateZ((float)a); }

    // ── Vertex / shapes ──────────────────────────────────────────────────────
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
    void bezier(float x1,float y1,float cx1,float cy1,float cx2,float cy2,float x2,float y2);
    void curve(float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3);
    float bezierPoint(float a, float b, float c, float d, float t);
    float bezierTangent(float a, float b, float c, float d, float t);
    float curvePoint(float a, float b, float c, float d, float t);
    float curveTangent(float a, float b, float c, float d, float t);
    void curveDetail(int d);
    void curveTightness(float t);
    void bezierDetail(int d);

    // ── Matrix ───────────────────────────────────────────────────────────────
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
    template<typename A,typename B,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
    void translate(A x,B y){ translate((float)x,(float)y); }
    template<typename A,typename B,typename C,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>>>
    void translate(A x,B y,C z){ translate((float)x,(float)y,(float)z); }
    template<typename A,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
    void scale(A s){ scale((float)s); }
    template<typename A,typename B,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
    void scale(A sx,B sy){ scale((float)sx,(float)sy); }
    template<typename A,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>>>
    void rotate(A a){ rotate((float)a); }
    template<typename A,typename B,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
    void size(A w,B h){ size((int)w,(int)h); }
    template<typename A,typename B,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>>>
    void size(A w,B h,int renderer){ size((int)w,(int)h,renderer); }

    // ── Camera ───────────────────────────────────────────────────────────────
    void camera();
    void camera(float ex,float ey,float ez,float cx,float cy,float cz,float ux,float uy,float uz);
    void beginCamera();
    void endCamera();
    void perspective();
    void perspective(float fov, float aspect, float zNear, float zFar);
    void ortho();
    void ortho(float l, float r, float b, float t, float n, float f);
    void frustum(float l, float r, float b, float t, float n, float f);
    void printCamera();
    void printProjection();

    // ── Lights ───────────────────────────────────────────────────────────────
    void lights();
    void noLights();
    void ambientLight(float r, float g, float b);
    void ambientLight(float r, float g, float b, float x, float y, float z);
    void directionalLight(float r, float g, float b, float nx, float ny, float nz);
    void pointLight(float r, float g, float b, float x, float y, float z);
    void spotLight(float r, float g, float b, float x, float y, float z,
                   float nx, float ny, float nz, float angle, float conc);
    void lightFalloff(float c, float l, float q);
    void lightSpecular(float r, float g, float b);
    void normal(float nx, float ny, float nz);

    // ── Material ─────────────────────────────────────────────────────────────
    void ambient(float r, float g, float b);
    void ambient(color c);
    void emissive(float r, float g, float b);
    void emissive(color c);
    void specular(float r, float g, float b);
    void specular(color c);
    void shininess(float s);

    // ── Text ─────────────────────────────────────────────────────────────────
    void text(const ::std::string& msg, float x, float y);
    void text(const ::std::string& msg, float x, float y, float w, float h);
    void text(int val, float x, float y);
    void text(float val, float x, float y);
    void text(char c, float x, float y) { text(::std::string(1,c), x, y); }
    void text(char c, float x, float y, float w, float h) { text(::std::string(1,c), x, y, w, h); }
    template<typename X,typename Y,typename=::std::enable_if_t<::std::is_arithmetic_v<X>&&::std::is_arithmetic_v<Y>>>
    void text(char c, X x, Y y) { text(::std::string(1,c),(float)x,(float)y); }
    void textSize(float size);
    void textAlign(int alignX, int alignY=-1);
    void textLeading(float leading);
    void textMode(int mode);
    float textWidth(const ::std::string& s);
    float textAscent();
    float textDescent();
    PFont loadFont(const ::std::string& filename);
    PFont* createFont(const ::std::string& name, float size, bool smooth=true);
    void textFont(const PFont& font);
    void textFont(const PFont& font, float size);
    void textFont(const PFont* font) { if(font) textFont(*font); }
    void textFont(const PFont* font, float size) { if(font) textFont(*font,size); }
    template<typename X,typename Y,typename=::std::enable_if_t<::std::is_arithmetic_v<X>&&::std::is_arithmetic_v<Y>>>
    void text(const ::std::string& s,X x,Y y){ text(s,(float)x,(float)y); }
    template<typename X,typename Y,typename=::std::enable_if_t<::std::is_arithmetic_v<X>&&::std::is_arithmetic_v<Y>>>
    void text(int v,X x,Y y){ text(v,(float)x,(float)y); }
    template<typename X,typename Y,typename=::std::enable_if_t<::std::is_arithmetic_v<X>&&::std::is_arithmetic_v<Y>>>
    void text(float v,X x,Y y){ text(v,(float)x,(float)y); }

    // ── Image ────────────────────────────────────────────────────────────────
    PImage*    loadImage(const ::std::string& path);
    PImage*    createImage(int w, int h, int mode=1);
    PGraphics* createGraphics(int w, int h);
    PGraphics* createGraphics(int w, int h, int renderer); // matches size(w,h,renderer)
    PImage*    requestImage(const ::std::string& path);
    void imageMode(int mode);
    void image(PImage* img, float x, float y);
    void image(PImage* img, float x, float y, float w, float h);
    void image(PImage* img, float dx1,float dy1,float dx2,float dy2,float sx1,float sy1,float sx2,float sy2);
    void image(const PImage& img, float x, float y);
    void image(const PImage& img, float x, float y, float w, float h);
    void image(const PImage* img, float x, float y) { if(img) image(*img,x,y); }
    void image(const PImage* img, float x, float y, float w, float h) { if(img) image(*img,x,y,w,h); }
    void image(PGraphics& pg, float x, float y);
    void image(PGraphics& pg, float x, float y, float w, float h);
    // BUG FIX: sketches declare "PGraphics* pg;" (today's convention --
    // PGraphics is non-copyable, so it's always pointer-typed), and call
    // "image(pg, x, y);" with that pointer directly. Without an explicit
    // PGraphics* overload, C++ overload resolution silently converts
    // PGraphics* to PImage* (since PGraphics publicly inherits PImage)
    // and calls the PLAIN PImage* image() overload instead -- completely
    // bypassing drawPGraphicsRect() and its FBO-texture-aware rendering.
    // The buffer's content was always being rendered correctly
    // internally; it just never reached the screen, because the wrong
    // overload silently won via an implicit derived-to-base pointer
    // conversion that nothing here ever warned about.
    void image(PGraphics* pg, float x, float y) { if (pg) image(*pg, x, y); }
    void image(PGraphics* pg, float x, float y, float w, float h) { if (pg) image(*pg, x, y, w, h); }
    void filter(int mode);
    void filter(int mode, float param);
    void loadPixels();
    void updatePixels();
    color get(int x, int y);
    void  set(int x, int y, color c);
    void saveFrame(const ::std::string& filename="frame-####.png");
    void save(const ::std::string& filename);

    // ── Blend / clip ─────────────────────────────────────────────────────────
    void blendMode(int mode);
    void clip(float x, float y, float w, float h);
    void noClip();
    void blend(int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh, int mode);
    void copy(int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh);

    // ── Texture ──────────────────────────────────────────────────────────────
    void textureMode(int mode);
    void textureWrap(int mode);
    void texture(PImage& img);
    void texture(PImage* img) { if (img) texture(*img); }

    // ── Shader ───────────────────────────────────────────────────────────────
    PShader* loadShader(const ::std::string& fragPath, const ::std::string& vertPath="");
    void shader(PShader& s);
    void resetShader();

    // ── PShape ───────────────────────────────────────────────────────────────
    PShape  createShape(int kind=-1);
    PShape* loadShape(const ::std::string& path);
    void shape(const PShape& s, float x=0, float y=0);
    void shape(const PShape& s, float x, float y, float w, float h);
    void shape(const PShape* s, float x=0, float y=0) { if(s) shape(*s,x,y); }
    void shape(const PShape* s, float x, float y, float w, float h) { if(s) shape(*s,x,y,w,h); }
    void shapeMode(int mode);

    // ── Math ─────────────────────────────────────────────────────────────────
    static float sin(float x)   { return ::std::sin(x);  }
    static float cos(float x)   { return ::std::cos(x);  }
    static float tan(float x)   { return ::std::tan(x);  }
    static float asin(float x)  { return ::std::asin(x); }
    static float acos(float x)  { return ::std::acos(x); }
    static float atan(float x)  { return ::std::atan(x); }
    static float atan2(float y, float x) { return ::std::atan2(y,x); }
    static float sqrt(float x)  { return ::std::sqrt(x);  }
    static float sq(float x)    { return x*x; }
    static float abs(float x)   { return ::std::fabs(x); }
    static float ceil(float x)  { return ::std::ceil(x); }
    static float floor(float x) { return ::std::floor(x); }
    static float round(float x) { return ::std::round(x); }
    static float exp(float x)   { return ::std::exp(x); }
    static float log(float x)   { return ::std::log(x); }
    static float pow(float b, float e) { return ::std::pow(b,e); }
    static float mag(float x, float y)          { return ::std::sqrt(x*x+y*y); }
    static float mag(float x, float y, float z) { return ::std::sqrt(x*x+y*y+z*z); }
    static float norm(float v, float lo, float hi) { return (v-lo)/(hi-lo); }
    static float degrees(float r) { return r*180.f/PI; }
    static float radians(float d) { return d*PI/180.f; }
    static float lerp(float a, float b, float t) { return a+t*(b-a); }
    static float dist(float x1,float y1,float x2,float y2)
        { float dx=x2-x1,dy=y2-y1; return ::std::sqrt(dx*dx+dy*dy); }
    static float dist(float x1,float y1,float z1,float x2,float y2,float z2)
        { float dx=x2-x1,dy=y2-y1,dz=z2-z1; return ::std::sqrt(dx*dx+dy*dy+dz*dz); }
    static float map(float v,float i0,float i1,float o0,float o1) { if (i1==i0) return o0; return o0+(v-i0)*(o1-o0)/(i1-i0); }
    static float constrain(float v,float lo,float hi) { return v<lo?lo:(v>hi?hi:v); }
    static float max(float a,float b)          { return a>b?a:b; }
    static float min(float a,float b)          { return a<b?a:b; }
    static float max(float a,float b,float c)  { float m=a>b?a:b; return m>c?m:c; }
    static float min(float a,float b,float c)  { float m=a<b?a:b; return m<c?m:c; }
    static bool isNaN(float v)      { return ::std::isnan(v); }
    static bool isInfinite(float v) { return ::std::isinf(v); }
    template<typename A,typename B,typename C,typename D,typename=::std::enable_if_t<::Processing::_is_numeric_v<A>&&::Processing::_is_numeric_v<B>&&::Processing::_is_numeric_v<C>&&::Processing::_is_numeric_v<D>>>
    static float dist(A x1,B y1,C x2,D y2){ float dx=(float)x2-(float)x1,dy=(float)y2-(float)y1; return ::std::sqrt(dx*dx+dy*dy); }

    // ── Random / noise ───────────────────────────────────────────────────────
    void  randomSeed(long s);
    float random(float lo, float hi);
    float random(float hi);
    float randomGaussian();
    void  noiseSeed(int seed);
    void  noiseDetail(int octaves, float falloff=0.5f);
    float noise(float x);
    float noise(float x, float y);
    float noise(float x, float y, float z);

    // ── Print ────────────────────────────────────────────────────────────────
    template<typename T> static void print(const T& v)   { ::std::cout << v; ::std::cout.flush(); }
    template<typename T> static void println(const T& v) { ::std::cout << v << "\n"; ::std::cout.flush(); }
    static void println() { ::std::cout << "\n"; ::std::cout.flush(); }

    // ── String helpers ───────────────────────────────────────────────────────
    static ::std::string str(int v)   { return ::std::to_string(v); }
    static ::std::string str(float v) { return ::std::to_string(v); }
    static ::std::string str(double v) { return ::std::to_string(v); }
    static ::std::string str(bool v)  { return v?"true":"false"; }
    static ::std::string str(char v)  { return ::std::string(1,v); }
    static ::std::string str(char16_t v)  { return ::std::string(1,(char)v); }
    static ::std::vector<::std::string> split(const ::std::string& s, char d);
    static ::std::vector<::std::string> splitTokens(const ::std::string& s, const ::std::string& d);
    static ::std::string join(const ::std::vector<::std::string>& v, const ::std::string& sep);
    static ::std::string trim(const ::std::string& s);
    static ::std::string nf(int v);
    static ::std::string nf(int v, int digits);
    static ::std::string nf(float v, int digits);
    static ::std::string nf(float v, int left, int right);
    static ::std::vector<::std::string> nf(const ::std::vector<int>& nums);
    static ::std::vector<::std::string> nf(const ::std::vector<int>& nums, int digits);
    static ::std::vector<::std::string> nf(const ::std::vector<float>& nums, int left, int right);
    static ::std::string nfc(int v);
    static ::std::string nfc(float v, int right);
    static ::std::vector<::std::string> nfc(const ::std::vector<int>& nums);
    static ::std::vector<::std::string> nfc(const ::std::vector<float>& nums, int right);
    static ::std::string nfp(int v);
    static ::std::string nfp(int v, int digits);
    static ::std::string nfp(float v, int left, int right);
    static ::std::vector<::std::string> nfp(const ::std::vector<int>& nums);
    static ::std::vector<::std::string> nfp(const ::std::vector<int>& nums, int digits);
    static ::std::vector<::std::string> nfp(const ::std::vector<float>& nums, int left, int right);
    static ::std::string nfs(int v);
    static ::std::string nfs(int v, int digits);
    static ::std::string nfs(float v, int left, int right);
    static ::std::vector<::std::string> nfs(const ::std::vector<int>& nums);
    static ::std::vector<::std::string> nfs(const ::std::vector<int>& nums, int digits);
    static ::std::vector<::std::string> nfs(const ::std::vector<float>& nums, int left, int right);
    static ::std::string hex(int v);
    static ::std::string binary(int v);
    static int   toInt(const ::std::string& s)     { try{return ::std::stoi(s);}catch(...){return 0;} }
    static float toFloat(const ::std::string& s)   { try{return ::std::stof(s);}catch(...){return 0;} }
    static bool  toBoolean(const ::std::string& s) { return s=="true"||s=="1"||s=="yes"; }

    // ── File I/O ─────────────────────────────────────────────────────────────
    static ::std::vector<::std::string>   loadStrings(const ::std::string& path);
    static bool saveStrings(const ::std::string& path, const ::std::vector<::std::string>& lines);
    static ::std::vector<unsigned char> loadBytes(const ::std::string& path);
    static bool saveBytes(const ::std::string& path, const ::std::vector<unsigned char>& d);
    static BufferedReader* createReader(const ::std::string& path);
    static PrintWriter*    createWriter(const ::std::string& path);
    static ::std::string selectInput(const ::std::string& prompt="",  const ::std::string& filter="");
    static ::std::string selectOutput(const ::std::string& prompt="", const ::std::string& filter="");
    static ::std::string selectFolder(const ::std::string& prompt="");

    // ── JSON ─────────────────────────────────────────────────────────────────
    static JSONValue   parseJSON(const ::std::string& src);
    static ::std::string toJSONString(const JSONValue& v, int indent=0);
    static JSONValue   loadJSONObject(const ::std::string& path);
    static JSONValue   loadJSONArray(const ::std::string& path);
    static bool saveJSONObject(const ::std::string& path, const JSONValue& v, int indent=2);
    static bool saveJSONArray(const ::std::string& path,  const JSONValue& v, int indent=2);
    static JSONValue parseJSONObject(const ::std::string& s) { return parseJSON(s); }
    static JSONValue parseJSONArray(const ::std::string& s)  { return parseJSON(s); }

    // ── XML ──────────────────────────────────────────────────────────────────
    static XML  loadXML(const ::std::string& path);
    static XML  parseXML(const ::std::string& src);
    static bool saveXML(const ::std::string& path, const XML& x);

    // ── Table ────────────────────────────────────────────────────────────────
    static Table* loadTable(const ::std::string& path, const ::std::string& options="header");
    static bool   saveTable(const ::std::string& path, const Table& t, const ::std::string& ext="csv");

    // ── No-ops ───────────────────────────────────────────────────────────────
    static void beginRecord(const ::std::string&, const ::std::string&) {}
    static void endRecord()  {}
    static void beginRaw(const ::std::string&, const ::std::string&)    {}
    static void endRaw()     {}

    // ── Private state ────────────────────────────────────────────────────────
    // (implementation details, not for user code)
    GLFWwindow* gWindow = nullptr;
    bool  mouseInWindow = false;
    bool  is3DMode = false;
    int   sphereRes = 48;
    int   curveDetailVal = 20;
    float curveTightnessVal = 0.0f;
    int   bezierDetailVal = 60;
    bool  lightsEnabled = false;
    int   lightIndex = 0;
    bool  redrawOnce = false;
    double targetFrameTime = 1.0 / 60.0;
    bool  defaultP3D = false;
    bool  _setupDone = false;
    bool  _inWinsizeCb = false;
    bool  mouseWasPressed = false;
    bool  g_pendingKeyPressed = false;
    int   g_currentMods = 0;
    char  _g_lastChar = 0;

    float pendingSpecR = 0, pendingSpecG = 0, pendingSpecB = 0;
    float lightConcentration[8] = { 0, 0, 0, 0, 0, 0, 0, 0 };
    float lightCutoffCos[8]     = { -1, -1, -1, -1, -1, -1, -1, -1 };

    int   shapeKind = -1;
    bool  inShape = false, inContour = false;
    bool  shape3D = false;
    ::std::vector<::std::pair<float,float>>      shapeVerts;
    ::std::vector<::std::array<float,3>>         shapeVerts3D;
    ::std::vector<::std::pair<float,float>>      contourVerts;

    struct Style {
        float fillR, fillG, fillB, fillA;
        float strokeR, strokeG, strokeB, strokeA, strokeW;
        bool  doFill, doStroke;
        int   rectMode, ellipseMode, imageMode;
        float tintR, tintG, tintB, tintA;
        bool  doTint;
        int   colorMode;
        float cmH, cmS, cmB, cmA;
    };
    ::std::vector<Style> styleStack;

    float bgR = 0.8f, bgG = 0.8f, bgB = 0.8f, bgA = 1.0f;

    // Noise
    static const int PERLIN_YWRAPB = 4;
    static const int PERLIN_YWRAP  = 1 << PERLIN_YWRAPB;
    static const int PERLIN_ZWRAPB = 8;
    static const int PERLIN_ZWRAP  = 1 << PERLIN_ZWRAPB;
    static const int PERLIN_SIZE   = 4095;
    int   noiseOctaves = 4;
    float noiseFalloff = 0.5f;
    float perlinTable[PERLIN_SIZE + 1] = {};
    bool  perlinInit = false;

    ::std::mt19937 _rng{::std::mt19937::default_seed};
    ::std::uniform_real_distribution<float> _rngDist{0.0f, 1.0f};

    // Font / text
    float g_textSize = 14.0f;
    int   g_textAlignX = LEFT_ALIGN;
    int   g_textAlignY = BASELINE;
    float g_textLeading = 0.0f;
#if PROCESSING_HAS_STB_TRUETYPE
    struct TTFAtlas {
        GLuint texID = 0;
        stbtt_bakedchar chars[96];
    };
    struct TTFFont {
        stbtt_fontinfo info;
        ::std::vector<unsigned char> data;
        ::std::unordered_map<int, TTFAtlas> atlasCache; // keyed by round(pixelSize*2)
        TTFAtlas* current = nullptr;
        GLuint texID = 0;
        stbtt_bakedchar* chars = nullptr;
        int atlasW = 512, atlasH = 512;
        float bakeSize = 0.0f;
        bool  loaded   = false;
    };
    TTFFont g_ttf;
    float ttfStrWidth(const ::std::string& s);
    void  drawTTFStr(float x, float y, const ::std::string& s);
#endif
    ::std::vector<PFont> _fontPool;
    PFont currentFont;

    // FBO persistence
    GLuint persistFBO = 0, persistTex = 0;

    // Phong shader
    GLuint phongProg = 0;

    // Texture / shader
    int textureModeVal = IMAGE;
    int textureWrapVal = CLAMP;
    PShader* activeShader = nullptr;

    // PShape draw mode
    int shapeDrawMode = CORNER;

    // OBJ loader scratch
    ::std::string objDir;

    virtual ~PApplet() = default;

protected:
    // Internal helpers (defined in Processing.cpp)
    void applyFill();
    void applyStroke();
    void restoreLighting();
    void initPersistFBO();
    void saveToPersist();
    void restoreFromPersist();
    void _restoreMainCanvas();
    void drawEllipseGeom(float cx,float cy,float rx,float ry,float sa=0,float ea=TWO_PI,int segs=-1);
    void resolveRect(float& x,float& y,float& w,float& h);
    void resolveEllipse(float& cx,float& cy,float& rx,float& ry);
    void setFillFromColor(color c);
    void setStrokeFromColor(color c);
    void applyDefaultCamera();
    void applyStandardModelview();
    void captureStyle(Style& s);
    void restoreStyle(const Style& s);
    void initPhongShader();
    void initPerlin(unsigned int seed);
    void bakeAtlas(float pixelSize);
    bool loadTTFFile(const ::std::string& path);
    bool tryLoadTTF(const ::std::string& path, float size);
    void drawBitmapStr(float x, float y, const ::std::string& s, int scale);
    float bitmapStrWidth(const ::std::string& s, int scale);
#if PROCESSING_HAS_STB_TRUETYPE
#endif
    float getLineWidth(const ::std::string& line);
    void  renderText(const ::std::string& msg, float x, float y);
    void  drawImageRect(PImage& img, float x, float y, float w, float h);
    void  drawImage_impl(PImage* img, float x, float y, float w, float h);
    void  drawPGraphicsRect(PGraphics& pg, float x, float y, float w, float h);
    void  drawPShape(const PShape& s, float x, float y, float w=-1, float h=-1, bool parentStyleEnabled=true);
    void  hsbToRgb(float h, float s, float b, float& outR, float& outG, float& outB);
    void  setBg(float r, float g, float b, float a);

    // GLFW callback installers / forwarders
    void installCallbacks();

    // Static GLFW callbacks (forward to g_papplet)
    static void cb_cursor_pos(GLFWwindow*, double x, double y);
    static void cb_mouse_btn(GLFWwindow*, int btn, int action, int mods);
    static void cb_scroll(GLFWwindow*, double, double yoffset);
    static void cb_char(GLFWwindow*, unsigned int codepoint);
    static void cb_key(GLFWwindow*, int k, int scancode, int action, int mods);
    static void cb_focus(GLFWwindow*, int f);
    static void cb_winpos(GLFWwindow*, int, int);
    static void cb_winsize(GLFWwindow*, int lw, int lh);
    static void cb_fbsize(GLFWwindow*, int fw, int fh);
    static void cb_winrefresh(GLFWwindow* w);
    static int  glfw_to_java_keycode(int k);
};

inline PApplet* PApplet::g_papplet = nullptr;
// ── _api:: implementations (after PApplet is complete) ───────────────────────
namespace _api {
    inline void line(float x1,float y1,float x2,float y2){ if(PApplet::g_papplet) PApplet::g_papplet->line(x1,y1,x2,y2); }
    inline void line(float x1,float y1,float z1,float x2,float y2,float z2){ if(PApplet::g_papplet) PApplet::g_papplet->line(x1,y1,z1,x2,y2,z2); }
    inline void rect(float x,float y,float w,float h){ if(PApplet::g_papplet) PApplet::g_papplet->rect(x,y,w,h); }
    inline void rect(float x,float y,float w,float h,float r){ if(PApplet::g_papplet) PApplet::g_papplet->rect(x,y,w,h,r); }
    inline void ellipse(float x,float y,float w,float h){ if(PApplet::g_papplet) PApplet::g_papplet->ellipse(x,y,w,h); }
    inline void circle(float x,float y,float d){ if(PApplet::g_papplet) PApplet::g_papplet->circle(x,y,d); }
    inline void point(float x,float y){ if(PApplet::g_papplet) PApplet::g_papplet->point(x,y); }
    inline void point(float x,float y,float z){ if(PApplet::g_papplet) PApplet::g_papplet->point(x,y,z); }
    inline void triangle(float x1,float y1,float x2,float y2,float x3,float y3){ if(PApplet::g_papplet) PApplet::g_papplet->triangle(x1,y1,x2,y2,x3,y3); }
    inline void quad(float x1,float y1,float x2,float y2,float x3,float y3,float x4,float y4){ if(PApplet::g_papplet) PApplet::g_papplet->quad(x1,y1,x2,y2,x3,y3,x4,y4); }
    inline void arc(float x,float y,float w,float h,float s,float e){ if(PApplet::g_papplet) PApplet::g_papplet->arc(x,y,w,h,s,e); }
    inline void arc(float x,float y,float w,float h,float s,float e,int m){ if(PApplet::g_papplet) PApplet::g_papplet->arc(x,y,w,h,s,e,m); }
    inline void translate(float x,float y){ if(PApplet::g_papplet) PApplet::g_papplet->translate(x,y); }
    inline void translate(float x,float y,float z){ if(PApplet::g_papplet) PApplet::g_papplet->translate(x,y,z); }
    inline void rotate(float a){ if(PApplet::g_papplet) PApplet::g_papplet->rotate(a); }
    inline void scale(float s1,float s2){ if(PApplet::g_papplet) PApplet::g_papplet->scale(s1,s2); }
    inline void vertex(float x,float y){ if(PApplet::g_papplet) PApplet::g_papplet->vertex(x,y); }
    inline void vertex(float x,float y,float z){ if(PApplet::g_papplet) PApplet::g_papplet->vertex(x,y,z); }
    inline void vertex(float x,float y,float u,float v){ if(PApplet::g_papplet) PApplet::g_papplet->vertex(x,y,u,v); }
    inline void bezier(float x1,float y1,float cx1,float cy1,float cx2,float cy2,float x2,float y2){ if(PApplet::g_papplet) PApplet::g_papplet->bezier(x1,y1,cx1,cy1,cx2,cy2,x2,y2); }
    inline void curve(float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3){ if(PApplet::g_papplet) PApplet::g_papplet->curve(x0,y0,x1,y1,x2,y2,x3,y3); }
    inline void text(float v,float x,float y){ if(PApplet::g_papplet) PApplet::g_papplet->text(v,x,y); }
    inline void text(const ::std::string& s,float x,float y){ if(PApplet::g_papplet) PApplet::g_papplet->text(s,x,y); }
    inline void text(const ::std::string& s,float x,float y,float w,float h){ if(PApplet::g_papplet) PApplet::g_papplet->text(s,x,y,w,h); }
    inline float map(float v,float i0,float i1,float o0,float o1){ return PApplet::map(v,i0,i1,o0,o1); }
    inline float constrain(float v,float lo,float hi){ return PApplet::constrain(v,lo,hi); }
    inline float lerp(float a,float b,float t){ return PApplet::lerp(a,b,t); }
    inline void fill(float g){ if(PApplet::g_papplet) PApplet::g_papplet->fill(g); }
    inline void fill(float g,float a){ if(PApplet::g_papplet) PApplet::g_papplet->fill(g,a); }
    inline void fill(float r,float g,float b){ if(PApplet::g_papplet) PApplet::g_papplet->fill(r,g,b); }
    inline void fill(float r,float g,float b,float a){ if(PApplet::g_papplet) PApplet::g_papplet->fill(r,g,b,a); }
    inline void stroke(float g){ if(PApplet::g_papplet) PApplet::g_papplet->stroke(g); }
    inline void stroke(float g,float a){ if(PApplet::g_papplet) PApplet::g_papplet->stroke(g,a); }
    inline void stroke(float r,float g,float b){ if(PApplet::g_papplet) PApplet::g_papplet->stroke(r,g,b); }
    inline void stroke(float r,float g,float b,float a){ if(PApplet::g_papplet) PApplet::g_papplet->stroke(r,g,b,a); }
    inline void background(float g){ if(PApplet::g_papplet) PApplet::g_papplet->background(g); }
    inline void background(float g,float a){ if(PApplet::g_papplet) PApplet::g_papplet->background(g,a); }
    inline void background(float r,float g,float b){ if(PApplet::g_papplet) PApplet::g_papplet->background(r,g,b); }
    inline void background(float r,float g,float b,float a){ if(PApplet::g_papplet) PApplet::g_papplet->background(r,g,b,a); }
    inline void tint(float g){ if(PApplet::g_papplet) PApplet::g_papplet->tint(g); }
    inline void tint(float g,float a){ if(PApplet::g_papplet) PApplet::g_papplet->tint(g,a); }
    inline void tint(float r,float g,float b){ if(PApplet::g_papplet) PApplet::g_papplet->tint(r,g,b); }
    inline void tint(float r,float g,float b,float a){ if(PApplet::g_papplet) PApplet::g_papplet->tint(r,g,b,a); }
    inline void strokeWeight(float w){ if(PApplet::g_papplet) PApplet::g_papplet->strokeWeight(w); }
    inline void size(int w, int h)        { if(PApplet::g_papplet) PApplet::g_papplet->size(w,h); }
    inline void size(int w, int h, int m) { if(PApplet::g_papplet) PApplet::g_papplet->size(w,h,m); }
    inline void fullScreen()              { if(PApplet::g_papplet) PApplet::g_papplet->fullScreen(); }
    inline void fullScreen(int m)         { if(PApplet::g_papplet) PApplet::g_papplet->fullScreen(m); }
} // namespace _api




inline void PGraphics::beginDraw() {
    // Guard against beginDraw() called again before a matching endDraw().
    // savedViewport is a single field -- without this guard, a second
    // beginDraw() would overwrite the FIRST call's saved (correct) main-
    // canvas viewport with the buffer's OWN viewport (since that's what
    // the first beginDraw() just set), so the eventual endDraw() restores
    // the wrong thing entirely, corrupting the main canvas's viewport for
    // the rest of the frame (everything renders squished into a viewport
    // sized for the buffer instead of the real window). Calling
    // beginDraw() twice without an intervening endDraw() is unusual
    // sketch code to begin with, but it must not corrupt unrelated state.
    if (active) return;
    // ROOT-CAUSE FIX: previously captured "whatever the viewport happens
    // to be right now" via glGetIntegerv -- but if an EARLIER beginDraw()
    // (on this buffer or a different, abandoned one) was never matched
    // with endDraw(), the viewport at THIS moment may already be
    // corrupted (some other buffer's small viewport, not the real main
    // canvas's), and faithfully "restoring" that corrupted value just
    // propagates it forward into the next thing drawn -- this was the
    // actual cause of "the same line() call lands in a different place
    // depending on what PGraphics code ran earlier in the same frame."
    // Instead, always compute the TRUE main-canvas viewport directly
    // from PApplet's own known-good framebuffer dimensions, never trust
    // glGetIntegerv's possibly-already-wrong live value here.
    if (PApplet::g_papplet) {
        // fbW/fbH (the real framebuffer pixel size, accounting for
        // HiDPI) are a file-local static inside Processing.cpp, not
        // reachable from this header -- logicalW/logicalH ARE real
        // PApplet members though, and are exactly what _endDrawImpl()
        // already uses for restoring the projection's glOrtho call (see
        // the matching fix there), so using them here too keeps both
        // halves of the restore logically consistent with each other,
        // even though they're not pixel-perfect on a HiDPI display.
        savedViewport[0] = 0;
        savedViewport[1] = 0;
        savedViewport[2] = PApplet::g_papplet->logicalW;
        savedViewport[3] = PApplet::g_papplet->logicalH;
    } else {
        glGetIntegerv(GL_VIEWPORT, savedViewport);
    }
    // Render into the multisampled FBO when available (antialiased
    // drawing); the resolve target (fbo) gets filled via a blit in
    // _endDrawImpl(), not rendered into directly, when samples > 0.
    glBindFramebuffer(GL_FRAMEBUFFER, samples > 0 ? msaaFbo : fbo);
    glViewport(0, 0, width, height);
    glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity();
    if (is3D) {
        // BUG FIX: a P3D buffer previously got the SAME flat 2D ortho
        // projection as a plain 2D buffer (depth range -1..1), clipping
        // away any real 3D content entirely -- a box(80) translated even
        // slightly in Z extends far outside that paper-thin depth range
        // and never reaches the screen. This mirrors PApplet::
        // applyDefaultCamera()'s real perspective setup, scoped to this
        // buffer's own width/height instead of the main canvas's
        // logicalW/logicalH.
        float eyeZ  = ((float)height / 2.0f) / ::std::tan(PI * 60.0f / 360.0f);
        float near_ = eyeZ / 10.0f;
        float far_  = eyeZ * 10.0f;
        // glScalef(1.0f, -1.0f, 1.0f); // TEMPORARILY REMOVED for flip testing
        {
            // Inlined equivalent of _gluPerspective(60, width/height, near_, far_)
            // -- that helper is `static` (file-local) inside Processing.cpp,
            // not reachable from here, so the same small amount of matrix
            // construction is duplicated rather than changing its linkage
            // just for this one additional call site.
            double f = 1.0 / ::std::tan(60.0 * M_PI / 360.0);
            double aspect = (double)width / (double)height;
            double m[16] = {0};
            m[0]  = f / aspect;
            m[5]  = f;
            m[10] = (far_ + near_) / (near_ - far_);
            m[11] = -1.0;
            m[14] = (2.0 * far_ * near_) / (near_ - far_);
            glMultMatrixd(m);
        }
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity();
        {
            // Inlined equivalent of _gluLookAt(width/2, height/2, eyeZ,
            //                                  width/2, height/2, 0, 0, 1, 0)
            double ex = width/2.0, ey = height/2.0, ez = eyeZ;
            double cx = width/2.0, cy = height/2.0, cz = 0.0;
            double ux = 0.0, uy = 1.0, uz = 0.0;
            double fx = cx-ex, fy = cy-ey, fz = cz-ez;
            double fl = ::std::sqrt(fx*fx+fy*fy+fz*fz); fx/=fl; fy/=fl; fz/=fl;
            double sx = fy*uz - fz*uy, sy = fz*ux - fx*uz, sz = fx*uy - fy*ux;
            double sl = ::std::sqrt(sx*sx+sy*sy+sz*sz); sx/=sl; sy/=sl; sz/=sl;
            double ux2 = sy*fz - sz*fy, uy2 = sz*fx - sx*fz, uz2 = sx*fy - sy*fx;
            double m[16] = {
                sx, ux2, -fx, 0,
                sy, uy2, -fy, 0,
                sz, uz2, -fz, 0,
                0,  0,   0,   1
            };
            glMultMatrixd(m);
            glTranslated(-ex, -ey, -ez);
        }
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
    } else {
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity();
        glScalef(1.0f, -1.0f, 1.0f);
        glTranslatef(0.0f, -(float)height, 0.0f);
    }
    active = true;
    {
        GLint vp[4]; glGetIntegerv(GL_VIEWPORT, vp);
        double mv[16], proj[16];
        glGetDoublev(GL_MODELVIEW_MATRIX, mv);
        glGetDoublev(GL_PROJECTION_MATRIX, proj);
        PDEBUG("PGraphics::beginDraw: width=%d height=%d samples=%d msaaFbo=%u fbo=%u this=%p\n",
               width, height, samples, msaaFbo, fbo, (void*)this);
        PDEBUG("  GL viewport: x=%d y=%d w=%d h=%d\n", vp[0], vp[1], vp[2], vp[3]);
        PDEBUG("  proj[0]=%.6f proj[5]=%.6f proj[12]=%.6f proj[13]=%.6f\n", proj[0], proj[5], proj[12], proj[13]);
        PDEBUG("  mv[0]=%.6f mv[5]=%.6f mv[12]=%.6f mv[13]=%.6f\n", mv[0], mv[5], mv[12], mv[13]);
    }
    // Swap PApplet's CURRENT style out (stash it for restoration in
    // endDraw()) and swap THIS buffer's own remembered style in. On the
    // very first beginDraw() for a fresh buffer, myStyle still holds the
    // struct's default-initialized values (which match real Processing's
    // actual PGraphics defaults: black fill, no stroke, etc.) -- exactly
    // matching what a brand-new PGraphics should start with, independent
    // of whatever the main canvas's style happened to be.
    if (PApplet::g_papplet) {
        auto* p = PApplet::g_papplet;
        _savedMainStyle.fillR=p->fillR; _savedMainStyle.fillG=p->fillG; _savedMainStyle.fillB=p->fillB; _savedMainStyle.fillA=p->fillA;
        _savedMainStyle.strokeR=p->strokeR; _savedMainStyle.strokeG=p->strokeG; _savedMainStyle.strokeB=p->strokeB; _savedMainStyle.strokeA=p->strokeA;
        _savedMainStyle.strokeW=p->strokeW;
        _savedMainStyle.doFill=p->doFill; _savedMainStyle.doStroke=p->doStroke; _savedMainStyle.smoothing=p->smoothing;
        _savedMainStyle.currentRectMode=p->currentRectMode; _savedMainStyle.currentEllipseMode=p->currentEllipseMode; _savedMainStyle.currentImageMode=p->currentImageMode;
        _savedMainStyle.tintR=p->tintR; _savedMainStyle.tintG=p->tintG; _savedMainStyle.tintB=p->tintB; _savedMainStyle.tintA=p->tintA;
        _savedMainStyle.doTint=p->doTint;
        _savedMainStyle.colorModeVal=p->colorModeVal;
        _savedMainStyle.colorMaxH=p->colorMaxH; _savedMainStyle.colorMaxS=p->colorMaxS; _savedMainStyle.colorMaxB=p->colorMaxB; _savedMainStyle.colorMaxA=p->colorMaxA;
        _savedMainStyle.g_textSize=p->g_textSize;
        _savedMainStyle.g_textAlignX=p->g_textAlignX; _savedMainStyle.g_textAlignY=p->g_textAlignY;
        _savedMainStyle.g_textLeading=p->g_textLeading;

        p->fillR=myStyle.fillR; p->fillG=myStyle.fillG; p->fillB=myStyle.fillB; p->fillA=myStyle.fillA;
        p->strokeR=myStyle.strokeR; p->strokeG=myStyle.strokeG; p->strokeB=myStyle.strokeB; p->strokeA=myStyle.strokeA;
        p->strokeW=myStyle.strokeW;
        p->doFill=myStyle.doFill; p->doStroke=myStyle.doStroke; p->smoothing=myStyle.smoothing;
        p->currentRectMode=myStyle.currentRectMode; p->currentEllipseMode=myStyle.currentEllipseMode; p->currentImageMode=myStyle.currentImageMode;
        p->tintR=myStyle.tintR; p->tintG=myStyle.tintG; p->tintB=myStyle.tintB; p->tintA=myStyle.tintA;
        p->doTint=myStyle.doTint;
        p->colorModeVal=myStyle.colorModeVal;
        p->colorMaxH=myStyle.colorMaxH; p->colorMaxS=myStyle.colorMaxS; p->colorMaxB=myStyle.colorMaxB; p->colorMaxA=myStyle.colorMaxA;
        p->g_textSize=myStyle.g_textSize;
        p->g_textAlignX=myStyle.g_textAlignX; p->g_textAlignY=myStyle.g_textAlignY;
        p->g_textLeading=myStyle.g_textLeading;
    }
    myStyle.initialized = true;
}
inline void PGraphics::_endDrawImpl() {
    glMatrixMode(GL_PROJECTION); glPopMatrix();
    glMatrixMode(GL_MODELVIEW);  glPopMatrix();
    // Resolve the multisampled content down into the plain texture-backed
    // FBO that drawPGraphicsRect actually samples from. Without this
    // blit, anything rendered into msaaFbo would never reach the texture
    // used for display -- the buffer would appear blank/stale.
    if (samples > 0 && msaaFbo != 0) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        GLenum readStatus = glCheckFramebufferStatus(GL_READ_FRAMEBUFFER);
        GLenum drawStatus = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                           GL_COLOR_BUFFER_BIT, GL_LINEAR);
        GLenum err = glGetError();
        PDEBUG("_endDrawImpl blit-resolve: readStatus=0x%x drawStatus=0x%x glError=0x%x width=%d height=%d is3D=%d\n",
               readStatus, drawStatus, err, width, height, is3D);
    }
    {
        // Direct pixel readback from the buffer's own resolve-target FBO,
        // completely bypassing the texture-sampling/display path -- this
        // tells us definitively whether the rendered content actually
        // exists in the framebuffer's color attachment at all, or
        // whether the bug is specifically in how that texture gets
        // SAMPLED later (drawPGraphicsRect), not in the rendering itself.
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        unsigned char px[4] = {0,0,0,0};
        int cx = width/2, cy = height/2; // center of buffer, should be inside the cube
        glReadPixels(cx, cy, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
        PDEBUG("_endDrawImpl PIXEL READBACK at buffer center (%d,%d): r=%d g=%d b=%d a=%d (fbo=%u)\n",
               cx, cy, px[0], px[1], px[2], px[3], fbo);
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    active = false;
    // Save whatever style this buffer ended up with (so the NEXT
    // beginDraw() on this same buffer resumes where it left off, matching
    // real Processing semantics -- a PGraphics remembers its own style
    // across multiple begin/end cycles), then restore the main canvas's
    // style exactly as it was before beginDraw() touched it.
    if (PApplet::g_papplet) {
        auto* p = PApplet::g_papplet;
        myStyle.fillR=p->fillR; myStyle.fillG=p->fillG; myStyle.fillB=p->fillB; myStyle.fillA=p->fillA;
        myStyle.strokeR=p->strokeR; myStyle.strokeG=p->strokeG; myStyle.strokeB=p->strokeB; myStyle.strokeA=p->strokeA;
        myStyle.strokeW=p->strokeW;
        myStyle.doFill=p->doFill; myStyle.doStroke=p->doStroke; myStyle.smoothing=p->smoothing;
        myStyle.currentRectMode=p->currentRectMode; myStyle.currentEllipseMode=p->currentEllipseMode; myStyle.currentImageMode=p->currentImageMode;
        myStyle.tintR=p->tintR; myStyle.tintG=p->tintG; myStyle.tintB=p->tintB; myStyle.tintA=p->tintA;
        myStyle.doTint=p->doTint;
        myStyle.colorModeVal=p->colorModeVal;
        myStyle.colorMaxH=p->colorMaxH; myStyle.colorMaxS=p->colorMaxS; myStyle.colorMaxB=p->colorMaxB; myStyle.colorMaxA=p->colorMaxA;
        myStyle.g_textSize=p->g_textSize;
        myStyle.g_textAlignX=p->g_textAlignX; myStyle.g_textAlignY=p->g_textAlignY;
        myStyle.g_textLeading=p->g_textLeading;

        p->fillR=_savedMainStyle.fillR; p->fillG=_savedMainStyle.fillG; p->fillB=_savedMainStyle.fillB; p->fillA=_savedMainStyle.fillA;
        p->strokeR=_savedMainStyle.strokeR; p->strokeG=_savedMainStyle.strokeG; p->strokeB=_savedMainStyle.strokeB; p->strokeA=_savedMainStyle.strokeA;
        p->strokeW=_savedMainStyle.strokeW;
        p->doFill=_savedMainStyle.doFill; p->doStroke=_savedMainStyle.doStroke; p->smoothing=_savedMainStyle.smoothing;
        p->currentRectMode=_savedMainStyle.currentRectMode; p->currentEllipseMode=_savedMainStyle.currentEllipseMode; p->currentImageMode=_savedMainStyle.currentImageMode;
        p->tintR=_savedMainStyle.tintR; p->tintG=_savedMainStyle.tintG; p->tintB=_savedMainStyle.tintB; p->tintA=_savedMainStyle.tintA;
        p->doTint=_savedMainStyle.doTint;
        p->colorModeVal=_savedMainStyle.colorModeVal;
        p->colorMaxH=_savedMainStyle.colorMaxH; p->colorMaxS=_savedMainStyle.colorMaxS; p->colorMaxB=_savedMainStyle.colorMaxB; p->colorMaxA=_savedMainStyle.colorMaxA;
        p->g_textSize=_savedMainStyle.g_textSize;
        p->g_textAlignX=_savedMainStyle.g_textAlignX; p->g_textAlignY=_savedMainStyle.g_textAlignY;
        p->g_textLeading=_savedMainStyle.g_textLeading;
    }
    glViewport(savedViewport[0],savedViewport[1],savedViewport[2],savedViewport[3]);
    glMatrixMode(GL_PROJECTION);glLoadIdentity();
    // BUG FIX: savedViewport[2]/[3] are FRAMEBUFFER PIXEL dimensions
    // (fbW/fbH), captured via glGetIntegerv(GL_VIEWPORT,...) -- correct
    // for restoring the viewport itself, but WRONG for glOrtho. The main
    // render loop deliberately uses fbW/fbH for glViewport but
    // logicalW/logicalH for glOrtho specifically so sketch coordinates
    // map 1:1 regardless of HiDPI/Retina display scaling (see the
    // matching comment in PApplet::run()). Using savedViewport's pixel
    // dimensions for glOrtho here made every main-canvas draw call after
    // endDraw() appear at the wrong scale/position on any HiDPI display.
    if (PApplet::g_papplet) {
        auto* p = PApplet::g_papplet;
        glOrtho(0, p->logicalW, p->logicalH, 0, -1, 1);
    } else {
        glOrtho(0, savedViewport[2], savedViewport[3], 0, -1, 1);
    }
    glMatrixMode(GL_MODELVIEW);glLoadIdentity();
}
inline void PGraphics::background(float g)                          { if(PApplet::g_papplet) PApplet::g_papplet->background(g); }
inline void PGraphics::background(float r, float g2, float b)       { if(PApplet::g_papplet) PApplet::g_papplet->background(r,g2,b,255); }
inline void PGraphics::background(float r, float g2, float b, float a){ if(PApplet::g_papplet) PApplet::g_papplet->background(r,g2,b,a); }
inline void PGraphics::fill(float g)                                { if(PApplet::g_papplet) PApplet::g_papplet->fill(g); }
inline void PGraphics::fill(float r, float g2, float b)             { if(PApplet::g_papplet) PApplet::g_papplet->fill(r,g2,b); }
inline void PGraphics::fill(float r, float g2, float b, float a)    { if(PApplet::g_papplet) PApplet::g_papplet->fill(r,g2,b,a); }
inline void PGraphics::noFill()                                     { if(PApplet::g_papplet) PApplet::g_papplet->noFill(); }
inline void PGraphics::stroke(float g)                              { if(PApplet::g_papplet) PApplet::g_papplet->stroke(g); }
inline void PGraphics::stroke(float r, float g2, float b)           { if(PApplet::g_papplet) PApplet::g_papplet->stroke(r,g2,b); }
inline void PGraphics::noStroke()                                   { if(PApplet::g_papplet) PApplet::g_papplet->noStroke(); }
inline void PGraphics::strokeWeight(float w)                        { if(PApplet::g_papplet) PApplet::g_papplet->strokeWeight(w); }
inline void PGraphics::ellipse(float x, float y, float w2, float h2){ if(PApplet::g_papplet) PApplet::g_papplet->ellipse(x,y,w2,h2); }
inline void PGraphics::rect(float x, float y, float w2, float h2)   { if(PApplet::g_papplet) PApplet::g_papplet->rect(x,y,w2,h2); }
inline void PGraphics::line(float x1, float y1, float x2, float y2) { if(PApplet::g_papplet) PApplet::g_papplet->line(x1,y1,x2,y2); }
inline void PGraphics::point(float x, float y)                      { if(PApplet::g_papplet) PApplet::g_papplet->point(x,y); }
inline void PGraphics::triangle(float x1,float y1,float x2,float y2,float x3,float y3){ if(PApplet::g_papplet) PApplet::g_papplet->triangle(x1,y1,x2,y2,x3,y3); }
inline void PGraphics::text(const ::std::string& s, float x, float y) { if(PApplet::g_papplet) PApplet::g_papplet->text(s,x,y); }
inline void PGraphics::textSize(float size)                          { if(PApplet::g_papplet) PApplet::g_papplet->textSize(size); }
inline void PGraphics::textAlign(int alignX)                         { if(PApplet::g_papplet) PApplet::g_papplet->textAlign(alignX,0); }
inline void PGraphics::textAlign(int alignX, int alignY)             { if(PApplet::g_papplet) PApplet::g_papplet->textAlign(alignX,alignY); }
inline void PGraphics::translate(float x, float y)                  { if(PApplet::g_papplet) PApplet::g_papplet->translate(x,y); }
inline void PGraphics::rotate(float a)                              { if(PApplet::g_papplet) PApplet::g_papplet->rotate(a); }
inline void PGraphics::scale(float s)                               { if(PApplet::g_papplet) PApplet::g_papplet->scale(s); }
inline void PGraphics::pushMatrix()                                 { if(PApplet::g_papplet) PApplet::g_papplet->pushMatrix(); }
inline void PGraphics::popMatrix()                                  { if(PApplet::g_papplet) PApplet::g_papplet->popMatrix(); }
inline void PGraphics::beginShape()                                 { if(PApplet::g_papplet) PApplet::g_papplet->beginShape(); }
inline void PGraphics::endShape(int mode)                           { if(PApplet::g_papplet) PApplet::g_papplet->endShape(mode); }
inline void PGraphics::vertex(float x, float y)                     { if(PApplet::g_papplet) PApplet::g_papplet->vertex(x,y); }
inline void PGraphics::clear()                                      { if(PApplet::g_papplet) PApplet::g_papplet->clear(); }
inline void PGraphics::translate(float x, float y, float z)          { if(PApplet::g_papplet) PApplet::g_papplet->translate(x,y,z); }
inline void PGraphics::rotateX(float angle)                          { if(PApplet::g_papplet) PApplet::g_papplet->rotateX(angle); }
inline void PGraphics::rotateY(float angle)                          { if(PApplet::g_papplet) PApplet::g_papplet->rotateY(angle); }
inline void PGraphics::rotateZ(float angle)                          { if(PApplet::g_papplet) PApplet::g_papplet->rotateZ(angle); }
inline void PGraphics::box(float size)                               { if(PApplet::g_papplet) PApplet::g_papplet->box(size); }
inline void PGraphics::box(float w, float h, float d)                { if(PApplet::g_papplet) PApplet::g_papplet->box(w,h,d); }
inline void PGraphics::sphere(float r)                               { if(PApplet::g_papplet) PApplet::g_papplet->sphere(r); }
inline void PGraphics::lights()                                      { if(PApplet::g_papplet) PApplet::g_papplet->lights(); }
inline void PGraphics::noLights()                                    { if(PApplet::g_papplet) PApplet::g_papplet->noLights(); }
inline void PGraphics::ambientLight(float r, float g, float b)       { if(PApplet::g_papplet) PApplet::g_papplet->ambientLight(r,g,b); }
inline void PGraphics::ambientLight(float r, float g, float b, float x, float y, float z) { if(PApplet::g_papplet) PApplet::g_papplet->ambientLight(r,g,b,x,y,z); }
inline void PGraphics::directionalLight(float r, float g, float b, float nx, float ny, float nz) { if(PApplet::g_papplet) PApplet::g_papplet->directionalLight(r,g,b,nx,ny,nz); }
inline void PGraphics::pointLight(float r, float g, float b, float x, float y, float z) { if(PApplet::g_papplet) PApplet::g_papplet->pointLight(r,g,b,x,y,z); }
inline void PGraphics::spotLight(float r, float g, float b, float x, float y, float z, float nx, float ny, float nz, float angle, float conc) { if(PApplet::g_papplet) PApplet::g_papplet->spotLight(r,g,b,x,y,z,nx,ny,nz,angle,conc); }
inline void PGraphics::lightFalloff(float c, float l, float q)       { if(PApplet::g_papplet) PApplet::g_papplet->lightFalloff(c,l,q); }
inline void PGraphics::lightSpecular(float r, float g, float b)      { if(PApplet::g_papplet) PApplet::g_papplet->lightSpecular(r,g,b); }


inline void link(const ::std::string& url) {
#ifdef _WIN32
    ShellExecuteA(nullptr,"open",url.c_str(),nullptr,nullptr,SW_SHOWNORMAL);
#elif defined(__APPLE__)
    system(("open "+url).c_str());
#else
    system(("xdg-open "+url+" &").c_str());
#endif
}
inline void link(const char* url){link(::std::string(url));}



} // namespace Processing
