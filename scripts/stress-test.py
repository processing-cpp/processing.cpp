#!/usr/bin/env python3
"""
Runs CppMode's full parser/pipeline stress-test suite.

Works no matter where you put this file (project root, scripts/,
wherever) -- it finds the real CppMode checkout by walking up looking
for mode.properties, rather than assuming its own location is the root.

Before running the suite, it writes a small set of known-good fixed/new
files into place (the corrected Scrollbar.pde example, and 5 new-user
edge-case fixtures: blank template, only-setup, only-draw, empty file,
comment-only file). This means a single plain run does everything --
nothing else needs to be created or copy-pasted by hand. Pass --no-write
to skip this step if you'd rather manage those files yourself.

Then it compiles the standalone tools/cpp-parser/ project and runs it
against every real example sketch under examples/, in four layers:

  1. ParserCorpusSweep    -- parse-only check (fastest, no g++ needed)
  2. RealCorpusStressTest -- full pipeline + g++ against a hand-built,
                             deliberately narrow Processing API stub.
                             A non-zero fail count here is EXPECTED and
                             not a sign of a problem -- see Layer 3.
  3. RealHeaderStressTest -- full pipeline + g++ against the REAL
                             Processing.h/Processing_api.h headers
                             (needs libglfw3-dev + libglew-dev installed;
                             auto-skipped with a clear message if not
                             found, not a hard failure). This is the
                             layer whose fail count should be at or near
                             zero.
  4. RealHeaderStressTest again, against new-user edge cases (the blank
     template, partial sketches) instead of the real example corpus.
     Same real-headers requirement as Layer 3.

Usage:
    stress-test.py
    stress-test.py --quick       (skip layers 3/4, no GLFW3/GLEW needed)
    stress-test.py --no-write    (skip writing the known-good fixtures)
"""
import os
import shutil
import subprocess
import sys


# ──────────────────────────────────────────────────────────────────────
# Root detection: walk up from this script's own location looking for
# mode.properties (CppMode's real top-level marker file), rather than
# assuming this script's own directory IS the project root. Works
# whether this file lives at the project root or in scripts/.
# ──────────────────────────────────────────────────────────────────────

def find_cppmode_root():
    here = os.path.dirname(os.path.abspath(__file__))
    candidate = here
    for _ in range(5):
        if os.path.isfile(os.path.join(candidate, "mode.properties")):
            return candidate
        parent = os.path.dirname(candidate)
        if parent == candidate:
            break
        candidate = parent
    print(f"ERROR: could not find CppMode's mode.properties by walking up from {here}.")
    print("This script needs to live somewhere inside a real CppMode checkout")
    print("(either at the project root, or in scripts/, examples/, tools/, etc.)")
    sys.exit(1)


CPPMODE_DIR = find_cppmode_root()
CPP_PARSER_DIR = os.path.join(CPPMODE_DIR, "tools", "cpp-parser")
EXAMPLES_DIR = os.path.join(CPPMODE_DIR, "examples")
SRC_DIR = os.path.join(CPPMODE_DIR, "src")
OUT_DIR = os.path.join(CPP_PARSER_DIR, "out")
CORPUS_DIR = "/tmp/cppmode_stress_corpus"


# ──────────────────────────────────────────────────────────────────────
# Known-good fixed/new files, written into place by default. Pass
# --no-write to skip this step.
#
# NOTE: src/java/Parser.java and src/java/CodeGen.java are NOT written
# here -- they're large, hand-maintained files delivered separately.
# If Layer 3 shows Pie_Chart.pde / Game_Of_Life.pde / Characters_Strings.pde
# / Scrollbar.pde failing (or CRASHing), those two files are out of date;
# this script can't fix that for you, only flag it.
# ──────────────────────────────────────────────────────────────────────

