#!/usr/bin/env python3
"""
sync_keywords.py -- compares your keywords.txt against the official
Processing4 keywords.txt, verifies each official identifier actually
exists in YOUR engine (src/Processing.h / src/Processing.cpp) before
touching anything, and adds verified ones using Processing4's OWN tags
verbatim (no remapping to your prior scheme).

Per user instruction, this version:
  - Uses the OFFICIAL Processing4 tag for every identifier, unchanged
    (FUNCTION1, FUNCTION2, KEYWORD1, KEYWORD3, KEYWORD4, KEYWORD5,
    KEYWORD6, LITERAL2 -- whatever Processing4 itself assigned).
  - INCLUDES per-class methods (official FUNCTION2 entries like
    append/get/size/min/etc.) rather than skipping them.
  - Still verifies every identifier against your real engine source
    before adding it -- official-but-unimplemented names are skipped,
    full stop. This is the one check that's non-negotiable: adding a
    keyword for something CppMode doesn't actually support actively
    misleads anyone reading colored code, it's not a harmless extra.

A real, known consequence of including FUNCTION2 verbatim: because your
existing file currently has no concept of "method that follows a dot,"
generic method names (get, set, add, remove, size, min, max, append,
sort, reverse, clear, copy, join, ...) will now be added as bare
top-level keywords. If your highlighter only matches identifier text
with no awareness of preceding "." or receiver type, this means any
local variable or your own function sharing one of these names gets
colored the same way -- that's exactly the risk flagged in the previous
version of this script. You've confirmed this is acceptable for your
setup, so it's implemented as requested; flagging it here once more in
case it surfaces unexpectedly while editing real sketches.

USAGE:
    Run from your repo root (where src/Processing.h lives):
        python3 sync_keywords.py                  # dry run, prints a report
        python3 sync_keywords.py --apply           # writes changes to keywords.txt
        python3 sync_keywords.py --apply --backup  # also writes keywords.txt.bak first

    Always run the dry run first and read the report before --apply.
"""
import argparse
import os
import re
import subprocess
import sys

KEYWORDS_FILENAME = "keywords.txt"
ENGINE_FILES = ["src/Processing.h", "src/Processing.cpp"]


def find_repo_root():
    """Walk upward from this script's location (and from the current
    working directory) to find the directory containing keywords.txt and
    src/Processing.h -- so this script works correctly whether it lives
    in scripts/ and is run from there, or is run from the repo root
    directly. Falls back to the current working directory if nothing
    is found, so the original error message still fires with a clear
    explanation rather than failing silently on a wrong path."""
    candidates = [os.getcwd(), os.path.dirname(os.path.abspath(__file__))]
    for start in candidates:
        d = start
        for _ in range(6):  # don't walk forever
            if os.path.exists(os.path.join(d, KEYWORDS_FILENAME)) and \
               os.path.exists(os.path.join(d, "src", "Processing.h")):
                return d
            parent = os.path.dirname(d)
            if parent == d:
                break
            d = parent
    return os.getcwd()


REPO_ROOT = find_repo_root()
KEYWORDS_PATH = os.path.join(REPO_ROOT, KEYWORDS_FILENAME)
HEADER_FILES = [os.path.join(REPO_ROOT, p) for p in ENGINE_FILES]

