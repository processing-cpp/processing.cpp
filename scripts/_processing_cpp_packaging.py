"""
_processing_cpp_packaging.py -- shared engine-file and example-program
content used by both generate_dragdrop.py and generate_cmake.py.

Not run directly. This exists so the two generator scripts don't each
carry their own copy of "which files make up the engine" or "what the
example sketches look like" -- there is exactly one place that lists
ENGINE_HEADERS/ENGINE_SOURCES and one place that defines each example
program, imported by both generators, so they can never drift apart
from each other on those specifics. Everything that's genuinely
PACKAGE-specific (the README, the build script, the CMakeLists.txt)
stays in each generator's own file, not here.
"""
import shutil
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SRC_DIR = REPO_ROOT / "src"
DIST_DIR = REPO_ROOT / "dist"

# Every one of these must exist in src/ -- if the engine's file layout
# ever changes, check_source_layout() fails loudly rather than silently
# shipping a stale/incomplete package.
#
# Notably absent: Processing_api.h and Platform.h. Both exist in the real
# CppMode source tree, but neither is included by Processing.h or
# Processing.cpp -- the engine itself never needs them. Processing_api.h
# is only ever included by CODE THE TRANSPILER GENERATES (CppBuild.java
# writes that #include into every sketch it emits, to support the
# free-function setup()/draw() style); Platform.h isn't included by
# anything in src/ at all. A hand-written PApplet subclass -- which is
# the only way either of these packages is meant to be used -- needs
# neither, confirmed by actually compiling examples/ without them before
# removing them from this list.
ENGINE_HEADERS = [
    "Processing.h",
    "stb_image.h",
    "stb_image_write.h",
    "stb_truetype.h",
]
ENGINE_SOURCES = [
    "Processing.cpp",
    "Processing_defaults.cpp",
]

# Fallback only -- get_library_version() below reads the real value from
# mode.properties at generation time. This constant exists so a missing
# or unparseable mode.properties degrades to an obviously-a-fallback
# version rather than crashing the generator outright, since shipping a
# package without a version isn't worse than a hard failure here.
_FALLBACK_VERSION = "0.0.0"


def get_library_version() -> str:
    """Reads prettyVersion from mode.properties -- the same version
    number CppMode itself reports inside the Processing IDE -- so the
    standalone packages and the IDE plugin can never silently drift to
    different version numbers from having two separately-maintained
    sources of truth. Used for the CMake package's
    write_basic_package_version_file() call, so find_package(processing_cpp 1.0)
    -style version constraints (if anyone ever writes one) check against
    a real, meaningful number instead of an arbitrary one."""
    props_path = REPO_ROOT / "mode.properties"
    try:
        for line in props_path.read_text().splitlines():
            line = line.strip()
            if line.startswith("prettyVersion="):
                value = line.split("=", 1)[1].strip()
                if value:
                    return value
    except OSError:
        pass
    print(
        f"warning: could not read prettyVersion from {props_path} -- "
        f"falling back to {_FALLBACK_VERSION}. The generated package's "
        f"version number will not reflect the real CppMode version.",
        file=sys.stderr,
    )
    return _FALLBACK_VERSION


def die(msg: str) -> None:
    print(f"error: {msg}", file=sys.stderr)
    sys.exit(1)


def check_source_layout() -> None:
    missing = [f for f in ENGINE_HEADERS + ENGINE_SOURCES if not (SRC_DIR / f).is_file()]
    if missing:
        die(
            "src/ is missing expected engine file(s): " + ", ".join(missing) + "\n"
            "This script must be run from a CppMode checkout with an intact src/ directory."
        )


def copy_engine_files(out_dir: Path) -> None:
    include_dir = out_dir / "include"
    src_out_dir = out_dir / "src"
    include_dir.mkdir(parents=True, exist_ok=True)
    src_out_dir.mkdir(parents=True, exist_ok=True)
    for name in ENGINE_HEADERS:
        shutil.copy2(SRC_DIR / name, include_dir / name)
    for name in ENGINE_SOURCES:
        shutil.copy2(SRC_DIR / name, src_out_dir / name)