FILES_TO_WRITE = {
    "examples/Topics/gui/Scrollbar/Scrollbar.pde": '''/**
 * Scrollbar.
 *
 * Move the scrollbars left and right to change the positions of the images.
 */
//True if a mouse button was pressed while no other button was.
boolean firstMousePress = false;
HScrollbar* hs1, * hs2;  // Two scrollbars
PImage* img1 = nullptr;
PImage* img2 = nullptr;  // Two images to load
void setup() {
  size(640, 360);
  noStroke();
  hs1 = new HScrollbar(0, height/2-8, width, 16, 16);
  hs2 = new HScrollbar(0, height/2+8, width, 16, 16);
  // Load images
  img1 = loadImage("seedTop.jpg");
  img2 = loadImage("seedBottom.jpg");
}
void draw() {
  background(255);
  // Get the position of the img1 scrollbar
  // and convert to a value to display the img1 image
  float img1Pos = hs1->getPos()-width/2;
  fill(255);
  image(img1, width/2-img1->width/2 + img1Pos*1.5, 0);
  // Get the position of the img2 scrollbar
  // and convert to a value to display the img2 image
  float img2Pos = hs2->getPos()-width/2;
  fill(255);
  image(img2, width/2-img2->width/2 + img2Pos*1.5, height/2);
  hs1->update();
  hs2->update();
  hs1->display();
  hs2->display();
  stroke(0);
  line(0, height/2, width, height/2);
  //After it has been used in the sketch, set it back to false
  if (firstMousePress) {
    firstMousePress = false;
  }
}
void mousePressed() {
  if (!firstMousePress) {
    firstMousePress = true;
  }
}
class HScrollbar {
  int swidth, sheight;    // width and height of bar
  float xpos, ypos;       // x and y position of bar
  float spos, newspos;    // x position of slider
  float sposMin, sposMax; // max and min values of slider
  int loose;              // how loose/heavy
  boolean over;           // is the mouse over the slider?
  boolean locked;
  float ratio;
  HScrollbar (float xp, float yp, int sw, int sh, int l) {
    swidth = sw;
    sheight = sh;
    int widthtoheight = sw - sh;
    ratio = (float)sw / (float)widthtoheight;
    xpos = xp;
    ypos = yp-sheight/2;
    spos = xpos + swidth/2 - sheight/2;
    newspos = spos;
    sposMin = xpos;
    sposMax = xpos + swidth - sheight;
    loose = l;
  }
  void update() {
    if (overEvent()) {
      over = true;
    } else {
      over = false;
    }
    if (firstMousePress && over) {
      locked = true;
    }
    if (!_mousePressed) {
      locked = false;
    }
    if (locked) {
      newspos = constrain(mouseX-sheight/2, sposMin, sposMax);
    }
    if (abs(newspos - spos) > 1) {
      spos = spos + (newspos-spos)/loose;
    }
  }
  float constrain(float val, float minv, float maxv) {
    return min(max(val, minv), maxv);
  }
  boolean overEvent() {
    if (mouseX > xpos && mouseX < xpos+swidth &&
      mouseY > ypos && mouseY < ypos+sheight) {
      return true;
    } else {
      return false;
    }
  }
  void display() {
    noStroke();
    fill(204);
    rect(xpos, ypos, swidth, sheight);
    if (over || locked) {
      fill(0, 0, 0);
    } else {
      fill(102, 102, 102);
    }
    rect(spos, ypos, sheight, sheight);
  }
  float getPos() {
    // Convert spos to be values between
    // 0 and the total width of the scrollbar
    return spos * ratio;
  }
}
''',

    "tools/cpp-parser/fixtures/new-user-edge-cases/blank_template.pde": '''// Full C++ STL is available without any #include.
// See https://processing-cpp.github.io/libraries

void setup() {
  // put your setup code here, to run once:
}

void draw() {
  // put your main code here, to run repeatedly:
}
''',

    "tools/cpp-parser/fixtures/new-user-edge-cases/only_setup.pde": '''void setup() {
  size(400, 400);
}
''',

    "tools/cpp-parser/fixtures/new-user-edge-cases/only_draw.pde": '''void draw() {
  background(0);
}
''',

    "tools/cpp-parser/fixtures/new-user-edge-cases/completely_empty.pde": "",

    "tools/cpp-parser/fixtures/new-user-edge-cases/just_a_comment.pde": "// nothing here yet\n",
}


def write_known_good_files():
    print("=" * 70)
    print("STEP 0: writing known-good fixed/new files into place")
    print("=" * 70)
    for rel_path, content in FILES_TO_WRITE.items():
        full_path = os.path.join(CPPMODE_DIR, rel_path)
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        with open(full_path, "w") as f:
            f.write(content)
        print(f"  wrote {rel_path} ({len(content)} bytes)")
    print()


# ──────────────────────────────────────────────────────────────────────
# Compile + run the 4-layer suite.
# ──────────────────────────────────────────────────────────────────────