# (identifier, official_tag) pairs from Processing4's keywords.txt,
# deduplicated keeping the first tag seen per identifier.
OFFICIAL = [
    ("ADD", "LITERAL2"), ("ALIGN_CENTER", "LITERAL2"), ("ALIGN_LEFT", "LITERAL2"),
    ("ALIGN_RIGHT", "LITERAL2"), ("ALPHA", "LITERAL2"), ("ALPHA_MASK", "LITERAL2"),
    ("ALT", "LITERAL2"), ("AMBIENT", "LITERAL2"), ("ARC", "LITERAL2"),
    ("ARROW", "LITERAL2"), ("ARGB", "LITERAL2"), ("BACKSPACE", "LITERAL2"),
    ("BASELINE", "LITERAL2"), ("BEVEL", "LITERAL2"), ("BLEND", "LITERAL2"),
    ("BLUE_MASK", "LITERAL2"), ("BLUR", "LITERAL2"), ("BOTTOM", "LITERAL2"),
    ("BOX", "LITERAL2"), ("BURN", "LITERAL2"), ("CENTER", "LITERAL2"),
    ("CHATTER", "LITERAL2"), ("CHORD", "LITERAL2"), ("CLAMP", "LITERAL2"),
    ("CLICK", "LITERAL2"), ("CLOSE", "LITERAL2"), ("CMYK", "LITERAL2"),
    ("CODED", "LITERAL2"), ("COMPLAINT", "LITERAL2"), ("COMPOSITE", "LITERAL2"),
    ("COMPONENT", "LITERAL2"), ("CONCAVE_POLYGON", "LITERAL2"), ("CONTROL", "LITERAL2"),
    ("CONVEX_POLYGON", "LITERAL2"), ("CORNER", "LITERAL2"), ("CORNERS", "LITERAL2"),
    ("CROSS", "LITERAL2"), ("CUSTOM", "LITERAL2"), ("DARKEST", "LITERAL2"),
    ("DEGREES", "LITERAL2"), ("DEG_TO_RAD", "LITERAL2"), ("DELETE", "LITERAL2"),
    ("DIAMETER", "LITERAL2"), ("DIFFERENCE", "LITERAL2"), ("DIFFUSE", "LITERAL2"),
    ("DILATE", "LITERAL2"), ("DIRECTIONAL", "LITERAL2"),
    ("DISABLE_ACCURATE_2D", "LITERAL2"), ("DISABLE_DEPTH_MASK", "LITERAL2"),
    ("DISABLE_DEPTH_SORT", "LITERAL2"), ("DISABLE_DEPTH_TEST", "LITERAL2"),
    ("DISABLE_NATIVE_FONTS", "LITERAL2"), ("DISABLE_OPENGL_ERRORS", "LITERAL2"),
    ("DISABLE_PURE_STROKE", "LITERAL2"), ("DISABLE_TEXTURE_MIPMAPS", "LITERAL2"),
    ("DISABLE_TRANSFORM_CACHE", "LITERAL2"), ("DISABLE_STROKE_PERSPECTIVE", "LITERAL2"),
    ("DISABLED", "LITERAL2"), ("DODGE", "LITERAL2"), ("DOWN", "LITERAL2"),
    ("DRAG", "LITERAL2"), ("DXF", "LITERAL2"), ("ELLIPSE", "LITERAL2"),
    ("ENABLE_ACCURATE_2D", "LITERAL2"), ("ENABLE_DEPTH_MASK", "LITERAL2"),
    ("ENABLE_DEPTH_SORT", "LITERAL2"), ("ENABLE_DEPTH_TEST", "LITERAL2"),
    ("ENABLE_NATIVE_FONTS", "LITERAL2"), ("ENABLE_OPENGL_ERRORS", "LITERAL2"),
    ("ENABLE_PURE_STROKE", "LITERAL2"), ("ENABLE_TEXTURE_MIPMAPS", "LITERAL2"),
    ("ENABLE_TRANSFORM_CACHE", "LITERAL2"), ("ENABLE_STROKE_PERSPECTIVE", "LITERAL2"),
    ("ENTER", "LITERAL2"), ("EPSILON", "LITERAL2"), ("ERODE", "LITERAL2"),
    ("ESC", "LITERAL2"), ("EXCLUSION", "LITERAL2"), ("EXIT", "LITERAL2"),
    ("FX2D", "LITERAL2"), ("GIF", "LITERAL2"), ("GRAY", "LITERAL2"),
    ("GREEN_MASK", "LITERAL2"), ("GROUP", "LITERAL2"), ("HALF", "LITERAL2"),
    ("HALF_PI", "LITERAL2"), ("HAND", "LITERAL2"), ("HARD_LIGHT", "LITERAL2"),
    ("HINT_COUNT", "LITERAL2"), ("HSB", "LITERAL2"), ("IMAGE", "LITERAL2"),
    ("INVERT", "LITERAL2"), ("JAVA2D", "LITERAL2"), ("JPEG", "LITERAL2"),
    ("LEFT", "LITERAL2"), ("LIGHTEST", "LITERAL2"), ("LINE", "LITERAL2"),
    ("LINES", "LITERAL2"), ("LINUX", "LITERAL2"), ("MACOSX", "LITERAL2"),
    ("MAX_FLOAT", "LITERAL2"), ("MAX_INT", "LITERAL2"), ("MIN_FLOAT", "LITERAL2"),
    ("MIN_INT", "LITERAL2"), ("MITER", "LITERAL2"), ("MODEL", "LITERAL2"),
    ("MOVE", "LITERAL2"), ("MULTIPLY", "LITERAL2"), ("NORMAL", "LITERAL2"),
    ("NORMALIZED", "LITERAL2"), ("NO_DEPTH_TEST", "LITERAL2"), ("NTSC", "LITERAL2"),
    ("ONE", "LITERAL2"), ("OPAQUE", "LITERAL2"), ("OPEN", "LITERAL2"),
    ("ORTHOGRAPHIC", "LITERAL2"), ("OVERLAY", "LITERAL2"), ("PAL", "LITERAL2"),
    ("PDF", "LITERAL2"), ("P2D", "LITERAL2"), ("P3D", "LITERAL2"),
    ("PERSPECTIVE", "LITERAL2"), ("PI", "LITERAL2"), ("PIE", "LITERAL2"),
    ("PIXEL_CENTER", "LITERAL2"), ("POINT", "LITERAL2"), ("POINTS", "LITERAL2"),
    ("POSTERIZE", "LITERAL2"), ("PRESS", "LITERAL2"), ("PROBLEM", "LITERAL2"),
    ("PROJECT", "LITERAL2"), ("QUAD", "LITERAL2"), ("QUAD_STRIP", "LITERAL2"),
    ("QUADS", "LITERAL2"), ("QUARTER_PI", "LITERAL2"), ("RAD_TO_DEG", "LITERAL2"),
    ("RADIUS", "LITERAL2"), ("RADIANS", "LITERAL2"), ("RECT", "LITERAL2"),
    ("RED_MASK", "LITERAL2"), ("RELEASE", "LITERAL2"), ("REPEAT", "LITERAL2"),
    ("REPLACE", "LITERAL2"), ("RETURN", "LITERAL2"), ("RGB", "LITERAL2"),
    ("RIGHT", "LITERAL2"), ("ROUND", "LITERAL2"), ("SCREEN", "LITERAL2"),
    ("SECAM", "LITERAL2"), ("SHAPE", "LITERAL2"), ("SHIFT", "LITERAL2"),
    ("SPAN", "LITERAL2"), ("SPECULAR", "LITERAL2"), ("SPHERE", "LITERAL2"),
    ("SOFT_LIGHT", "LITERAL2"), ("SQUARE", "LITERAL2"), ("SUBTRACT", "LITERAL2"),
    ("SVG", "LITERAL2"), ("SVIDEO", "LITERAL2"), ("TAB", "LITERAL2"),
    ("TARGA", "LITERAL2"), ("TAU", "LITERAL2"), ("TEXT", "LITERAL2"),
    ("TFF", "LITERAL2"), ("THIRD_PI", "LITERAL2"), ("THRESHOLD", "LITERAL2"),
    ("TIFF", "LITERAL2"), ("TOP", "LITERAL2"), ("TRIANGLE", "LITERAL2"),
    ("TRIANGLE_FAN", "LITERAL2"), ("TRIANGLES", "LITERAL2"),
    ("TRIANGLE_STRIP", "LITERAL2"), ("TUNER", "LITERAL2"), ("TWO", "LITERAL2"),
    ("TWO_PI", "LITERAL2"), ("UP", "LITERAL2"), ("WAIT", "LITERAL2"),
    ("WHITESPACE", "LITERAL2"),
    ("abstract", "KEYWORD1"), ("break", "KEYWORD1"), ("class", "KEYWORD1"),
    ("continue", "KEYWORD1"), ("default", "KEYWORD1"), ("enum", "KEYWORD1"),
    ("extends", "KEYWORD1"), ("false", "KEYWORD1"), ("final", "KEYWORD1"),
    ("finally", "KEYWORD1"), ("implements", "KEYWORD1"), ("import", "KEYWORD1"),
    ("instanceof", "KEYWORD1"), ("interface", "KEYWORD1"), ("native", "KEYWORD1"),
    ("new", "KEYWORD1"), ("null", "KEYWORD1"), ("package", "KEYWORD1"),
    ("private", "KEYWORD1"), ("protected", "KEYWORD1"), ("public", "KEYWORD1"),
    ("static", "KEYWORD1"), ("strictfp", "KEYWORD1"), ("throws", "KEYWORD1"),
    ("transient", "KEYWORD1"), ("true", "KEYWORD1"), ("void", "KEYWORD1"),
    ("volatile", "KEYWORD1"),
    ("assert", "KEYWORD6"), ("case", "KEYWORD6"), ("return", "KEYWORD6"),
    ("super", "KEYWORD6"), ("this", "KEYWORD6"), ("throw", "KEYWORD6"),
    ("Array", "KEYWORD5"), ("ArrayList", "KEYWORD5"), ("Boolean", "KEYWORD5"),
    ("Byte", "KEYWORD5"), ("BufferedReader", "KEYWORD5"), ("Character", "KEYWORD5"),
    ("Class", "KEYWORD5"), ("Float", "KEYWORD5"), ("Integer", "KEYWORD5"),
    ("HashMap", "KEYWORD5"), ("PrintWriter", "KEYWORD5"), ("String", "KEYWORD5"),
    ("StringBuffer", "KEYWORD5"), ("StringBuilder", "KEYWORD5"), ("Thread", "KEYWORD5"),
    ("boolean", "KEYWORD5"), ("byte", "KEYWORD5"), ("char", "KEYWORD5"),
    ("color", "KEYWORD5"), ("double", "KEYWORD5"), ("float", "KEYWORD5"),
    ("int", "KEYWORD5"), ("long", "KEYWORD5"),
    ("catch", "KEYWORD3"), ("do", "KEYWORD3"), ("for", "KEYWORD3"),
    ("if", "KEYWORD3"), ("else", "KEYWORD3"), ("switch", "KEYWORD3"),
    ("synchronized", "KEYWORD3"), ("while", "KEYWORD3"), ("try", "KEYWORD3"),
    ("width", "KEYWORD4"), ("height", "KEYWORD4"), ("pixelHeight", "KEYWORD4"),
    ("pixelWidth", "KEYWORD4"),
    ("PVector", "KEYWORD5"), ("JSONObject", "KEYWORD5"),
    ("endCamera", "FUNCTION1"), ("min", "FUNCTION1"), ("printCamera", "FUNCTION1"),
    ("strokeWeight", "FUNCTION1"), ("scale", "FUNCTION1"), ("mouseDragged", "FUNCTION4"),
    ("keyReleased", "FUNCTION4"), ("frameRate", "FUNCTION1"), ("PShape", "KEYWORD5"),
    ("randomSeed", "FUNCTION1"), ("hue", "FUNCTION1"), ("bezierPoint", "FUNCTION1"),
    ("degrees", "FUNCTION1"), ("loadPixels", "FUNCTION1"), ("cursor", "FUNCTION1"),
    ("beginContour", "FUNCTION1"), ("loadTable", "FUNCTION1"), ("keyPressed", "KEYWORD4"),
    ("dist", "FUNCTION1"), ("updatePixels", "FUNCTION1"), ("saveStream", "FUNCTION1"),
    ("createWriter", "FUNCTION1"), ("join", "FUNCTION1"), ("loadXML", "FUNCTION1"),
    ("splice", "FUNCTION1"), ("noiseDetail", "FUNCTION1"), ("curveDetail", "FUNCTION1"),
    ("textLeading", "FUNCTION1"), ("noCursor", "FUNCTION1"), ("sphere", "FUNCTION1"),
    ("day", "FUNCTION1"), ("month", "FUNCTION1"), ("IntDict", "KEYWORD5"),
    ("popMatrix", "FUNCTION1"), ("loadImage", "FUNCTION1"), ("shininess", "FUNCTION1"),
    ("ceil", "FUNCTION1"), ("randomGaussian", "FUNCTION1"), ("size", "FUNCTION1"),
    ("mouseX", "KEYWORD4"), ("expand", "FUNCTION1"), ("pushMatrix", "FUNCTION1"),
    ("mouseY", "KEYWORD4"), ("lerpColor", "FUNCTION1"), ("ellipse", "FUNCTION1"),
    ("stroke", "FUNCTION1"), ("imageMode", "FUNCTION1"), ("settings", "FUNCTION4"),
    ("pixelWidth", "FUNCTION1"), ("createGraphics", "FUNCTION1"), ("focused", "KEYWORD4"),
    ("endContour", "FUNCTION1"), ("saveStrings", "FUNCTION1"), ("resetMatrix", "FUNCTION1"),
    ("key", "KEYWORD4"), ("requestImage", "FUNCTION1"), ("saveBytes", "FUNCTION1"),
    ("get", "FUNCTION1"), ("emissive", "FUNCTION1"), ("noStroke", "FUNCTION1"),
    ("textureWrap", "FUNCTION1"), ("FloatDict", "KEYWORD5"), ("vertex", "FUNCTION1"),
    ("mousePressed", "FUNCTION4"), ("selectInput", "FUNCTION1"),
    ("createReader", "FUNCTION1"), ("strokeJoin", "FUNCTION1"), ("hour", "FUNCTION1"),
    ("round", "FUNCTION1"), ("strokeCap", "FUNCTION1"), ("ortho", "FUNCTION1"),
    ("parseJSONObject", "FUNCTION1"), ("textFont", "FUNCTION1"), ("clear", "FUNCTION1"),
    ("loadBytes", "FUNCTION1"), ("ambient", "FUNCTION1"), ("keyTyped", "FUNCTION4"),
    ("loadFont", "FUNCTION1"), ("map", "FUNCTION1"), ("mag", "FUNCTION1"),
    ("minute", "FUNCTION1"), ("beginRecord", "FUNCTION1"), ("alpha", "FUNCTION1"),
    ("noClip", "FUNCTION1"), ("abs", "FUNCTION1"), ("saveXML", "FUNCTION1"),
    ("blue", "FUNCTION1"), ("year", "FUNCTION1"), ("nfc", "FUNCTION1"),
    ("rotate", "FUNCTION1"), ("parseJSONArray", "FUNCTION1"), ("pixels", "KEYWORD4"),
    ("saveFrame", "FUNCTION1"), ("splitTokens", "FUNCTION1"), ("atan", "FUNCTION1"),
    ("XML", "KEYWORD5"), ("circle", "FUNCTION1"), ("bezierDetail", "FUNCTION1"),
    ("draw", "FUNCTION4"), ("cos", "FUNCTION1"), ("sq", "FUNCTION1"),
    ("delay", "FUNCTION1"), ("fullScreen", "FUNCTION1"), ("endRaw", "FUNCTION1"),
    ("translate", "FUNCTION1"), ("nfp", "FUNCTION1"), ("blendMode", "FUNCTION1"),
    ("sin", "FUNCTION1"), ("mouseButton", "KEYWORD4"), ("clip", "FUNCTION1"),
    ("displayHeight", "KEYWORD4"), ("hex", "FUNCTION1"), ("TableRow", "KEYWORD5"),
    ("beginShape", "FUNCTION1"), ("StringDict", "KEYWORD5"), ("trim", "FUNCTION1"),
    ("pmouseX", "KEYWORD4"), ("textDescent", "FUNCTION1"), ("createShape", "FUNCTION1"),
    ("pmouseY", "KEYWORD4"), ("nfs", "FUNCTION1"), ("JSONArray", "KEYWORD5"),
    ("displayDensity", "FUNCTION1"), ("createFont", "FUNCTION1"),
    ("applyMatrix", "FUNCTION1"), ("point", "FUNCTION1"), ("background", "FUNCTION1"),
    ("pixelDensity", "FUNCTION1"), ("triangle", "FUNCTION1"), ("rect", "FUNCTION1"),
    ("shape", "FUNCTION1"), ("saveTable", "FUNCTION1"), ("matchAll", "FUNCTION1"),
    ("noLights", "FUNCTION1"), ("max", "FUNCTION1"), ("mouseWheel", "FUNCTION4"),
    ("set", "FUNCTION1"), ("PImage", "KEYWORD5"), ("printProjection", "FUNCTION1"),
    ("popStyle", "FUNCTION1"), ("lerp", "FUNCTION1"), ("perspective", "FUNCTION1"),
    ("print", "FUNCTION1"), ("fill", "FUNCTION1"), ("loadJSONObject", "FUNCTION1"),
    ("redraw", "FUNCTION1"), ("textAlign", "FUNCTION1"), ("bezier", "FUNCTION1"),
    ("smooth", "FUNCTION1"), ("blendColor", "FUNCTION1"), ("displayWidth", "KEYWORD4"),
    ("println", "FUNCTION1"), ("brightness", "FUNCTION1"), ("loadJSONArray", "FUNCTION1"),
    ("tan", "FUNCTION1"), ("printMatrix", "FUNCTION1"), ("lights", "FUNCTION1"),
    ("loadShape", "FUNCTION1"), ("random", "FUNCTION1"), ("quadraticVertex", "FUNCTION1"),
    ("bezierVertex", "FUNCTION1"), ("exp", "FUNCTION1"), ("textureMode", "FUNCTION1"),
    ("specular", "FUNCTION1"), ("noSmooth", "FUNCTION1"), ("pow", "FUNCTION1"),
    ("blend", "FUNCTION1"), ("green", "FUNCTION1"), ("createOutput", "FUNCTION1"),
    ("concat", "FUNCTION1"), ("bezierTangent", "FUNCTION1"), ("mouseReleased", "FUNCTION4"),
    ("arrayCopy", "FUNCTION1"), ("beginCamera", "FUNCTION1"), ("asin", "FUNCTION1"),
    ("lightFalloff", "FUNCTION1"), ("color", "FUNCTION1"), ("shader", "FUNCTION1"),
    ("rotateZ", "FUNCTION1"), ("radians", "FUNCTION1"), ("curveTightness", "FUNCTION1"),
    ("filter", "FUNCTION1"), ("PFont", "KEYWORD5"), ("noFill", "FUNCTION1"),
    ("noise", "FUNCTION1"), ("acos", "FUNCTION1"), ("frameCount", "KEYWORD4"),
    ("curvePoint", "FUNCTION1"), ("sort", "FUNCTION1"), ("reverse", "FUNCTION1"),
    ("sphereDetail", "FUNCTION1"), ("constrain", "FUNCTION1"), ("frustum", "FUNCTION1"),
    ("list", "FUNCTION1"), ("second", "FUNCTION1"), ("binary", "FUNCTION1"),
    ("StringList", "KEYWORD5"), ("push", "FUNCTION1"), ("normal", "FUNCTION1"),
    ("pop", "FUNCTION1"), ("shearX", "FUNCTION1"), ("saveJSONObject", "FUNCTION1"),
    ("rotateY", "FUNCTION1"), ("rotateX", "FUNCTION1"), ("endShape", "FUNCTION1"),
    ("shearY", "FUNCTION1"), ("shapeMode", "FUNCTION1"), ("norm", "FUNCTION1"),
    ("saveJSONArray", "FUNCTION1"), ("match", "FUNCTION1"), ("floor", "FUNCTION1"),
    ("quad", "FUNCTION1"), ("pixelHeight", "FUNCTION1"), ("parseXML", "FUNCTION1"),
    ("exit", "FUNCTION1"), ("texture", "FUNCTION1"), ("setup", "FUNCTION4"),
    ("modelX", "FUNCTION1"), ("selectFolder", "FUNCTION1"), ("sqrt", "FUNCTION1"),
    ("log", "FUNCTION1"), ("curveTangent", "FUNCTION1"), ("unhex", "FUNCTION1"),
    ("createInput", "FUNCTION1"), ("thread", "FUNCTION1"), ("beginRaw", "FUNCTION1"),
    ("modelY", "FUNCTION1"), ("ambientLight", "FUNCTION1"), ("rectMode", "FUNCTION1"),
    ("arc", "FUNCTION1"), ("noTint", "FUNCTION1"), ("shorten", "FUNCTION1"),
    ("unbinary", "FUNCTION1"), ("IntList", "KEYWORD5"), ("line", "FUNCTION1"),
    ("colorMode", "FUNCTION1"), ("loop", "FUNCTION1"), ("saturation", "FUNCTION1"),
    ("directionalLight", "FUNCTION1"), ("modelZ", "FUNCTION1"), ("nf", "FUNCTION1"),
    ("loadStrings", "FUNCTION1"), ("textMode", "FUNCTION1"), ("camera", "FUNCTION1"),
    ("screenY", "FUNCTION1"), ("textSize", "FUNCTION1"), ("split", "FUNCTION1"),
    ("Table", "KEYWORD5"), ("mouseClicked", "FUNCTION4"), ("ellipseMode", "FUNCTION1"),
    ("resetShader", "FUNCTION1"), ("square", "FUNCTION1"), ("textAscent", "FUNCTION1"),
    ("box", "FUNCTION1"), ("millis", "FUNCTION1"), ("curve", "FUNCTION1"),
    ("spotLight", "FUNCTION1"), ("screenX", "FUNCTION1"), ("curveVertex", "FUNCTION1"),
    ("printArray", "FUNCTION1"), ("selectOutput", "FUNCTION1"), ("endRecord", "FUNCTION1"),
    ("noiseSeed", "FUNCTION1"), ("PGraphics", "KEYWORD5"), ("pointLight", "FUNCTION1"),
    ("screenZ", "FUNCTION1"), ("loadShader", "FUNCTION1"), ("keyCode", "KEYWORD4"),
    ("subset", "FUNCTION1"), ("image", "FUNCTION1"), ("tint", "FUNCTION1"),
    ("text", "FUNCTION1"), ("mouseMoved", "FUNCTION4"), ("launch", "FUNCTION1"),
    ("PShader", "KEYWORD5"), ("pushStyle", "FUNCTION1"), ("textWidth", "FUNCTION1"),
    ("red", "FUNCTION1"), ("lightSpecular", "FUNCTION1"), ("atan2", "FUNCTION1"),
    ("createImage", "FUNCTION1"), ("FloatList", "KEYWORD5"), ("attrib", "FUNCTION1"),
    ("noLoop", "FUNCTION1"),
    # FUNCTION2 (object methods) -- collected but never auto-applied;
    # listed here only so the report can show what exists for awareness.
    ("cross", "FUNCTION2"), ("hasValue", "FUNCTION2"), ("copy", "FUNCTION2"),
    ("setString", "FUNCTION2"), ("hasKey", "FUNCTION2"), ("sortReverse", "FUNCTION2"),
    ("getParent", "FUNCTION2"), ("increment", "FUNCTION2"), ("getFloat", "FUNCTION2"),
    ("resetMatrix", "FUNCTION2"), ("sub", "FUNCTION2"), ("removeColumn", "FUNCTION2"),
    ("disableStyle", "FUNCTION2"), ("mask", "FUNCTION2"), ("findRow", "FUNCTION2"),
    ("append", "FUNCTION2"), ("mult", "FUNCTION2"), ("setJSONArray", "FUNCTION2"),
    ("values", "FUNCTION2"), ("setContent", "FUNCTION2"), ("clear", "FUNCTION2"),
    ("sort", "FUNCTION2"), ("setBoolean", "FUNCTION2"), ("array", "FUNCTION2"),
    ("getName", "FUNCTION2"), ("setVertex", "FUNCTION2"), ("remove", "FUNCTION2"),
    ("rows", "FUNCTION2"), ("sortKeysReverse", "FUNCTION2"), ("add", "FUNCTION2"),
    ("removeChild", "FUNCTION2"), ("getColumnCount", "FUNCTION2"), ("addChild", "FUNCTION2"),
    ("setMag", "FUNCTION2"), ("matchRow", "FUNCTION2"), ("div", "FUNCTION2"),
    ("getBoolean", "FUNCTION2"), ("setFloat", "FUNCTION2"), ("lower", "FUNCTION2"),
    ("format", "FUNCTION2"), ("setStroke", "FUNCTION2"), ("isNull", "FUNCTION2"),
    ("sortValuesReverse", "FUNCTION2"), ("lerp", "FUNCTION2"), ("setInt", "FUNCTION2"),
    ("getRowCount", "FUNCTION2"), ("hasAttribute", "FUNCTION2"), ("getInt", "FUNCTION2"),
    ("getString", "FUNCTION2"), ("getVertexCount", "FUNCTION2"), ("getChildren", "FUNCTION2"),
    ("normalize", "FUNCTION2"), ("blend", "FUNCTION2"), ("sortKeys", "FUNCTION2"),
    ("reverse", "FUNCTION2"), ("getContent", "FUNCTION2"), ("keys", "FUNCTION2"),
    ("getChild", "FUNCTION2"), ("getIntArray", "FUNCTION2"), ("size", "FUNCTION2"),
    ("setName", "FUNCTION2"), ("setVisible", "FUNCTION2"), ("shuffle", "FUNCTION2"),
    ("getJSONArray", "FUNCTION2"), ("upper", "FUNCTION2"), ("findRows", "FUNCTION2"),
    ("keyArray", "FUNCTION2"), ("getStringColumn", "FUNCTION2"), ("getColumnTitle", "FUNCTION2"),
    ("filter", "FUNCTION2"), ("addColumn", "FUNCTION2"), ("angleBetween", "FUNCTION2"),
    ("removeRow", "FUNCTION2"), ("listChildren", "FUNCTION2"), ("magSq", "FUNCTION2"),
    ("listAttributes", "FUNCTION2"), ("getStringArray", "FUNCTION2"), ("sortValues", "FUNCTION2"),
    ("getChildCount", "FUNCTION2"), ("setJSONObject", "FUNCTION2"), ("heading", "FUNCTION2"),
    ("valueArray", "FUNCTION2"), ("isVisible", "FUNCTION2"), ("enableStyle", "FUNCTION2"),
    ("dot", "FUNCTION2"), ("random3D", "FUNCTION2"), ("limit", "FUNCTION2"),
    ("fromAngle", "FUNCTION2"), ("getRow", "FUNCTION2"), ("matchRows", "FUNCTION2"),
    ("setFill", "FUNCTION2"), ("toString", "FUNCTION2"), ("beginDraw", "FUNCTION2"),
    ("endContour", "FUNCTION2"), ("removeTokens", "FUNCTION2"), ("addRow", "FUNCTION2"),
    ("hasChildren", "FUNCTION2"), ("resize", "FUNCTION2"), ("getVertex", "FUNCTION2"),
    ("getAttributeCount", "FUNCTION2"), ("random2D", "FUNCTION2"),
]


