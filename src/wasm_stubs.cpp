#ifdef __EMSCRIPTEN__
#include <GL/gl.h>

// These fixed-function OpenGL calls are not implemented by Emscripten's
// LEGACY_GL_EMULATION. They are either no-ops in WebGL context or can be
// approximated by their float equivalents.

void glColorMaterial(GLenum face, GLenum mode) { (void)face; (void)mode; }
void glLightModeli(GLenum pname, GLint param)  { (void)pname; (void)param; }

void glGetDoublev(GLenum pname, GLdouble* params) {
    (void)pname;
    if (params) for (int i = 0; i < 16; i++) params[i] = (i%5==0) ? 1.0 : 0.0;
}

void glMultMatrixd(const GLdouble* m) {
    GLfloat mf[16];
    for (int i = 0; i < 16; i++) mf[i] = (GLfloat)m[i];
    glMultMatrixf(mf);
}

void glTranslated(GLdouble x, GLdouble y, GLdouble z) {
    glTranslatef((GLfloat)x, (GLfloat)y, (GLfloat)z);
}

// Remap unsupported immediate modes to supported WebGL equivalents.
// GL_QUADS (7) and GL_QUAD_STRIP (8) abort in Emscripten LEGACY_GL_EMULATION.
// Redefining them here affects all subsequent glBegin() calls in this TU
// and any TUs that include this header after these defines.
#undef GL_QUADS
#define GL_QUADS GL_TRIANGLES
#undef GL_QUAD_STRIP
#define GL_QUAD_STRIP GL_TRIANGLE_STRIP
#endif
