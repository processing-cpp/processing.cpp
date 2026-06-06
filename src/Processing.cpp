#include "Processing.h"
#include <cstdlib>
#include <cmath>
#include <sys/stat.h>
#include <iostream>
#include <iomanip>
#include <sstream>
#include <fstream>
#include <thread>
#include <chrono>
#include <vector>
#include <array>
#include <string>
#include <functional>
#include <unordered_map>
#include <unordered_set>
#include <random>
#include <memory>

// stb_image.h must be in the src/ folder for loadImage() to work.
// Download from: https://github.com/nothings/stb/blob/master/stb_image.h
#ifdef PROCESSING_HAS_STB_IMAGE
#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"
#endif

// Uncomment + drop stb_image_write.h to enable saveFrame()/save():
// #define STB_IMAGE_WRITE_IMPLEMENTATION
// #include "stb_image_write.h"

// ── Manual glu replacements (no GLU header needed) ───────────────────────────
static void _gluPerspective(double fovY_deg, double aspect, double zNear, double zFar) {
    // Identical to gluPerspective -- sets up a perspective projection matrix
    double f = 1.0 / std::tan(fovY_deg * M_PI / 360.0);
    double m[16] = {0};
    m[0]  = f / aspect;
    m[5]  = f;
    m[10] = (zFar + zNear) / (zNear - zFar);
    m[11] = -1.0;
    m[14] = (2.0 * zFar * zNear) / (zNear - zFar);
    glMultMatrixd(m);
}
static void _gluLookAt(double ex,double ey,double ez,
                       double cx,double cy,double cz,
                       double ux,double uy,double uz) {
    // Identical to gluLookAt
    double fx=cx-ex, fy=cy-ey, fz=cz-ez;
    double len=std::sqrt(fx*fx+fy*fy+fz*fz); fx/=len;fy/=len;fz/=len;
    double rx=fy*uz-fz*uy, ry=fz*ux-fx*uz, rz=fx*uy-fy*ux;
    len=std::sqrt(rx*rx+ry*ry+rz*rz); rx/=len;ry/=len;rz/=len;
    double upx=ry*fz-rz*fy, upy=rz*fx-rx*fz, upz=rx*fy-ry*fx;
    double m[16]={
        rx,  upx, -fx, 0,
        ry,  upy, -fy, 0,
        rz,  upz, -fz, 0,
        0,   0,   0,   1
    };
    glMultMatrixd(m);
    glTranslated(-ex,-ey,-ez);
}

// stb_truetype -- drop stb_truetype.h next to this file for TTF font rendering.
// default.ttf in the project root is loaded automatically as the default font.
#if __has_include("stb_truetype.h")
#  define STB_TRUETYPE_IMPLEMENTATION
#  include "stb_truetype.h"
#  define PROCESSING_HAS_STB_TRUETYPE 1
#else
#  define PROCESSING_HAS_STB_TRUETYPE 0
#endif

namespace Processing {

// =============================================================================
// STATE
// =============================================================================

// Window / canvas size
int   winWidth  = 640, winHeight  = 480;    // current window size (updated by size())
int   logicalW  = 640, logicalH  = 480;    // sketch's coordinate space (from size())
static int fbW  = 640, fbH       = 480;    // actual framebuffer (may be 2× on HiDPI)
int   displayWidth  = 0, displayHeight  = 0;
int   pixelWidth    = 0, pixelHeight    = 0;
int   pixelDensityValue = 1;
bool  isResizable = false;
bool  focused     = false;

// Mouse state
float mouseX = 0, mouseY = 0, pmouseX = 0, pmouseY = 0;
bool  mouseInWindow = false;    // true once cursor has entered the window
bool  _mousePressed = false;
int   mouseButton   = -1;

// Keyboard state
bool  _keyPressed = false;
int   keyCode = 0;
char  key     = 0;

// Frame timing
int   frameCount     = 1;
float currentFrameRate    = 60.0f;
float _frameRate          = 60.0f;
float deltaTime          = 0.0f;
bool  looping        = true;
static bool   redrawOnce = false;
float measuredFrameRate  = 0.0f;
static double targetFrameTime = 1.0 / 60.0;

// Current draw style
float fillR = 1, fillG = 1, fillB = 1, fillA = 1;
float strokeR = 0, strokeG = 0, strokeB = 0, strokeA = 1;
float strokeW = 1;
bool  doFill = true, doStroke = true, smoothing = true;

int   currentRectMode    = CORNER;
int   currentEllipseMode = CENTER;
int   currentImageMode   = CORNER;

float tintR = 1, tintG = 1, tintB = 1, tintA = 1;
bool  doTint = false;

// Lighting state
static float pendingSpecR = 0, pendingSpecG = 0, pendingSpecB = 0;  // from lightSpecular()
static float lightConcentration[8] = { 0, 0, 0, 0, 0, 0, 0, 0 };   // spotlight exponent
static float lightCutoffCos[8]     = { -1, -1, -1, -1, -1, -1, -1, -1 }; // cos(cutoff), -1 = no cone

// Color mode
int   colorModeVal = RGB;
float colorMaxH = 255.f, colorMaxS = 255.f, colorMaxB = 255.f, colorMaxA = 255.f;

std::vector<unsigned int> pixels;

// User event callbacks
std::function<void()>    _onKeyPressed;
std::function<void()>    _onKeyReleased;
std::function<void()>    _onKeyTyped;
std::function<void()>    _onMousePressed;
std::function<void()>    _onMouseReleased;
std::function<void()>    _onMouseClicked;
std::function<void()>    _onMouseMoved;
std::function<void()>    _onMouseDragged;
std::function<void(int)> _onMouseWheel;
std::function<void()>    _onWindowMoved;
std::function<void()>    _onWindowResized;

// On Windows: IDE.cpp stores a raw function pointer here before static init
// of Processing.cpp completes. Raw function pointers are POD -- zero-initialized
// at program start before ANY constructor runs, so this is always safe to write.
void (*_wireCallbacksFn)() = nullptr;
void (*_staticSketchSetup)() = nullptr; // set by static sketches for i3 redraw

static GLFWwindow* gWindow=nullptr;
std::string g_sketchName = "Sketch";

// Read sketch name from environment (set by CppRunner at runtime)
static struct _SketchNameInit {
    _SketchNameInit() {
        const char* env = std::getenv("PROCESSING_SKETCH_NAME");
        if (env && env[0]) g_sketchName = env;
    }
} _sketchNameInit;
static bool is3DMode=false;
static int   sphereRes=48;
static int   curveDetailVal=20;
static float curveTightnessVal=0.0f;
static int   bezierDetailVal=60;
static bool  lightsEnabled=false;
static int   lightIndex=0;

// Shape state
static int  shapeKind=-1;
static bool inShape=false,inContour=false;
static bool shape3D=false;
static std::vector<std::pair<float,float>>          shapeVerts;   // 2D projections (for stroke outlines)
static std::vector<std::array<float,3>>             shapeVerts3D; // full 3D positions (for fill)
static std::vector<std::pair<float,float>> contourVerts;

// Style stack
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
static std::vector<Style> styleStack;

// =============================================================================
// NOISE - exact Java Processing implementation (PApplet.java)
// Uses a 4096-entry float lookup table with cosine interpolation.
// This matches Processing Java's noise() output exactly.
// =============================================================================

static const int PERLIN_YWRAPB = 4;
static const int PERLIN_YWRAP  = 1 << PERLIN_YWRAPB;  // 16
static const int PERLIN_ZWRAPB = 8;
static const int PERLIN_ZWRAP  = 1 << PERLIN_ZWRAPB;  // 256
static const int PERLIN_SIZE   = 4095;

static int   noiseOctaves  = 4;
static float noiseFalloff  = 0.5f;
static float perlinTable[PERLIN_SIZE + 1];
static bool  perlinInit    = false;

static void initPerlin(unsigned int seed) {
    // Java Processing seeds with a simple LCG and fills with rand values in [0,1)
    // It uses its own random to not disturb the sketch's random()
    uint32_t s = seed;
    auto jrand = [&]() -> float {
        s = s * 1664525u + 1013904223u; // LCG like Java's Random
        return (s >> 8) / (float)(1 << 24);
    };
    for (int i = 0; i < PERLIN_SIZE + 1; i++)
        perlinTable[i] = jrand();
    perlinInit = true;
}

void noiseSeed(int s) { initPerlin((unsigned int)s); }
void noiseDetail(int o, float f) { noiseOctaves = o; noiseFalloff = f; }

// Cosine interpolation curve -- Java Processing's noise_fsc()
static inline float noise_fsc(float i) {
    return 0.5f * (1.0f - std::cos(i * PI));
}

float noise(float x, float y, float z) {
    if (!perlinInit) initPerlin(0);  // default seed 0 like Java
    if (x < 0) x = -x;
    if (y < 0) y = -y;
    if (z < 0) z = -z;
    int xi = (int)x, yi = (int)y, zi = (int)z;
    float xf = x - xi, yf = y - yi, zf = z - zi;
    float r = 0.0f, ampl = 0.5f;
    for (int oct = 0; oct < noiseOctaves; oct++) {
        int of = xi + (yi << PERLIN_YWRAPB) + (zi << PERLIN_ZWRAPB);
        float rxf = noise_fsc(xf), ryf = noise_fsc(yf);
        float n1 = perlinTable[of & PERLIN_SIZE];
        n1 += rxf * (perlinTable[(of+1) & PERLIN_SIZE] - n1);
        float n2 = perlinTable[(of + PERLIN_YWRAP) & PERLIN_SIZE];
        n2 += rxf * (perlinTable[(of + PERLIN_YWRAP + 1) & PERLIN_SIZE] - n2);
        n1 += ryf * (n2 - n1);
        of += PERLIN_ZWRAP;
        n2 = perlinTable[of & PERLIN_SIZE];
        n2 += rxf * (perlinTable[(of+1) & PERLIN_SIZE] - n2);
        float n3 = perlinTable[(of + PERLIN_YWRAP) & PERLIN_SIZE];
        n3 += rxf * (perlinTable[(of + PERLIN_YWRAP + 1) & PERLIN_SIZE] - n3);
        n2 += ryf * (n3 - n2);
        n1 += noise_fsc(zf) * (n2 - n1);
        r   += n1 * ampl;
        ampl *= noiseFalloff;
        // Double frequency each octave
        xi <<= 1; xf *= 2.0f; if (xf >= 1.0f) { xi++; xf--; }
        yi <<= 1; yf *= 2.0f; if (yf >= 1.0f) { yi++; yf--; }
        zi <<= 1; zf *= 2.0f; if (zf >= 1.0f) { zi++; zf--; }
    }
    return r;
}
float noise(float x)           { return noise(x, 0.0f, 0.0f); }
float noise(float x, float y)  { return noise(x, y,    0.0f); }

// Seeded random -- Mersenne Twister for reproducibility matching Java Processing
static std::mt19937 _rng(std::mt19937::default_seed);
static std::uniform_real_distribution<float> _rngDist(0.0f, 1.0f);

void randomSeed(long s) {
    _rng.seed(static_cast<uint32_t>(s));
    _rngDist.reset();
}
float random(float lo, float hi) {
    return lo + _rngDist(_rng) * (hi - lo);
}
float random(float hi) { return random(0.f, hi); }

float randomGaussian(){
    static float spare; static bool has=false;
    if(has){has=false;return spare;}
    float u,v,s;
    do{u=_rngDist(_rng)*2-1;v=_rngDist(_rng)*2-1;s=u*u+v*v;}while(s>=1||s==0);
    s=std::sqrt(-2*std::log(s)/s);spare=v*s;has=true;return u*s;
}

// =============================================================================
// COLOR MODE & HELPERS
// =============================================================================

static void hsbToRgb(float h, float s, float b, float& outR, float& outG, float& outB) {
    // Normalise each channel to [0, 1]
    h /= colorMaxH;
    s /= colorMaxS;
    b /= colorMaxB;

    if (s == 0.0f) {
        // Achromatic (grey): all channels equal brightness
        outR = outG = outB = b;
        return;
    }

    float sector  = h * 6.0f;           // which 60-degree sector of the hue wheel
    int   i       = (int)sector;
    float frac    = sector - i;          // fractional part within sector

    float p = b * (1.0f - s);
    float q = b * (1.0f - s * frac);
    float t = b * (1.0f - s * (1.0f - frac));

    switch (i % 6) {
        case 0:  outR = b; outG = t; outB = p; break;
        case 1:  outR = q; outG = b; outB = p; break;
        case 2:  outR = p; outG = b; outB = t; break;
        case 3:  outR = p; outG = q; outB = b; break;
        case 4:  outR = t; outG = p; outB = b; break;
        default: outR = b; outG = p; outB = q; break;
    }
}

color makeColor(float a, float b, float c, float d) {
    float r = 0, g = 0, bv = 0, aa = 0;
    if (colorModeVal == HSB) {
        hsbToRgb(a, b, c, r, g, bv);
        aa = d / colorMaxA;
    } else {
        r  = a / colorMaxH;
        g  = b / colorMaxS;
        bv = c / colorMaxB;
        aa = d / colorMaxA;
    }
    return colorVal((int)(r*255), (int)(g*255), (int)(bv*255), (int)(aa*255));
}
color makeColor(float gray,float alpha){
    // In HSB mode, a single-value gray maps to brightness only (hue=0, sat=0)
    // matching Processing Java behavior -- background(v) in HSB gives gray
    if(colorModeVal==HSB){
        float br=gray/colorMaxB;
        br=std::fmax(0.f,std::fmin(1.f,br));
        int v=(int)(br*255);
        unsigned int a=std::fmax(0.f,std::fmin(1.f,alpha/colorMaxA))*255;
        return colorVal(v,v,v,(int)a);
    }
    return makeColor(gray,gray,gray,alpha);
}

// =============================================================================
// color STRUCT CONSTRUCTORS
// =============================================================================
color::color(int gray)              { value = makeColor((float)gray, colorMaxA).value; }
color::color(int gray, int a)       { value = makeColor((float)gray, (float)a).value; }
color::color(int r, int g, int b)   { value = makeColor((float)r,(float)g,(float)b,colorMaxA).value; }
color::color(int r,int g,int b,int a){ value = makeColor((float)r,(float)g,(float)b,(float)a).value; }
color::color(float gray)            { value = makeColor(gray, colorMaxA).value; }
color::color(float gray, float a)   { value = makeColor(gray, a).value; }
color::color(float r,float g,float b){ value = makeColor(r,g,b,colorMaxA).value; }
color::color(float r,float g,float b,float a){ value = makeColor(r,g,b,a).value; }

void colorMode(int mode, float mx) { colorModeVal=mode; colorMaxH=colorMaxS=colorMaxB=colorMaxA=mx; }
void colorMode(int mode, float mH, float mS, float mB, float mA) { colorModeVal=mode; colorMaxH=mH; colorMaxS=mS; colorMaxB=mB; colorMaxA=mA; }

// Color channel accessors -- scaled to current colorMode range
float red(color c)       { unsigned int v=c.value; return (v>>16&0xFF)/255.0f*colorMaxH; }
float green(color c)     { unsigned int v=c.value; return (v>>8&0xFF)/255.0f*colorMaxS; }
float blue(color c)      { unsigned int v=c.value; return (v&0xFF)/255.0f*colorMaxB; }
float alpha(color c)     { unsigned int v=c.value; return (v>>24&0xFF)/255.0f*colorMaxA; }
float brightness(color c) {
    unsigned int v=c.value;
    float r=(v>>16&0xFF)/255.f, g=(v>>8&0xFF)/255.f, b=(v&0xFF)/255.f;
    return max(r, max(g, b)) * colorMaxB;
}
float saturation(color c) {
    unsigned int v = c.value;
    float r  = (v >> 16 & 0xFF) / 255.f;
    float g  = (v >>  8 & 0xFF) / 255.f;
    float b  = (v       & 0xFF) / 255.f;
    float mx = max(r, max(g, b));
    float mn = min(r, min(g, b));
    return (mx == 0 ? 0 : (mx - mn) / mx) * colorMaxS;
}
float hue(color c){
    unsigned int v=c.value;
    float r=(v>>16&0xFF)/255.f,g=(v>>8&0xFF)/255.f,b=(v&0xFF)/255.f;
    float mx=max(r,max(g,b)),mn=min(r,min(g,b)),d=mx-mn;
    if(d==0)return 0;
    float h=(mx==r)?(g-b)/d:(mx==g)?2+(b-r)/d:4+(r-g)/d;
    h*=60;if(h<0)h+=360;return h/360.0f*colorMaxH;
}
color lerpColor(color c1, color c2, float t) {
    unsigned int v1 = c1.value;
    unsigned int v2 = c2.value;

    int r1 = (v1 >> 16) & 0xFF,  r2 = (v2 >> 16) & 0xFF;
    int g1 = (v1 >>  8) & 0xFF,  g2 = (v2 >>  8) & 0xFF;
    int b1 =  v1        & 0xFF,  b2 =  v2        & 0xFF;
    int a1 = (v1 >> 24) & 0xFF,  a2 = (v2 >> 24) & 0xFF;

    return colorVal(
        (int)(r1 + t * (r2 - r1)),
        (int)(g1 + t * (g2 - g1)),
        (int)(b1 + t * (b2 - b1)),
        (int)(a1 + t * (a2 - a1))
    );
}

// =============================================================================
// INTERNAL HELPERS
// =============================================================================

static void applyFill() {
    glColor4f(fillR, fillG, fillB, fillA);
    // Always enable blend -- shapes with alpha need it, opaque shapes don't hurt
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}

// Temporarily suspend lighting so stroke lines/points render with their exact
// colour. Processing Java does the same -- strokes are never affected by lights.
static void applyStroke() {
    if (lightsEnabled) {
        glDisable(GL_LIGHTING);
        glDisable(GL_COLOR_MATERIAL);
    }
    glColor4f(strokeR, strokeG, strokeB, strokeA);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}

// Restore lighting after a stroke draw call.
static void restoreLighting() {
    if (lightsEnabled) {
        glEnable(GL_LIGHTING);
        glEnable(GL_COLOR_MATERIAL);
        glColorMaterial(GL_FRONT_AND_BACK, GL_DIFFUSE);
        // Reapply fill colour so the next lit shape uses the right material
        glColor4f(fillR, fillG, fillB, fillA);
    }
}

static GLuint persistFBO=0, persistTex=0;
static void initPersistFBO(){
    if(persistFBO){glDeleteFramebuffers(1,&persistFBO);glDeleteTextures(1,&persistTex);persistFBO=0;}
    glGenFramebuffers(1,&persistFBO);
    glGenTextures(1,&persistTex);
    glBindTexture(GL_TEXTURE_2D,persistTex);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,fbW,fbH,0,GL_RGBA,GL_UNSIGNED_BYTE,nullptr);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    glBindTexture(GL_TEXTURE_2D,0);
    glBindFramebuffer(GL_FRAMEBUFFER,persistFBO);
    glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,persistTex,0);
    glBindFramebuffer(GL_FRAMEBUFFER,0);
}

// Copy current back buffer into persist FBO
static void saveToPersist(){
    if(!persistFBO) initPersistFBO();
    // Blit back buffer -> persist FBO
    glBindFramebuffer(GL_READ_FRAMEBUFFER,0);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER,persistFBO);
    glBlitFramebuffer(0,0,fbW,fbH,0,0,fbW,fbH,GL_COLOR_BUFFER_BIT,GL_NEAREST);
    glBindFramebuffer(GL_FRAMEBUFFER,0);
}

// Restore persist FBO into back buffer
static void restoreFromPersist(){
    if(!persistFBO) return;
    glBindFramebuffer(GL_READ_FRAMEBUFFER,persistFBO);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER,0);
    glBlitFramebuffer(0,0,fbW,fbH,0,0,fbW,fbH,GL_COLOR_BUFFER_BIT,GL_NEAREST);
    glBindFramebuffer(GL_FRAMEBUFFER,0);
}

static void _restoreMainCanvas(){
    // Restore main window viewport and projection after PGraphics endDraw()
    glViewport(0,0,fbW,fbH);
    glMatrixMode(GL_PROJECTION);glLoadIdentity();
    glOrtho(0,logicalW,logicalH,0,-1,1);
    glMatrixMode(GL_MODELVIEW);glLoadIdentity();
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_LIGHTING);
}

static void setProjection(int,int){
    // Viewport = actual framebuffer size (handles HiDPI where fb > logical).
    // Ortho = logical sketch size (coordinates always match what size() set).
    if (gWindow) {
        int fw=logicalW, fh=logicalH;
        glfwGetFramebufferSize(gWindow, &fw, &fh);
        if (fw > 0) fbW = fw;
        if (fh > 0) fbH = fh;
    }
    glViewport(0, 0, fbW, fbH);
    glMatrixMode(GL_PROJECTION);glLoadIdentity();
    glOrtho(0, logicalW, logicalH, 0, -1, 1);
    glMatrixMode(GL_MODELVIEW);glLoadIdentity();
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_LIGHTING);
}

static void drawEllipseGeom(float cx,float cy,float rx,float ry,
                             float sa=0,float ea=TWO_PI,int segs=-1){
    if(segs < 0){
        float maxR = rx > ry ? rx : ry;
        segs = (int)(maxR * 2.5f);
        if(segs < 64)  segs = 64;
        if(segs > 512) segs = 512;
    }
    float range=ea-sa;
    bool isFullCircle = (std::fabs(range) >= TWO_PI - 0.001f);
    if(doFill){
        applyFill();
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx,cy); // center
        for(int i=0;i<=segs;i++){
            float a=sa+range*i/segs;
            glVertex2f(cx+rx*std::cos(a),cy+ry*std::sin(a));
        }
        if(isFullCircle){
            // close the circle back to the first arc point
            glVertex2f(cx+rx*std::cos(sa),cy+ry*std::sin(sa));
        }
        glEnd();
    }
    if(doStroke){
        applyStroke();glLineWidth(strokeW);
        if(isFullCircle){
            glBegin(GL_LINE_LOOP);
        } else {
            glBegin(GL_LINE_STRIP);
        }
        for(int i=0;i<=segs;i++){
            float a=sa+range*i/segs;
            glVertex2f(cx+rx*std::cos(a),cy+ry*std::sin(a));
        }
        glEnd();
    }
}

static void resolveRect(float& x,float& y,float& w,float& h){
    if(currentRectMode==CENTER){x-=w*0.5f;y-=h*0.5f;}
    else if(currentRectMode==RADIUS){x-=w;y-=h;w*=2;h*=2;}
    else if(currentRectMode==CORNERS){w=w-x;h=h-y;}
}
static void resolveEllipse(float& cx,float& cy,float& rx,float& ry){
    if(currentEllipseMode==CORNER){cx+=rx;cy+=ry;}
    else if(currentEllipseMode==CORNERS){float ex=rx,ey=ry;rx=(ex-cx)*0.5f;ry=(ey-cy)*0.5f;cx=(cx+ex)*0.5f;cy=(cy+ey)*0.5f;}
    else if(currentEllipseMode==CENTER){rx*=0.5f;ry*=0.5f;}
}
static void setFillFromColor(color c) {
    unsigned int v = c.value;
    fillR = (v >> 16 & 0xFF) / 255.f;
    fillG = (v >>  8 & 0xFF) / 255.f;
    fillB = (v       & 0xFF) / 255.f;
    fillA = (v >> 24 & 0xFF) / 255.f;
    doFill = true;
}
static void setStrokeFromColor(color c) {
    unsigned int v = c.value;
    strokeR = (v >> 16 & 0xFF) / 255.f;
    strokeG = (v >>  8 & 0xFF) / 255.f;
    strokeB = (v       & 0xFF) / 255.f;
    strokeA = (v >> 24 & 0xFF) / 255.f;
    doStroke = true;
}

// =============================================================================
// ENVIRONMENT
// =============================================================================

static bool defaultP3D = false;
static void applyDefaultCamera(); // forward decl for use in size()