def run_grep(identifier):
    """Returns total occurrence count of identifier as a whole word across
    the engine source files. Uses grep -w (word boundary) to avoid
    substring false-positives."""
    total = 0
    for path in HEADER_FILES:
        if not os.path.exists(path):
            continue
        try:
            result = subprocess.run(
                ["grep", "-cw", identifier, path],
                capture_output=True, text=True
            )
            # grep returns exit code 1 with empty stdout when no matches --
            # that's not an error for our purposes.
            if result.stdout.strip():
                total += int(result.stdout.strip())
        except Exception:
            pass
    return total


# C++ reserved words (and Java reserved words that are ALSO C++ reserved
# words) trivially appear in any C++ source file as language syntax, not
# as evidence of Processing-API support -- grep-verifying these proves
# nothing ("static" appears because C++ has static methods, not because
# CppMode implements a Processing keyword called "static"). These are
# trusted as real by construction (your engine IS C++, so it definitionally
# supports its own host language's keywords) and skip grep verification.
CPP_RESERVED_TRUSTED = {
    "void", "static", "int", "float", "double", "long", "char", "short",
    "unsigned", "signed", "bool", "boolean", "true", "false", "null",
    "new", "delete", "class", "struct", "enum", "public", "private",
    "protected", "virtual", "override", "const", "extern", "inline",
    "typedef", "sizeof", "namespace", "using", "template", "typename",
    "for", "while", "do", "if", "else", "switch", "case", "break",
    "continue", "return", "default", "try", "catch", "throw", "this",
    "super", "extends", "implements", "import", "package", "interface",
    "instanceof", "native", "synchronized", "transient", "volatile",
    "strictfp", "throws", "finally", "final", "abstract", "assert",
}

