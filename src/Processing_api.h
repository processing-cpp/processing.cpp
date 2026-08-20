// Processing_api.h
// Included in user-defined classes that don't inherit from PApplet.
// Exposes all Processing API functions via using directives.
// Zero maintenance: add functions to _api namespace, they appear here automatically.
#pragma once
#include "Processing.h"
#include <string>

using namespace ::Processing;
using namespace ::Processing::_api;

// ── Sketch state accessors (PApplet member variables) ─────────────────────────
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