def write_examples(out_dir: Path) -> None:
    examples_dir = out_dir / "examples"
    examples_dir.mkdir(parents=True, exist_ok=True)
    (examples_dir / "bouncing_ball.cpp").write_text(EXAMPLE_BOUNCING_BALL)
    embed_dir = examples_dir / "embedding"
    embed_dir.mkdir(exist_ok=True)
    (embed_dir / "app.h").write_text(EXAMPLE_EMBED_APP_H)
    (embed_dir / "app.cpp").write_text(EXAMPLE_EMBED_APP_CPP)
    (embed_dir / "main.cpp").write_text(EXAMPLE_EMBED_MAIN_CPP)


def zip_directory(src_dir: Path, zip_path: Path) -> Path:
    """Zips src_dir's contents under a top-level folder named after src_dir,
    e.g. zipping .../dist/processing-cpp-dragdrop/ produces a zip where
    every entry starts with processing-cpp-dragdrop/ -- so unzipping it
    anywhere drops a single clean folder, not a pile of loose files."""
    if zip_path.exists():
        zip_path.unlink()
    zip_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(src_dir.rglob("*")):
            if path.is_file():
                zf.write(path, arcname=src_dir.name / path.relative_to(src_dir))
    return zip_path


# =============================================================================
# Example programs -- identical content in both packages. Only the build
# instructions in each package's own README differ, which is genuinely
# package-specific, unlike the engine-usage code itself.
# =============================================================================

EXAMPLE_BOUNCING_BALL = '''\
// bouncing_ball.cpp -- a complete sketch, no Processing IDE involved.
// Build: see this package's README.md.
#include "Processing.h"

struct BouncingBall : public Processing::PApplet {
    float x = 0, y = 0;
    float vx = 180, vy = 140;
    float radius = 30;

    void settings() override {
        size(640, 360);
    }

    void setup() override {
        windowTitle("processing-cpp standalone example");
        x = width / 2.0f;
        y = height / 2.0f;
    }

    void draw() override {
        x += vx * deltaTime;
        y += vy * deltaTime;

        if (x < radius || x > width - radius)  vx = -vx;
        if (y < radius || y > height - radius) vy = -vy;

        background(20);
        noStroke();
        fill(255, 140, 0);
        circle(x, y, radius * 2);
    }

    void keyPressed() override {
        if (key == ' ') { vx = -vx; vy = -vy; }
    }
};

int main() {
    BouncingBall sketch;
    sketch.run();
    return 0;
}
'''

EXAMPLE_EMBED_APP_H = '''\
// app.h -- the "drop into an existing project" pattern: keep the PApplet
// subclass and the Processing.h include confined to app.cpp. The rest of
// your codebase only ever needs to see this plain function declaration.
#pragma once

void run_particle_view();
'''

EXAMPLE_EMBED_APP_CPP = '''\
// app.cpp -- the only file in this example that touches Processing.h.
#include "Processing.h"
#include "app.h"
#include <algorithm>
#include <vector>

using namespace Processing;

namespace {

struct Particle {
    PVector pos;
    PVector vel;
    float life = 1.0f;

    void draw() {
        fill(255, 200, 60, life * 255);
        circle(pos.x, pos.y, 8);
    }
};

struct ParticleView : public PApplet {
    std::vector<Particle> particles;

    void settings() override { size(800, 500); }
    void setup()    override { windowTitle("processing-cpp -- embedded in an existing project"); }

    void spawn(float x, float y) {
        Particle p;
        p.pos = PVector(x, y);
        p.vel = PVector::random2D() * random(60.0f, 200.0f);
        particles.push_back(p);
    }

    void draw() override {
        if (isMousePressed()) spawn(mouseX, mouseY);

        background(0);
        noStroke();
        for (auto& p : particles) {
            p.pos += p.vel * deltaTime;
            p.life -= deltaTime * 0.6f;
            p.draw();
        }
        particles.erase(
            std::remove_if(particles.begin(), particles.end(),
                            [](const Particle& p) { return p.life <= 0.0f; }),
            particles.end());
    }
};

} // namespace

void run_particle_view() {
    ParticleView view;
    view.run();
}
'''

EXAMPLE_EMBED_MAIN_CPP = '''\
// main.cpp -- knows nothing about Processing, PApplet, GLFW, or GLEW.
#include "app.h"

int main() {
    run_particle_view();
    return 0;
}
'''
