// Processing_api.h
// Included in user-defined classes that don't inherit from PApplet.
// Provides access to sketch state via g_papplet singleton.
// Most free functions (fill, stroke, rect, sin, cos, etc.) come from Processing.h.
#pragma once
#include "Processing.h"
#include <string>

// ── Sketch state accessors (require g_papplet) ────────────────────────────────
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
inline bool* getKeysDown()  { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->keysDown  : nullptr; }
inline bool* getMouseDown() { return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->mouseDown : nullptr; }
inline bool  isKeyDown(int k) {
    if(!::Processing::PApplet::g_papplet||k<0||k>=256) return false;
    return ::Processing::PApplet::g_papplet->keysDown[k];
}
inline ::std::vector<::std::string> loadStrings(const ::std::string& p) {
    return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->loadStrings(p) : ::std::vector<::std::string>{};
}
inline ::std::vector<unsigned char> loadBytes(const ::std::string& p) {
    return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->loadBytes(p) : ::std::vector<unsigned char>{};
}
inline JSONValue loadJSON(const ::std::string& p) {
    static JSONValue e; return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->loadJSON(p) : e;
}
inline Table* loadTable(const ::std::string& p, const ::std::string& opts="") {
    return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->loadTable(p,opts) : nullptr;
}
inline XML loadXML(const ::std::string& p) {
    return ::Processing::PApplet::g_papplet ? ::Processing::PApplet::g_papplet->loadXML(p) : XML();
}