void size(int w,int h){
    winWidth=w;winHeight=h;
    logicalW=w;logicalH=h;  // remember what the sketch requested
    pixelWidth=w;pixelHeight=h;
    if(gWindow){
        glfwSetWindowSize(gWindow,w,h);
        // Poll until the framebuffer is actually the requested size.
        // glfwSetWindowSize is async; without this the window stays 100x100
        // when setup() calls background() or draws anything.
        // Poll until the window is actually resized (or timeout after 500ms)
        for(int _wait=0; _wait<500; _wait++){
            glfwPollEvents();
            int fw=0,fh=0;
            glfwGetFramebufferSize(gWindow,&fw,&fh);
            // Accept exact match or close match (HiDPI scaling)
            if(fw>0 && fh>0){
                pixelWidth=fw; pixelHeight=fh;
                // Check if the logical window size matches what we requested
                int lw=0,lh=0;
                glfwGetWindowSize(gWindow,&lw,&lh);
                if(lw==w && lh==h) break;  // exact match
            }
            if(_wait>=50){ // after 50ms, accept whatever we have
                int fw2=0,fh2=0;
                glfwGetFramebufferSize(gWindow,&fw2,&fh2);
                if(fw2>0&&fh2>0){ pixelWidth=fw2; pixelHeight=fh2; }
                break;
            }
            #ifdef _WIN32
            Sleep(1);
            #else
            usleep(1000);
            #endif
        }
        // Force coordinate system to requested size regardless of WM.
        // logicalW/H are the sketch's coordinate space; viewport uses actual size.
        logicalW = w; logicalH = h;
        winWidth = w; winHeight = h;
        setProjection(w,h);
    }
}
void size(int w,int h,int renderer){
    defaultP3D=(renderer==P3D);
    size(w,h);
    // For P3D mode: set up depth test and apply default camera/perspective
    // immediately so setup() draws with the correct projection.
    if(defaultP3D && gWindow){
        {int fw=logicalW,fh=logicalH;if(gWindow)glfwGetFramebufferSize(gWindow,&fw,&fh);if(fw>0)fbW=fw;if(fh>0)fbH=fh;}
        glViewport(0,0,fbW,fbH);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glDisable(GL_CULL_FACE);
        glFrontFace(GL_CW);
        glEnable(GL_NORMALIZE);
        glClearColor(0.8f,0.8f,0.8f,1); // Java Processing default grey (204,204,204)
        glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        applyDefaultCamera();
    }
}
void fullScreen() {
    if (!gWindow) {
        winWidth = displayWidth;
        winHeight = displayHeight;
    } else {
        GLFWmonitor* m = glfwGetPrimaryMonitor();
        const GLFWvidmode* v = glfwGetVideoMode(m);
        glfwSetWindowMonitor(gWindow, m, 0, 0, v->width, v->height, v->refreshRate);
    }
}
void frameRate(int fps){currentFrameRate=fps;targetFrameTime=1.0/fps;}
void settings(){}
void noLoop(){looping=false;}
void loop()  {looping=true;}
void redraw(){redrawOnce=true;}
void exit_sketch(){if(gWindow)glfwSetWindowShouldClose(gWindow,GLFW_TRUE);}
void windowTitle(const std::string& t){if(gWindow)glfwSetWindowTitle(gWindow,t.c_str());}
void windowMove(int x,int y){if(gWindow)glfwSetWindowPos(gWindow,x,y);}
void windowResize(int w,int h){size(w,h);}
void windowResizable(bool r){isResizable=r;if(gWindow)glfwSetWindowAttrib(gWindow,GLFW_RESIZABLE,r?GLFW_TRUE:GLFW_FALSE);}
// ---------------------------------------------------------------------------
// Clipboard
// ---------------------------------------------------------------------------
void setClipboard(const std::string& s) {
    if (s.empty()) return;
    if (gWindow) glfwSetClipboardString(gWindow, s.c_str());
}
std::string getClipboard() {
    if (!gWindow) return "";
    const char* cb = glfwGetClipboardString(gWindow);
    return cb ? std::string(cb) : "";
}

// ---------------------------------------------------------------------------
// Window icon
// ---------------------------------------------------------------------------
void setWindowIcon(PImage* img) {
    if (!img || !gWindow) return;
    // Convert ARGB pixels (Processing internal) to RGBA (GLFW wants RGBA)
    std::vector<unsigned char> rgba(img->width * img->height * 4);
    for (int p = 0; p < img->width * img->height; p++) {
        unsigned int px = img->pixels[p];
        rgba[p*4+0] = (px >> 16) & 0xFF;  // R
        rgba[p*4+1] = (px >>  8) & 0xFF;  // G
        rgba[p*4+2] = (px      ) & 0xFF;  // B
        rgba[p*4+3] = (px >> 24) & 0xFF;  // A
    }
    GLFWimage gi;
    gi.width  = img->width;
    gi.height = img->height;
    gi.pixels = rgba.data();
    glfwSetWindowIcon(gWindow, 1, &gi);
}

// ---------------------------------------------------------------------------
// Modifier key state
// ---------------------------------------------------------------------------
static int g_currentMods = 0; // GLFW modifier bitmask, set in key/mouse callbacks

bool isCtrlDown() {
    // Use GLFW mods bitmask (reliable from callbacks) OR glfwGetKey (for polling)
    if (g_currentMods & GLFW_MOD_CONTROL) return true;
    if (!gWindow) return false;
    return glfwGetKey(gWindow, GLFW_KEY_LEFT_CONTROL)  == GLFW_PRESS
        || glfwGetKey(gWindow, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS;
}
bool isShiftDown() {
    if (g_currentMods & GLFW_MOD_SHIFT) return true;
    if (!gWindow) return false;
    return glfwGetKey(gWindow, GLFW_KEY_LEFT_SHIFT)  == GLFW_PRESS
        || glfwGetKey(gWindow, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
}
bool isAltDown() {
    if (g_currentMods & GLFW_MOD_ALT) return true;
    if (!gWindow) return false;
    return glfwGetKey(gWindow, GLFW_KEY_LEFT_ALT)  == GLFW_PRESS
        || glfwGetKey(gWindow, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS;
}

void windowRatio(int w,int h){if(gWindow)glfwSetWindowAspectRatio(gWindow,w,h);}
void pixelDensity(int d){pixelDensityValue=d;}
void smooth()  {smoothing=true; glEnable(GL_LINE_SMOOTH);glHint(GL_LINE_SMOOTH_HINT,GL_NICEST);glEnable(GL_POINT_SMOOTH);glHint(GL_POINT_SMOOTH_HINT,GL_NICEST);glEnable(GL_MULTISAMPLE);}
void noSmooth(){smoothing=false;glDisable(GL_LINE_SMOOTH);glDisable(GL_POLYGON_SMOOTH);glDisable(GL_POINT_SMOOTH);glDisable(GL_MULTISAMPLE);}
void hint(int which){
    switch(which){
        case ENABLE_DEPTH_TEST:  glEnable(GL_DEPTH_TEST);  break;
        case DISABLE_DEPTH_TEST: glDisable(GL_DEPTH_TEST); break;
        case ENABLE_DEPTH_SORT:  glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL); break;
        case DISABLE_DEPTH_SORT: glDepthFunc(GL_LESS); break;
        default: break;
    }
}
void cursor()       {if(gWindow)glfwSetInputMode(gWindow,GLFW_CURSOR,GLFW_CURSOR_NORMAL);}
void cursor(int type){if(!gWindow)return;GLFWcursor* c=glfwCreateStandardCursor(type);if(c)glfwSetCursor(gWindow,c);}
void noCursor()     {if(gWindow)glfwSetInputMode(gWindow,GLFW_CURSOR,GLFW_CURSOR_HIDDEN);}
// captureMouse(): locks cursor to window and provides unlimited delta movement.
// Use releaseMouse() or press ESC to free it.
void captureMouse() {if(gWindow){glfwSetInputMode(gWindow,GLFW_CURSOR,GLFW_CURSOR_DISABLED);
    // Enable raw motion if supported (removes OS acceleration)
    if(glfwRawMouseMotionSupported())
        glfwSetInputMode(gWindow,GLFW_RAW_MOUSE_MOTION,GLFW_TRUE);}}
void releaseMouse(){if(gWindow){glfwSetInputMode(gWindow,GLFW_CURSOR,GLFW_CURSOR_NORMAL);
    glfwSetInputMode(gWindow,GLFW_RAW_MOUSE_MOTION,GLFW_FALSE);}}

// =============================================================================
// STYLE STACK
// =============================================================================

static void captureStyle(Style& s) {
    s = { fillR, fillG, fillB, fillA,
          strokeR, strokeG, strokeB, strokeA, strokeW,
          doFill, doStroke,
          currentRectMode, currentEllipseMode, currentImageMode,
          tintR, tintG, tintB, tintA, doTint,
          colorModeVal, colorMaxH, colorMaxS, colorMaxB, colorMaxA };
}
static void restoreStyle(const Style& s) {
    fillR = s.fillR; fillG = s.fillG; fillB = s.fillB; fillA = s.fillA;
    strokeR = s.strokeR; strokeG = s.strokeG; strokeB = s.strokeB;
    strokeA = s.strokeA; strokeW = s.strokeW;
    doFill = s.doFill; doStroke = s.doStroke;
    currentRectMode   = s.rectMode;
    currentEllipseMode = s.ellipseMode;
    currentImageMode  = s.imageMode;
    tintR = s.tintR; tintG = s.tintG; tintB = s.tintB; tintA = s.tintA;
    doTint = s.doTint;
    colorModeVal = s.colorMode;
    colorMaxH = s.cmH; colorMaxS = s.cmS; colorMaxB = s.cmB; colorMaxA = s.cmA;
}
void pushStyle() {
    Style s;
    captureStyle(s);
    styleStack.push_back(s);
}

void popStyle() {
    if (!styleStack.empty()) {
        restoreStyle(styleStack.back());
        styleStack.pop_back();
    }
}

void push()       { glPushMatrix(); pushStyle(); }
void pop()        { glPopMatrix();  popStyle();  }
void pushMatrix() { glPushMatrix(); }
void popMatrix()  { glPopMatrix();  }

// =============================================================================
// BACKGROUND / CLEAR
// =============================================================================

static float bgR=0.8f,bgG=0.8f,bgB=0.8f,bgA=1; // Java Processing default grey // last background() color
static void setBg(float r,float g,float b,float a){
    bgR=r;bgG=g;bgB=b;bgA=a;
    glClearColor(r,g,b,a);
    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
}
void background(float gray)           {background(gray, colorMaxA);}
void background(float gray, float a) {
    color c = makeColor(gray, a);
    unsigned int v = c.value;
    setBg((v>>16&0xFF)/255.f, (v>>8&0xFF)/255.f, (v&0xFF)/255.f, (v>>24&0xFF)/255.f);
}
void background(float r, float g, float b, float a) {
    color c = makeColor(r, g, b, a);
    unsigned int v = c.value;
    setBg((v>>16&0xFF)/255.f, (v>>8&0xFF)/255.f, (v&0xFF)/255.f, (v>>24&0xFF)/255.f);
}
void background(color c) {
    unsigned int v = c.value;
    setBg((v>>16&0xFF)/255.f, (v>>8&0xFF)/255.f, (v&0xFF)/255.f, (v>>24&0xFF)/255.f);
}
static void drawImageRect(PImage& img,float x,float y,float w,float h); // fwd for background(PImage)
void background(const PImage& img) {
    // Draw image as full-canvas background
    if (img.width == 0 || img.height == 0) return;
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glMatrixMode(GL_PROJECTION); glPushMatrix(); glLoadIdentity();
    glOrtho(0, logicalW, logicalH, 0, -1, 1);
    glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity();
    glDisable(GL_DEPTH_TEST);
    drawImageRect(const_cast<PImage&>(img), 0, 0, (float)logicalW, (float)logicalH);
    glMatrixMode(GL_PROJECTION); glPopMatrix();
    glMatrixMode(GL_MODELVIEW); glPopMatrix();
    if (defaultP3D) glEnable(GL_DEPTH_TEST);
}
void clear(){glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);}

// =============================================================================
// FILL / STROKE
// =============================================================================

void fill(float gray,float a)              {setFillFromColor(makeColor(gray,a));}
void fill(float gray)                      {setFillFromColor(makeColor(gray,colorMaxA));}
void fill(float r,float g,float b,float a) {setFillFromColor(makeColor(r,g,b,a));}
void fill(float r,float g,float b)           {setFillFromColor(makeColor(r,g,b,colorMaxA));}
void fill(color c)                         {setFillFromColor(c);}
void fill(color c, float a) {
    unsigned int v = c.value;
    fillR = (v>>16&0xFF)/255.f;
    fillG = (v>>8 &0xFF)/255.f;
    fillB = (v    &0xFF)/255.f;
    fillA = std::min(255.f, std::max(0.f, a)) / 255.f;
    doFill = true;
}
void noFill()                              {doFill=false;}
void stroke(float gray,float a)            {setStrokeFromColor(makeColor(gray,a));}
void stroke(float gray)                    {setStrokeFromColor(makeColor(gray,colorMaxA));}
void stroke(float r,float g,float b,float a){setStrokeFromColor(makeColor(r,g,b,a));}
void stroke(float r,float g,float b)          {setStrokeFromColor(makeColor(r,g,b,colorMaxA));}
void stroke(color c)                       {setStrokeFromColor(c);}
void noStroke()                            {doStroke=false;}
void strokeWeight(float w)                 {strokeW=w;}
void strokeCap(int)  {}
void strokeJoin(int) {}

// =============================================================================
// PCOLOR CONVENIENCE OVERLOADS
// =============================================================================

void fill(const PColor& c)      { fill(c.r, c.g, c.b, c.a); }
void stroke(const PColor& c)    { stroke(c.r, c.g, c.b, c.a); }
void background(const PColor& c){ background(c.r, c.g, c.b, c.a); }
void tint(const PColor& c)      { tint(c.r, c.g, c.b, c.a); }
void rectMode(int m)    {currentRectMode=m;}
void ellipseMode(int m) {currentEllipseMode=m;}

// =============================================================================
// 2D PRIMITIVES
// =============================================================================

static void flushPoints(){} // no-op, points drawn immediately now

void point(float x, float y) {
    if (!doStroke) return;
    applyStroke();
    if (!smoothing && strokeW <= 1.0f) {
        glPointSize(1.0f);
        glBegin(GL_POINTS); glVertex2f(std::floor(x)+0.5f, std::floor(y)+0.5f); glEnd();
    } else {
        glPointSize(strokeW);
        glBegin(GL_POINTS); glVertex2f(x, y); glEnd();
    }
    restoreLighting();
}
void point(float x, float y, float z) {
    if (!doStroke) return;
    applyStroke();
    glPointSize(strokeW);
    glBegin(GL_POINTS); glVertex3f(x, y, z); glEnd();
    restoreLighting();
}
void line(float x1, float y1, float x2, float y2) {
    if (!doStroke) return;
    applyStroke();
    float w = strokeW;
    if (w <= 1.0f) {
        // Thin lines: use GL_LINES
        glLineWidth(1.0f);
        glBegin(GL_LINES); glVertex2f(x1,y1); glVertex2f(x2,y2); glEnd();
        restoreLighting();
        return;
    }
    // Thick line: quad body + round caps using stencil to prevent double-blend
    float dx = x2-x1, dy = y2-y1;
    float len = std::sqrt(dx*dx+dy*dy);
    if(len < 0.0001f){ restoreLighting(); return; }
    float ux = dx/len, uy = dy/len;
    float r = w*0.5f;
    float px2 = -uy*r, py2 = ux*r; // perpendicular
    int segs = std::max(16, (int)(r*4.0f));
    // Use stencil to draw all geometry, then fill once -- prevents double-blend
    glEnable(GL_STENCIL_TEST);
    glClearStencil(0);
    glClear(GL_STENCIL_BUFFER_BIT);
    glStencilFunc(GL_ALWAYS, 1, 0xFF);
    glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
    glColorMask(GL_FALSE,GL_FALSE,GL_FALSE,GL_FALSE);
    // Draw body into stencil
    glBegin(GL_QUADS);
    glVertex2f(x1+px2,y1+py2); glVertex2f(x2+px2,y2+py2);
    glVertex2f(x2-px2,y2-py2); glVertex2f(x1-px2,y1-py2);
    glEnd();
    // Draw end caps into stencil
    for(int ep=0;ep<2;ep++){
        float cx2=ep?x2:x1, cy2=ep?y2:y1;
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx2,cy2);
        for(int s=0;s<=segs;s++){
            float a=s*TWO_PI/segs;
            glVertex2f(cx2+std::cos(a)*r, cy2+std::sin(a)*r);
        }
        glEnd();
    }
    // Now draw color only where stencil=1
    glColorMask(GL_TRUE,GL_TRUE,GL_TRUE,GL_TRUE);
    glStencilFunc(GL_EQUAL, 1, 0xFF);
    glStencilOp(GL_KEEP,GL_KEEP,GL_KEEP);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
    // Fill bounding box with stroke color
    float minx=std::min(x1,x2)-r, maxx=std::max(x1,x2)+r;
    float miny=std::min(y1,y2)-r, maxy=std::max(y1,y2)+r;
    glBegin(GL_QUADS);
    glVertex2f(minx,miny); glVertex2f(maxx,miny);
    glVertex2f(maxx,maxy); glVertex2f(minx,maxy);
    glEnd();
    glDisable(GL_STENCIL_TEST);
    restoreLighting();
}
void line(float x1, float y1, float z1, float x2, float y2, float z2) {
    if (!doStroke) return;
    applyStroke();
    glLineWidth(strokeW);
    glBegin(GL_LINES); glVertex3f(x1,y1,z1); glVertex3f(x2,y2,z2); glEnd();
    restoreLighting();
}
void ellipse(float cx, float cy, float w, float h) {
    float rx = w, ry = h;
    resolveEllipse(cx, cy, rx, ry);
    drawEllipseGeom(cx, cy, rx, ry);
}

void circle(float cx, float cy, float diameter) {
    ellipse(cx, cy, diameter, diameter);
}

void arc(float cx, float cy, float w, float h, float startAngle, float endAngle) {
    float rx = w, ry = h;
    resolveEllipse(cx, cy, rx, ry);
    drawEllipseGeom(cx, cy, rx, ry, startAngle, endAngle);
}

void arc(float cx, float cy, float w, float h, float startAngle, float endAngle, int /*mode*/) {
    // mode = OPEN / CHORD / PIE -- basic implementation uses OPEN
    arc(cx, cy, w, h, startAngle, endAngle);
}
void rect(float x, float y, float w, float h) {
    resolveRect(x, y, w, h);

    if (doFill) {
        applyFill();
        glBegin(GL_QUADS);
            glVertex2f(x,     y    );
            glVertex2f(x + w, y    );
            glVertex2f(x + w, y + h);
            glVertex2f(x,     y + h);
        glEnd();
    }

    if (doStroke) {
        // Expand stroke outward by half strokeWeight so it sits outside the fill
        float half = strokeW * 0.5f;
        applyStroke();
        glLineWidth(strokeW);
        glBegin(GL_LINE_LOOP);
            glVertex2f(x - half,     y - half    );
            glVertex2f(x + w + half, y - half    );
            glVertex2f(x + w + half, y + h + half);
            glVertex2f(x - half,     y + h + half);
        glEnd();
        restoreLighting();
    }
}
void rect(float x, float y, float w, float h, float r) {
    resolveRect(x, y, w, h);

    // Clamp radius so it never exceeds half the shortest side
    r = min(r, min(w, h) * 0.5f);

    const int cornerSegs = 8;  // segments per 90-degree corner arc

    // Emit a corner arc: center (cx,cy), from angle sa to ea
    auto corner = [&](float cx, float cy, float sa, float ea) {
        for (int i = 0; i <= cornerSegs; i++) {
            float a = sa + (ea - sa) * i / cornerSegs;
            glVertex2f(cx + r * std::cos(a), cy + r * std::sin(a));
        }
    };

    if (doFill) {
        applyFill();
        glBegin(GL_TRIANGLE_FAN);
            // Fan center at rect midpoint
            glVertex2f(x + w * 0.5f, y + h * 0.5f);
            // Four corners in CCW order, closing back to the first arc point
            corner(x + r,     y + r,     PI,             PI + HALF_PI);  // top-left
            corner(x + w - r, y + r,     PI + HALF_PI,   TWO_PI      );  // top-right
            corner(x + w - r, y + h - r, 0,              HALF_PI     );  // bottom-right
            corner(x + r,     y + h - r, HALF_PI,        PI          );  // bottom-left
            // Close the fan back to the first arc point (avoids gap/seam)
            glVertex2f(x + r + r * std::cos(PI), y + r + r * std::sin(PI));
        glEnd();
    }

    if (doStroke) {
        applyStroke();
        glLineWidth(strokeW);
        glBegin(GL_LINE_LOOP);
            corner(x + r,     y + r,     PI,           PI + HALF_PI);
            corner(x + w - r, y + r,     PI + HALF_PI, TWO_PI      );
            corner(x + w - r, y + h - r, 0,            HALF_PI     );
            corner(x + r,     y + h - r, HALF_PI,      PI          );
        glEnd();
    }
}
void square(float x,float y,float s){rect(x,y,s,s);}
void triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
    if (doFill) {
        applyFill();
        glBegin(GL_TRIANGLES);
            glVertex2f(x1, y1);
            glVertex2f(x2, y2);
            glVertex2f(x3, y3);
        glEnd();
    }
    if (doStroke) {
        applyStroke();
        glLineWidth(strokeW);
        glBegin(GL_LINE_LOOP);
            glVertex2f(x1, y1);
            glVertex2f(x2, y2);
            glVertex2f(x3, y3);
        glEnd();
        restoreLighting();
    }
}
void quad(float x1, float y1, float x2, float y2,
          float x3, float y3, float x4, float y4) {
    if (doFill) {
        applyFill();
        glBegin(GL_QUADS);
            glVertex2f(x1, y1);
            glVertex2f(x2, y2);
            glVertex2f(x3, y3);
            glVertex2f(x4, y4);
        glEnd();
    }
    if (doStroke) {
        applyStroke();
        glLineWidth(strokeW);
        glBegin(GL_LINE_LOOP);
            glVertex2f(x1, y1);
            glVertex2f(x2, y2);
            glVertex2f(x3, y3);
            glVertex2f(x4, y4);
        glEnd();
        restoreLighting();
    }
}

// =============================================================================
// 3D PRIMITIVES
// =============================================================================

void rotateX(float a){glRotatef(a*180.0f/PI,1,0,0);}
void rotateY(float a){glRotatef(a*180.0f/PI,0,1,0);}
void rotateZ(float a){glRotatef(a*180.0f/PI,0,0,1);}
void sphereDetail(int r){sphereRes=r;}

// =============================================================================
// PHONG SHADING SHADER
// =============================================================================
static GLuint phongProg = 0;
static int    phongVersion = 0; // increment to force recompile

static GLuint compileShader(GLenum type, const char* code) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &code, nullptr);
    glCompileShader(sh);
    GLint ok; glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) { char b[512]; glGetShaderInfoLog(sh,512,nullptr,b); fprintf(stderr,"[sh] %s\n",b); }
    return sh;
}
static void initPhongShader() {
    if (phongProg) return;
    // Phong shader using compatibility profile built-ins.
    // Works on any system that supports our fixed-function GL pipeline.
    const char* vs = R"VERT(
varying vec3 vN;
varying vec3 vP;
void main(){
    // gl_NormalMatrix = inverse-transpose of modelview upper 3x3
    vN = gl_NormalMatrix * gl_Normal;
    vP = vec3(gl_ModelViewMatrix * gl_Vertex);
    gl_Position = ftransform();
    gl_FrontColor = gl_Color;
}
)VERT";
    const char* fs = R"FRAG(
varying vec3 vN;
varying vec3 vP;
uniform int uNumLights;
uniform float uLightConc[8];
uniform float uLightCutCos[8];
void main(){
    vec3 N = normalize(vN);
    vec3 V = normalize(-vP);
    vec3 col = gl_Color.rgb * gl_LightModel.ambient.rgb;
    for(int i=0;i<8;i++){
        if(i>=uNumLights) break;
        vec4 lp = gl_LightSource[i].position;
        bool isDir = (lp.w < 0.5);
        vec3 L = isDir ? normalize(lp.xyz) : normalize(lp.xyz - vP);
        float spotAtten = 1.0;
        if(!isDir && uLightCutCos[i] > -0.5){
            vec3 sd = normalize(gl_LightSource[i].spotDirection);
            float sc = dot(L, -sd);
            if(sc < uLightCutCos[i]) spotAtten = 0.0;
            else spotAtten = pow(max(sc,0.0), uLightConc[i]);
        }
        if(spotAtten < 0.0001) continue;
        col += gl_Color.rgb * gl_LightSource[i].ambient.rgb;
        float d = max(dot(N, L), 0.0);
        col += gl_Color.rgb * gl_LightSource[i].diffuse.rgb * d * spotAtten;
        vec3 H = normalize(L + V);
        float nh = max(dot(N, H), 0.0);
        float sh = pow(nh, gl_FrontMaterial.shininess + 1.0);
        col += gl_FrontMaterial.specular.rgb * gl_LightSource[i].specular.rgb * sh * spotAtten;
    }
    gl_FragColor = vec4(col, gl_Color.a);
}
)FRAG";
    GLuint v=compileShader(GL_VERTEX_SHADER,vs);
    GLuint f=compileShader(GL_FRAGMENT_SHADER,fs);
    phongProg=glCreateProgram();
    glAttachShader(phongProg,v); glAttachShader(phongProg,f);
    glLinkProgram(phongProg);
    GLint ok; glGetProgramiv(phongProg,GL_LINK_STATUS,&ok);
    if(!ok){char b[512];glGetProgramInfoLog(phongProg,512,nullptr,b);
            fprintf(stderr,"[sh link] %s\n",b);phongProg=0;}
    glDeleteShader(v); glDeleteShader(f);
}