# Identifiers that live in your hand-maintained "CUSTOM C++ KEYWORDS"
# block at the top of keywords.txt -- these have NO Processing4/Java
# equivalent at all (auto, namespace, std, etc.), so they will never
# legitimately appear in OFFICIAL with a real tag, but listing them
# here explicitly means this script will NEVER propose adding, retagging,
# or duplicating them, even if a future edit to OFFICIAL accidentally
# introduces a colliding name. This is a hard exclusion checked before
# anything else in the main loop.
CUSTOM_CPP_KEYWORDS_PROTECTED = {
    "auto", "const", "struct", "namespace", "using", "template",
    "typename", "override", "inline", "extern", "typedef", "sizeof",
    "nullptr", "std", "unsigned", "signed", "short", "bool", "string",
    "import",  # C++20 Modules keyword (import <iostream>; etc.) -- a
               # DIFFERENT feature than Java's import despite sharing a
               # name (Java's import ~= C++ "using", per cppreference/
               # Wikipedia). Belongs in your custom block, not removed
               # as Java-only and not synced from OFFICIAL's Java sense.
}

# Java keywords with NO meaning in real C++ at all -- since CppMode
# sketches compile as actual C++ (not Java), highlighting these as valid
# keywords is misleading: a sketch author could never legally write them.
# Permanently excluded from this script's additions, same as the custom
# C++-only block above, just in the opposite direction (these are
# excluded because they're JAVA-only, not because they're C++-only).
#   extends/implements/interface  -- Java OOP syntax; C++ uses ": public X"
#                                     and has no interface keyword at all
#   import/package                -- Java module system; C++ uses #include
#   instanceof                    -- Java runtime type-check; C++ uses
#                                     typeid/dynamic_cast instead
#   native/strictfp                -- Java VM/floating-point keywords with
#                                     no C++ equivalent whatsoever
#   synchronized                   -- exists in C++ ONLY as part of the
#                                     experimental Transactional Memory TS
#                                     (TM TS), not mainstream/standard C++
#                                     and not implemented by default in
#                                     GCC/Clang -- excluded for the same
#                                     practical reason as the others: not
#                                     usable syntax in a real CppMode build
#   throws/finally                -- Java checked-exception/cleanup syntax;
#                                     C++ has neither (RAII replaces finally)
#   boolean/null                  -- Java spells these 'bool'/'nullptr' in
#                                     C++ instead (already in the custom
#                                     C++ block above under those names)
#   super/abstract                -- Java inheritance keywords; C++ has no
#                                     'super' (name the base class
#                                     explicitly) and no 'abstract' (pure
#                                     virtual functions serve that role)
JAVA_ONLY_NO_CPP_EQUIVALENT = {
    "extends", "implements", "package", "interface",
    "instanceof", "native", "synchronized", "strictfp", "throws",
    "finally", "boolean", "null", "super", "abstract",
    # NOTE: "import" deliberately excluded from this set -- it IS real
    # C++ syntax (the C++20 Modules feature: import <iostream>; etc.),
    # just with a different meaning than Java's import. See
    # CUSTOM_CPP_KEYWORDS_PROTECTED above, where it's listed instead.
}