def run(cmd, cwd=None, check=True):
    print(f"$ {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=cwd)
    if check and result.returncode != 0:
        print(f"FAILED (exit code {result.returncode}): {' '.join(cmd)}")
        sys.exit(result.returncode)
    return result.returncode


def find_java_sources(*subdirs):
    files = []
    for sub in subdirs:
        base = os.path.join(CPP_PARSER_DIR, sub)
        for root, _, names in os.walk(base):
            for name in names:
                if name.endswith(".java"):
                    files.append(os.path.join(root, name))
    return files


def compile_parser_project():
    if not os.path.isdir(os.path.join(CPP_PARSER_DIR, "src", "main")):
        print(f"ERROR: {CPP_PARSER_DIR}/src/main not found.")
        print("Is tools/cpp-parser/ actually present and complete?")
        sys.exit(1)

    sources = find_java_sources("src/main", "src/test")
    if not sources:
        print(f"ERROR: no .java files found under {CPP_PARSER_DIR}/src/")
        sys.exit(1)

    print(f"Compiling {len(sources)} Java source files...")
    os.makedirs(OUT_DIR, exist_ok=True)
    run(["javac", "-d", OUT_DIR] + sources)


def gather_real_corpus():
    if os.path.isdir(CORPUS_DIR):
        shutil.rmtree(CORPUS_DIR)
    os.makedirs(CORPUS_DIR)

    if not os.path.isdir(EXAMPLES_DIR):
        print(f"ERROR: examples directory not found at {EXAMPLES_DIR}")
        sys.exit(1)

    count = 0
    for root, _, names in os.walk(EXAMPLES_DIR):
        for name in names:
            if name.endswith(".pde"):
                shutil.copy(os.path.join(root, name), CORPUS_DIR)
                count += 1

    if count == 0:
        print(f"ERROR: no .pde files found under {EXAMPLES_DIR}")
        sys.exit(1)
    print(f"Gathered {count} real example sketches into {CORPUS_DIR}")
    return count


def has_real_headers():
    """True if libglfw3-dev/libglew-dev are installed -- checked via
    pkg-config first, falling back to a direct header existence check."""
    for pkg in ("glfw3", "glew"):
        result = subprocess.run(["pkg-config", "--exists", pkg],
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if result.returncode != 0:
            candidates = {
                "glfw3": ["/usr/include/GLFW/glfw3.h", "/usr/local/include/GLFW/glfw3.h"],
                "glew": ["/usr/include/GL/glew.h", "/usr/local/include/GL/glew.h"],
            }
            if not any(os.path.exists(p) for p in candidates[pkg]):
                return False
    return True


def run_parser_sweep_layer():
    print()
    print("=" * 70)
    print("LAYER 1: ParserCorpusSweep (parse-only, no g++ needed)")
    print("=" * 70)
    run(["java", "-cp", OUT_DIR, "cppmode.parser.ParserCorpusSweep", CORPUS_DIR],
        cwd=CPP_PARSER_DIR, check=False)


def run_stub_pipeline_layer(quick):
    print()
    print("=" * 70)
    print("LAYER 2: RealCorpusStressTest (full pipeline + g++, narrow stub)")
    print("=" * 70)
    if quick:
        print("Skipped (--quick).")
        return
    if has_real_headers():
        print("Skipped: real headers (libglfw3-dev/libglew-dev) are available,")
        print("so Layer 3 already gives the real, trustworthy signal -- this")
        print("layer's deliberately tiny, hand-written API stub only ever")
        print("covers a small slice of the real Processing API, so its g++")
        print("failures (RGB, PI, PShape, etc. \"not declared\") are EXPECTED")
        print("noise, not real problems, and just distract from Layer 3's")
        print("answer. Pass --force-layer-2 to run it anyway.")
        if "--force-layer-2" not in sys.argv:
            return
    run(["java", "-cp", OUT_DIR, "cppmode.parser.RealCorpusStressTest", CORPUS_DIR],
        cwd=CPP_PARSER_DIR, check=False)


def run_real_header_layer(quick):
    print()
    print("=" * 70)
    print("LAYER 3: RealHeaderStressTest (full pipeline + g++, REAL Processing.h)")
    print("=" * 70)
    if quick:
        print("Skipped (--quick).")
        return
    if not has_real_headers():
        print("Skipped: libglfw3-dev and/or libglew-dev not found.")
        print("Install them to run this layer, e.g. on Debian/Ubuntu:")
        print("    sudo apt-get install libglfw3-dev libglew-dev")
        print("On Arch:")
        print("    sudo pacman -S glfw glew")
        return
    fixtures_stub = os.path.join(CPP_PARSER_DIR, "fixtures", "extracted_real_api_stub.h")
    if not os.path.exists(fixtures_stub):
        print(f"Skipped: {fixtures_stub} not found.")
        print("This file is expected alongside the other tools/cpp-parser/fixtures/.")
        return
    # cwd=CPP_PARSER_DIR is required: RealCorpusStressTest/RealHeaderStressTest
    # both read "fixtures/extracted_real_api_stub.h" as a path relative to
    # the JVM's working directory, not relative to this script's location.
    run(["java", "-cp", OUT_DIR, "cppmode.parser.RealHeaderStressTest", CORPUS_DIR, SRC_DIR],
        cwd=CPP_PARSER_DIR, check=False)


def run_new_user_edge_cases_layer(quick):
    print()
    print("=" * 70)
    print("LAYER 4: new-user edge cases (blank template, partial sketches)")
    print("=" * 70)
    edge_cases_dir = os.path.join(CPP_PARSER_DIR, "fixtures", "new-user-edge-cases")
    if not os.path.isdir(edge_cases_dir):
        print(f"Skipped: {edge_cases_dir} not found.")
        return
    if quick:
        print("Skipped (--quick) -- this layer also needs real headers.")
        return
    if not has_real_headers():
        print("Skipped: libglfw3-dev and/or libglew-dev not found (same requirement as Layer 3).")
        return
    run(["java", "-cp", OUT_DIR, "cppmode.parser.RealHeaderStressTest", edge_cases_dir, SRC_DIR],
        cwd=CPP_PARSER_DIR, check=False)


def run_stress_cases_layer():
    """
    Layer 5: the ever-growing adversarial stress-cases/ folder.
    Pure parser + codegen, no g++ and no real headers needed, so this
    always runs regardless of --quick or GLFW3/GLEW availability -- it's
    cheap and should never be skipped.

    Each .cpp file under tools/cpp-parser/stress-cases/ declares its own
    expectation via a "// EXPECT: PASS" or "// EXPECT: FAIL" header
    comment. PASS files that fail are real regressions (nonzero exit).
    FAIL files that start passing are reported but not treated as
    failures -- just a sign that file's header may be worth updating.

    To add a new adversarial case: drop a new .cpp file in
    tools/cpp-parser/stress-cases/ with an EXPECT header and a short
    explanation of what it's probing. No code changes needed -- it's
    picked up automatically next run.
    """
    print()
    print("=" * 70)
    print("LAYER 5: stress-cases (ever-growing adversarial parser fixtures)")
    print("=" * 70)
    stress_cases_dir = os.path.join(CPP_PARSER_DIR, "stress-cases")
    if not os.path.isdir(stress_cases_dir):
        print(f"Skipped: {stress_cases_dir} not found.")
        return
    run(["java", "-cp", OUT_DIR, "cppmode.parser.StressCasesRunner", stress_cases_dir],
        cwd=CPP_PARSER_DIR, check=False)


def main():
    quick = "--quick" in sys.argv
    no_write = "--no-write" in sys.argv

    print(f"CppMode root:      {CPPMODE_DIR}")
    print(f"cpp-parser at:     {CPP_PARSER_DIR}")
    print(f"examples at:       {EXAMPLES_DIR}")
    print()

    if not no_write:
        write_known_good_files()

    compile_parser_project()
    gather_real_corpus()

    run_parser_sweep_layer()
    run_stub_pipeline_layer(quick)
    run_real_header_layer(quick)
    run_new_user_edge_cases_layer(quick)
    run_stress_cases_layer()

    print()
    print("=" * 70)
    print("Stress test run complete.")
    print("=" * 70)
    print("Read each layer's own summary above for pass/fail counts.")
    print("'PIPELINE CRASHES' is the number that matters most -- that's a")
    print("real Java exception in the pass logic, not g++ noise.")
    print("Layer 3/4's g++ fail count is the real signal -- it should be at or")
    print("near zero against the actual Processing.h.")
    print("Everything else is explained in")
    print("tools/cpp-parser/DECISION_two_parser_implementations.md.")


if __name__ == "__main__":
    main()