void box(float s){box(s,s,s);}
void box(float bw,float bh,float bd){
    float hw=bw/2,hh=bh/2,hd=bd/2;
    struct Face{ float nx,ny,nz; float v[4][3]; };
    Face faces[]={
        { 0, 0, 1,{{-hw,-hh,hd},{hw,-hh,hd},{hw,hh,hd},{-hw,hh,hd}}},
        { 0, 0,-1,{{hw,-hh,-hd},{-hw,-hh,-hd},{-hw,hh,-hd},{hw,hh,-hd}}},
        {-1, 0, 0,{{-hw,-hh,-hd},{-hw,-hh,hd},{-hw,hh,hd},{-hw,hh,-hd}}},
        { 1, 0, 0,{{hw,-hh,hd},{hw,-hh,-hd},{hw,hh,-hd},{hw,hh,hd}}},
        { 0,-1, 0,{{-hw,-hh,-hd},{hw,-hh,-hd},{hw,-hh,hd},{-hw,-hh,hd}}},
        { 0, 1, 0,{{-hw,hh,hd},{hw,hh,hd},{hw,hh,-hd},{-hw,hh,-hd}}},
    };
    if(doFill){
        applyFill();
        glBegin(GL_QUADS);
        for(auto& f:faces){
            glNormal3f(f.nx,f.ny,f.nz);
            for(auto& v:f.v) glVertex3f(v[0],v[1],v[2]);
        }
        glEnd();
    }
    if(doStroke){
        applyStroke();glLineWidth(strokeW);
        float vx[]={-hw,-hw,hw,hw,-hw,-hw,hw,hw};
        float vy[]={-hh,hh,hh,-hh,-hh,hh,hh,-hh};
        float vz[]={hd,hd,hd,hd,-hd,-hd,-hd,-hd};
        int e[][2]={{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        glBegin(GL_LINES);
        for(auto& ee:e){glVertex3f(vx[ee[0]],vy[ee[0]],vz[ee[0]]);glVertex3f(vx[ee[1]],vy[ee[1]],vz[ee[1]]);}
        glEnd();
        restoreLighting();
    }
}
void sphere(float r){
    int stacks=sphereRes, slices=sphereRes;
    if(doFill){
        // Use Phong (per-pixel) shading when lighting is on for smooth highlights
        bool usePhong = lightsEnabled;
        if (usePhong) {
            initPhongShader();
            if (phongProg) {
                glUseProgram(phongProg);
                GLint loc = glGetUniformLocation(phongProg, "uNumLights");
                if (loc >= 0) glUniform1i(loc, lightIndex);
                GLint locC = glGetUniformLocation(phongProg, "uLightConc");
                if (locC >= 0) glUniform1fv(locC, 8, lightConcentration);
                GLint locK = glGetUniformLocation(phongProg, "uLightCutCos");
                if (locK >= 0) glUniform1fv(locK, 8, lightCutoffCos);
            } else usePhong = false;
        }
        applyFill();
        for(int i=0;i<stacks;i++){
            float a0=PI*i/stacks-HALF_PI, a1=PI*(i+1)/stacks-HALF_PI;
            glBegin(GL_QUAD_STRIP);
            for(int j=0;j<=slices;j++){
                float b=TWO_PI*j/slices;
                float x1=std::cos(a1)*std::cos(b),y1=std::sin(a1),z1=std::cos(a1)*std::sin(b);
                float x0=std::cos(a0)*std::cos(b),y0=std::sin(a0),z0=std::cos(a0)*std::sin(b);
                glNormal3f(x1,y1,z1); glVertex3f(r*x1,r*y1,r*z1);
                glNormal3f(x0,y0,z0); glVertex3f(r*x0,r*y0,r*z0);
            }
            glEnd();
        }
        if (usePhong) glUseProgram(0);
    }
    if(doStroke){
        applyStroke(); glLineWidth(strokeW);
        // Draw sphere as a wireframe of triangles -- matches Processing Java's
        // sphere appearance with diagonal lines across each quad cell.
        auto sv = [&](float lat, float lng) {
            glVertex3f(r*std::cos(lat)*std::cos(lng),
                       r*std::sin(lat),
                       r*std::cos(lat)*std::sin(lng));
        };
        glBegin(GL_LINES);
        for(int i=0;i<stacks;i++){
            float lat0=PI*(-0.5f+(float)i/stacks);
            float lat1=PI*(-0.5f+(float)(i+1)/stacks);
            for(int j=0;j<slices;j++){
                float lng0=TWO_PI*(float)j/slices;
                float lng1=TWO_PI*(float)(j+1)/slices;
                // Four edges of the quad + one diagonal (like Processing Java)
                // Bottom edge (latitude ring)
                sv(lat0,lng0); sv(lat0,lng1);
                // Left edge (longitude line)
                sv(lat0,lng0); sv(lat1,lng0);
                // Diagonal (gives the characteristic triangulated look)
                sv(lat0,lng1); sv(lat1,lng0);
            }
        }
        // Top latitude ring
        {
            float lat1=PI*(-0.5f+(float)stacks/stacks);
            for(int j=0;j<slices;j++){
                float lng0=TWO_PI*(float)j/slices;
                float lng1=TWO_PI*(float)(j+1)/slices;
                sv(lat1,lng0); sv(lat1,lng1);
            }
        }
        glEnd();
        restoreLighting();
    }
}

// =============================================================================
// VERTEX / SHAPES
// =============================================================================

void beginShape(int kind){
    shapeKind = kind;
    inShape   = true;
    shape3D   = false;
    shapeVerts.clear();
    shapeVerts3D.clear();
    // Vertices are always collected in shapeVerts / shapeVerts3D.
    // endShape() draws fill and/or stroke from those collections.
    // shape3D is set when 3-component vertices are used (vertex(x,y,z)).
}
void vertex(float x, float y) {
    if (inContour) { contourVerts.push_back({x,y}); return; }
    if (!inShape)  return;
    shapeVerts.push_back({x, y});
    shapeVerts3D.push_back({x, y, 0.0f});
}
void vertex(float x, float y, float z) {
    if (inContour) { contourVerts.push_back({x,y}); return; }
    if (!inShape)  return;
    shapeVerts.push_back({x, y});
    shapeVerts3D.push_back({x, y, z});
    if (z != 0.0f) shape3D = true;
}
void vertex(float x, float y, float u, float v) {
    if (!inShape) return;
    shapeVerts.push_back({x, y});
    shapeVerts3D.push_back({x, y, 0.0f});
    // UV stored for future texture mapping support
}
void vertex(float x, float y, float z, float u, float v) {
    if (!inShape) return;
    shapeVerts.push_back({x, y});
    shapeVerts3D.push_back({x, y, z});
    if (z != 0.0f) shape3D = true;
}
void beginContour(){inContour=true;contourVerts.clear();}
void endContour()  {inContour=false;}

void endShape(int mode){
    if(!inShape){return;}
    bool cl=(mode==CLOSE);
    int  n3 = (int)shapeVerts3D.size();

    // -- Explicit shape kinds (QUADS, QUAD_STRIP, TRIANGLES, etc.) ----------
    // Draw fill and/or stroke from the collected vertex arrays.
    if(shapeKind != -1 && n3 > 0){
        GLenum gm;
        switch(shapeKind){
            case POINTS:        gm=GL_POINTS;        break;
            case LINES:         gm=GL_LINES;         break;
            case TRIANGLES:     gm=GL_TRIANGLES;     break;
            case TRIANGLE_FAN:  gm=GL_TRIANGLE_FAN;  break;
            case TRIANGLE_STRIP:gm=GL_TRIANGLE_STRIP;break;
            case QUADS:         gm=GL_QUADS;         break;
            case QUAD_STRIP:    gm=GL_QUAD_STRIP;    break;
            default:            gm=GL_POLYGON;       break;
        }

        if(doFill && shapeKind != POINTS && shapeKind != LINES){
            glEnable(GL_POLYGON_OFFSET_FILL);
            glPolygonOffset(1.0f, 1.0f);
            applyFill();
            glBegin(gm);
            for(auto& v : shapeVerts3D) glVertex3f(v[0], v[1], v[2]);
            glEnd();
            glDisable(GL_POLYGON_OFFSET_FILL);
        }
        // POINTS and LINES use stroke color drawn directly
        if(shapeKind == POINTS){
            applyStroke();
            glPointSize(strokeW);
            glBegin(GL_POINTS);
            for(auto& v : shapeVerts3D) glVertex3f(v[0], v[1], v[2]);
            glEnd();
            restoreLighting();
            inShape=false; shape3D=false; shapeVerts.clear(); shapeVerts3D.clear();
            return;
        }
        if(shapeKind == LINES){
            applyStroke(); glLineWidth(strokeW);
            glBegin(GL_LINES);
            for(auto& v : shapeVerts3D) glVertex3f(v[0], v[1], v[2]);
            glEnd();
            restoreLighting();
            inShape=false; shape3D=false; shapeVerts.clear(); shapeVerts3D.clear();
            return;
        }
        // Draw stroke outlines -- shapeVerts was collected in vertex()
        if(doStroke && !shapeVerts.empty()){
            applyStroke(); glLineWidth(strokeW);
            int n=(int)shapeVerts.size();
            // Use 3D verts for correct depth in transformed space
            auto lv=[&](int i)->std::array<float,3>{ return shapeVerts3D[i]; };
            switch(shapeKind){
                case TRIANGLE_STRIP:
                    glBegin(GL_LINES);
                    for(int i=0;i+2<n;i++){
                        auto a=lv(i),b=lv(i+1),c=lv(i+2);
                        glVertex3f(a[0],a[1],a[2]);glVertex3f(b[0],b[1],b[2]);
                        glVertex3f(b[0],b[1],b[2]);glVertex3f(c[0],c[1],c[2]);
                        glVertex3f(c[0],c[1],c[2]);glVertex3f(a[0],a[1],a[2]);
                    }
                    glEnd();
                    break;
                case TRIANGLE_FAN:
                    glBegin(GL_LINES);
                    for(int i=1;i+1<n;i++){
                        auto a=lv(0),b=lv(i),c=lv(i+1);
                        glVertex3f(a[0],a[1],a[2]);glVertex3f(b[0],b[1],b[2]);
                        glVertex3f(b[0],b[1],b[2]);glVertex3f(c[0],c[1],c[2]);
                        glVertex3f(c[0],c[1],c[2]);glVertex3f(a[0],a[1],a[2]);
                    }
                    glEnd();
                    break;
                case TRIANGLES:
                    glBegin(GL_LINES);
                    for(int i=0;i+2<n;i+=3){
                        auto a=lv(i),b=lv(i+1),c=lv(i+2);
                        glVertex3f(a[0],a[1],a[2]);glVertex3f(b[0],b[1],b[2]);
                        glVertex3f(b[0],b[1],b[2]);glVertex3f(c[0],c[1],c[2]);
                        glVertex3f(c[0],c[1],c[2]);glVertex3f(a[0],a[1],a[2]);
                    }
                    glEnd();
                    break;
                case QUADS:
                    glBegin(GL_LINES);
                    for(int i=0;i+3<n;i+=4){
                        auto a=lv(i),b=lv(i+1),c=lv(i+2),d=lv(i+3);
                        glVertex3f(a[0],a[1],a[2]);glVertex3f(b[0],b[1],b[2]);
                        glVertex3f(b[0],b[1],b[2]);glVertex3f(c[0],c[1],c[2]);
                        glVertex3f(c[0],c[1],c[2]);glVertex3f(d[0],d[1],d[2]);
                        glVertex3f(d[0],d[1],d[2]);glVertex3f(a[0],a[1],a[2]);
                    }
                    glEnd();
                    break;
                case QUAD_STRIP:
                    glBegin(GL_LINES);
                    for(int i=0;i+3<n;i+=2){
                        auto a=lv(i),b=lv(i+1),c=lv(i+3),d=lv(i+2);
                        glVertex3f(a[0],a[1],a[2]);glVertex3f(b[0],b[1],b[2]);
                        glVertex3f(b[0],b[1],b[2]);glVertex3f(c[0],c[1],c[2]);
                        glVertex3f(c[0],c[1],c[2]);glVertex3f(d[0],d[1],d[2]);
                        glVertex3f(d[0],d[1],d[2]);glVertex3f(a[0],a[1],a[2]);
                    }
                    glEnd();
                    break;
                default:
                    glBegin(cl?GL_LINE_LOOP:GL_LINE_STRIP);
                    for(auto& v:shapeVerts3D)glVertex3f(v[0],v[1],v[2]);
                    glEnd();
                    break;
            }
        }
        restoreLighting();
        inShape=false; shape3D=false; shapeVerts.clear(); shapeVerts3D.clear();
        return;
    }
    if(shapeVerts.empty()){inShape=false;return;}

    // Default beginShape() / endShape(CLOSE) path.
    // Use stencil buffer to fill arbitrary (including concave) polygons correctly.
    // This matches Java Processing's polygon fill behavior.
    if(shapeKind==-1 || shapeKind==CLOSE){
        if(doFill && shapeVerts.size()>=3){
            glDisable(GL_CULL_FACE);

            // Step 1: Write polygon to stencil buffer using odd-even fill rule
            glEnable(GL_STENCIL_TEST);
            glClear(GL_STENCIL_BUFFER_BIT);
            glStencilFunc(GL_ALWAYS, 0, ~0);
            glStencilOp(GL_KEEP, GL_KEEP, GL_INVERT); // toggle stencil bit
            glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE); // don't write color
            glBegin(GL_TRIANGLE_FAN);
            for(auto& v : shapeVerts3D) glVertex3f(v[0], v[1], v[2]);
            glEnd();
            glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);

            // Step 2: Draw fill color only where stencil != 0
            glStencilFunc(GL_NOTEQUAL, 0, ~0);
            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
            applyFill();
            // Draw a bounding quad covering the shape
            float minx=shapeVerts3D[0][0], maxx=minx;
            float miny=shapeVerts3D[0][1], maxy=miny;
            float minz=shapeVerts3D[0][2], maxz=minz;
            for(auto& v:shapeVerts3D){
                minx=std::min(minx,v[0]); maxx=std::max(maxx,v[0]);
                miny=std::min(miny,v[1]); maxy=std::max(maxy,v[1]);
                minz=std::min(minz,v[2]); maxz=std::max(maxz,v[2]);
            }
            float mz=(minz+maxz)*0.5f;
            glBegin(GL_QUADS);
            glVertex3f(minx,miny,mz); glVertex3f(maxx,miny,mz);
            glVertex3f(maxx,maxy,mz); glVertex3f(minx,maxy,mz);
            glEnd();

            glDisable(GL_STENCIL_TEST);
        }
        if(doStroke){
            applyStroke(); glLineWidth(strokeW);
            glBegin(cl ? GL_LINE_LOOP : GL_LINE_STRIP);
            // Use 3D coords so transforms (rotateX/Y) are respected
            for(auto& v : shapeVerts3D) glVertex3f(v[0], v[1], v[2]);
            glEnd();
            restoreLighting();
        }
    }
    inShape=false; shapeVerts.clear(); shapeVerts3D.clear();
}

void bezierVertex(float cx1,float cy1,float cx2,float cy2,float x,float y){
    if(!inShape||shapeVerts.empty())return;
    auto[x0,y0]=shapeVerts.back();const int sg=bezierDetailVal;
    for(int i=1;i<=sg;i++){float t=i/(float)sg,u=1-t;
        float bx=u*u*u*x0+3*u*u*t*cx1+3*u*t*t*cx2+t*t*t*x, by=u*u*u*y0+3*u*u*t*cy1+3*u*t*t*cy2+t*t*t*y;
        shapeVerts.push_back({bx,by}); shapeVerts3D.push_back({bx,by,0.0f});}
}
void quadraticVertex(float cx,float cy,float x,float y){
    if(!inShape||shapeVerts.empty())return;
    auto[x0,y0]=shapeVerts.back();const int sg=bezierDetailVal;
    for(int i=1;i<=sg;i++){float t=i/(float)sg,u=1-t;float qx=u*u*x0+2*u*t*cx+t*t*x,qy=u*u*y0+2*u*t*cy+t*t*y;shapeVerts.push_back({qx,qy});shapeVerts3D.push_back({qx,qy,0.0f});}
}
void curveVertex(float x,float y){if(inShape){shapeVerts.push_back({x,y});shapeVerts3D.push_back({x,y,0.0f});}}

void bezier(float x1,float y1,float cx1,float cy1,float cx2,float cy2,float x2,float y2){
    if(!doStroke)return;applyStroke();glLineWidth(strokeW);glBegin(GL_LINE_STRIP);
    for(int i=0;i<=bezierDetailVal;i++){float t=i/(float)bezierDetailVal,u=1-t;
        glVertex2f(u*u*u*x1+3*u*u*t*cx1+3*u*t*t*cx2+t*t*t*x2,u*u*u*y1+3*u*u*t*cy1+3*u*t*t*cy2+t*t*t*y2);}
    glEnd();
}
void curve(float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3){
    if(!doStroke)return;applyStroke();glLineWidth(strokeW);glBegin(GL_LINE_STRIP);
    float s=curveTightnessVal;
    for(int i=0;i<=curveDetailVal;i++){
        float t=i/(float)curveDetailVal,t2=t*t,t3=t2*t;
        float b0=(-s*t3+2*s*t2-s*t)/2.f,b1=((2-s)*t3+(s-3)*t2+1)/2.f;
        float b2=((s-2)*t3+(3-2*s)*t2+s*t)/2.f,b3=(s*t3-s*t2)/2.f;
        glVertex2f(b0*x0+b1*x1+b2*x2+b3*x3,b0*y0+b1*y1+b2*y2+b3*y3);
    }
    glEnd();
}
float bezierPoint(float a,float b,float c,float d,float t){float u=1-t;return u*u*u*a+3*u*u*t*b+3*u*t*t*c+t*t*t*d;}
float bezierTangent(float a,float b,float c,float d,float t){float u=1-t;return 3*u*u*(b-a)+6*u*t*(c-b)+3*t*t*(d-c);}
float curvePoint(float a,float b,float c,float d,float t){float t2=t*t,t3=t2*t,s=curveTightnessVal;return 0.5f*((-s*t3+2*s*t2-s*t)*a+((2-s)*t3+(s-3)*t2+1)*b+((s-2)*t3+(3-2*s)*t2+s*t)*c+(s*t3-s*t2)*d);}
float curveTangent(float a,float b,float c,float d,float t){float t2=t*t,s=curveTightnessVal;return 0.5f*((-3*s*t2+4*s*t-s)*a+(3*(2-s)*t2+2*(s-3)*t)*b+(3*(s-2)*t2+2*(3-2*s)*t+s)*c+(3*s*t2-2*s*t)*d);}
void curveDetail(int d)     {curveDetailVal=d;}
void curveTightness(float t){curveTightnessVal=t;}
void bezierDetail(int d)    {bezierDetailVal=d;}

// =============================================================================
// MATRIX
// =============================================================================

void resetMatrix(){glLoadIdentity();}
void applyMatrix(float n00,float n01,float n02,float n03,float n10,float n11,float n12,float n13,float n20,float n21,float n22,float n23,float n30,float n31,float n32,float n33){
    float m[]={n00,n10,n20,n30,n01,n11,n21,n31,n02,n12,n22,n32,n03,n13,n23,n33};
    glMultMatrixf(m);
}
void translate(float x,float y)        {glTranslatef(x,y,0);}
void translate(float x,float y,float z){glTranslatef(x,y,z);}
void scale(float s)                    {glScalef(s,s,1);}
void scale(float sx,float sy)          {glScalef(sx,sy,1);}
void rotate(float a)                   {glRotatef(a*180.0f/PI,0,0,1);}
void shearX(float a)                   {float m[]={1,0,0,0,std::tan(a),1,0,0,0,0,1,0,0,0,0,1};glMultMatrixf(m);}
void shearY(float a)                   {float m[]={1,std::tan(a),0,0,0,1,0,0,0,0,1,0,0,0,0,1};glMultMatrixf(m);}
void printMatrix(){float m[16];glGetFloatv(GL_MODELVIEW_MATRIX,m);for(int i=0;i<4;i++){for(int j=0;j<4;j++)std::cout<<m[j*4+i]<<" ";std::cout<<"\n";}}

static void projectPoint(float x,float y,float z,float& ox,float& oy,float& oz){
    float mv[16],proj[16];int vp[4];
    glGetFloatv(GL_MODELVIEW_MATRIX,mv);glGetFloatv(GL_PROJECTION_MATRIX,proj);glGetIntegerv(GL_VIEWPORT,vp);
    float cx=mv[0]*x+mv[4]*y+mv[8]*z+mv[12];
    float cy=mv[1]*x+mv[5]*y+mv[9]*z+mv[13];
    float cz=mv[2]*x+mv[6]*y+mv[10]*z+mv[14];
    float cw=mv[3]*x+mv[7]*y+mv[11]*z+mv[15];
    float px=proj[0]*cx+proj[4]*cy+proj[8]*cz+proj[12]*cw;
    float py=proj[1]*cx+proj[5]*cy+proj[9]*cz+proj[13]*cw;
    float pz=proj[2]*cx+proj[6]*cy+proj[10]*cz+proj[14]*cw;
    float pw=proj[3]*cx+proj[7]*cy+proj[11]*cz+proj[15]*cw;
    if(pw!=0){px/=pw;py/=pw;pz/=pw;}
    ox=vp[0]+(px+1)*0.5f*vp[2];
    oy=vp[1]+(1-py)*0.5f*vp[3];
    oz=(pz+1)*0.5f;
}
float screenX(float x,float y,float z){float ox,oy,oz;projectPoint(x,y,z,ox,oy,oz);return ox;}
float screenY(float x,float y,float z){float ox,oy,oz;projectPoint(x,y,z,ox,oy,oz);return oy;}
float screenZ(float x,float y,float z){float ox,oy,oz;projectPoint(x,y,z,ox,oy,oz);return oz;}
float modelX(float x,float y,float z) {float mv[16];glGetFloatv(GL_MODELVIEW_MATRIX,mv);return mv[0]*x+mv[4]*y+mv[8]*z+mv[12];}
float modelY(float x,float y,float z) {float mv[16];glGetFloatv(GL_MODELVIEW_MATRIX,mv);return mv[1]*x+mv[5]*y+mv[9]*z+mv[13];}
float modelZ(float x,float y,float z) {float mv[16];glGetFloatv(GL_MODELVIEW_MATRIX,mv);return mv[2]*x+mv[6]*y+mv[10]*z+mv[14];}

// =============================================================================
// CAMERA
// =============================================================================
static void applyStandardModelview(); // forward declaration

// Internal helper -- sets up the standard Processing Y-flipped perspective
// camera. Called by camera() and perspective() so they stay in sync.
static void applyDefaultCamera() {
    float eyeZ  = ((float)logicalH / 2.0f) / std::tan(PI * 60.0f / 360.0f);
    float near_ = eyeZ / 10.0f;
    float far_  = eyeZ * 10.0f;
    if(gWindow){int fw=logicalW,fh=logicalH;glfwGetFramebufferSize(gWindow,&fw,&fh);if(fw>0)fbW=fw;if(fh>0)fbH=fh;}
    glViewport(0, 0, fbW, fbH);
    glMatrixMode(GL_PROJECTION); glLoadIdentity();
    glScalef(1,-1,1);
    _gluPerspective(60.0, (double)logicalW / logicalH, near_, far_);
    applyStandardModelview();
}

void camera(){
    applyDefaultCamera();
}
void camera(float ex,float ey,float ez,float cx,float cy,float cz,float ux,float uy,float uz){
    float eyeZ = ((float)logicalH/2.0f) / std::tan(PI*60.0f/360.0f);
    float near_ = eyeZ/10.0f, far_ = eyeZ*10.0f;
    glMatrixMode(GL_PROJECTION); glLoadIdentity();
    glScalef(1,-1,1);
    _gluPerspective(60.0,(double)logicalW/logicalH,near_,far_);
    glMatrixMode(GL_MODELVIEW); glLoadIdentity();
    _gluLookAt(ex,ey,ez,cx,cy,cz,ux,uy,uz);
    glFrontFace(GL_CW);
    glDisable(GL_CULL_FACE);
    glEnable(GL_DEPTH_TEST);
}
void beginCamera(){glMatrixMode(GL_MODELVIEW);glPushMatrix();}
void endCamera()  {glPopMatrix();}

void perspective(){
    applyDefaultCamera();
}
void perspective(float fov, float aspect, float zNear, float zFar) {
    glMatrixMode(GL_PROJECTION); glLoadIdentity();
    glScalef(1,-1,1);
    _gluPerspective(degrees(fov), aspect, zNear, zFar);
    applyStandardModelview();
}
// Helper shared by ortho() and perspective() -- sets up the standard
// Processing modelview camera (eye at eyeZ looking at canvas centre,
// Y-down screen coordinates) and enables depth test.
static void applyStandardModelview() {
    float eyeZ = ((float)logicalH / 2.0f) / std::tan(PI * 60.0f / 360.0f);
    glMatrixMode(GL_MODELVIEW); glLoadIdentity();
    _gluLookAt(logicalW/2.0, logicalH/2.0, eyeZ,
              logicalW/2.0, logicalH/2.0, 0,
              0, 1, 0);
    glFrontFace(GL_CW);
    glDisable(GL_CULL_FACE);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LESS);
}

void ortho() {
    // Default ortho: 1:1 pixel mapping, origin at top-left, Y increases downward.
    // glOrtho(l, r, bottom, top, near, far):
    //   bottom = winHeight (screen bottom = large Y in Processing)
    //   top    = 0         (screen top    = Y=0 in Processing)
    // gluLookAt is NOT used here -- we want raw screen-space coordinates,
    // so we use a simple identity modelview with Y-flipped glOrtho.
    glMatrixMode(GL_PROJECTION); glLoadIdentity();
    glOrtho(0, logicalW, logicalH, 0, -logicalH, logicalH);
    glMatrixMode(GL_MODELVIEW); glLoadIdentity();
    glFrontFace(GL_CW);
    glDisable(GL_CULL_FACE);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LESS);
}

void ortho(float l, float r, float b, float t, float n, float f) {
    // Processing Java ortho() uses the SAME standard modelview as perspective()
    // (eye at eyeZ above canvas center, looking at canvas center).
    // Only the projection changes: orthographic instead of frustum.
    // The Y-flip (glScalef 1,-1,1) is applied in projection space so that
    // Processing's Y-down screen coordinate convention is preserved.
    //
    // With the standard camera, world point (w/2, h/2, 0) is at screen center.
    // ortho(-w/2, w/2, -h/2, h/2) covers that range around the camera target.
    // So translate(w/2, h/2) correctly lands at the center of the screen.
    glMatrixMode(GL_PROJECTION); glLoadIdentity();
    glScalef(1,-1,1);  // Y-flip before ortho (Java order)
    glOrtho(l, r, b, t, n, f);
    applyStandardModelview();
}