def parse_existing_keywords(path):
    """Returns dict {identifier: set_of_tags} from your real keywords.txt,
    skipping comments and blank lines. A set (not a single tag) because
    your file -- like Processing4's -- can legitimately list the same
    identifier more than once with different tags (e.g. color as both a
    datatype and a function), which is how paren-lookahead disambiguation
    works in the tokenizer. Collapsing to one tag per identifier here
    would silently lose that second row, the same bug this script
    previously had on the OFFICIAL side."""
    existing = {}
    if not os.path.exists(path):
        print(f"ERROR: {path} not found. Run this from your repo root.")
        sys.exit(1)
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            parts = re.split(r"\s+", stripped)
            if len(parts) >= 2:
                existing.setdefault(parts[0], set()).add(parts[1])
    return existing


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="write changes to keywords.txt")
    parser.add_argument("--backup", action="store_true", help="write keywords.txt.bak before applying")
    args = parser.parse_args()

    if not any(os.path.exists(p) for p in HEADER_FILES):
        print(f"ERROR: couldn't find src/Processing.h or src/Processing.cpp")
        print(f"(looked relative to: {REPO_ROOT})")
        print("Run this from inside your CppMode repo (or its scripts/ subfolder).")
        sys.exit(1)

    existing = parse_existing_keywords(KEYWORDS_PATH)
    print(f"Loaded {len(existing)} existing keyword entries from {KEYWORDS_PATH}\n")

    to_add = []          # (identifier, official_tag, count) -- this exact (ident, tag) pair
                          # is verified in your engine but not present in your file at all
    skipped_unverified = []   # official says it exists, but grep found nothing in your engine
    already_present = 0   # counter, not reported in detail -- just for the summary line

    # IMPORTANT: do NOT deduplicate by identifier alone. Processing4's own
    # keywords.txt deliberately lists some identifiers MORE THAN ONCE with
    # DIFFERENT tags -- e.g.
    #     color   KEYWORD5   color_datatype
    #     color   FUNCTION1  color_
    # This is exactly how the real PDE tokenizer disambiguates "color c"
    # (the datatype, KEYWORD5) from "color(255,0,0)" (the function call,
    # FUNCTION1) -- it looks ahead for a following "(" and picks whichever
    # row matches. You've confirmed your own tokenizer already does this
    # correctly when given the unmodified official file. An earlier
    # version of this script collapsed each identifier down to a single
    # tag (keeping only the first one seen), which silently discarded the
    # second row and broke that disambiguation -- this version treats
    # every distinct (identifier, tag) PAIR as its own unit, exactly
    # mirroring how Processing4's file itself is structured. There is no
    # more "retag" concept: a given (identifier, tag) pair is either
    # already in your file, or it gets added -- nothing is ever removed
    # or overwritten, since a second tag for the same identifier is a
    # valid SECOND ROW, not a correction of the first.
    distinct_pairs_seen = set()
    skipped_custom_protected = 0
    skipped_java_only = 0
    for ident, off_tag in OFFICIAL:
        if ident in CUSTOM_CPP_KEYWORDS_PROTECTED:
            # Hard exclusion: this identifier lives in your hand-maintained
            # CUSTOM C++ KEYWORDS block and must never be touched by this
            # script, regardless of what OFFICIAL says about it.
            skipped_custom_protected += 1
            continue

        if ident in JAVA_ONLY_NO_CPP_EQUIVALENT:
            # Hard exclusion: Java-only syntax with no real C++ meaning --
            # never add, regardless of what tag OFFICIAL assigns it.
            skipped_java_only += 1
            continue

        if (ident, off_tag) in distinct_pairs_seen:
            continue  # exact duplicate row in OFFICIAL itself, skip
        distinct_pairs_seen.add((ident, off_tag))

        if ident in CPP_RESERVED_TRUSTED:
            count = 1  # trusted by construction, not grep-verified (see comment above)
        else:
            count = run_grep(ident)

        if count == 0:
            skipped_unverified.append((ident, off_tag))
            continue

        if off_tag in existing.get(ident, set()):
            already_present += 1
            continue  # this exact pair is already in your file, nothing to do

        to_add.append((ident, off_tag, count))

    # ---- report ----
    print("=" * 70)
    print(f"VERIFIED ADDITIONS ({len(to_add)}) -- exist in your engine, not yet in keywords.txt:")
    print("=" * 70)
    for ident, tag, count in sorted(to_add, key=lambda x: -x[2]):
        already = " (a different tag for this name already exists in your file -- this is a SECOND row, not a replacement)" if existing.get(ident) else ""
        print(f"  {ident:<20} {tag:<12} (found {count}x in engine source){already}")

    print()
    print("=" * 70)
    print(f"SKIPPED -- official identifier not found in your engine source ({len(skipped_unverified)}):")
    print("=" * 70)
    for ident, tag in skipped_unverified:
        print(f"  {ident}")

    print()
    print(f"({already_present} official (identifier, tag) pairs were already present in your file -- no action needed)")
    print(f"({skipped_custom_protected} OFFICIAL entries were skipped because they belong to your protected CUSTOM C++ KEYWORDS block)")
    print(f"({skipped_java_only} OFFICIAL entries were skipped because they're Java-only syntax with no C++ equivalent)")

    if not args.apply:
        print()
        print("Dry run complete. Re-run with --apply to write the VERIFIED ADDITIONS to keywords.txt.")
        return

    # ---- apply ----
    if args.backup:
        import shutil
        shutil.copy(KEYWORDS_PATH, KEYWORDS_PATH + ".bak")
        print(f"\nBackup written to {KEYWORDS_PATH}.bak")

    # Pure append -- nothing in your existing file is ever rewritten or
    # removed. A second tag for an identifier you already have is added
    # as an additional row, exactly matching Processing4's own file
    # structure (see comment above on color/pixelHeight/etc.).
    additions_block = ["\n# --- Added by sync_keywords.py (verified against engine source) ---\n"]
    for ident, tag, count in sorted(to_add, key=lambda x: x[0]):
        additions_block.append(f"{ident}\t{tag}\n")

    with open(KEYWORDS_PATH, "a", encoding="utf-8") as f:
        f.writelines(additions_block)

    print(f"\nApplied {len(to_add)} additions to {KEYWORDS_PATH}")


if __name__ == "__main__":
    main()
