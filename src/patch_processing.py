#!/usr/bin/env python3
"""
Patches Processing.h and Processing.cpp to add:
  - mouseDX / mouseDY  (accumulated mouse delta per frame)
  - captureMouse() / releaseMouse()  (cursor lock for FPS)
"""

import os, re, sys

def find_file(filename):
    search = [
        f"/home/pep/sketchbook/modes/CppMode/src/{filename}",
        f"/home/pep/Projects/processing-cpp-dev/src/{filename}",
    ]
    for p in search:
        if os.path.exists(p):
            return p
    sys.exit(f"ERROR: could not find {filename}")

h_path  = find_file("Processing.h")
cpp_path = find_file("Processing.cpp")

print(f"Patching:\n  {h_path}\n  {cpp_path}\n")

# ─── Processing.h ────────────────────────────────────────────────────────────

with open(h_path) as f:
    h = f.read()

# 1. Add mouseDX/mouseDY extern near mouseX/mouseY
if "mouseDX" not in h:
    h = h.replace(
        "extern float mouseX, mouseY;",
        "extern float mouseX, mouseY;\nextern float mouseDX, mouseDY;  // accumulated delta since last frame"
    )
    print("✓ Added mouseDX/mouseDY extern to Processing.h")
else:
    print("· mouseDX already in Processing.h")

# 2. Add captureMouse / releaseMouse declarations if missing
if "captureMouse" not in h:
    h = h.replace(
        "void releaseMouse();",
        "void captureMouse();   // lock cursor to window (FPS mode)\nvoid releaseMouse();    // unlock cursor"
    )
    # if releaseMouse not found either, append near noCursor
    if "captureMouse" not in h:
        h = h.replace(
            "void noCursor();",
            "void noCursor();\nvoid captureMouse();   // lock cursor to window (FPS mode)\nvoid releaseMouse();    // unlock cursor"
        )
    print("✓ Added captureMouse/releaseMouse to Processing.h")
else:
    print("· captureMouse already in Processing.h")

with open(h_path, "w") as f:
    f.write(h)

# ─── Processing.cpp ──────────────────────────────────────────────────────────

with open(cpp_path) as f:
    cpp = f.read()

# 1. Add mouseDX/mouseDY definition near mouseX/mouseY
if "mouseDX" not in cpp:
    cpp = cpp.replace(
        "float mouseX = 0, mouseY = 0, pmouseX = 0, pmouseY = 0;",
        "float mouseX = 0, mouseY = 0, pmouseX = 0, pmouseY = 0;\nfloat mouseDX = 0, mouseDY = 0;"
    )
    print("✓ Added mouseDX/mouseDY definition to Processing.cpp")
else:
    print("· mouseDX already defined in Processing.cpp")

# 2. Patch cursor_pos_cb to accumulate delta
old_cb = """\
static void cursor_pos_cb(GLFWwindow*, double x, double y) {
    mouseInWindow = true;
    pmouseX = mouseX;
    pmouseY = mouseY;
    mouseX  = (float)x;
    mouseY  = (float)y;"""

new_cb = """\
static void cursor_pos_cb(GLFWwindow*, double x, double y) {
    mouseInWindow = true;
    mouseDX += (float)x - mouseX;
    mouseDY += (float)y - mouseY;
    pmouseX = mouseX;
    pmouseY = mouseY;
    mouseX  = (float)x;
    mouseY  = (float)y;"""

if "mouseDX +=" not in cpp:
    if old_cb in cpp:
        cpp = cpp.replace(old_cb, new_cb)
        print("✓ Patched cursor_pos_cb to accumulate mouseDX/mouseDY")
    else:
        print("⚠ Could not find cursor_pos_cb pattern — patch manually")
else:
    print("· cursor_pos_cb already accumulates mouseDX/mouseDY")

# 3. Reset mouseDX/mouseDY after draw()
old_draw = "++frameCount; draw();"
new_draw = "++frameCount; draw();\n                mouseDX = 0; mouseDY = 0;  // reset delta each frame"

if "mouseDX = 0; mouseDY = 0;" not in cpp:
    if old_draw in cpp:
        cpp = cpp.replace(old_draw, new_draw, 1)
        print("✓ Added mouseDX/mouseDY reset after draw()")
    else:
        print("⚠ Could not find draw() call pattern — patch manually")
else:
    print("· mouseDX/mouseDY already reset after draw()")

# 4. Add captureMouse / releaseMouse implementation
if "void Processing::captureMouse" not in cpp and "captureMouse()" not in cpp:
    # Append before the closing of the Processing namespace or at end of file
    impl = """
void captureMouse() {
    if (gWindow) {
        glfwSetInputMode(gWindow, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        // Enable raw mouse motion if supported (eliminates OS acceleration)
        if (glfwRawMouseMotionSupported())
            glfwSetInputMode(gWindow, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
    }
}

void releaseMouse() {
    if (gWindow) {
        glfwSetInputMode(gWindow, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        if (glfwRawMouseMotionSupported())
            glfwSetInputMode(gWindow, GLFW_RAW_MOUSE_MOTION, GLFW_FALSE);
    }
}
"""
    # Insert before the last closing brace of the namespace
    cpp = cpp.rstrip()
    cpp += "\n" + impl
    print("✓ Added captureMouse/releaseMouse implementation")
else:
    print("· captureMouse/releaseMouse already implemented")

with open(cpp_path, "w") as f:
    f.write(cpp)

print("\nDone. Now rebuild CppMode:")
print("  cd /home/pep/Projects/processing4 && ./gradlew :java:jar")
PYEOF