void frustum(float l, float r, float b, float t, float n, float f) {
    glMatrixMode(GL_PROJECTION); glLoadIdentity();
    glFrustum(l, r, b, t, n, f);
    applyStandardModelview();
}
void printCamera(){float m[16];glGetFloatv(GL_MODELVIEW_MATRIX,m);std::cout<<"Camera matrix:\n";for(int i=0;i<4;i++){for(int j=0;j<4;j++)std::cout<<m[j*4+i]<<" ";std::cout<<"\n";}}
void printProjection(){float m[16];glGetFloatv(GL_PROJECTION_MATRIX,m);std::cout<<"Projection matrix:\n";for(int i=0;i<4;i++){for(int j=0;j<4;j++)std::cout<<m[j*4+i]<<" ";std::cout<<"\n";}}

// =============================================================================
// LIGHTS
// =============================================================================

// ---------------------------------------------------------------------------
// LIGHTING
//
// Processing Java passes light colors in the range [0..255].
// OpenGL fixed-function expects [0..1].  We normalise on entry.
//
// Light positions/directions must be submitted while the modelview matrix
// is in EYE SPACE (camera transform only, no object transforms).
// We use glPushMatrix/glLoadIdentity to achieve this for every light.
// ---------------------------------------------------------------------------

// Convenience: normalise a 0-255 colour component to 0.0-1.0
static inline float lc(float v) { return v / colorMaxH; } // respects colorMode max

// Apply all common light state and reset the index counter.
// Call this at the start of a draw() that uses lights.
void lights() {
    // Matches Processing Java lights() exactly:
    //   ambientLight(128, 128, 128)
    //   directionalLight(128, 128, 128,  0, 0, -1)
    // Result: shadow faces = 50% brightness, front face = 100%, sides interpolated.
    glEnable(GL_LIGHTING);
    glEnable(GL_NORMALIZE);
    glEnable(GL_COLOR_MATERIAL);
    // GL_COLOR_MATERIAL drives AMBIENT_AND_DIFFUSE from fill color
    glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);

    lightsEnabled = true;
    lightIndex    = 0;

    // Zero out global ambient -- we set it explicitly via LIGHT0 ambient below
    GLfloat noGlobalAmb[] = { 0.0f, 0.0f, 0.0f, 1.0f };
    glLightModelfv(GL_LIGHT_MODEL_AMBIENT, noGlobalAmb);
    glLightModeli(GL_LIGHT_MODEL_TWO_SIDE, GL_FALSE);
    glLightModeli(GL_LIGHT_MODEL_LOCAL_VIEWER, GL_FALSE);

    GLfloat zero[] = { 0.0f, 0.0f, 0.0f, 1.0f };

    // LIGHT0: ambient component = 128/255 (matches Java ambientLight(128,128,128))
    // This contributes equally to all faces regardless of normal.
    GLfloat amb[]  = { 0.502f, 0.502f, 0.502f, 1.0f }; // 128/255
    // LIGHT0: diffuse component = 128/255 (matches Java directionalLight(128,128,128,...))
    GLfloat dif[]  = { 0.502f, 0.502f, 0.502f, 1.0f }; // 128/255
    // Direction (0,0,-1) in Java = light travels toward -Z = toward-light vector (0,0,1)
    // In eye space with default camera looking down -Z, (0,0,1) points at camera = front-lit
    GLfloat pos[]  = { 0.0f, 0.0f, 1.0f, 0.0f }; // w=0 = directional

    glEnable(GL_LIGHT0);
    glLightfv(GL_LIGHT0, GL_AMBIENT,  amb);
    glLightfv(GL_LIGHT0, GL_DIFFUSE,  dif);
    glLightfv(GL_LIGHT0, GL_SPECULAR, zero);

    // Set in eye space so light direction is fixed relative to camera
    glPushMatrix();
    glLoadIdentity();
    glLightfv(GL_LIGHT0, GL_POSITION, pos);
    glPopMatrix();

    lightIndex = 1;
}

void noLights() {
    glDisable(GL_LIGHTING);
    glDisable(GL_COLOR_MATERIAL);
    lightsEnabled = false;
    lightIndex    = 0;
}

// Ambient light: emits equally in all directions, no diffuse.
void ambientLight(float r, float g, float b) { ambientLight(r,g,b,0,0,0); }
void ambientLight(float r, float g, float b, float x, float y, float z) {
    if (lightIndex >= 8) return;
    GLenum lt = GL_LIGHT0 + lightIndex++;

    GLfloat col[] = { lc(r), lc(g), lc(b), 1.0f };
    GLfloat pos[] = { x, y, z, 1.0f };
    GLfloat zero[]= { 0.0f, 0.0f, 0.0f, 1.0f };

    glEnable(GL_LIGHTING);
    glEnable(GL_NORMALIZE);
    glEnable(GL_COLOR_MATERIAL);
    glColorMaterial(GL_FRONT_AND_BACK, GL_DIFFUSE);
    glEnable(lt);
    glLightfv(lt, GL_AMBIENT,  col);
    glLightfv(lt, GL_DIFFUSE,  zero);
    glLightfv(lt, GL_SPECULAR, zero);
    // Position is transformed by the current modelview (world space)
    glLightfv(lt, GL_POSITION, pos);
}

// Directional light: parallel rays from an infinite distance (w=0).
// 'nx,ny,nz' is the direction the light TRAVELS (toward the scene).
// OpenGL position with w=0 points TOWARD the light source, so we negate.
void directionalLight(float r, float g, float b, float nx, float ny, float nz) {
    if (lightIndex >= 8) return;
    GLenum lt = GL_LIGHT0 + lightIndex++;

    GLfloat col[]  = { lc(r), lc(g), lc(b), 1.0f };
    // Negate direction and flip Y to compensate for glScalef(1,-1,1) in modelview
    GLfloat pos[]  = { -nx, ny, -nz, 0.0f };
    GLfloat zero[] = { 0.0f, 0.0f, 0.0f, 1.0f };

    glEnable(GL_LIGHTING);
    glEnable(GL_NORMALIZE);
    glDisable(GL_COLOR_MATERIAL);
    glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);
    glEnable(GL_COLOR_MATERIAL);
    // No global ambient for directionalLight alone (matches Java Processing)
    GLfloat gAmb[] = { 0.0f, 0.0f, 0.0f, 1.0f };
    glLightModelfv(GL_LIGHT_MODEL_AMBIENT, gAmb);
    lightsEnabled = true;
    GLfloat spec[] = { pendingSpecR, pendingSpecG, pendingSpecB, 1.0f };
    glEnable(lt);
    glLightfv(lt, GL_AMBIENT,  zero);
    glLightfv(lt, GL_DIFFUSE,  col);
    glLightfv(lt, GL_SPECULAR, spec);
    // Set in pure eye space (no world transforms) like Java Processing.
    // toward-light in eye space: negate direction, flip Y for our Y-down convention.
    glPushMatrix();
    glLoadIdentity();
    // toward-light = negate direction. Y negated because Processing Y-down maps to eye Y-up
    // (glScalef(1,-1,1) in projection flips screen Y, so world +Y = eye -Y)
    GLfloat posE[] = { -nx, -ny, -nz, 0.0f };
    glLightfv(lt, GL_POSITION, posE);
    glPopMatrix();
}

// Point light: emits in all directions from a world-space position.
void pointLight(float r, float g, float b, float x, float y, float z) {
    if (lightIndex >= 8) return;
    GLenum lt = GL_LIGHT0 + lightIndex++;

    GLfloat col[]  = { lc(r), lc(g), lc(b), 1.0f };
    GLfloat pos[]  = { x, y, z, 1.0f };            // w=1 = positional
    GLfloat zero[] = { 0.0f, 0.0f, 0.0f, 1.0f };

    glEnable(GL_LIGHTING);
    glEnable(GL_NORMALIZE);
    glEnable(GL_COLOR_MATERIAL);
    glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);
    glEnable(lt);
    glLightfv(lt, GL_AMBIENT,  zero);
    glLightfv(lt, GL_DIFFUSE,  col);
    glLightfv(lt, GL_SPECULAR, zero);
    glLightf(lt, GL_CONSTANT_ATTENUATION,  1.0f);
    glLightf(lt, GL_LINEAR_ATTENUATION,    0.0f);
    glLightf(lt, GL_QUADRATIC_ATTENUATION, 0.0f);
    // Position transformed by current modelview (world space)
    glLightfv(lt, GL_POSITION, pos);
}

// Spot light: cone of light from a position toward a direction.
// angle  = half-angle of the cone in radians (OpenGL takes degrees)
// conc   = concentration exponent (higher = tighter beam)
void spotLight(float r, float g, float b,
               float x,  float y,  float z,
               float nx, float ny, float nz,
               float angle, float conc) {
    // Java Processing sets spotlight pos/dir through the CURRENT modelview
    // (which includes LookAt * Scale(1,-1,1)) -- same as world space draw.
    // We flip Y to compensate for our glScalef(1,-1,1) baked into modelview.
    if (lightIndex >= 8) return;
    GLenum lt = GL_LIGHT0 + lightIndex++;

    GLfloat col[]  = { lc(r), lc(g), lc(b), 1.0f };
    GLfloat zero[] = { 0.0f, 0.0f, 0.0f, 1.0f };

    glEnable(GL_LIGHTING);
    glEnable(GL_NORMALIZE);
    glDisable(GL_COLOR_MATERIAL);
    glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);
    glEnable(GL_COLOR_MATERIAL);
    lightsEnabled = true;

    glEnable(lt);
    glLightfv(lt, GL_AMBIENT,  zero);
    glLightfv(lt, GL_DIFFUSE,  col);
    glLightfv(lt, GL_SPECULAR, zero);

    // Store concentration and cutoff cosine for the shader
    // (GL_SPOT_EXPONENT caps at 128, but Java uses values like 600)
    int li = lightIndex - 1;
    lightConcentration[li] = conc;
    lightCutoffCos[li]     = std::cos(angle); // precompute cos(cutoff)

    // Still set GL_SPOT_CUTOFF so fixed-function fallback works
    float cutDeg = std::min(angle * 180.0f / PI, 90.0f);
    glLightf(lt, GL_SPOT_CUTOFF,   cutDeg);
    glLightf(lt, GL_SPOT_EXPONENT, std::min(conc, 128.0f)); // capped for GL
    glLightf(lt, GL_CONSTANT_ATTENUATION,  1.0f);
    glLightf(lt, GL_LINEAR_ATTENUATION,    0.0f);
    glLightf(lt, GL_QUADRATIC_ATTENUATION, 0.0f);

    // Position: NO Y-flip (modelview Scale already handles it correctly)
    // Direction: Y-flip ny to compensate for Scale(1,-1,1) in modelview
    GLfloat posW[] = { x,   y,  z,  1.0f };
    GLfloat dirW[] = { nx, -ny, nz };
    glLightfv(lt, GL_POSITION,       posW);
    glLightfv(lt, GL_SPOT_DIRECTION, dirW);
}

// Override attenuation on all active lights (call after the light functions)
void lightFalloff(float c, float l, float q) {
    for (int i = 0; i < lightIndex; i++) {
        GLenum lt = GL_LIGHT0 + i;
        glLightf(lt, GL_CONSTANT_ATTENUATION,  c);
        glLightf(lt, GL_LINEAR_ATTENUATION,    l);
        glLightf(lt, GL_QUADRATIC_ATTENUATION, q);
    }
}

// Set specular colour on all active lights
void lightSpecular(float r, float g, float b) {
    // Store as pending so subsequent light calls pick it up
    pendingSpecR = lc(r); pendingSpecG = lc(g); pendingSpecB = lc(b);
    // Also apply to any already-created lights
    for (int i = 0; i < lightIndex; i++) {
        GLenum  lt  = GL_LIGHT0 + i;
        GLfloat col[] = { pendingSpecR, pendingSpecG, pendingSpecB, 1.0f };
        glLightfv(lt, GL_SPECULAR, col);
    }
}

void normal(float nx, float ny, float nz) { glNormal3f(nx, ny, nz); }

// =============================================================================
// MATERIAL
// =============================================================================

void ambient(float r, float g, float b) {
    GLfloat col[] = { lc(r), lc(g), lc(b), 1.0f };
    glMaterialfv(GL_FRONT_AND_BACK, GL_AMBIENT, col);
}
void ambient(color c) {
    unsigned int v = c.value;
    ambient((v >> 16 & 0xFF) / 255.f,
            (v >>  8 & 0xFF) / 255.f,
            (v       & 0xFF) / 255.f);
}

void emissive(float r, float g, float b) {
    GLfloat col[] = { lc(r), lc(g), lc(b), 1.0f };
    glMaterialfv(GL_FRONT_AND_BACK, GL_EMISSION, col);
}
void emissive(color c) {
    unsigned int v = c.value;
    emissive((v >> 16 & 0xFF) / 255.f,
             (v >>  8 & 0xFF) / 255.f,
             (v       & 0xFF) / 255.f);
}

void specular(float r, float g, float b) {
    GLfloat col[] = { lc(r), lc(g), lc(b), 1.0f };
    glMaterialfv(GL_FRONT_AND_BACK, GL_SPECULAR, col);
}
void specular(color c) {
    unsigned int v = c.value;
    specular((v >> 16 & 0xFF) / 255.f,
             (v >>  8 & 0xFF) / 255.f,
             (v       & 0xFF) / 255.f);
}

void shininess(float s) {
    glMaterialf(GL_FRONT_AND_BACK, GL_SHININESS, s);
}

// =============================================================================
// TEXT
// =============================================================================

// -- Embedded 6x8 bitmap font fallback (ASCII 32-126) -------------------------
static const unsigned char g_font6x8[][6] = {
    {0x00,0x00,0x00,0x00,0x00,0x00},{0x04,0x04,0x04,0x04,0x00,0x04},{0x0A,0x0A,0x00,0x00,0x00,0x00},{0x0A,0x1F,0x0A,0x1F,0x0A,0x00},{0x0E,0x15,0x1C,0x07,0x15,0x0E},{0x19,0x1A,0x02,0x04,0x0B,0x13},{0x08,0x14,0x08,0x15,0x12,0x0D},{0x04,0x04,0x00,0x00,0x00,0x00},
    {0x02,0x04,0x04,0x04,0x04,0x02},{0x08,0x04,0x04,0x04,0x04,0x08},{0x00,0x0A,0x04,0x1F,0x04,0x0A},{0x00,0x04,0x04,0x1F,0x04,0x04},{0x00,0x00,0x00,0x00,0x04,0x08},{0x00,0x00,0x00,0x1F,0x00,0x00},{0x00,0x00,0x00,0x00,0x00,0x04},{0x01,0x02,0x02,0x04,0x08,0x10},
    {0x0E,0x11,0x13,0x15,0x19,0x0E},{0x04,0x0C,0x04,0x04,0x04,0x0E},{0x0E,0x11,0x02,0x04,0x08,0x1F},{0x1F,0x02,0x06,0x01,0x11,0x0E},{0x02,0x06,0x0A,0x1F,0x02,0x02},{0x1F,0x10,0x1E,0x01,0x11,0x0E},{0x06,0x08,0x1E,0x11,0x11,0x0E},{0x1F,0x01,0x02,0x04,0x04,0x04},
    {0x0E,0x11,0x0E,0x11,0x11,0x0E},{0x0E,0x11,0x0F,0x01,0x02,0x0C},{0x00,0x04,0x00,0x00,0x04,0x00},{0x00,0x04,0x00,0x00,0x04,0x08},{0x02,0x04,0x08,0x08,0x04,0x02},{0x00,0x1F,0x00,0x1F,0x00,0x00},{0x08,0x04,0x02,0x02,0x04,0x08},{0x0E,0x11,0x02,0x04,0x00,0x04},
    {0x0E,0x11,0x17,0x15,0x17,0x0E},{0x04,0x0A,0x11,0x1F,0x11,0x11},{0x1E,0x11,0x1E,0x11,0x11,0x1E},{0x0E,0x11,0x10,0x10,0x11,0x0E},{0x1C,0x12,0x11,0x11,0x12,0x1C},{0x1F,0x10,0x1E,0x10,0x10,0x1F},{0x1F,0x10,0x1E,0x10,0x10,0x10},{0x0E,0x11,0x10,0x17,0x11,0x0F},
    {0x11,0x11,0x1F,0x11,0x11,0x11},{0x0E,0x04,0x04,0x04,0x04,0x0E},{0x07,0x02,0x02,0x02,0x12,0x0C},{0x11,0x12,0x1C,0x12,0x11,0x11},{0x10,0x10,0x10,0x10,0x10,0x1F},{0x11,0x1B,0x15,0x11,0x11,0x11},{0x11,0x19,0x15,0x13,0x11,0x11},{0x0E,0x11,0x11,0x11,0x11,0x0E},
    {0x1E,0x11,0x11,0x1E,0x10,0x10},{0x0E,0x11,0x11,0x15,0x12,0x0D},{0x1E,0x11,0x11,0x1E,0x11,0x11},{0x0F,0x10,0x0E,0x01,0x11,0x0E},{0x1F,0x04,0x04,0x04,0x04,0x04},{0x11,0x11,0x11,0x11,0x11,0x0E},{0x11,0x11,0x11,0x0A,0x0A,0x04},{0x11,0x11,0x15,0x15,0x1B,0x11},
    {0x11,0x0A,0x04,0x04,0x0A,0x11},{0x11,0x0A,0x04,0x04,0x04,0x04},{0x1F,0x02,0x04,0x08,0x10,0x1F},{0x0E,0x08,0x08,0x08,0x08,0x0E},{0x10,0x08,0x08,0x04,0x02,0x01},{0x0E,0x02,0x02,0x02,0x02,0x0E},{0x04,0x0A,0x11,0x00,0x00,0x00},{0x00,0x00,0x00,0x00,0x00,0x1F},
    {0x08,0x04,0x00,0x00,0x00,0x00},{0x00,0x0E,0x01,0x0F,0x11,0x0F},{0x10,0x10,0x1E,0x11,0x11,0x1E},{0x00,0x0E,0x10,0x10,0x10,0x0E},{0x01,0x01,0x0F,0x11,0x11,0x0F},{0x00,0x0E,0x11,0x1F,0x10,0x0E},{0x06,0x08,0x1E,0x08,0x08,0x08},{0x00,0x0F,0x11,0x0F,0x01,0x0E},
    {0x10,0x10,0x1E,0x11,0x11,0x11},{0x04,0x00,0x04,0x04,0x04,0x0E},{0x02,0x00,0x02,0x02,0x12,0x0C},{0x10,0x12,0x14,0x1C,0x12,0x11},{0x0C,0x04,0x04,0x04,0x04,0x0E},{0x00,0x1B,0x15,0x15,0x11,0x11},{0x00,0x1E,0x11,0x11,0x11,0x11},{0x00,0x0E,0x11,0x11,0x11,0x0E},
    {0x00,0x1E,0x11,0x1E,0x10,0x10},{0x00,0x0F,0x11,0x0F,0x01,0x01},{0x00,0x16,0x19,0x10,0x10,0x10},{0x00,0x0E,0x10,0x0E,0x01,0x1E},{0x08,0x1F,0x08,0x08,0x08,0x07},{0x00,0x11,0x11,0x11,0x13,0x0D},{0x00,0x11,0x11,0x0A,0x0A,0x04},{0x00,0x11,0x15,0x15,0x1B,0x11},
    {0x00,0x11,0x0A,0x04,0x0A,0x11},{0x00,0x11,0x0A,0x04,0x08,0x10},{0x00,0x1F,0x02,0x04,0x08,0x1F},{0x06,0x04,0x0C,0x04,0x04,0x06},{0x04,0x04,0x04,0x04,0x04,0x04},{0x0C,0x04,0x06,0x04,0x04,0x0C},{0x08,0x15,0x02,0x00,0x00,0x00},
};

// -- stb_truetype font state ---------------------------------------------------
#if PROCESSING_HAS_STB_TRUETYPE
struct TTFFont {
    stbtt_fontinfo info;
    std::vector<unsigned char> data;
    GLuint texID = 0;
    // Baked atlas: ASCII 32-126
    stbtt_bakedchar chars[96];
    int atlasW = 512, atlasH = 512;
    float bakeSize = 0.0f;   // size the atlas was baked at
    bool  loaded   = false;
};
static TTFFont g_ttf;

static bool loadTTFFile(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;
    g_ttf.data = std::vector<unsigned char>(
        (std::istreambuf_iterator<char>(f)),
         std::istreambuf_iterator<char>());
    return stbtt_InitFont(&g_ttf.info, g_ttf.data.data(), 0) != 0;
}

static void bakeAtlas(float pixelSize) {
    if (!g_ttf.loaded) return;
    if (std::fabs(pixelSize - g_ttf.bakeSize) < 0.5f) return; // already baked at this size
    g_ttf.bakeSize = pixelSize;
    std::vector<unsigned char> bitmap(g_ttf.atlasW * g_ttf.atlasH);
    stbtt_BakeFontBitmap(g_ttf.data.data(), 0, pixelSize,
                         bitmap.data(), g_ttf.atlasW, g_ttf.atlasH,
                         32, 96, g_ttf.chars);
    // Upload as alpha-only texture
    if (g_ttf.texID == 0) glGenTextures(1, &g_ttf.texID);
    glBindTexture(GL_TEXTURE_2D, g_ttf.texID);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, g_ttf.atlasW, g_ttf.atlasH,
                 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap.data());
    glBindTexture(GL_TEXTURE_2D, 0);
}
#endif // PROCESSING_HAS_STB_TRUETYPE

// -- Shared text state ---------------------------------------------------------
static float g_textSize    = 14.0f;
static int   g_textAlignX  = LEFT_ALIGN;
static int   g_textAlignY  = BASELINE;
static float g_textLeading = 0.0f;

// -- Bitmap font rendering (fallback) -----------------------------------------
static const int BF_GW = 6;
static const int BF_GH = 8;

static void drawBitmapStr(float x, float y, const std::string& s, int scale) {
    glColor4f(fillR, fillG, fillB, fillA);
    glDisable(GL_TEXTURE_2D);
    float cx = x;
    for (char ch : s) {
        if (ch < 32 || ch > 126) { cx += (BF_GW+1)*scale; continue; }
        const unsigned char* bm = g_font6x8[(unsigned char)ch - 32];
        for (int row = 0; row < BF_GH; row++) {
            unsigned char bits = bm[row];
            for (int col = 0; col < BF_GW; col++) {
                if (bits & (1 << (BF_GW - 1 - col))) {
                    glBegin(GL_QUADS);
                    float px = cx + col*scale, py = y + row*scale;
                    glVertex2f(px,        py);
                    glVertex2f(px+scale,  py);
                    glVertex2f(px+scale,  py+scale);
                    glVertex2f(px,        py+scale);
                    glEnd();
                }
            }
        }
        cx += (BF_GW+1)*scale;
    }
}

static float bitmapStrWidth(const std::string& s, int scale) {
    return s.size() * (BF_GW+1) * scale;
}

// -- TTF rendering -------------------------------------------------------------
#if PROCESSING_HAS_STB_TRUETYPE
static float ttfStrWidth(const std::string& s) {
    float x = 0;
    for (char ch : s) {
        if (ch < 32 || ch > 127) continue;
        int advance, lsb;
        stbtt_GetCodepointHMetrics(&g_ttf.info, ch, &advance, &lsb);
        float sc = stbtt_ScaleForMappingEmToPixels(&g_ttf.info, g_textSize);
        x += advance * sc;
    }
    return x;
}

static void drawTTFStr(float x, float y, const std::string& s) {
    if (!g_ttf.loaded) return;
    bakeAtlas(g_textSize);

    glEnable(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D, g_ttf.texID);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    // Use fill colour modulated by font alpha
    glColor4f(fillR, fillG, fillB, fillA);
    glBegin(GL_QUADS);

    float cx = x;
    for (char ch : s) {
        if (ch < 32 || ch >= 128) continue;
        stbtt_aligned_quad q;
        stbtt_GetBakedQuad(g_ttf.chars, g_ttf.atlasW, g_ttf.atlasH,
                           ch - 32, &cx, &y, &q, 1);
        glTexCoord2f(q.s0, q.t0); glVertex2f(q.x0, q.y0);
        glTexCoord2f(q.s1, q.t0); glVertex2f(q.x1, q.y0);
        glTexCoord2f(q.s1, q.t1); glVertex2f(q.x1, q.y1);
        glTexCoord2f(q.s0, q.t1); glVertex2f(q.x0, q.y1);
    }
    glEnd();
    glBindTexture(GL_TEXTURE_2D, 0);
    glDisable(GL_TEXTURE_2D);
}
#endif

// -- Main renderText entry point -----------------------------------------------
static float getLineWidth(const std::string& line) {
#if PROCESSING_HAS_STB_TRUETYPE
    if (g_ttf.loaded) return ttfStrWidth(line);
#endif
    int sc = std::max(1,(int)(g_textSize/8.0f));
    return bitmapStrWidth(line, sc);
}

static void renderText(const std::string& msg, float x, float y) {
    if (msg.empty()) return;

    float leading = (g_textLeading > 0) ? g_textLeading : g_textSize * 1.25f;

    // Split on \n
    std::vector<std::string> ls;
    std::string cur;
    for (char c : msg) { if(c=='\n'){ls.push_back(cur);cur.clear();}else cur+=c; }
    ls.push_back(cur);

    for (int li = 0; li < (int)ls.size(); li++) {
        float lw  = getLineWidth(ls[li]);
        float dx  = x;
        if      (g_textAlignX == RIGHT_ALIGN)  dx = x - lw;
        else if (g_textAlignX == CENTER_ALIGN) dx = x - lw * 0.5f;

        float dy = y + li * leading;

#if PROCESSING_HAS_STB_TRUETYPE
        if (g_ttf.loaded) { drawTTFStr(dx, dy, ls[li]); continue; }
#endif
        // Bitmap fallback
        int sc = std::max(1,(int)(g_textSize/8.0f));
        // Shift so baseline sits at y (bitmap font: ascent = BF_GH-2 rows)
        float ascent = (BF_GH - 2) * sc;
        drawBitmapStr(dx, dy - ascent, ls[li], sc);
    }
}

void text(const std::string& msg, float x, float y) { renderText(msg, x, y); }
void text(int   v, float x, float y) { renderText(std::to_string(v), x, y); }
void text(float v, float x, float y) {
    std::ostringstream ss; ss << v; renderText(ss.str(), x, y);
}

// text(str, x, y, w, h) -- word-wrap into a bounding box, matching Processing Java.
// Words are wrapped when they exceed width w. Lines are clipped at height h.
// x,y is top-left corner of the box. Text starts at the baseline of the first line.
void text(const std::string& msg, float x, float y, float w, float h) {
    if (msg.empty() || w <= 0) return;
    float leading = (g_textLeading > 0) ? g_textLeading : g_textSize * 1.4f;
    float maxY = (h > 0) ? y + h : 1e9f;

    // Split message into tokens: words and explicit newlines
    std::vector<std::string> tokens;
    {
        std::string cur;
        for (char c : msg) {
            if (c == '\n') {
                if (!cur.empty()) { tokens.push_back(cur); cur.clear(); }
                tokens.push_back("\n");
            } else if (c == ' ') {
                if (!cur.empty()) { tokens.push_back(cur); cur.clear(); }
            } else {
                cur += c;
            }
        }
        if (!cur.empty()) tokens.push_back(cur);
    }

    // Layout: accumulate words into lines, wrap when line width exceeds w
    float cy = y;
    std::string line;

    auto flushLine = [&](){
        if (!line.empty() && cy + g_textSize <= maxY) {
            renderText(line, x, cy);
        }
        line.clear();
        cy += leading;
    };

    for (auto& tok : tokens) {
        if (tok == "\n") {
            // Explicit newline: flush current line and advance
            if (cy + g_textSize <= maxY) renderText(line, x, cy);
            line.clear();
            cy += leading;
            continue;
        }
        // Try adding this word to the current line
        std::string candidate = line.empty() ? tok : line + " " + tok;
        float cw = getLineWidth(candidate);

        if (!line.empty() && cw > w) {
            // Word doesn't fit: flush current line, start new line with this word
            if (cy + g_textSize <= maxY) renderText(line, x, cy);
            line.clear();
            cy += leading;
            // If the word itself is wider than w, wrap it character by character
            if (getLineWidth(tok) > w) {
                std::string charLine;
                for (char c : tok) {
                    std::string test2 = charLine + c;
                    if (!charLine.empty() && getLineWidth(test2) > w) {
                        if (cy + g_textSize <= maxY) renderText(charLine, x, cy);
                        charLine.clear();
                        cy += leading;
                    }
                    charLine += c;
                }
                line = charLine;
            } else {
                line = tok;
            }
        } else if (line.empty() && cw > w) {
            // First word on line is too wide: wrap char by char
            std::string charLine;
            for (char c : tok) {
                std::string test2 = charLine + c;
                if (!charLine.empty() && getLineWidth(test2) > w) {
                    if (cy + g_textSize <= maxY) renderText(charLine, x, cy);
                    charLine.clear();
                    cy += leading;
                }
                charLine += c;
            }
            line = charLine;
        } else {
            line = candidate;
        }
    }
    // Flush remaining text
    if (!line.empty() && cy + g_textSize <= maxY) {
        renderText(line, x, cy);
    }
}

void textSize(float size) {
    g_textSize = size;
#if PROCESSING_HAS_STB_TRUETYPE
    if (g_ttf.loaded) bakeAtlas(size);
#endif
}

void textAlign(int alignX, int alignY) {
    // Map standard constants (LEFT=37, RIGHT=39, CENTER=3) to align constants
    auto mapAlign=[](int a)->int{
        if(a==37||a==20) return LEFT_ALIGN;   // LEFT or LEFT_ALIGN
        if(a==39||a==21) return RIGHT_ALIGN;  // RIGHT or RIGHT_ALIGN
        if(a==3 ||a==25) return CENTER_ALIGN; // CENTER or CENTER_ALIGN
        if(a==0)         return BASELINE;
        return a;
    };
    g_textAlignX = mapAlign(alignX);
    if(alignY >= 0) g_textAlignY = mapAlign(alignY);
}
void textLeading(float v) { g_textLeading = v; }
void textMode(int) {}

float textWidth(const std::string& s) { return getLineWidth(s); }

float textAscent() {
#if PROCESSING_HAS_STB_TRUETYPE
    if (g_ttf.loaded) {
        float sc = stbtt_ScaleForMappingEmToPixels(&g_ttf.info, g_textSize);
        int asc; stbtt_GetFontVMetrics(&g_ttf.info, &asc, nullptr, nullptr);
        return asc * sc;
    }
#endif
    int sc = std::max(1,(int)(g_textSize/8.0f));
    return (BF_GH - 2) * sc;
}

float textDescent() {
#if PROCESSING_HAS_STB_TRUETYPE
    if (g_ttf.loaded) {
        float sc = stbtt_ScaleForMappingEmToPixels(&g_ttf.info, g_textSize);
        int desc; stbtt_GetFontVMetrics(&g_ttf.info, nullptr, &desc, nullptr);
        return std::fabs(desc * sc);
    }
#endif
    int sc = std::max(1,(int)(g_textSize/8.0f));
    return 2.0f * sc;
}

// =============================================================================
// IMAGE
// =============================================================================
static void drawImageRect(PImage& img,float x,float y,float w,float h);

PImage* createImage(int w,int h,int mode){
    PImage* img = new PImage(w,h);
    if(mode==3/*ARGB*/) {
        std::fill(img->pixels.begin(),img->pixels.end(),0x00000000);
    }
    img->dirty=true;
    return img;
}

PImage* loadImage(const std::string& path){
    // Handle URLs: download with curl/wget and validate image magic bytes
    if (path.size()>7 && (path.substr(0,7)=="http://" || path.substr(0,8)=="https://")){
#ifdef _WIN32
        std::string tmp=std::string(getenv("TEMP")?getenv("TEMP"):"C:\\Temp")+"\\pg_img_";
#else
        std::string tmp="/tmp/pg_img_";
#endif
        size_t sl=path.rfind('/');
        std::string bn=(sl!=std::string::npos)?path.substr(sl+1):"img.png";
        { size_t q=bn.find('?'); if(q!=std::string::npos) bn=bn.substr(0,q); }
        if(bn.empty()||bn.find('.')==std::string::npos) bn="img.png";
        for(char& c:bn) if(c==':'||c=='*'||c=='<'||c=='>'||c=='|') c='_';
        tmp+=bn;
        auto isValidImg=[&]()->bool{
            FILE* f2=fopen(tmp.c_str(),"rb"); if(!f2) return false;
            fseek(f2,0,SEEK_END); long sz=ftell(f2); fseek(f2,0,SEEK_SET);
            unsigned char h[4]={}; fread(h,1,4,f2); fclose(f2);
            if(sz<100) return false;
            return (h[0]==0x89&&h[1]=='P')|| // PNG
                   (h[0]==0xFF&&h[1]==0xD8)|| // JPEG
                   (h[0]=='G'&&h[1]=='I')||   // GIF
                   (h[0]=='B'&&h[1]=='M')||   // BMP
                   (h[0]=='R'&&h[1]=='I');    // WEBP
        };
        // Try curl
#ifdef _WIN32
        system(("curl -sL --max-time 15 -o \""+tmp+"\" \""+path+"\"").c_str());
#else
        system(("curl -sL --max-time 15 -o '"+tmp+"' '"+path+"'").c_str());
#endif
        if(!isValidImg()){
            // Try wget as fallback
#ifdef _WIN32
            system(("wget -q -O \""+tmp+"\" \""+path+"\"").c_str());
#else
            system(("wget -q -O '"+tmp+"' '"+path+"'").c_str());
#endif
        }
        if(isValidImg()){
            PImage* r2=loadImage(tmp);
            if(r2&&r2->width>0) return r2;
        }
        fprintf(stderr,"[loadImage] URL download failed: %s\n",path.c_str());
        return nullptr;
    }
    // Search paths: current dir, data/, files/, and sketch subdirs
    // Check PROCESSING_SKETCH_PATH env var set by IDE
    std::string _sketchDir;
    if (const char* _sp = std::getenv("PROCESSING_SKETCH_PATH"))
        _sketchDir = std::string(_sp) + "/";
    // Also get the directory of the running executable
    std::string _exeDir;
    {
        char _buf[4096] = {};
#ifdef _WIN32
        GetModuleFileNameA(nullptr, _buf, sizeof(_buf));
        std::string _ep(_buf);
        size_t _sl = _ep.find_last_of("\\\\");
#else
        ssize_t _len = readlink("/proc/self/exe", _buf, sizeof(_buf)-1);
        if (_len > 0) _buf[_len] = 0;
        std::string _ep(_buf);
        size_t _sl = _ep.find_last_of("/");
#endif
        if (_sl != std::string::npos) _exeDir = _ep.substr(0, _sl+1);
    }
    std::vector<std::string> tries = {
        path,
        "data/" + path,
        "files/" + path,
        "../data/" + path,
        _sketchDir + path,
        _sketchDir + "data/" + path,
        _exeDir + path,
        _exeDir + "data/" + path,    // in case running from a subdirectory
    };
#ifdef PROCESSING_HAS_STB_IMAGE
    for (auto& p : tries) {
        int w, h, ch;
        unsigned char* data = stbi_load(p.c_str(), &w, &h, &ch, 4);
        if (!data) continue;
        PImage* img = new PImage(w, h);
        for (int i = 0; i < w*h; i++) {
            unsigned char r=data[i*4+0], g=data[i*4+1], b=data[i*4+2], a=data[i*4+3];
            img->pixels[i] = ((unsigned int)a<<24)|((unsigned int)r<<16)|((unsigned int)g<<8)|b;
        }
        stbi_image_free(data);
        img->dirty = true;
        return img;
    }
#endif
    for (auto& p : tries)
        std::cerr << "[loadImage] not found: " << p << "\n";
    std::cerr << "[loadImage] returning empty image -- check the path above\n";
    return new PImage(); // return empty (not null) so -> calls are safe
}

PGraphics* createGraphics(int w,int h){return new PGraphics(w,h);}

// ── PImage::uploadTexture ────────────────────────────────────────────────────
void PImage::uploadTexture() {
    if (width <= 0 || height <= 0 || pixels.empty()) return;
    if ((int)pixels.size() < width * height) return;
    std::vector<unsigned char> rgba((size_t)width * height * 4);
    for (int i = 0; i < width*height; i++) {
        unsigned int p = pixels[i];
        rgba[i*4+0] = (p>>16)&0xFF;
        rgba[i*4+1] = (p>>8) &0xFF;
        rgba[i*4+2] =  p     &0xFF;
        rgba[i*4+3] = (p>>24)&0xFF;
    }
    if (texID == 0) glGenTextures(1, &texID);
    glBindTexture(GL_TEXTURE_2D, texID);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
    glBindTexture(GL_TEXTURE_2D, 0);
    dirty = false;
}

static void drawImageRect(PImage& img,float x,float y,float w,float h){
    if(img.width==0||img.height==0) return;
    if(img.dirty) img.uploadTexture();
    if(img.texID==0) return;
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glEnable(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D,img.texID);
    glColor4f(doTint?tintR:1.f,doTint?tintG:1.f,doTint?tintB:1.f,doTint?tintA:1.f);
    glBegin(GL_QUADS);
    glTexCoord2f(0,0);glVertex2f(x,y);
    glTexCoord2f(1,0);glVertex2f(x+w,y);
    glTexCoord2f(1,1);glVertex2f(x+w,y+h);
    glTexCoord2f(0,1);glVertex2f(x,y+h);
    glEnd();
    glBindTexture(GL_TEXTURE_2D,0);
    glDisable(GL_TEXTURE_2D);
    glDisable(GL_BLEND);
    // Restore white so non-textured drawing after image() looks correct
    glColor4f(1.f,1.f,1.f,1.f);
}

// ── image() canonical implementation ─────────────────────────────────────────
// Single entry point: everything goes through drawImage_impl.
// Takes a raw PImage pointer so no reference/ABI issues across TUs.
static void drawImage_impl(PImage* img, float x, float y, float w, float h) {
    if (!img || img->width == 0 || img->height == 0) return;
    // Apply imageMode to x,y,w,h
    float dx = x, dy = y, dw = w, dh = h;
    if (currentImageMode == CENTER)  { dx -= dw*0.5f; dy -= dh*0.5f; }
    else if (currentImageMode == CORNERS) { dw = w - x; dh = h - y; dx = x; dy = y; }
    drawImageRect(*img, dx, dy, dw, dh);
}

// ── Public image() entry points ─────────────────────────────────────────────
static void drawPGraphicsRect(PGraphics& pg, float x, float y, float w, float h){
    if(pg.width==0||pg.height==0) return;
    if(pg.texID==0) return;
    glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
    glEnable(GL_TEXTURE_2D); glBindTexture(GL_TEXTURE_2D,pg.texID);
    glColor4f(1,1,1,1);
    glBegin(GL_QUADS);
    // Y-up FBO with Processing Y-down remap: Processing y=0 -> v=1 (top of texture)
    // Display with V-flip: v=1 at screen top = Processing y=0
    glTexCoord2f(0,0);glVertex2f(x,y);
    glTexCoord2f(1,0);glVertex2f(x+w,y);
    glTexCoord2f(1,1);glVertex2f(x+w,y+h);
    glTexCoord2f(0,1);glVertex2f(x,y+h);
    glEnd();
    glBindTexture(GL_TEXTURE_2D,0); glDisable(GL_TEXTURE_2D); glDisable(GL_BLEND);
    glColor4f(1,1,1,1);
}
void image(PGraphics& pg, float x, float y){ drawPGraphicsRect(pg,x,y,(float)pg.width,(float)pg.height); }
void image(PGraphics& pg, float x, float y, float w, float h){ drawPGraphicsRect(pg,x,y,w,h); }
void image(PImage* img, float x, float y) {
    if(!img || img->width==0 || img->height==0) return;
    drawImage_impl(img, x, y, (float)img->width, (float)img->height);
}
void image(PImage* img, float x, float y, float w, float h) {
    if(!img || img->width==0 || img->height==0) return;
    drawImage_impl(img, x, y, w, h);
}

void imageMode(int m){currentImageMode=m;}
void tint(float gray)           {tint(gray, colorMaxA);}
void tint(float gray, float a) {
    // tint(gray, alpha) -- both in 0-255 range like Processing Java
    tintR = tintG = tintB = gray / 255.f;
    tintA = a / 255.f;
    doTint = true;
}
void tint(float r, float g, float b, float a) {
    // tint(r, g, b, alpha) -- all in 0-255 range
    tintR = r / 255.f;
    tintG = g / 255.f;
    tintB = b / 255.f;
    tintA = a / 255.f;
    doTint = true;
}
void noTint(){doTint=false;}

void filter(int mode) { filter(mode, 0.5f); }
void filter(int mode, float /*param*/) {
    int total = winWidth * winHeight;
    std::vector<unsigned char> buf(total * 4);
    glReadPixels(0, 0, winWidth, winHeight, GL_RGBA, GL_UNSIGNED_BYTE, buf.data());

    for (int i = 0; i < total; i++) {
        int r = buf[i*4], g = buf[i*4+1], b = buf[i*4+2];
        int grey = (r + g + b) / 3;

        if (mode == GRAY) {
            buf[i*4] = buf[i*4+1] = buf[i*4+2] = (unsigned char)grey;
        } else if (mode == INVERT) {
            buf[i*4]   = 255 - r;
            buf[i*4+1] = 255 - g;
            buf[i*4+2] = 255 - b;
        } else if (mode == THRESHOLD) {
            unsigned char t = (grey > 127) ? 255 : 0;
            buf[i*4] = buf[i*4+1] = buf[i*4+2] = t;
        }
    }
    glDrawPixels(winWidth, winHeight, GL_RGBA, GL_UNSIGNED_BYTE, buf.data());
}
void loadPixels() {
    int total = winWidth * winHeight;
    pixels.resize(total);

    std::vector<unsigned char> rgba(total * 4);
    glReadPixels(0, 0, winWidth, winHeight, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());

    for (int i = 0; i < total; i++) {
        unsigned char r = rgba[i*4 + 0];
        unsigned char g = rgba[i*4 + 1];
        unsigned char b = rgba[i*4 + 2];
        unsigned char a = rgba[i*4 + 3];
        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;  // ARGB format
    }
}

void updatePixels() {
    int total = winWidth * winHeight;
    std::vector<unsigned char> rgba(total * 4);

    for (int i = 0; i < total; i++) {
        rgba[i*4 + 0] = (pixels[i] >> 16) & 0xFF;  // R
        rgba[i*4 + 1] = (pixels[i] >>  8) & 0xFF;  // G
        rgba[i*4 + 2] =  pixels[i]        & 0xFF;  // B
        rgba[i*4 + 3] = (pixels[i] >> 24) & 0xFF;  // A
    }
    glDrawPixels(winWidth, winHeight, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
}
color get(int x, int y) {
    unsigned char p[4];
    glReadPixels(x, winHeight - 1 - y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, p);
    return colorVal(p[0], p[1], p[2], p[3]);
}

void set(int x, int y, color c) {
    unsigned int v = c.value;
    unsigned char p[] = {
        (unsigned char)((v >> 16) & 0xFF),  // R
        (unsigned char)((v >>  8) & 0xFF),  // G
        (unsigned char)( v        & 0xFF),  // B
        (unsigned char)((v >> 24) & 0xFF)   // A
    };
    glWindowPos2i(x, winHeight - 1 - y);
    glDrawPixels(1, 1, GL_RGBA, GL_UNSIGNED_BYTE, p);
}

// =============================================================================
// BLEND / CLIP
// =============================================================================

void blendMode(int mode) {
    glEnable(GL_BLEND);
    // Reset equation first (SUBTRACT changes it)
    glBlendEquation(GL_FUNC_ADD);
    switch (mode) {
        case ADD:      glBlendFunc(GL_SRC_ALPHA, GL_ONE);                break;
        case MULTIPLY: glBlendFunc(GL_DST_COLOR, GL_ZERO);               break;
        case SCREEN:   glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_COLOR);      break;
        case SUBTRACT:
            glBlendEquation(GL_FUNC_REVERSE_SUBTRACT);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            break;
        default:       glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); break;
    }
}

void clip(float x, float y, float w, float h) {
    // Scissor coordinates are in GL space (Y=0 at bottom), so flip Y
    glEnable(GL_SCISSOR_TEST);
    glScissor((int)x, winHeight - (int)(y + h), (int)w, (int)h);
}

void noClip() {
    glDisable(GL_SCISSOR_TEST);
}

// =============================================================================
// SAVE
// =============================================================================

void saveFrame(const std::string& fn){
    std::cout<<"saveFrame: "<<fn<<" (enable stb_image_write in Processing.cpp)\n";
}
void save(const std::string& fn){saveFrame(fn);}

// =============================================================================
// GLFW CALLBACKS
// =============================================================================

static bool mouseWasPressed=false;

static void cursor_pos_cb(GLFWwindow*, double x, double y) {
    mouseInWindow = true;
    pmouseX = mouseX;
    pmouseY = mouseY;
    mouseX  = (float)x;
    mouseY  = (float)y;
    if (_mousePressed) { if (_onMouseDragged) _onMouseDragged(); }
    else               { if (_onMouseMoved)   _onMouseMoved();   }
}
static void mouse_btn_cb(GLFWwindow*, int btn, int action, int mods) {
    g_currentMods = mods;
    if (action == GLFW_PRESS) {
        _mousePressed = true;
        if      (btn == GLFW_MOUSE_BUTTON_LEFT)   mouseButton = LEFT;
        else if (btn == GLFW_MOUSE_BUTTON_RIGHT)  mouseButton = RIGHT;
        else                                       mouseButton = CENTER;
        if (_onMousePressed) _onMousePressed();
        mouseWasPressed = true;
    } else if (action == GLFW_RELEASE) {
        _mousePressed = false;
        if (_onMouseReleased) _onMouseReleased();
        if (mouseWasPressed && _onMouseClicked) _onMouseClicked();
        mouseWasPressed = false;
        mouseButton = -1;
    }
}
static void scroll_cb(GLFWwindow*,double,double yoffset){
    if(_onMouseWheel)_onMouseWheel((int)yoffset);
}
static char g_lastChar = 0;  // set by char_cb, consumed by key_cb

static bool g_pendingKeyPressed = false; // key_cb deferred to after char_cb

static void char_cb(GLFWwindow*, unsigned int codepoint) {
    // char_cb fires after key_cb for printable keys, with the correct
    // Unicode character (shift/caps/layout all applied).
    if (codepoint < 128) {
        key = (char)codepoint;
    }
    // Fire deferred keyPressed() now that key has the correct char value
    if (g_pendingKeyPressed) {
        g_pendingKeyPressed = false;
        if (_onKeyPressed) _onKeyPressed();
    }
    // keyTyped() -- Processing standard: only printable chars, no action keys
    // Action keys (Ctrl, Shift, Alt, etc.) never reach char_cb, so this is correct.
    if (_onKeyTyped) _onKeyTyped();
}

// Translate GLFW key code to Java KeyEvent.VK_* value.
// The Processing reference says keyCode matches Java's KeyEvent constants.
static int glfw_to_java_keycode(int k) {
    switch (k) {
        // Arrow / navigation
        case GLFW_KEY_UP:        return 38;
        case GLFW_KEY_DOWN:      return 40;
        case GLFW_KEY_LEFT:      return 37;
        case GLFW_KEY_RIGHT:     return 39;
        case GLFW_KEY_HOME:      return 36;
        case GLFW_KEY_END:       return 35;
        case GLFW_KEY_PAGE_UP:   return 33;
        case GLFW_KEY_PAGE_DOWN: return 34;
        // Modifiers
        case GLFW_KEY_LEFT_SHIFT:
        case GLFW_KEY_RIGHT_SHIFT:   return 16;
        case GLFW_KEY_LEFT_CONTROL:
        case GLFW_KEY_RIGHT_CONTROL: return 17;
        case GLFW_KEY_LEFT_ALT:
        case GLFW_KEY_RIGHT_ALT:     return 18;
        case GLFW_KEY_LEFT_SUPER:
        case GLFW_KEY_RIGHT_SUPER:   return 157;  // VK_META
        // ASCII control (key is set directly, not CODED, but keyCode is still set)
        case GLFW_KEY_BACKSPACE: return 8;
        case GLFW_KEY_TAB:       return 9;
        case GLFW_KEY_ENTER:
        case GLFW_KEY_KP_ENTER:  return 10;       // ENTER (also fires RETURN=13 via key)
        case GLFW_KEY_ESCAPE:    return 27;
        case GLFW_KEY_DELETE:    return 127;
        case GLFW_KEY_INSERT:    return 155;
        // Function keys
        case GLFW_KEY_F1:  return 112; case GLFW_KEY_F2:  return 113;
        case GLFW_KEY_F3:  return 114; case GLFW_KEY_F4:  return 115;
        case GLFW_KEY_F5:  return 116; case GLFW_KEY_F6:  return 117;
        case GLFW_KEY_F7:  return 118; case GLFW_KEY_F8:  return 119;
        case GLFW_KEY_F9:  return 120; case GLFW_KEY_F10: return 121;
        case GLFW_KEY_F11: return 122; case GLFW_KEY_F12: return 123;
        // Letter keys: GLFW A=65..Z=90 matches Java VK_A=65..VK_Z=90
        // (GLFW uses uppercase, Java uses uppercase -- same values)
        default:
            if (k >= GLFW_KEY_A && k <= GLFW_KEY_Z) return k;  // 65..90 match
            if (k >= GLFW_KEY_0 && k <= GLFW_KEY_9) return k - GLFW_KEY_0 + 48; // 48..57
            return k; // best effort for anything else
    }
}

// Current modifier state -- set from key_cb mods parameter (reliable on Windows)
static void key_cb(GLFWwindow* w, int k, int /*scancode*/, int action, int mods) {
    g_currentMods = mods; // capture before callbacks fire
    if (action == GLFW_PRESS || action == GLFW_REPEAT) {
        _keyPressed = true;
        g_pendingKeyPressed = false; // reset for each new key event

        // Translate GLFW code -> Java KeyEvent.VK_* value (Processing reference standard)
        keyCode = glfw_to_java_keycode(k);

        // Set key=CODED for all non-printable/special keys.
        // ASCII keys that Processing handles via key directly (not CODED):
        //   BACKSPACE(8), TAB(9), ENTER(10), ESC(27), DELETE(127)
        // Everything else that has no ASCII representation gets CODED.
        switch (k) {
            case GLFW_KEY_BACKSPACE: key = (char)8;   break;
            case GLFW_KEY_TAB:       key = (char)9;   break;
            case GLFW_KEY_ENTER:
            case GLFW_KEY_KP_ENTER:  key = (char)10;  break;
            case GLFW_KEY_ESCAPE:    key = (char)27;  break;
            case GLFW_KEY_SPACE:     key = (char)32;  break;
            case GLFW_KEY_DELETE:    key = (char)127; break;
            // All other special keys set key=CODED
            case GLFW_KEY_UP: case GLFW_KEY_DOWN:
            case GLFW_KEY_LEFT: case GLFW_KEY_RIGHT:
            case GLFW_KEY_HOME: case GLFW_KEY_END:
            case GLFW_KEY_PAGE_UP: case GLFW_KEY_PAGE_DOWN:
            case GLFW_KEY_LEFT_SHIFT: case GLFW_KEY_RIGHT_SHIFT:
            case GLFW_KEY_LEFT_CONTROL: case GLFW_KEY_RIGHT_CONTROL:
            case GLFW_KEY_LEFT_ALT: case GLFW_KEY_RIGHT_ALT:
            case GLFW_KEY_LEFT_SUPER: case GLFW_KEY_RIGHT_SUPER:
            case GLFW_KEY_INSERT: case GLFW_KEY_CAPS_LOCK:
            case GLFW_KEY_F1: case GLFW_KEY_F2: case GLFW_KEY_F3:
            case GLFW_KEY_F4: case GLFW_KEY_F5: case GLFW_KEY_F6:
            case GLFW_KEY_F7: case GLFW_KEY_F8: case GLFW_KEY_F9:
            case GLFW_KEY_F10: case GLFW_KEY_F11: case GLFW_KEY_F12:
                key = (char)CODED;
                break;
            default:
                // Printable key: if a modifier (Ctrl/Alt) is held, char_cb will
                // NOT fire on Windows for Ctrl+letter combos. Fire immediately.
                // If no modifier, defer to char_cb so key gets the correct char.
                if (mods & (GLFW_MOD_CONTROL | GLFW_MOD_ALT | GLFW_MOD_SUPER)) {
                    // Ctrl/Alt/Meta combos: char_cb won't fire.
                    // Set key to the lowercase letter and fire immediately.
                    if (k >= GLFW_KEY_A && k <= GLFW_KEY_Z)
                        key = (char)('a' + (k - GLFW_KEY_A));
                    g_pendingKeyPressed = false; // fire immediately
                } else {
                    // Plain printable key (possibly with Shift): defer to char_cb
                    // so key gets the correct shifted/layout-aware character.
                    g_pendingKeyPressed = true;
                }
                break;
        }

        // Fire keyPressed() now unless deferred to char_cb
        if (!g_pendingKeyPressed) {
            if (_onKeyPressed) _onKeyPressed();
            // Java Processing: ESC closes the sketch unless keyPressed() set key=0
            if (key == (char)27 && gWindow)
                glfwSetWindowShouldClose(gWindow, GLFW_TRUE);
        }

    } else if (action == GLFW_RELEASE) {
        _keyPressed = false;
        g_currentMods = mods; // update on release too
        if (_onKeyReleased) _onKeyReleased();
    }
}
static void focus_cb(GLFWwindow*,int f){
    focused = (f == GLFW_TRUE);
    if (!focused) g_pendingKeyPressed = false; // clear pending if focus lost
}
static void winpos_cb(GLFWwindow*,int,int){if(_onWindowMoved)_onWindowMoved();}
static bool _setupDone = false; // set true after setup() returns and main loop starts

static bool _inWinsizeCb = false; // prevent re-entry
static void winsize_cb(GLFWwindow*,int lw,int lh){
    if(!lw||!lh||_inWinsizeCb)return;
    _inWinsizeCb = true;
    if(_setupDone){
        if(isResizable){
            // Update logical size for resizable sketches so width/height reflect new size
            winWidth=lw; winHeight=lh;
            logicalW=lw; logicalH=lh;
        }
        if(_onWindowResized)_onWindowResized();
    }
    // setProjection uses logicalW/H for ortho, actual size for viewport.
    setProjection(winWidth, winHeight);
    // Only trigger redraw if size actually changed from last draw.
    // This prevents flashing from rapid i3 resize events.
    static int _lastDrawW = 0, _lastDrawH = 0;
    if(_setupDone && !looping) {
        if(lw != _lastDrawW || lh != _lastDrawH) {
            _lastDrawW = lw; _lastDrawH = lh;
            redrawOnce = true;
        }
    }
    _inWinsizeCb = false;
}
static void fbsize_cb(GLFWwindow*,int fw,int fh){
    if(!fw||!fh)return;
    pixelWidth=fw;pixelHeight=fh;
}

// =============================================================================
// RUN
// =============================================================================

static bool tryLoadTTF(const std::string& path, float size); // forward decl

// Called from main() before run() if --debug flag is present
void enableDebugConsole() {
#ifdef _WIN32
    // Allocate a Windows console for debug output without needing -mconsole
    if (AllocConsole()) {
        FILE* f;
        freopen_s(&f, "CONOUT$", "w", stdout);
        freopen_s(&f, "CONOUT$", "w", stderr);
        freopen_s(&f, "CONIN$",  "r", stdin);
        fprintf(stderr, "[debug] processing-cpp debug console enabled\n");
    }
#endif
}

void run(){
    // Write directly to a file since -mwindows kills stderr on Windows
    setvbuf(stdout, nullptr, _IONBF, 0);
    std::srand((unsigned)std::time(nullptr));
    initPerlin(0); // initialize noise table with default seed

    if(!glfwInit()){
        fprintf(stderr, "[ERR] glfwInit() failed. Make sure libglfw3.dll is next to ide.exe\n");
#ifdef _WIN32
        MessageBoxA(NULL, "glfwInit() failed.\nMake sure libglfw3.dll and glew32.dll are next to ide.exe", "processing-cpp Error", MB_OK|MB_ICONERROR);
#endif
        return;
    }
    GLFWmonitor* mon=glfwGetPrimaryMonitor();
    if(mon){const GLFWvidmode* vm=glfwGetVideoMode(mon);displayWidth=vm->width;displayHeight=vm->height;}

    // Reset all style state to defaults BEFORE setup()
    doFill=true;doStroke=true;
    fillR=1;fillG=1;fillB=1;fillA=1;
    strokeR=0;strokeG=0;strokeB=0;strokeA=1;strokeW=1;
    colorModeVal=RGB;
    colorMaxH=255;colorMaxS=255;colorMaxB=255;colorMaxA=255;
    currentRectMode=CORNER;currentEllipseMode=CENTER;currentImageMode=CORNER;
    doTint=false;tintR=1;tintG=1;tintB=1;tintA=1;
    lightsEnabled=false;lightIndex=0;
    looping=true;

    settings();

    glfwWindowHint(GLFW_RESIZABLE,isResizable?GLFW_TRUE:GLFW_FALSE);
    glfwWindowHint(GLFW_SAMPLES,4); // 4x MSAA for crisp P3D rendering; 2D noSmooth() disables at runtime
    glfwWindowHint(GLFW_STENCIL_BITS,8);  // needed for concave shape fill
    gWindow=glfwCreateWindow(winWidth,winHeight,g_sketchName.c_str(),nullptr,nullptr);
    // Prevent freeze when dragging title bar on Windows
    glfwSetWindowRefreshCallback(gWindow,[](GLFWwindow* w){
        glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        glfwSwapBuffers(w);
    });
    if(!gWindow){
#ifdef _WIN32
        MessageBoxA(NULL, "Window creation failed.\nCheck that your GPU supports OpenGL 2.0+", "processing-cpp Error", MB_OK|MB_ICONERROR);
#else
        fprintf(stderr, "[ERR] Window creation failed. Check OpenGL 2.0+ support.\n");
#endif
        glfwTerminate(); return;
    }
    glfwMakeContextCurrent(gWindow);
    GLenum glewErr = glewInit();
    if(glewErr != GLEW_OK){
#ifdef _WIN32
        char msg[256]; snprintf(msg,sizeof(msg),"glewInit() failed: %s", glewGetErrorString(glewErr));
        MessageBoxA(NULL, msg, "processing-cpp Error", MB_OK|MB_ICONERROR);
#else
        fprintf(stderr, "[ERR] glewInit() failed: %s\n", glewGetErrorString(glewErr));
#endif
        glfwDestroyWindow(gWindow); glfwTerminate(); return;
    }

    // Reset cached Phong shader so it recompiles fresh with current source
    if(phongProg){glDeleteProgram(phongProg);phongProg=0;}
    glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
    glEnable(GL_DEPTH_TEST);
    glShadeModel(GL_SMOOTH);
    glEnable(GL_NORMALIZE);
    smooth();
    // Don't call setProjection here -- let size() in setup() do it
    // with the correct dimensions. Calling it now with winWidth=640,winHeight=480
    // (defaults) would set the wrong ortho before setup() changes the size.
    {int fw,fh;glfwGetFramebufferSize(gWindow,&fw,&fh);pixelWidth=fw;pixelHeight=fh;fbW=fw>0?fw:logicalW;fbH=fh>0?fh:logicalH;}

    // Enable sticky keys/buttons: GLFW will keep state as PRESSED until polled,
    // Clear both buffers once at startup so the sketch starts with
    // a known clean state (no GPU garbage in either buffer).
    glClearColor(0.8f,0.8f,0.8f,1); // Java Processing default grey
    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
    glfwSwapBuffers(gWindow);
    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);

    // preventing missed inputs on Windows where events can arrive between polls.
    glfwSetInputMode(gWindow, GLFW_STICKY_KEYS, GLFW_TRUE);
    glfwSetInputMode(gWindow, GLFW_STICKY_MOUSE_BUTTONS, GLFW_TRUE);
    glfwSetCursorPosCallback(gWindow,   cursor_pos_cb);
    glfwSetMouseButtonCallback(gWindow, mouse_btn_cb);
    glfwSetScrollCallback(gWindow,      scroll_cb);
    glfwSetKeyCallback(gWindow,         key_cb);
    glfwSetCharCallback(gWindow,        char_cb);
    glfwSetFramebufferSizeCallback(gWindow,fbsize_cb);
    glfwSetWindowSizeCallback(gWindow,  winsize_cb);
    glfwSetWindowFocusCallback(gWindow, focus_cb);
    glfwSetWindowPosCallback(gWindow,   winpos_cb);
    focused=(glfwGetWindowAttrib(gWindow,GLFW_FOCUSED)==GLFW_TRUE);

    // Auto-load default.ttf from project root as the default font
    // (matches Processing Java's "a generic sans-serif font will be used")
    // Try to load default.ttf from several common locations
    {
        std::string _homeDir;
#ifdef _WIN32
        if (const char* h = std::getenv("USERPROFILE")) _homeDir = h;
#else
        if (const char* h = std::getenv("HOME")) _homeDir = h;
#endif
        std::string _modePath;
        if (const char* mp = std::getenv("PROCESSING_MODE_PATH"))
            _modePath = std::string(mp) + "/";

        // Font name used by Processing4
        const std::string _font = "ProcessingSansPro-Regular.ttf";

        // Check Documents/Processing on Windows (user sketchbook)
        std::string _docsPath;
#ifdef _WIN32
        if (const char* ud = std::getenv("USERPROFILE"))
            _docsPath = std::string(ud) + "/Documents/Processing/";
#endif

        if (!tryLoadTTF(_modePath + "fonts/" + _font, g_textSize) &&
            !tryLoadTTF(_modePath + _font, g_textSize) &&
            !tryLoadTTF(_docsPath + "modes/CppMode/fonts/" + _font, g_textSize) &&
            // Windows: Processing4 install locations
            !tryLoadTTF("C:/Program Files/Processing/core/library/" + _font, g_textSize) &&
            !tryLoadTTF("C:/Program Files (x86)/Processing/core/library/" + _font, g_textSize) &&
            !tryLoadTTF("C:/Program Files/processing-4.3/core/library/" + _font, g_textSize) &&
            !tryLoadTTF(_homeDir + "/AppData/Local/Programs/Processing/core/library/" + _font, g_textSize) &&
            // Windows: dev build
            !tryLoadTTF(_homeDir + "/Projects/processing4/core/src/font/" + _font, g_textSize) &&
            // Linux: installed Processing4
            !tryLoadTTF("/usr/lib/processing4/core/library/" + _font, g_textSize) &&
            !tryLoadTTF("/usr/share/processing4/core/library/" + _font, g_textSize) &&
            !tryLoadTTF("/opt/processing4/core/library/" + _font, g_textSize) &&
            !tryLoadTTF("/opt/processing/core/library/" + _font, g_textSize) &&
            !tryLoadTTF(_homeDir + "/.local/share/processing4/core/library/" + _font, g_textSize) &&
            !tryLoadTTF(_homeDir + "/processing-4.3/core/library/" + _font, g_textSize) &&
            !tryLoadTTF(_homeDir + "/Projects/processing4/core/src/font/" + _font, g_textSize) &&
            // macOS
            !tryLoadTTF("/Applications/Processing.app/Contents/Java/core/library/" + _font, g_textSize) &&
            !tryLoadTTF(_homeDir + "/Applications/Processing.app/Contents/Java/core/library/" + _font, g_textSize)) {
            std::cerr << "[font] ProcessingSansPro-Regular.ttf not found -- using bitmap fallback\n";
        }
    }

    glfwFocusWindow(gWindow);  // ensure input focus on Windows

    // Settle loop: poll+swap several times BEFORE setup() runs so i3/tiling WMs
    // fully resize the window first. winsize_cb fires during these polls and
    // sets the correct viewport. After settling, setup() and draw() use the
    // correct dimensions from the start -- no shifted first frame.
    _setupDone = true; // enable winsize_cb to update projection during settle
    // Initialize the framebuffer before the settle loop -- required on Windows
    // to avoid swapping an uninitialized back buffer which crashes the driver.
    glClearColor(0.8f,0.8f,0.8f,1);
    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
    glfwSwapBuffers(gWindow);
    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
    glfwSwapBuffers(gWindow);
    for (int _settle = 0; _settle < 5; _settle++) {
        glClearColor(0.8f,0.8f,0.8f,1);
        glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        glfwPollEvents();
        glfwSwapBuffers(gWindow);
    }
    if (!defaultP3D) setProjection(winWidth, winHeight);

    setup();
    // 2D sketches: disable MSAA so pixels stay crisp (noSmooth() effect).
    // P3D sketches keep MSAA from window creation for smooth 3D edges.
    if (!defaultP3D) {
        glDisable(GL_MULTISAMPLE);
        glDisable(GL_POINT_SMOOTH);
        glDisable(GL_LINE_SMOOTH);
    }
    if (_wireCallbacksFn) _wireCallbacksFn();

    if (!defaultP3D) {
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();
    }

    // If setup() called noLoop(): run draw() once then swap.
    // Static sketches have an empty draw() -- no-op.
    // Structured noLoop sketches (like Pie Chart) need draw() to show content.
    if (!looping) {
        if (defaultP3D) {
            // P3D static sketch: setup() already drew everything with the correct
            // camera. Just apply projection state for the draw() call (which may
            // be empty for static sketches, or may draw content for structured ones).
            glViewport(0, 0, fbW, fbH);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS);
            glDisable(GL_CULL_FACE);
            glFrontFace(GL_CW);
            glEnable(GL_NORMALIZE);
            // Do NOT clear -- setup() already drew to the back buffer.
            // Only clear if draw() will redraw everything (i.e. calls background()).
            applyDefaultCamera();
        } else {
            setProjection(logicalW, logicalH);
        }
        ++frameCount; draw();
        flushPoints(); // flush any pending points before swap
        saveToPersist(); // save back buffer before swap for next frame restore
        glfwSwapBuffers(gWindow);
    }

    redrawOnce = looping;
    auto last=std::chrono::steady_clock::now();
    // Drain Windows message queue before entering main loop.
    // On Windows, a WM_QUIT from a previous sketch run can be in the queue.
    // Poll multiple times to flush it, then forcibly clear the close flag.
    for(int _flush=0; _flush<10; _flush++) glfwPollEvents();
    glfwSetWindowShouldClose(gWindow, GLFW_FALSE);
    // For looping sketches: clear both buffers to bgColor so the
    // front-to-back blit starts from the correct background color.
    // For static sketches (noLoop): don't clear - preserve what setup() drew.
    if(looping){
        for(int _b=0;_b<2;_b++){
            glClearColor(bgR,bgG,bgB,bgA);
            glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            glfwSwapBuffers(gWindow);
        }
    }
    while(!glfwWindowShouldClose(gWindow)){
        pmouseX=mouseX;pmouseY=mouseY;
        if(looping||redrawOnce){
            redrawOnce=false;

            if(defaultP3D){
                glViewport(0, 0, fbW, fbH);
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_LESS);
                glDisable(GL_CULL_FACE);
                glFrontFace(GL_CW);
                glEnable(GL_NORMALIZE);
                // Do NOT clear color here -- the sketch calls background() itself.
                // Clearing color would erase anything drawn in setup().
                flushPoints(); // flush any pending points from previous frame
            glClear(GL_DEPTH_BUFFER_BIT);
                // Auto-apply the default Processing camera BEFORE draw() --
                // matches Java Processing behaviour exactly. The sketch can
                // override by calling camera() / perspective() itself.
                // This means lights() called anywhere in draw() will always
                // see the camera matrix and lock correctly in eye space.
                applyDefaultCamera();
            } else {
                // 2D mode: reset viewport and projection every frame.
                // Use actual framebuffer size for viewport (handles HiDPI/Retina
                // where framebuffer may be 2x logical size) but logical
                // winWidth/winHeight for the ortho matrix so sketch pixel
                // coordinates map 1:1 regardless of DPI scaling.
                glViewport(0, 0, fbW, fbH);
                glMatrixMode(GL_PROJECTION); glLoadIdentity();
                glOrtho(0, logicalW, logicalH, 0, -1, 1);
                glMatrixMode(GL_MODELVIEW); glLoadIdentity();
                glDisable(GL_DEPTH_TEST);
                glDisable(GL_LIGHTING);
                // Restore previous frame content from persist FBO.
                // Sketches that call background() will overwrite this.
                // Sketches that don't (like Keyboard) preserve their drawn content.
                restoreFromPersist();
                glClear(GL_DEPTH_BUFFER_BIT);
            }

            // Reset all light slots each frame so lights from the previous
            // frame don't accumulate. The sketch re-establishes its lights
            // in each draw() call.
            for (int _li = 0; _li < 8; _li++) {
                glDisable(GL_LIGHT0 + _li);
                // Reset each slot to black so disabled lights contribute nothing
                GLfloat black[] = { 0.f, 0.f, 0.f, 1.f };
                glLightfv(GL_LIGHT0+_li, GL_DIFFUSE,  black);
                glLightfv(GL_LIGHT0+_li, GL_AMBIENT,  black);
                glLightfv(GL_LIGHT0+_li, GL_SPECULAR, black);
            }
            glDisable(GL_LIGHTING);
            glDisable(GL_COLOR_MATERIAL);
            // No global ambient by default (Java Processing uses 0 unless ambientLight() called)
            GLfloat noAmb[] = { 0.0f, 0.0f, 0.0f, 1.0f };
            glLightModelfv(GL_LIGHT_MODEL_AMBIENT, noAmb);
            lightsEnabled = false;
            lightIndex    = 0;
            pendingSpecR=0; pendingSpecG=0; pendingSpecB=0;
            for(int _li=0;_li<8;_li++){lightConcentration[_li]=0;lightCutoffCos[_li]=-1;}
            // Reset material to neutral so non-lit objects look correct
            GLfloat matWhite[] = { 0.8f, 0.8f, 0.8f, 1.0f };
            GLfloat matBlack[] = { 0.0f, 0.0f, 0.0f, 1.0f };
            glMaterialfv(GL_FRONT_AND_BACK, GL_AMBIENT,  matBlack);
            glMaterialfv(GL_FRONT_AND_BACK, GL_DIFFUSE,  matWhite);
            glMaterialfv(GL_FRONT_AND_BACK, GL_SPECULAR, matBlack);
            glMaterialf (GL_FRONT_AND_BACK, GL_SHININESS, 0.0f);
            // Reset specular material to none
            GLfloat noSpec[] = {0,0,0,1};
            glMaterialfv(GL_FRONT_AND_BACK, GL_SPECULAR, noSpec);
            glMaterialfv(GL_FRONT_AND_BACK, GL_EMISSION, noSpec);

            // Draw guard: handle different sketch types
            if (!looping && _staticSketchSetup) {
                // Static sketch: reset style and redraw
                doFill=true; doStroke=true;
                fillR=1; fillG=1; fillB=1; fillA=1;
                strokeR=0; strokeG=0; strokeB=0; strokeA=1; strokeW=1;
                colorModeVal=RGB;
                colorMaxH=255; colorMaxS=255; colorMaxB=255; colorMaxA=255;
                lightsEnabled=false; lightIndex=0;
                _staticSketchSetup();
            } else {
                // Run draw() every frame -- no mouseInWindow guard.
                // The Hue sketch first-frame issue is handled by the settle loop
                // ensuring mouseX/mouseY are 0 which is acceptable.
                // Reset tint state each frame (Processing Java behavior)
                doTint=false; tintR=1; tintG=1; tintB=1; tintA=1;
                glGetError();
                ++frameCount; draw();
                glGetError(); // consume any GL errors
            }

            glfwSwapBuffers(gWindow);
        }
        glfwPollEvents();
        auto now=std::chrono::steady_clock::now();
        double elapsed=std::chrono::duration<double>(now-last).count();
        if(elapsed>0) measuredFrameRate=measuredFrameRate*0.9f+(float)(1.0/elapsed)*0.1f;
        _frameRate=(float)(1.0/elapsed);
        deltaTime=(float)elapsed;
        double sl=targetFrameTime-elapsed;
        if(sl>0)std::this_thread::sleep_for(std::chrono::duration<double>(sl));
        last=std::chrono::steady_clock::now();
    }
    if(phongProg){glDeleteProgram(phongProg);phongProg=0;}
    glfwDestroyWindow(gWindow);gWindow=nullptr;glfwTerminate();
}

// =============================================================================
// JSON IMPLEMENTATION
// =============================================================================

static void skipWS(const std::string& s, size_t& i){
    while(i<s.size()&&(s[i]==' '||s[i]=='\t'||s[i]=='\n'||s[i]=='\r'))i++;
}
static std::string parseString(const std::string& s, size_t& i){
    i++;
    std::string r;
    while(i<s.size()&&s[i]!='"'){
        if(s[i]=='\\'&&i+1<s.size()){i++;
            switch(s[i]){case '"':r+='"';break;case '\\':r+='\\';break;case '/':r+='/';break;
                         case 'n':r+='\n';break;case 't':r+='\t';break;case 'r':r+='\r';break;default:r+=s[i];break;}
        } else r+=s[i];
        i++;
    }
    if(i<s.size())i++;
    return r;
}
static JSONValue parseJSONValue(const std::string& s, size_t& i);
static JSONObject parseJSONObj(const std::string& s, size_t& i){
    JSONObject obj; i++;
    skipWS(s,i);
    while(i<s.size()&&s[i]!='}'){
        skipWS(s,i); if(s[i]!='"')break;
        std::string key=parseString(s,i);
        skipWS(s,i); if(i<s.size()&&s[i]==':')i++;
        skipWS(s,i);
        obj[key]=parseJSONValue(s,i);
        skipWS(s,i); if(i<s.size()&&s[i]==',')i++;
        skipWS(s,i);
    }
    if(i<s.size())i++;
    return obj;
}
static JSONArray parseJSONArr(const std::string& s, size_t& i){
    JSONArray arr; i++;
    skipWS(s,i);
    while(i<s.size()&&s[i]!=']'){
        skipWS(s,i);
        arr.push_back(parseJSONValue(s,i));
        skipWS(s,i); if(i<s.size()&&s[i]==',')i++;
        skipWS(s,i);
    }
    if(i<s.size())i++;
    return arr;
}
static JSONValue parseJSONValue(const std::string& s, size_t& i){
    skipWS(s,i);
    if(i>=s.size()) return JSONValue();
    if(s[i]=='"')  return JSONValue(parseString(s,i));
    if(s[i]=='{')  return JSONValue(parseJSONObj(s,i));
    if(s[i]=='[')  return JSONValue(parseJSONArr(s,i));
    if(s.substr(i,4)=="null") { i+=4; return JSONValue(); }
    if(s.substr(i,4)=="true") { i+=4; return JSONValue(true); }
    if(s.substr(i,5)=="false"){ i+=5; return JSONValue(false); }
    size_t start=i;
    bool isFloat=false;
    if(i<s.size()&&s[i]=='-')i++;
    while(i<s.size()&&(std::isdigit(s[i])||s[i]=='.'||s[i]=='e'||s[i]=='E'||s[i]=='+'||s[i]=='-')){
        if(s[i]=='.'||s[i]=='e'||s[i]=='E')isFloat=true; i++;
    }
    std::string num=s.substr(start,i-start);
    if(isFloat) return JSONValue(std::stod(num));
    return JSONValue(std::stoi(num));
}

JSONValue parseJSON(const std::string& src){ size_t i=0; return parseJSONValue(src,i); }

std::string toJSONString(const JSONValue& v, int indent){
    std::string pad(indent*2,' ');
    std::string pad2((indent+1)*2,' ');
    switch(v.type){
        case JSONValue::NULL_T:   return "null";
        case JSONValue::BOOL_T:   return v.b?"true":"false";
        case JSONValue::INT_T:    return std::to_string((int)v.n);
        case JSONValue::FLOAT_T:  { std::ostringstream ss; ss<<v.n; return ss.str(); }
        case JSONValue::STRING_T: return "\""+v.s+"\"";
        case JSONValue::ARRAY_T: {
            if(v.arr->empty())return "[]";
            std::string r="[\n";
            for(size_t i=0;i<v.arr->size();i++){
                r+=pad2+toJSONString((*v.arr)[i],indent+1);
                if(i+1<v.arr->size())r+=",";
                r+="\n";
            }
            return r+pad+"]";
        }
        case JSONValue::OBJECT_T: {
            if(v.obj->empty())return "{}";
            std::string r="{\n";
            size_t n=0;
            for(auto& p:*v.obj){
                r+=pad2+"\""+p.first+"\": "+toJSONString(p.second,indent+1);
                if(++n<v.obj->size())r+=",";
                r+="\n";
            }
            return r+pad+"}";
        }
    }
    return "null";
}

static std::string readFileString(const std::string& path){
    std::ifstream f(path); if(!f)return "";
    return std::string((std::istreambuf_iterator<char>(f)),std::istreambuf_iterator<char>());
}

JSONValue loadJSONObject(const std::string& path){ return parseJSON(readFileString(path)); }
JSONValue loadJSONArray(const std::string& path) { return parseJSON(readFileString(path)); }
bool saveJSONObject(const std::string& path,const JSONValue& v,int indent){
    std::ofstream f(path); if(!f)return false; f<<toJSONString(v,indent); return true;
}
bool saveJSONArray(const std::string& path,const JSONValue& v,int indent){
    return saveJSONObject(path,v,indent);
}

// =============================================================================
// XML IMPLEMENTATION
// =============================================================================

static void xmlSkipWS(const std::string& s,size_t& i){ while(i<s.size()&&std::isspace(s[i]))i++; }

static XML parseXMLNode(const std::string& s, size_t& i){
    XML node;
    xmlSkipWS(s,i);
    if(i>=s.size()||s[i]!='<')return node;
    i++;
    if(i<s.size()&&s[i]=='?'){ while(i<s.size()&&!(s[i]=='>'||s.substr(i,2)=="?>"))i++; if(i<s.size())i++; return node; }
    if(i<s.size()&&s[i]=='!'){ while(i<s.size()&&s[i]!='>')i++; i++; return node; }
    while(i<s.size()&&!std::isspace(s[i])&&s[i]!='>'&&s[i]!='/')node.name+=s[i++];
    xmlSkipWS(s,i);
    while(i<s.size()&&s[i]!='>'&&s[i]!='/'){
        std::string key; while(i<s.size()&&!std::isspace(s[i])&&s[i]!='='&&s[i]!='>'&&s[i]!='/')key+=s[i++];
        xmlSkipWS(s,i);
        if(i<s.size()&&s[i]=='='){i++;xmlSkipWS(s,i);
            char q=s[i++]; std::string val;
            while(i<s.size()&&s[i]!=q)val+=s[i++]; if(i<s.size())i++;
            node.attributes[key]=val;
        }
        xmlSkipWS(s,i);
    }
    if(i<s.size()&&s[i]=='/'){i++;if(i<s.size())i++;return node;}
    if(i<s.size())i++;
    while(i<s.size()){
        xmlSkipWS(s,i);
        if(i+1<s.size()&&s[i]=='<'&&s[i+1]=='/'){
            i++; while(i<s.size()&&s[i]!='>')i++; if(i<s.size())i++; break;
        }
        if(i<s.size()&&s[i]=='<'){ XML child=parseXMLNode(s,i); if(!child.name.empty())node.children.push_back(child); }
        else { std::string text; while(i<s.size()&&s[i]!='<')text+=s[i++]; auto t=text;t.erase(0,t.find_first_not_of(" \t\n\r"));t.erase(t.find_last_not_of(" \t\n\r")+1);if(!t.empty())node.content+=t; }
    }
    return node;
}

XML parseXML(const std::string& src){ size_t i=0; return parseXMLNode(src,i); }
XML loadXML(const std::string& path){ return parseXML(readFileString(path)); }

std::string XML::toString(int indent) const {
    std::string pad(indent*2,' ');
    std::string r=pad+"<"+name;
    for(auto& a:attributes)r+=" "+a.first+"=\""+a.second+"\"";
    if(children.empty()&&content.empty()){r+="/>\n";return r;}
    r+=">";
    if(!content.empty())r+=content;
    if(!children.empty()){r+="\n";for(auto& c:children)r+=c.toString(indent+1);r+=pad;}
    r+="</"+name+">\n";
    return r;
}
bool saveXML(const std::string& path,const XML& x){ std::ofstream f(path);if(!f)return false;f<<x.toString();return true; }

// =============================================================================
// TABLE IMPLEMENTATION
// =============================================================================

Table* loadTable(const std::string& path,const std::string& options){
    Table* t=new Table();
    bool hasHeader=options.find("header")!=std::string::npos;
    char delim=path.find(".tsv")!=std::string::npos?'\t':',';
    auto lines=loadStrings(path);
    if(lines.empty())return t;
    int start=0;
    if(hasHeader){
        auto cols=split(lines[0],delim);
        for(auto& c:cols)t->addColumn(c);
        start=1;
    }
    for(int i=start;i<(int)lines.size();i++){
        auto& row=t->addRow();
        auto cells=split(lines[i],delim);
        for(int j=0;j<(int)cells.size()&&j<(int)row.size();j++)row[j]=cells[j];
    }
    return t;
}
bool saveTable(const std::string& path,const Table& t,const std::string& ext){
    std::ofstream f(path);if(!f)return false;
    char delim=ext=="tsv"?'\t':',';
    if(!t.columns.empty()){for(size_t i=0;i<t.columns.size();i++){if(i)f<<delim;f<<t.columns[i];}f<<"\n";}
    for(auto& row:t.rows){for(size_t i=0;i<row.size();i++){if(i)f<<delim;f<<row[i];}f<<"\n";}
    return true;
}

// =============================================================================
// PSHAPE IMPLEMENTATION
// =============================================================================

static int shapeDrawMode=CORNER;
void shapeMode(int mode){ shapeDrawMode=mode; }
PShape createShape(int kind){ return PShape(kind); }

// ── Minimal SVG loader ────────────────────────────────────────────────────────
// Parses basic SVG shapes (path, rect, circle, ellipse, polygon, polyline, line)
// into PShape children for rendering. Handles fill/stroke from style attributes.

static float svgParseFloat(const std::string& s, size_t& i){
    while(i<s.size()&&(s[i]==' '||s[i]==','))i++;
    size_t j=i;
    if(j<s.size()&&(s[j]=='-'||s[j]=='+'))j++;
    while(j<s.size()&&(isdigit(s[j])||s[j]=='.'||s[j]=='e'||s[j]=='E'||((s[j]=='-'||s[j]=='+')&&j>i&&(s[j-1]=='e'||s[j-1]=='E'))))j++;
    float v=0; try{v=std::stof(s.substr(i,j-i));}catch(...){}
    i=j; return v;
}
static uint32_t svgParseColor(const std::string& s){
    if(s.empty()||s=="none") return 0;
    if(s[0]=='#'){
        std::string h=s.substr(1);
        if(h.size()==3)h={h[0],h[0],h[1],h[1],h[2],h[2]};
        uint32_t v=0; try{v=(uint32_t)std::stoul(h,nullptr,16);}catch(...){}
        return v|0xFF000000u;
    }
    // Named colors (common subset)
    static const std::unordered_map<std::string,uint32_t> nc={
        {"black",0xFF000000},{"white",0xFFFFFFFF},{"red",0xFF0000FF},
        {"green",0xFF008000},{"blue",0xFF0000FF},{"none",0},
        {"gray",0xFF808080},{"grey",0xFF808080},{"yellow",0xFFFFFF00},
    };
    auto it=nc.find(s); return it!=nc.end()?it->second:0xFF000000;
}
static std::string svgAttr(const std::string& tag, const std::string& attr){
    // Find attr="value" -- whole-word match to avoid "id=" matching "d="
    size_t p=0;
    std::string needle=attr+"=";
    while(true){
        p=tag.find(needle,p);
        if(p==std::string::npos) return "";
        // Must be preceded by space, tab, or start (whole attribute name)
        bool ok=(p==0||tag[p-1]==' '||tag[p-1]==9||tag[p-1]==58); // 58=':'
        if(ok) break;
        p++;
    }
    p+=needle.size();
    if(p>=tag.size()) return "";
    char q=tag[p++];
    if(q!=34&&q!=39) return ""; // 34='"', 39="'"
    size_t e=tag.find(q,p);
    return e==std::string::npos?tag.substr(p):tag.substr(p,e-p);
}
static void svgApplyStyle(PShape& sh, const std::string& tag){
    // Parse style="..." or individual fill/stroke/stroke-width attrs
    std::string style=svgAttr(tag,"style");
    auto getVal=[&](const std::string& k)->std::string{
        // from inline style
        size_t p=style.find(k+":"); if(p!=std::string::npos){
            p+=k.size()+1; while(p<style.size()&&style[p]==' ')p++;
            size_t e=style.find(';',p); return style.substr(p,e==std::string::npos?std::string::npos:e-p);
        }
        // from attribute
        return svgAttr(tag,k);
    };
    std::string fs=getVal("fill"),ss=getVal("stroke"),sw=getVal("stroke-width"),op=getVal("opacity"),fo=getVal("fill-opacity");
    float alpha=1.0f;
    if(!op.empty())try{alpha=std::stof(op);}catch(...){}
    float falpha=alpha;
    if(!fo.empty())try{falpha=std::stof(fo)*alpha;}catch(...){}
    if(!fs.empty()&&fs!="none"){
        uint32_t c=svgParseColor(fs);
        sh.setFill(((c>>16)&0xFF)/255.f,((c>>8)&0xFF)/255.f,(c&0xFF)/255.f,falpha);
        sh.hasFill=true;
    } else if(fs=="none"){sh.hasFill=false;}
    if(!ss.empty()&&ss!="none"){
        uint32_t c=svgParseColor(ss);
        sh.setStroke(((c>>16)&0xFF)/255.f,((c>>8)&0xFF)/255.f,(c&0xFF)/255.f,alpha);
        sh.hasStroke=true;
    }
    if(!sw.empty())try{sh.strokeW=std::stof(sw);}catch(...){}
}

// Parse SVG path 'd' attribute into vertices
static PShape svgParsePath(const std::string& tag){
    PShape sh; sh.kind=-1; sh.hasFill=true; sh.hasStroke=false;
    svgApplyStyle(sh,tag);
    std::string d=svgAttr(tag,"d");
    if(d.empty())return sh;
    float cx=0,cy=0,sx=0,sy=0; // current pos and subpath start
    float lcx2=0,lcy2=0; // last control point (for S/T reflection)
    char lastCmd=0;
    size_t i=0;
    auto nextF=[&]()->float{
        while(i<d.size()&&(d[i]==' '||d[i]==','||d[i]=='\t'||d[i]=='\n'||d[i]=='\r'))i++;
        return svgParseFloat(d,i);
    };
    int maxIter=100000, iter=0;
    while(i<d.size()&&iter++<maxIter){
        while(i<d.size()&&(d[i]==' '||d[i]==','||d[i]=='\t'||d[i]=='\n'||d[i]=='\r'))i++;
        if(i>=d.size())break;
        char cmd=d[i];
        bool isCmd=isalpha((unsigned char)cmd);
        if(isCmd){i++;lastCmd=cmd;}
        else cmd=lastCmd;
        if(!cmd)break;
        bool rel=islower((unsigned char)cmd);
        char C=(char)toupper((unsigned char)cmd);

        if(C=='M'){
            float x=nextF()+(rel?cx:0),y=nextF()+(rel?cy:0);
            // Track where each subpath starts
            sh.subpathStarts.push_back((int)sh.verts.size());
            sh.verts.push_back({x,y,0,0,0}); cx=x;cy=y; sx=cx;sy=cy;
            lastCmd=rel?'l':'L';
        } else if(C=='L'){
            float x=nextF()+(rel?cx:0),y=nextF()+(rel?cy:0);
            sh.verts.push_back({x,y,0,0,0}); cx=x;cy=y;
        } else if(C=='H'){
            float x=nextF()+(rel?cx:0);
            sh.verts.push_back({x,cy,0,0,0}); cx=x;
        } else if(C=='V'){
            float y=nextF()+(rel?cy:0);
            sh.verts.push_back({cx,y,0,0,0}); cy=y;
        } else if(C=='C'){
            float x1=nextF()+(rel?cx:0),y1=nextF()+(rel?cy:0);
            float x2=nextF()+(rel?cx:0),y2=nextF()+(rel?cy:0);
            float x=nextF()+(rel?cx:0),y=nextF()+(rel?cy:0);
            {
                // Adaptive segments: based on chord length of control polygon
                float dx1=x1-cx,dy1=y1-cy,dx2=x2-x1,dy2=y2-y1,dx3=x-x2,dy3=y-y2;
                float clen=std::sqrt(dx1*dx1+dy1*dy1)+std::sqrt(dx2*dx2+dy2*dy2)+std::sqrt(dx3*dx3+dy3*dy3);
                int seg=std::max(2,(int)(clen/2));if(seg>128)seg=128;
                for(int s2=1;s2<=seg;s2++){
                    float t=s2/(float)seg,u=1-t;
                    sh.verts.push_back({u*u*u*cx+3*u*u*t*x1+3*u*t*t*x2+t*t*t*x,
                                        u*u*u*cy+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y,0,0,0});
                }
            }
            lcx2=x2;lcy2=y2; cx=x;cy=y;
        } else if(C=='S'){
            // Smooth cubic: reflect last C's second control point
            float x1=2*cx-lcx2,y1=2*cy-lcy2;
            float x2=nextF()+(rel?cx:0),y2=nextF()+(rel?cy:0);
            float x=nextF()+(rel?cx:0),y=nextF()+(rel?cy:0);
            for(int s2=1;s2<=20;s2++){
                float t=s2/20.f,u=1-t;
                float bx=u*u*u*cx+3*u*u*t*x1+3*u*t*t*x2+t*t*t*x;
                float by=u*u*u*cy+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y;
                sh.verts.push_back({bx,by,0,0,0});
            }
            lcx2=x2;lcy2=y2; cx=x;cy=y;
        } else if(C=='Q'){
            float x1=nextF()+(rel?cx:0),y1=nextF()+(rel?cy:0);
            float x=nextF()+(rel?cx:0),y=nextF()+(rel?cy:0);
            for(int s2=1;s2<=20;s2++){
                float t=s2/20.f,u=1-t;
                sh.verts.push_back({u*u*cx+2*u*t*x1+t*t*x,u*u*cy+2*u*t*y1+t*t*y,0,0,0});
            }
            lcx2=x1;lcy2=y1; cx=x;cy=y;
        } else if(C=='T'){
            // Smooth quadratic: reflect last Q's control point
            float x1=2*cx-lcx2,y1=2*cy-lcy2;
            float x=nextF()+(rel?cx:0),y=nextF()+(rel?cy:0);
            for(int s2=1;s2<=20;s2++){
                float t=s2/20.f,u=1-t;
                sh.verts.push_back({u*u*cx+2*u*t*x1+t*t*x,u*u*cy+2*u*t*y1+t*t*y,0,0,0});
            }
            lcx2=x1;lcy2=y1; cx=x;cy=y;
        } else if(C=='A'){
            float rx=nextF(),ry=nextF();
            nextF();nextF();nextF(); // x-rot, large-arc, sweep
            float ex=nextF()+(rel?cx:0),ey=nextF()+(rel?cy:0);
            // Approximate arc with line to endpoint
            {
                // Approximate arc chord length for adaptive segments
                float adx=ex-cx,ady=ey-cy;
                float chord=std::sqrt(adx*adx+ady*ady);
                // rx,ry give the arc radius -- use larger for segment count
                float r2=std::max(rx,ry);
                int arcSeg=std::max(4,(int)(chord/2));
                if(r2>0){int byRadius=(int)(r2*0.5f);if(byRadius>arcSeg)arcSeg=byRadius;}
                if(arcSeg>96)arcSeg=96;
                for(int s2=1;s2<=arcSeg;s2++){
                    float t=s2/(float)arcSeg;
                    sh.verts.push_back({cx+(ex-cx)*t, cy+(ey-cy)*t,0,0,0});
                }
            }
            cx=ex;cy=ey;
        } else if(C=='Z'){
            sh.closed=true;
            sh.verts.push_back({sx,sy,0,0,0});
            cx=sx;cy=sy;
        } else {
            // Unknown -- skip to next alpha or end
            while(i<d.size()&&!isalpha((unsigned char)d[i])&&d[i]!=' ')i++;
            if(i<d.size()&&!isalpha((unsigned char)d[i]))i++;
        }
    }
    return sh;
}

static PShape* svgLoad(const std::string& path){
    // Search paths
    std::vector<std::string> tries={path,"data/"+path,"files/"+path};
    std::string found;
    for(auto& t:tries){FILE* f=fopen(t.c_str(),"r");if(f){fclose(f);found=t;break;}}
    if(found.empty()){std::cerr<<"loadShape: file not found: "<<path<<"\n";return new PShape();}

    std::ifstream f(found);
    std::string xml((std::istreambuf_iterator<char>(f)),std::istreambuf_iterator<char>());

    PShape* root=new PShape();
    root->hasFill=false;
    // Extract viewBox for scaling
    float vbW=0,vbH=0;
    size_t vbp=xml.find("viewBox=");
    if(vbp!=std::string::npos){
        vbp+=9;size_t i2=vbp;
        svgParseFloat(xml,i2);svgParseFloat(xml,i2); // minX minY
        vbW=svgParseFloat(xml,i2);vbH=svgParseFloat(xml,i2);
    }

    // Parse all shape elements
    size_t p=0;
    int maxTags=20000, tagCount=0;
    std::string currentGroupId; // id from nearest enclosing <g id="...">
    std::vector<std::string> groupIdStack; // stack for nested groups
    while(p<xml.size() && tagCount++<maxTags){
        size_t lt=xml.find('<',p); if(lt==std::string::npos)break;
        // Skip comments <!-- ... -->
        if(lt+3<xml.size() && xml.substr(lt,4)=="<!--"){
            size_t end=xml.find("-->",lt+4);
            p=(end==std::string::npos)?xml.size():end+3; continue;
        }
        // Skip DOCTYPE and CDATA <! ... > (may contain > inside [...])
        if(lt+1<xml.size() && xml[lt+1]=='!'){
            // Find matching > accounting for [] nesting
            size_t i2=lt+2; int brackets=0;
            while(i2<xml.size()){
                if(xml[i2]=='[') brackets++;
                else if(xml[i2]==']') brackets--;
                else if(xml[i2]=='>' && brackets<=0){i2++;break;}
                i2++;
            }
            p=i2; continue;
        }
                // Find closing > -- skip quoted attribute values
        size_t gt=lt+1;
        {
            bool inQ=false; char qc=0;
            while(gt<xml.size()){
                char c3=xml[gt];
                if(!inQ && (c3==34||c3==39)){ inQ=true; qc=c3; gt++; }
                else if(inQ && c3==qc){ inQ=false; gt++; }
                else if(!inQ && c3==62){ break; }
                else gt++;
            }
        }
        if(gt>=xml.size())break;
        std::string tag=xml.substr(lt+1,gt-lt-1);
        p=gt+1;
        if(tag.empty()||tag[0]=='?')continue;
        if(tag[0]=='/'){
            // Closing tag
            std::string ctag=tag.substr(1);
            size_t csp=ctag.find_first_of(" /\t");
            if(csp!=std::string::npos) ctag=ctag.substr(0,csp);
            if(ctag=="g"&&!groupIdStack.empty()){
                groupIdStack.pop_back();
                currentGroupId=groupIdStack.empty()?"":groupIdStack.back();
            }
            continue;
        }
        // Remove newlines/tabs from tag for easier parsing
        for(size_t ci2=0;ci2<tag.size();ci2++) if(tag[ci2]==10||tag[ci2]==13||tag[ci2]==9) tag[ci2]=' ';
        // get element name
        size_t sp=tag.find_first_of(" /");
        std::string elem=(sp==std::string::npos)?tag:tag.substr(0,sp);

        PShape child;
        if(elem=="path"){
            child=svgParsePath(tag);
        } else if(elem=="rect"){
            float x=0,y=0,w=0,h=0,rx=0,ry=0;
            auto a=[&](const std::string& k)->float{std::string v=svgAttr(tag,k);if(v.empty())return 0;try{return std::stof(v);}catch(...){return 0;}};
            x=a("x");y=a("y");w=a("width");h=a("height");rx=a("rx");ry=a("ry");
            child.kind=-1; svgApplyStyle(child,tag);
            child.verts.push_back({x,y,0,0,0});child.verts.push_back({x+w,y,0,0,0});
            child.verts.push_back({x+w,y+h,0,0,0});child.verts.push_back({x,y+h,0,0,0});
            child.closed=true;
        } else if(elem=="circle"||elem=="ellipse"){
            float cx2=0,cy2=0,rx=0,ry=0;
            auto a=[&](const std::string& k)->float{std::string v=svgAttr(tag,k);if(v.empty())return 0;try{return std::stof(v);}catch(...){return 0;}};
            cx2=a("cx");cy2=a("cy");
            if(elem=="circle"){rx=ry=a("r");}else{rx=a("rx");ry=a("ry");}
            child.kind=-1; svgApplyStyle(child,tag);
            int seg=32;
            for(int s2=0;s2<=seg;s2++){
                float t=s2*TWO_PI/seg;
                child.verts.push_back({cx2+cos(t)*rx,cy2+sin(t)*ry,0,0,0});
            }
            child.closed=true;
        } else if(elem=="polygon"||elem=="polyline"){
            child.kind=-1; svgApplyStyle(child,tag);
            child.closed=(elem=="polygon");
            std::string pts=svgAttr(tag,"points"); size_t pi=0;
            while(pi<pts.size()){
                while(pi<pts.size()&&(pts[pi]==' '||pts[pi]==','))pi++;
                if(pi>=pts.size())break;
                float x=svgParseFloat(pts,pi),y=svgParseFloat(pts,pi);
                child.verts.push_back({x,y,0,0,0});
            }
        } else if(elem=="line"){
            child.kind=LINES; svgApplyStyle(child,tag);
            auto a=[&](const std::string& k)->float{std::string v=svgAttr(tag,k);if(v.empty())return 0;try{return std::stof(v);}catch(...){return 0;}};
            child.verts.push_back({a("x1"),a("y1"),0,0,0});
            child.verts.push_back({a("x2"),a("y2"),0,0,0});
        } else if(elem=="defs"||elem=="title"||elem=="desc"){
            continue;
        } else if(elem=="svg"){
            continue;
        } else if(elem=="g"){
            // Push group id onto stack so child shapes inherit it
            std::string gid=svgAttr(tag,"id");
            if(gid.empty()) gid=svgAttr(tag,"inkscape:label");
            groupIdStack.push_back(gid);
            currentGroupId=gid.empty()?currentGroupId:gid;
            continue;
        } else continue;

        {
            std::string sid = svgAttr(tag,"id");
            if(sid.empty()) sid = svgAttr(tag,"inkscape:label");
            if(sid.empty()) sid = currentGroupId;
            child.name = sid;
            // Always add child so getChildCount() matches Java Processing
            root->children.push_back(child);
        }
        // Also handle <g id="..."> groups by storing named empty shapes
        // so getChild can find them even if they hold no direct verts
    }

    // Store viewBox dimensions on root shape for reference
    // Do NOT normalize coordinates -- keep raw SVG space so shape(s,x,y) works
    // with sketch-supplied offsets. shape(s,x,y,w,h) will scale via glScalef.
    if(vbW>0&&vbH>0){
        root->verts.push_back({vbW,vbH,0,0,0}); // store vbW,vbH as sentinel in root
    }
    root->computeBounds();
    return root;
}

// ── OBJ Loader ───────────────────────────────────────────────────────────────

// Per-vertex data for OBJ (stored flat in child PShape)
struct ObjVertex {
    float x,y,z;   // position
    float nx,ny,nz; // normal
    float u,v;     // texcoord
};

static std::string objDir; // directory of the OBJ file

// Parse MTL file, return map of material name -> texture PImage*
static std::unordered_map<std::string,GLuint> objLoadMtl(const std::string& mtlPath){
    std::unordered_map<std::string,GLuint> mats;
    std::ifstream f(mtlPath);
    if(!f.is_open()) return mats;
    std::string curMat, line;
    while(std::getline(f,line)){
        if(line.empty()||line[0]=='#') continue;
        std::istringstream ss(line);
        std::string tok; ss>>tok;
        if(tok=="newmtl"){ ss>>curMat; }
        else if((tok=="map_Kd"||tok=="map_Ka")&&!curMat.empty()&&mats.find(curMat)==mats.end()){
            std::string texFile; ss>>texFile;
            // Try relative to obj dir
            std::vector<std::string> tries={
                objDir+texFile, objDir+"data/"+texFile,
                "data/"+texFile, texFile
            };
            for(auto& t:tries){
                #ifdef PROCESSING_HAS_STB_IMAGE
                int w2,h2,ch;
                unsigned char* d=stbi_load(t.c_str(),&w2,&h2,&ch,4);
                if(d){
                    GLuint tex;
                    glGenTextures(1,&tex);
                    glBindTexture(GL_TEXTURE_2D,tex);
                    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w2,h2,0,GL_RGBA,GL_UNSIGNED_BYTE,d);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_REPEAT);
                    glBindTexture(GL_TEXTURE_2D,0);
                    stbi_image_free(d);
                    mats[curMat]=tex;
                                    break;
                }
                #endif
            }
        }
    }
    return mats;
}

static PShape* objLoad(const std::string& path){
    std::vector<std::string> tries={path,"data/"+path,"files/"+path};
    std::string found;
    for(auto& t:tries){FILE* f2=fopen(t.c_str(),"r");if(f2){fclose(f2);found=t;break;}}
    if(found.empty()){std::cerr<<"loadShape: OBJ not found: "<<path<<"\n";return new PShape();}

    // Get directory for relative texture paths
    size_t slash=found.find_last_of("/\\");
    objDir=(slash==std::string::npos)?"":found.substr(0,slash+1);

    std::vector<std::array<float,3>> vp,vn;
    std::vector<std::array<float,2>> vt;
    std::unordered_map<std::string,GLuint> mats; // material name -> GL texture

    PShape* root=new PShape();
    root->name="__obj__";

    // Current group state
    struct Group {
        std::string name, matName;
        GLuint texId=0;
        std::vector<ObjVertex> verts;
    };
    std::vector<Group> groups;
    Group* cur=nullptr;

    auto getGroup=[&]()->Group*{
        if(!cur){groups.push_back({});cur=&groups.back();}
        return cur;
    };

    std::ifstream f(found);
    std::string line;
    while(std::getline(f,line)){
        // Strip \r
        if(!line.empty()&&line.back()=='\r') line.pop_back();
        if(line.empty()||line[0]=='#') continue;
        std::istringstream ss(line);
        std::string tok; ss>>tok;
        if(tok=="mtllib"){
            std::string mtlFile; ss>>mtlFile;
            std::vector<std::string> mtlTries={objDir+mtlFile,"data/"+mtlFile,mtlFile};
            for(auto& t:mtlTries){
                std::ifstream test(t);
                if(test.is_open()){mats=objLoadMtl(t);break;}
            }
        } else if(tok=="v"){
            float x,y,z; ss>>x>>y>>z; vp.push_back({x,y,z});
        } else if(tok=="vt"){
            float u,v2=0; ss>>u>>v2; vt.push_back({u,v2});
        } else if(tok=="vn"){
            float x,y,z; ss>>x>>y>>z; vn.push_back({x,y,z});
        } else if(tok=="o"||tok=="g"){
            groups.push_back({}); cur=&groups.back(); ss>>cur->name;
        } else if(tok=="usemtl"){
            std::string mat; ss>>mat;
            if(!cur){groups.push_back({});cur=&groups.back();}
            // Start new group for new material
            if(!cur->verts.empty()){groups.push_back({});cur=&groups.back();}
            cur->matName=mat;
            cur->texId=mats.count(mat)?mats[mat]:0;
        } else if(tok=="f"){
            std::vector<std::array<int,3>> face;
            std::string fv;
            while(ss>>fv){
                std::array<int,3> idx={0,0,0};
                std::istringstream fs(fv);
                std::string part; int fi=0;
                while(std::getline(fs,part,'/')&&fi<3){
                    if(!part.empty()) try{idx[fi]=std::stoi(part);}catch(...){}
                    fi++;
                }
                face.push_back(idx);
            }
            if(face.size()<3) continue;
            Group* g=getGroup();
            for(size_t fi=1;fi+1<face.size();fi++){
                for(int k=0;k<3;k++){
                    auto& fc=(k==0)?face[0]:(k==1)?face[fi]:face[fi+1];
                    ObjVertex ov={};
                    int vi=fc[0],ti=fc[1],ni=fc[2];
                    if(vi>0&&vi<=(int)vp.size()){ov.x=vp[vi-1][0];ov.y=vp[vi-1][1];ov.z=vp[vi-1][2];}
                    else if(vi<0&&(int)vp.size()+vi>=0){auto& p=vp[vp.size()+vi];ov.x=p[0];ov.y=p[1];ov.z=p[2];}
                    if(ti>0&&ti<=(int)vt.size()){ov.u=vt[ti-1][0];ov.v=1.0f-vt[ti-1][1];} // flip V
                    if(ni>0&&ni<=(int)vn.size()){ov.nx=vn[ni-1][0];ov.ny=vn[ni-1][1];ov.nz=vn[ni-1][2];}
                    g->verts.push_back(ov);
                }
            }
        }
    }

    // Convert groups to PShape children
    for(auto& g:groups){
        if(g.verts.empty()) continue;
        PShape child;
        child.kind=TRIANGLES;
        child.hasFill=true; child.hasStroke=false;
        child.fillR=0.8f;child.fillG=0.8f;child.fillB=0.8f;child.fillA=1.0f;
        child.name=g.matName;
        // Pack ObjVertex into PShape::Vertex (x,y,z) + subpathStarts for normals + verts.z4,z5 for uv
        // Store as flat arrays via a different approach:
        // verts: position (x,y,z), normal (nx via u field, ny via v field... messy)
        // Better: pack into verts with a 5-float struct matching PShape::Vertex
        // PShape::Vertex has {x,y,z,u,v} -- repurpose u,v for texcoords
        // Store normals separately in subpathStarts encoded as 3 floats packed per vertex
        for(auto& ov:g.verts){
            child.verts.push_back({ov.x,ov.y,ov.z,ov.u,ov.v});
        }
        // Store normals as extra data: pack 3 floats per vertex into children[0].verts
        // Use a sibling child marked as normals store
        PShape norms;
        norms.kind=-999; // marker for normal data
        for(auto& ov:g.verts) norms.verts.push_back({ov.nx,ov.ny,ov.nz,0,0});
        child.children.push_back(norms);
        // Store texture ID in strokeR (reuse float field)
        // Better: store as a proper field -- use hasTex + texID on PShape
        // We'll use the existing texId field if it exists, otherwise pack into a child name
        child.texId=g.texId;
        root->children.push_back(child);
    }

    return root;
}

PShape* loadShape(const std::string& path){
    auto ext=[&](const std::string& e)->bool{
        return path.size()>=e.size()&&path.substr(path.size()-e.size())==e;
    };
    if(ext(".svg")||ext(".SVG")) return svgLoad(path);
    if(ext(".obj")||ext(".OBJ")) return objLoad(path);
    std::cerr<<"loadShape: unsupported format: "<<path<<"\n";
    return new PShape();
}

static void drawPShape(const PShape& s,float x,float y,float w=-1,float h=-1,bool parentStyleEnabled=true){
    if(!s.visible)return;
    bool se = s.styleEnabled && parentStyleEnabled;
    glPushMatrix();
    glTranslatef(x,y,0);
    if(w>0&&h>0){
        // If root has viewBox stored as sentinel, scale to fit w,h
        float vbW=1,vbH=1;
        if(!s.verts.empty() && s.children.empty()==false && s.verts[0].z==0){
            vbW=s.verts[0].x; vbH=s.verts[0].y;
        }
        if(vbW>1) glScalef(w/vbW,h/vbH,1);
        else      glScalef(w,h,1);
    }
    for(auto& c:s.children) drawPShape(c,0,0,-1,-1,se);
    if(!s.verts.empty()){
        bool useFill   = se ? s.hasFill  : doFill;
        bool useStroke = se ? s.hasStroke : doStroke;
        int n=(int)s.verts.size();

        if(useFill && n>=3){
            if(se) glColor4f(s.fillR,s.fillG,s.fillB,s.fillA);
            else   applyFill();
            // OBJ triangles: per-vertex normals + texture
            if(s.kind==TRIANGLES){
                const PShape* normStore=nullptr;
                for(auto& c:s.children) if(c.kind==-999){normStore=&c;break;}
                bool hasTex=(s.texId!=0);
                if(hasTex){
                    glEnable(GL_TEXTURE_2D);
                    glBindTexture(GL_TEXTURE_2D,s.texId);
                    glTexEnvi(GL_TEXTURE_ENV,GL_TEXTURE_ENV_MODE,GL_MODULATE);
                    glColor4f(1,1,1,1);
                }
                glBegin(GL_TRIANGLES);
                for(int vi=0;vi<n;vi++){
                    if(normStore&&vi<(int)normStore->verts.size()){
                        glNormal3f(normStore->verts[vi].x,normStore->verts[vi].y,normStore->verts[vi].z);
                    } else if(vi%3==0&&vi+2<n){
                        float ax=s.verts[vi+1].x-s.verts[vi].x,ay=s.verts[vi+1].y-s.verts[vi].y,az=s.verts[vi+1].z-s.verts[vi].z;
                        float bx=s.verts[vi+2].x-s.verts[vi].x,by=s.verts[vi+2].y-s.verts[vi].y,bz=s.verts[vi+2].z-s.verts[vi].z;
                        float nx2=ay*bz-az*by,ny2=az*bx-ax*bz,nz2=ax*by-ay*bx;
                        float len=std::sqrt(nx2*nx2+ny2*ny2+nz2*nz2);
                        if(len>0){nx2/=len;ny2/=len;nz2/=len;}
                        glNormal3f(nx2,ny2,nz2);
                    }
                    if(hasTex) glTexCoord2f(s.verts[vi].u,s.verts[vi].v);
                    glVertex3f(s.verts[vi].x,s.verts[vi].y,s.verts[vi].z);
                }
                glEnd();
                if(hasTex){glDisable(GL_TEXTURE_2D);glBindTexture(GL_TEXTURE_2D,0);}
            } else {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            // Use stencil with evenodd rule -- draw each closed subpath separately
            // so multiple subpaths (e.g. Michigan's two peninsulas) each fill correctly
            glEnable(GL_STENCIL_TEST);
            glClear(GL_STENCIL_BUFFER_BIT);
            glStencilFunc(GL_ALWAYS,0,~0);
            glStencilOp(GL_KEEP,GL_KEEP,GL_INVERT);
            glColorMask(GL_FALSE,GL_FALSE,GL_FALSE,GL_FALSE);
            glDisable(GL_CULL_FACE);
            // Draw each subpath as its own triangle fan into stencil
            // Uses subpathStarts recorded during path parsing (one entry per M command)
            {
                auto drawFan=[&](int from, int to){
                    if(to-from<3) return;
                    glBegin(GL_TRIANGLE_FAN);
                    for(int vi=from;vi<to;vi++)
                        glVertex3f(s.verts[vi].x,s.verts[vi].y,s.verts[vi].z);
                    glVertex3f(s.verts[from].x,s.verts[from].y,s.verts[from].z); // close
                    glEnd();
                };
                if(s.subpathStarts.size()<=1){
                    // Single subpath
                    drawFan(0,(int)s.verts.size());
                } else {
                    // Multiple subpaths (e.g. Michigan's two peninsulas)
                    for(int si=0;si<(int)s.subpathStarts.size();si++){
                        int from=s.subpathStarts[si];
                        int to=(si+1<(int)s.subpathStarts.size())?s.subpathStarts[si+1]:(int)s.verts.size();
                        drawFan(from,to);
                    }
                }
            }
            glColorMask(GL_TRUE,GL_TRUE,GL_TRUE,GL_TRUE);
            glStencilFunc(GL_NOTEQUAL,0,~0);
            glStencilOp(GL_KEEP,GL_KEEP,GL_KEEP);
            float minx=s.verts[0].x,maxx=minx,miny=s.verts[0].y,maxy=miny;
            for(auto& v:s.verts){minx=std::min(minx,v.x);maxx=std::max(maxx,v.x);miny=std::min(miny,v.y);maxy=std::max(maxy,v.y);}
            glBegin(GL_QUADS);
            glVertex3f(minx,miny,0);glVertex3f(maxx,miny,0);
            glVertex3f(maxx,maxy,0);glVertex3f(minx,maxy,0);
            glEnd();
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_BLEND);
            } // end else (non-TRIANGLES path)
        }
        if(useStroke && s.kind!=TRIANGLES){
            if(se) glColor4f(s.strokeR,s.strokeG,s.strokeB,s.strokeA);
            else   applyStroke();
            glLineWidth(se?s.strokeW:strokeW);
            glBegin(s.closed?GL_LINE_LOOP:GL_LINE_STRIP);
            for(auto& v:s.verts) glVertex3f(v.x,v.y,v.z);
            glEnd();
            if(!se) restoreLighting();
        }
    }
    glPopMatrix();
}
void shape(const PShape& s,float x,float y)          { drawPShape(s,x,y); }
void shape(const PShape& s,float x,float y,float w,float h){ drawPShape(s,x,y,w,h); }

// =============================================================================
// PFONT / TYPOGRAPHY
// =============================================================================

static PFont currentFont;

// Internal helper -- try to load a TTF by path
static bool tryLoadTTF(const std::string& path, float size) {
#if PROCESSING_HAS_STB_TRUETYPE
    if (loadTTFFile(path)) {
        g_ttf.loaded = true;
        g_textSize   = size;
        bakeAtlas(size);
        // font load success -- silent (stderr only for failures)
        return true;
    }
    // Silent on individual failures -- caller reports if all fail
#else
    std::cerr << "[font] stb_truetype not available -- put stb_truetype.h in src/\n";
#endif
    return false;
}

// loadFont -- loads a .ttf file; falls back to bitmap font gracefully
PFont loadFont(const std::string& filename) {
    PFont f(filename, g_textSize);
    tryLoadTTF(filename, g_textSize);
    return f;
}

// createFont -- creates font from a name/path and size
static std::vector<PFont> _fontPool;
PFont* createFont(const std::string& name, float size, bool /*smooth*/) {
    PFont f(name, size);
    // Try as file path first, then common system paths
    // Strip extension from name if already present, to avoid double extensions
    std::string baseName = name;
    if (baseName.size() > 4 && (baseName.substr(baseName.size()-4) == ".ttf"
                              || baseName.substr(baseName.size()-4) == ".otf")) {
        // name already has extension -- use as-is for first attempt
    } else {
        baseName = ""; // will build paths with extensions below
    }

    std::string nameNoExt = name;
    if (nameNoExt.size() > 4 && (nameNoExt.substr(nameNoExt.size()-4) == ".ttf"
                               || nameNoExt.substr(nameNoExt.size()-4) == ".otf"))
        nameNoExt = nameNoExt.substr(0, nameNoExt.size()-4);

    std::vector<std::string> paths = {
        // Try exact name first (may already have extension)
        name,
        // Try adding extensions if not already present
        nameNoExt + ".ttf",
        nameNoExt + ".otf",
        // Sketch data/ folder
        std::string("data/") + name,
        std::string("data/") + nameNoExt + ".ttf",
        std::string("data/") + nameNoExt + ".otf",
        // Sketch fonts/ folder
        std::string("fonts/") + name,
        std::string("fonts/") + nameNoExt + ".ttf",
        std::string("fonts/") + nameNoExt + ".otf",
        // Linux system font directories
        std::string("/usr/share/fonts/truetype/") + nameNoExt + ".ttf",
        std::string("/usr/share/fonts/opentype/") + nameNoExt + ".otf",
        std::string("/usr/share/fonts/TTF/") + nameNoExt + ".ttf",
        std::string("/usr/share/fonts/OTF/") + nameNoExt + ".otf",
        // Windows system fonts
        std::string("C:/Windows/Fonts/") + name,
        std::string("C:/Windows/Fonts/") + nameNoExt + ".ttf",
        std::string("C:/Windows/Fonts/") + nameNoExt + ".otf",
        // macOS
        std::string("/Library/Fonts/") + name,
        std::string("/System/Library/Fonts/") + name,
    };

    // Also search system font dirs recursively (Linux: fc-list output)
    #ifndef _WIN32
    {
        FILE* fc = popen(("fc-list : file | grep -i '" + nameNoExt + "' | head -5").c_str(), "r");
        if (fc) {
            char buf[512];
            while (fgets(buf, sizeof(buf), fc)) {
                std::string line(buf);
                // fc-list format: /path/to/font.ttf: Family:style
                size_t colon = line.find(':');
                if (colon != std::string::npos) {
                    std::string fpath = line.substr(0, colon);
                    // strip whitespace
                    fpath.erase(0, fpath.find_first_not_of(" \t"));
                    fpath.erase(fpath.find_last_not_of(" \t\n\r") + 1);
                    if (!fpath.empty()) paths.push_back(fpath);
                }
            }
            pclose(fc);
        }
    }
    #endif

    bool found = false;
    for (auto& p : paths) {
        if (tryLoadTTF(p, size)) { found = true; break; }
    }
    if (!found) {
        std::cerr << "[font] Could not find font: " << name << "\n";
        std::cerr << "[font] Put the .ttf file in your sketch's data/ folder\n";
    }
    _fontPool.push_back(std::move(f)); return &_fontPool.back();
}

// textFont -- switch to a previously loaded font
void textFont(const PFont& font) {
    currentFont = font;
    g_textSize  = font.size;
    tryLoadTTF(font.name, font.size);
}
void textFont(const PFont& font, float size) {
    currentFont      = font;
    currentFont.size = size;
    g_textSize       = size;
    tryLoadTTF(font.name, size);
}

// =============================================================================
// TEXTURE
// =============================================================================

static int textureModeVal=IMAGE;
static int textureWrapVal=CLAMP;
void textureMode(int mode){ textureModeVal=mode; }
void textureWrap(int mode){
    textureWrapVal=mode;
    GLint wrap=mode==REPEAT?GL_REPEAT:GL_CLAMP_TO_EDGE;
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,wrap);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,wrap);
}
void texture(PImage& img){
    if(img.dirty)img.uploadTexture();
    if(img.texID){ glEnable(GL_TEXTURE_2D);glBindTexture(GL_TEXTURE_2D,img.texID); }
}

// =============================================================================
// FILE / IO HELPERS
// =============================================================================

BufferedReader* createReader(const std::string& path){ return new BufferedReader(path); }
PrintWriter*    createWriter(const std::string& path){ return new PrintWriter(path); }

std::string selectInput(const std::string& prompt,const std::string&){
    std::string cmd="zenity --file-selection --title=\""+prompt+"\" 2>/dev/null";
    FILE* p=popen(cmd.c_str(),"r"); if(!p)return "";
    char buf[4096]=""; fgets(buf,sizeof(buf),p); pclose(p);
    std::string r(buf); if(!r.empty()&&r.back()=='\n')r.pop_back(); return r;
}
std::string selectOutput(const std::string& prompt,const std::string&){
    std::string cmd="zenity --file-selection --save --title=\""+prompt+"\" 2>/dev/null";
    FILE* p=popen(cmd.c_str(),"r"); if(!p)return "";
    char buf[4096]=""; fgets(buf,sizeof(buf),p); pclose(p);
    std::string r(buf); if(!r.empty()&&r.back()=='\n')r.pop_back(); return r;
}
std::string selectFolder(const std::string& prompt){
    std::string cmd="zenity --file-selection --directory --title=\""+prompt+"\" 2>/dev/null";
    FILE* p=popen(cmd.c_str(),"r"); if(!p)return "";
    char buf[4096]=""; fgets(buf,sizeof(buf),p); pclose(p);
    std::string r(buf); if(!r.empty()&&r.back()=='\n')r.pop_back(); return r;
}

PImage* requestImage(const std::string& path){
    // Create a placeholder: width=-1 means "still loading", width=0 means "failed"
    // We use a heap PImage and fill it in from a background thread.
    // The sketch checks img->width != 0 && img->width != -1 to detect completion.
    PImage* img = new PImage();
    img->width  = -1;  // sentinel: loading in progress
    img->height = -1;
    std::thread([img, path]{
        // Resolve search paths same as loadImage
        // Check PROCESSING_SKETCH_PATH env var set by IDE
    std::string _sketchDir;
    if (const char* _sp = std::getenv("PROCESSING_SKETCH_PATH"))
        _sketchDir = std::string(_sp) + "/";
    // Also get the directory of the running executable
    std::string _exeDir;
    {
        char _buf[4096] = {};
#ifdef _WIN32
        GetModuleFileNameA(nullptr, _buf, sizeof(_buf));
        std::string _ep(_buf);
        size_t _sl = _ep.find_last_of("\\\\");
#else
        ssize_t _len = readlink("/proc/self/exe", _buf, sizeof(_buf)-1);
        if (_len > 0) _buf[_len] = 0;
        std::string _ep(_buf);
        size_t _sl = _ep.find_last_of("/");
#endif
        if (_sl != std::string::npos) _exeDir = _ep.substr(0, _sl+1);
    }
    std::vector<std::string> tries = {
            path,
            "data/" + path,
            "files/" + path,
        };
        std::string found;
        for (auto& t : tries) {
            FILE* f = fopen(t.c_str(), "rb");
            if (f) { fclose(f); found = t; break; }
        }
        if (found.empty()) {
            // Not found: mark as failed (width=0)
            img->width  = 0;
            img->height = 0;
            std::cerr << "requestImage: file not found: " << path << "\n";
            return;
        }
#ifdef PROCESSING_HAS_STB_IMAGE
        int w=0, h=0, ch=0;
        unsigned char* data = stbi_load(found.c_str(), &w, &h, &ch, 4);
        if (!data || w<=0 || h<=0) {
            img->width  = 0;
            img->height = 0;
            std::cerr << "requestImage: failed to decode: " << path << "\n";
            return;
        }
        img->pixels.resize((size_t)w * h);
        for (int i = 0; i < w*h; i++) {
            unsigned char r=data[i*4+0], g=data[i*4+1],
                          b=data[i*4+2], a=data[i*4+3];
            img->pixels[i] = ((unsigned int)a<<24)|((unsigned int)r<<16)|
                              ((unsigned int)g<<8)|(unsigned int)b;
        }
        stbi_image_free(data);
        img->dirty  = true;
        // Write width/height last so the sketch sees a consistent state
        img->height = h;
        img->width  = w;   // width != -1 signals "done"
#else
        img->width  = 0;
        img->height = 0;
        std::cerr << "requestImage: rebuild with -DPROCESSING_HAS_STB_IMAGE: " << path << "\n";
#endif
    }).detach();
    return img;
}

// =============================================================================
// PSHADER IMPLEMENTATION
// =============================================================================

static std::string readShaderFile(const std::string& path){
    std::ifstream f(path); if(!f)return "";
    return std::string((std::istreambuf_iterator<char>(f)),std::istreambuf_iterator<char>());
}

static PShader* activeShader=nullptr;

PShader* loadShader(const std::string& fragPath,const std::string& vertPath){
    std::string fSrc=readShaderFile(fragPath);
    std::string vSrc=vertPath.empty()?
        "#version 120\nvoid main(){gl_Position=ftransform();gl_TexCoord[0]=gl_MultiTexCoord0;gl_FrontColor=gl_Color;}":
        readShaderFile(vertPath);
    if(fSrc.empty()){std::cerr<<"loadShader: could not read "<<fragPath<<"\n";return nullptr;}
    PShader* s=new PShader(vSrc,fSrc);
    s->compile();
    return s;
}
void shader(PShader& s){ s.bind(); activeShader=&s; }
void resetShader(){ glUseProgram(0); activeShader=nullptr; }

// =============================================================================
// DISPLAY blend() and copy()
// =============================================================================

void blend(int sx,int sy,int sw,int sh,int dx,int dy,int dw,int dh,int mode){
    std::vector<unsigned char> src(sw*sh*4);
    glReadPixels(sx,winHeight-(sy+sh),sw,sh,GL_RGBA,GL_UNSIGNED_BYTE,src.data());
    std::vector<unsigned char> dst(dw*dh*4);
    for(int y=0;y<dh;y++) for(int x=0;x<dw;x++){
        int srcX=(int)(x*(float)sw/dw), srcY=(int)(y*(float)sh/dh);
        for(int c=0;c<4;c++) dst[(y*dw+x)*4+c]=src[(srcY*sw+srcX)*4+c];
    }
    GLenum sfact=GL_SRC_ALPHA,dfact=GL_ONE_MINUS_SRC_ALPHA;
    switch(mode){
        case ADD:     sfact=GL_SRC_ALPHA;dfact=GL_ONE;break;
        case MULTIPLY:sfact=GL_DST_COLOR;dfact=GL_ZERO;break;
        default: break;
    }
    glEnable(GL_BLEND);glBlendFunc(sfact,dfact);
    glWindowPos2i(dx,winHeight-(dy+dh));
    glDrawPixels(dw,dh,GL_RGBA,GL_UNSIGNED_BYTE,dst.data());
    glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
}

void copy(int sx,int sy,int sw,int sh,int dx,int dy,int dw,int dh){
    blend(sx,sy,sw,sh,dx,dy,dw,dh,BLEND);
}

// =============================================================================
// getRegion
// =============================================================================

PImage getRegion(int x,int y,int w,int h){
    PImage img(w,h);
    std::vector<unsigned char> buf(w*h*4);
    glReadPixels(x,winHeight-(y+h),w,h,GL_RGBA,GL_UNSIGNED_BYTE,buf.data());
    for(int iy=0;iy<h;iy++) for(int ix=0;ix<w;ix++){
        int si=((h-1-iy)*w+ix)*4;
        int di=iy*w+ix;
        img.pixels[di]=(buf[si+3]<<24)|(buf[si]<<16)|(buf[si+1]<<8)|buf[si+2];
    }
    img.dirty=true;
    return img;
}

} // namespace Processing
