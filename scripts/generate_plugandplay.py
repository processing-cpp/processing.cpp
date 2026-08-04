#!/usr/bin/env python3
"""
generate_plugandplay.py -- builds the drag-and-drop release of processing-cpp:
a zip containing the engine, two examples, and a README that only ever
shows plain g++ commands. No build system assumed.

Pulls the engine straight from this repo's real src/ (the same
Processing.h/Processing.cpp that CppMode's IDE plugin compiles sketches
against) -- there's no separate hand-maintained copy of the engine
anywhere. Run this again any time src/ changes and the release is back
in sync.

For people already using CMake, see generate_cmake.py instead -- that's
a separate release built for that audience, not a flag on this one.

Usage:
    scripts/generate_plugandplay.py                # writes dist/processing-cpp-plugandplay.zip
    scripts/generate_plugandplay.py --no-zip        # leave the folder unzipped, for inspection
    scripts/generate_plugandplay.py --out PATH      # write the zip somewhere else

dist/ is gitignored -- this is a generated release artifact, not
something to hand-edit or commit.
"""
import argparse
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _processing_cpp_packaging import (  # noqa: E402
    DIST_DIR,
    ENGINE_HEADERS,
    ENGINE_SOURCES,
    check_source_layout,
    copy_engine_files,
    get_library_version,
    write_examples,
    zip_directory,
)

REPO_ROOT = Path(__file__).resolve().parent.parent


def generate(out_dir: Path) -> None:
    check_source_layout()

    if out_dir.exists():
        shutil.rmtree(out_dir)
    copy_engine_files(out_dir)
    write_examples(out_dir)

    (out_dir / "README.md").write_text(README_MD)

    run_sh = out_dir / "run.sh"
    run_sh.write_text(RUN_SH)
    run_sh.chmod(0o755)
    (out_dir / "run.bat").write_text(RUN_BAT)

    # Covers this folder's own generated caches: lib/libprocessing_cpp.a
    # and include/Processing.h.gch. The .gch in particular is large
    # (100MB+) -- without this, anyone who runs `git add .` after their
    # first ./run.sh commits a 100MB+ binary straight into their repo
    # history. Doesn't cover .processing-cpp-build (the compiled sketch
    # binary), since that lands at the PROJECT root, one level above this
    # folder -- a .gitignore here can't reach it. The README tells the
    # user to add that line to their own project's .gitignore instead.
    (out_dir / ".gitignore").write_text(DRAGDROP_GITIGNORE)

    # VS Code task. This can't be shipped directly as .vscode/tasks.json
    # inside this folder -- VS Code only reads .vscode/ at the workspace
    # ROOT, which is one level up (wherever the user's own main.cpp lives),
    # not inside processing-cpp/ itself. So it ships here, with the README
    # telling the user to copy this one file up to their own .vscode/.
    vscode_dir = out_dir / "vscode-task"
    vscode_dir.mkdir(exist_ok=True)
    (vscode_dir / "tasks.json").write_text(VSCODE_TASKS_JSON)

    shutil.copy2(REPO_ROOT / "LICENSE", out_dir / "LICENSE")

    version = get_library_version()
    print(f"Generated plug-and-play package: {out_dir}")
    print(f"  version {version}")
    print(f"  include/ ({len(ENGINE_HEADERS)} headers), "
          f"src/ ({len(ENGINE_SOURCES)} engine source files), "
          f"README.md, run.sh, run.bat, .gitignore, vscode-task/, examples/, LICENSE")


# =============================================================================
# README.md -- written for a human who just unzipped this folder and has
# never seen processing-cpp before. Leads with the one command that
# matters (./run.sh); everything else is here for when they want more.
# =============================================================================

README_MD = '''\
# processing-cpp (plug-and-play)

This is [Processing](https://processing.org)'s API — `size()`, `ellipse()`,
`mouseX`, `draw()`, and the rest of it — implemented natively in C++.
There's no Processing IDE involved, no `.pde` files, no transpiler, and no
build system to set up. Unzip this folder, write a `.cpp` file next to
it, and run one script.

If you're already building your project with CMake, you probably want the
separate CMake release instead — look for `processing-cpp-cmake.zip` on
the same page you got this from, or check the project's repository. This
folder doesn't need CMake at all, and isn't meant to be used with it.

## Quick start

1. Unzip this folder into your project, however you like — as
   `processing-cpp/` sitting next to your own code is the usual way.
2. Write a sketch (see below, or copy `examples/bouncing_ball.cpp` to get
   started).
3. Run it:

```sh
   ./processing-cpp/run.sh
```

That's the entire workflow. `run.sh` finds your `.cpp` file, compiles it
against the engine, links it, and runs the result, in one step. The first
time you run it, it also compiles the engine itself and precompiles
`Processing.h`, which together take about 15-20 seconds. Every run after
that reuses both of those and only has to compile your own file, so it's
fast — well under a second on most machines.

On Windows, run `run.bat` the same way (from an MSYS2 shell, or by
double-clicking it).

## If your project is a git repo

`run.sh` caches its build outputs in two places: `processing-cpp/lib/`
and `processing-cpp/include/Processing.h.gch`. This folder already
includes a `.gitignore` covering both, so they won't get committed if you
run `git add processing-cpp`.

It can't cover `run.sh`'s other output, though: the compiled sketch
itself, `.processing-cpp-build`, lands one level up, at the root of
*your* project, not inside `processing-cpp/`. Add this line to your own
project's `.gitignore`:

```
.processing-cpp-build*
```

(The trailing `*` catches `.processing-cpp-build.exe` on Windows too.)

## Writing a sketch

```cpp
#include "Processing.h"

struct Sketch : public Processing::PApplet {
    void settings() override { size(640, 360); }
    void setup()    override { background(0); }
    void draw()     override {
        background(0);
        fill(255, 140, 0);
        circle(mouseX, mouseY, 40);
    }
};

int main() {
    Sketch sketch;
    sketch.run();
    return 0;
}
```

You inherit from `PApplet`, override whichever lifecycle methods you need
(`setup`, `draw`, `mousePressed`, `keyPressed`, and so on), and call
`.run()` in `main()`. Everything in the
[Processing reference](https://processing.org/reference) — `background()`,
`fill()`, `circle()`, `mouseX`, `width`, `height` — is available as a
member you inherit, so you can call it directly inside your overrides
without writing `Processing::` in front of it.

The one exception: if you write a *helper* class that isn't a `PApplet`
itself (a `Particle`, a `Boid`, anything like that) and it also wants to
call these functions, add one line near the top of that file:

```cpp
using namespace Processing;
```

`examples/embedding/` shows exactly this.

## More than one source file?

`run.sh` with no arguments picks up every `.cpp` file it finds next to
itself and builds them together, so a small multi-file sketch works with
no extra effort. If you want to be explicit, or your files live somewhere
else, list them directly:

```sh
./processing-cpp/run.sh main.cpp app.cpp
```

## What's actually happening under the hood

`run.sh` isn't doing anything magic — it runs the same command you'd type
by hand, with the right flags already filled in for your operating system:

```sh
g++ -std=c++2c -I include main.cpp \
    src/Processing.cpp src/Processing_defaults.cpp \
    -DPROCESSING_HAS_STB_IMAGE -DPROCESSING_HAS_STB_TRUETYPE \
    -lglfw -lGLEW -lGL -lGLU -lm -pthread \
    -o my_sketch && ./my_sketch
```

On macOS, the link line is
`-lglfw -lGLEW -framework OpenGL -framework Cocoa -framework IOKit -framework CoreVideo`
instead. On Windows with MSYS2, it's
`-lglfw3 -lglew32 -lopengl32 -lglu32 -lcomdlg32 -lshell32 -lole32 -luuid -mwindows -pthread -D_USE_MATH_DEFINES`.
This is mostly useful if you want to wire processing-cpp into your own
Makefile, editor build task, or something other than `run.sh` — you don't
need to read this section to just use the library.

(`run.sh` itself does a little more than this — it caches the compiled
engine and a precompiled header for `Processing.h` so repeated runs are
fast, as described above. Neither is required for a *working* build, only
for a *fast* one; the command above is enough to get a sketch running.)

## Requirements

Any recent g++ or clang with C++2c support works. All three major
platforms are supported: Linux, macOS, and Windows via MSYS2.

## Installing GLFW, GLEW, and a compiler

```sh
# Ubuntu / Debian
sudo apt install g++ libglfw3-dev libglew-dev

# Arch
sudo pacman -S gcc glfw glew

# macOS (Homebrew)
brew install glfw glew

# Windows, via MSYS2
pacman -S mingw-w64-x86_64-gcc mingw-w64-x86_64-glfw mingw-w64-x86_64-glew
```

## The two examples included here

- **`examples/bouncing_ball.cpp`** is the sketch shown above, with a
  little more in it — an orange ball bouncing around the window, space
  bar reverses it. Try it with:

```sh
  ./processing-cpp/run.sh processing-cpp/examples/bouncing_ball.cpp
```

- **`examples/embedding/`** shows how to drop a sketch into a project
  that already exists, without the rest of that project ever needing to
  know GLFW, GLEW, or `PApplet` exist. The `PApplet` subclass and the
  `#include "Processing.h"` live only inside `app.cpp`; `main.cpp` — and
  by extension the rest of a real project — only sees `app.h`'s plain
  `run_particle_view()` function. Try it with:

```sh
  ./processing-cpp/run.sh processing-cpp/examples/embedding/main.cpp processing-cpp/examples/embedding/app.cpp
```

## Using this from VS Code instead of a terminal

If you'd rather press a key than type `./processing-cpp/run.sh`, copy the
task file this package includes into your own project's `.vscode/`
folder:

```sh
mkdir -p .vscode
cp processing-cpp/vscode-task/tasks.json .vscode/tasks.json
```

After that, **Ctrl+Shift+B** (**Cmd+Shift+B** on macOS) builds and runs
your sketch. Compile errors show up in VS Code's Problems panel instead
of as raw terminal text. The task doesn't do anything `run.sh` doesn't
already do — it just calls `run.sh` (or `run.bat` on Windows) for you,
so there's nothing here that can drift out of sync with the plain
command-line instructions above.

## License

The engine is licensed under the **GNU Lesser General Public License
v2.1** (see `LICENSE`). LGPL is meant to allow linking from proprietary
software, unlike plain GPL, but it comes with real obligations, notably
around static linking, which is what `run.sh`/`run.bat` do here by
default (the engine gets compiled into `lib/libprocessing_cpp.a` and
linked directly into your sketch's binary). LGPL 2.1 requires that anyone
you distribute that binary to be able to relink it against a modified
version of the engine. In practice, that means making the engine's
object files (or this source) available alongside your binary, not just
the binary itself.

This isn't legal advice, and the specifics depend on how you're
distributing your project. Read `LICENSE` itself, and talk to an actual
lawyer if it matters for what you're shipping. It's flagged here mainly
so it doesn't come as a surprise after the fact.

`include/stb_image.h`, `stb_image_write.h`, and `stb_truetype.h` are
bundled third-party libraries (by Sean Barrett and contributors), not
part of the engine. They're each dual-licensed under MIT or public
domain (your choice), which is unrestricted enough that it doesn't add
anything beyond what's already true of the LGPL 2.1 engine itself. Their
full license text is included at the bottom of each of those files.

## Where this comes from

This release is built by `scripts/generate_dragdrop.py` in the main
CppMode repo, and it's generated directly from that repo's real engine
source (`src/Processing.h`, `src/Processing.cpp`) — the very same code
CppMode's Processing IDE plugin compiles your sketches against. If you
got this folder somewhere other than that repo, it's a snapshot of the
engine at some point in time; check the repo for anything newer.

If you're curious how this relates to writing a sketch inside the actual
Processing IDE with CppMode installed: the IDE's transpiler takes a
Processing-style sketch (free-standing `setup()` and `draw()` functions,
no class) and mechanically rewrites it into exactly the
`struct Sketch : public PApplet { ... }; sketch.run();` shape shown above.
There's no hidden behavior in that translation. Writing that shape
yourself, by hand, in a plain C++ file, produces the same program. This
release just lets you start from that shape directly, without going
through the IDE or the transpiler to get there.
'''

RUN_SH = '''\
#!/usr/bin/env bash
# Builds and runs your sketch -- the one command this whole package is
# built around. Finds your .cpp file(s) one directory up (wherever you
# dropped this processing-cpp/ folder), compiles, links, and runs.
#
# Usage:
#   ./processing-cpp/run.sh                  # auto-detects .cpp files next to this folder
#   ./processing-cpp/run.sh main.cpp app.cpp # explicit, e.g. a multi-file sketch
#
# Two things get cached here so repeated runs stay fast, both rebuilt
# automatically only when their inputs change:
#   - the engine itself, compiled once into processing-cpp/lib/
#   - a precompiled header for Processing.h, written next to it as
#     processing-cpp/include/Processing.h.gch (g++ only looks for a .gch
#     file in the SAME directory as the header it precompiles -- it
#     can't live in lib/ alongside the engine, even though that would
#     read more naturally)
# Neither cache is required for correctness -- delete processing-cpp/lib/
# and Processing.h.gch and the next run just rebuilds both from scratch.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

if ! command -v g++ >/dev/null 2>&1; then
    echo "error: g++ not found on PATH." >&2
    echo "Install a C++ compiler, then try again -- see the \\"Dependencies\\" section of processing-cpp/README.md." >&2
    exit 1
fi

# Wraps a build command so a failure gets a one-line, specific cause
# instead of just whatever raw error g++/ld printed. Every g++/ar call in
# this script goes through here -- the engine build is the first thing
# that touches GLFW/GLEW (Processing.h includes both directly), so a
# missing-dependency failure can show up there, in the PCH build, or in
# the final compile, depending on what is/isn't already cached.
run_build_step() {
    local description="$1"
    shift
    local output
    if ! output="$("$@" 2>&1)"; then
        echo "$output" >&2
        echo "" >&2
        if echo "$output" | grep -qE "GLFW/glfw3\\.h|GL/glew\\.h"; then
            echo "error: $description failed -- GLFW or GLEW headers not found." >&2
            echo "See the \\"Dependencies\\" section of processing-cpp/README.md to install them." >&2
        elif echo "$output" | grep -qE "cannot find -lglfw|cannot find -lGLEW"; then
            echo "error: $description failed -- GLFW or GLEW library not found at link time." >&2
            echo "See the \\"Dependencies\\" section of processing-cpp/README.md to install them." >&2
        else
            echo "error: $description failed. See the output above." >&2
        fi
        exit 1
    fi
}

if [ "$#" -gt 0 ]; then
    SOURCES=("$@")
else
    SOURCES=()
    while IFS= read -r -d '' f; do
        SOURCES+=("$f")
    done < <(find . -maxdepth 1 -name '*.cpp' -print0)

    if [ "${#SOURCES[@]}" -eq 0 ]; then
        echo "error: no .cpp files found in $PROJECT_DIR" >&2
        echo "Put your sketch's .cpp file next to the processing-cpp/ folder, or run:" >&2
        echo "  processing-cpp/run.sh path/to/your_file.cpp" >&2
        exit 1
    fi
fi

echo "Building: ${SOURCES[*]}"

OS="$(uname -s)"
case "$OS" in
    Darwin)
        GL_LIBS=(-lglfw -lGLEW -framework OpenGL -framework Cocoa -framework IOKit -framework CoreVideo)
        COMPILE_FLAGS=()
        ;;
    Linux)
        GL_LIBS=(-lglfw -lGLEW -lGL -lGLU -lm -pthread)
        # -pthread isn't just a link flag -- it also defines _REENTRANT
        # during compilation. The PCH build below has to see the same
        # define, or g++ rejects the cached .gch as stale and silently
        # re-parses Processing.h from source every time (caught by
        # actually building the package and checking with -Winvalid-pch
        # -- it doesn't show up as a build failure, just quietly stops
        # being fast).
        COMPILE_FLAGS=(-pthread)
        ;;
    *)
        echo "error: unrecognized OS '$OS' -- on Windows, use run.bat instead" >&2
        exit 1
        ;;
esac

# DEFINES must be IDENTICAL between the PCH build below and the final
# sketch compile further down, and so must COMPILE_FLAGS above -- g++
# only uses a .gch if every relevant flag matches the compile it's being
# considered for. A mismatch doesn't break the build, it just silently
# falls back to parsing Processing.h from source (with a -Winvalid-pch
# warning if you pass that flag to check), so the caching would quietly
# stop helping. Defined once here so the two invocations can't drift
# apart from each other.
DEFINES=(-DPROCESSING_HAS_STB_IMAGE -DPROCESSING_HAS_STB_TRUETYPE)

ENGINE_DIR="$SCRIPT_DIR"
LIB_DIR="$ENGINE_DIR/lib"
LIB_A="$LIB_DIR/libprocessing_cpp.a"
PCH_FILE="$ENGINE_DIR/include/Processing.h.gch"
mkdir -p "$LIB_DIR"

NEED_ENGINE_BUILD=0
if [ ! -f "$LIB_A" ]; then
    NEED_ENGINE_BUILD=1
else
    for src in "$ENGINE_DIR/src/Processing.cpp" "$ENGINE_DIR/src/Processing_defaults.cpp"; do
        if [ "$src" -nt "$LIB_A" ]; then
            NEED_ENGINE_BUILD=1
        fi
    done
fi

if [ "$NEED_ENGINE_BUILD" -eq 1 ]; then
    echo "Compiling engine (first run, or engine source changed; ~10-15s)..."
    run_build_step "engine compile" \\
        g++ -std=c++2c -O2 -c -I"$ENGINE_DIR/include" "${DEFINES[@]}" \\
            "$ENGINE_DIR/src/Processing.cpp" -o "$LIB_DIR/Processing.o"
    run_build_step "engine compile" \\
        g++ -std=c++2c -O2 -c -I"$ENGINE_DIR/include" "${DEFINES[@]}" \\
            "$ENGINE_DIR/src/Processing_defaults.cpp" -o "$LIB_DIR/Processing_defaults.o"
    run_build_step "engine archive" \\
        ar rcs "$LIB_A" "$LIB_DIR/Processing.o" "$LIB_DIR/Processing_defaults.o"
    rm -f "$LIB_DIR/Processing.o" "$LIB_DIR/Processing_defaults.o"
fi

NEED_PCH_BUILD=0
if [ ! -f "$PCH_FILE" ]; then
    NEED_PCH_BUILD=1
elif [ "$ENGINE_DIR/include/Processing.h" -nt "$PCH_FILE" ]; then
    NEED_PCH_BUILD=1
fi

if [ "$NEED_PCH_BUILD" -eq 1 ]; then
    echo "Precompiling Processing.h (first run, or it changed; speeds up every build after this one)..."
    run_build_step "header precompile" \\
        g++ -std=c++2c -I"$ENGINE_DIR/include" "${DEFINES[@]}" "${COMPILE_FLAGS[@]}" \\
            -x c++-header "$ENGINE_DIR/include/Processing.h" -o "$PCH_FILE"
fi

OUT="$PROJECT_DIR/.processing-cpp-build"
run_build_step "sketch compile" \\
    g++ -std=c++2c -I"$ENGINE_DIR/include" "${DEFINES[@]}" "${SOURCES[@]}" \\
        -L"$LIB_DIR" -lprocessing_cpp "${GL_LIBS[@]}" \\
        -o "$OUT"

echo "Running..."
exec "$OUT"
'''

RUN_BAT = '''\
@echo off
REM Builds and runs your sketch -- the one command this package is built
REM around. Finds your .cpp file(s) one directory up (wherever you dropped
REM this processing-cpp folder), compiles, links, and runs. Requires
REM MSYS2 (g++, ar) on PATH.
REM
REM Usage:
REM   processing-cpp\\run.bat
REM   processing-cpp\\run.bat main.cpp app.cpp
REM
REM Caches two things so repeated runs stay fast: the engine itself
REM (processing-cpp\\lib\\libprocessing_cpp.a) and a precompiled header
REM for Processing.h (processing-cpp\\include\\Processing.h.gch -- g++
REM only looks for a .gch file next to the header it precompiles, so it
REM has to live in include\\, not next to the engine in lib\\). Neither
REM is required for correctness; delete both and the next run rebuilds
REM them from scratch.
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%\\.."
set "PROJECT_DIR=%CD%"

where g++ >nul 2>nul
if errorlevel 1 (
    echo error: g++ not found on PATH.
    echo Install a C++ compiler ^(MSYS2^), then try again -- see the "Dependencies" section of processing-cpp\\README.md.
    exit /b 1
)

set "SOURCES=%*"
if "%SOURCES%"=="" (
    set "SOURCES="
    for %%f in (*.cpp) do set "SOURCES=!SOURCES! %%f"
)
if "%SOURCES%"=="" (
    echo error: no .cpp files found in %PROJECT_DIR%
    echo Put your sketch's .cpp file next to the processing-cpp folder, or run:
    echo   processing-cpp\\run.bat path\\to\\your_file.cpp
    exit /b 1
)

echo Building: %SOURCES%

REM Must match the DEFINES used for the PCH build exactly, or g++ will
REM reject the cached .gch as stale and silently fall back to parsing
REM Processing.h from source every time (with a -Winvalid-pch warning).
set "DEFINES=-DPROCESSING_HAS_STB_IMAGE -DPROCESSING_HAS_STB_TRUETYPE -D_USE_MATH_DEFINES"

set "ENGINE_DIR=%SCRIPT_DIR%"
set "LIB_DIR=%ENGINE_DIR%lib"
set "LIB_A=%LIB_DIR%\\libprocessing_cpp.a"
set "PCH_FILE=%ENGINE_DIR%include\\Processing.h.gch"
set "BUILD_LOG=%TEMP%\\processing-cpp-build-%RANDOM%.log"
if not exist "%LIB_DIR%" mkdir "%LIB_DIR%"

goto :main

REM Runs one build command, capturing its output to BUILD_LOG so it can
REM be scanned for known failure patterns if it fails. Mirrors run.sh's
REM run_build_step: same two patterns (missing GLFW/GLEW headers at
REM compile time, missing GLFW/GLEW at link time), same fallback to "see
REM the output above" for anything else. Batch has no clean equivalent
REM of bash's output="$(...)" capture, so this goes through a temp file
REM instead -- printed either way, kept only long enough to grep. This
REM routine sits ABOVE :main and is only ever reached via `call`, never
REM by execution falling into it top-down -- the `goto :main` right
REM above is what makes that safe to place here.
REM
REM Takes the step description as %1, then the rest of the line as the
REM command to run. NOTE: `shift` does not update %* in cmd.exe -- %*
REM always reflects the full original argument list, regardless of how
REM many times shift has been called. So instead of shifting past %1,
REM this captures %* up front and strips the %1 text from its front
REM (the standard, documented way to get "everything after the first
REM argument" in batch, since there is no %2-and-onward slice syntax).
:run_build_step
set "STEP_DESC=%~1"
set "REST=%*"
call set "REST=%%REST:*%1=%%"
%REST% >"%BUILD_LOG%" 2>&1
set "STEP_RESULT=%ERRORLEVEL%"
type "%BUILD_LOG%"
if not "%STEP_RESULT%"=="0" (
    echo.
    findstr /C:"GLFW/glfw3.h" /C:"GL/glew.h" "%BUILD_LOG%" >nul
    if not errorlevel 1 (
        echo error: %STEP_DESC% failed -- GLFW or GLEW headers not found.
        echo See the "Dependencies" section of processing-cpp\\README.md to install them.
    ) else (
        findstr /C:"cannot find -lglfw" /C:"cannot find -lGLEW" "%BUILD_LOG%" >nul
        if not errorlevel 1 (
            echo error: %STEP_DESC% failed -- GLFW or GLEW library not found at link time.
            echo See the "Dependencies" section of processing-cpp\\README.md to install them.
        ) else (
            echo error: %STEP_DESC% failed. See the output above.
        )
    )
    del "%BUILD_LOG%" >nul 2>nul
    exit /b 1
)
del "%BUILD_LOG%" >nul 2>nul
goto :eof

:main
set NEED_ENGINE_BUILD=0
if not exist "%LIB_A%" set NEED_ENGINE_BUILD=1

if %NEED_ENGINE_BUILD%==1 (
    echo Compiling engine ^(first run; ~10-15s^)...
    call :run_build_step "engine compile" g++ -std=c++2c -O2 -c -I"%ENGINE_DIR%include" %DEFINES% "%ENGINE_DIR%src\\Processing.cpp" -o "%LIB_DIR%\\Processing.o"
    if errorlevel 1 exit /b 1
    call :run_build_step "engine compile" g++ -std=c++2c -O2 -c -I"%ENGINE_DIR%include" %DEFINES% "%ENGINE_DIR%src\\Processing_defaults.cpp" -o "%LIB_DIR%\\Processing_defaults.o"
    if errorlevel 1 exit /b 1
    ar rcs "%LIB_A%" "%LIB_DIR%\\Processing.o" "%LIB_DIR%\\Processing_defaults.o"
    del "%LIB_DIR%\\Processing.o" "%LIB_DIR%\\Processing_defaults.o"
)

set NEED_PCH_BUILD=0
if not exist "%PCH_FILE%" set NEED_PCH_BUILD=1

if %NEED_PCH_BUILD%==1 (
    echo Precompiling Processing.h ^(first run; speeds up every build after this one^)...
    REM -pthread has to be here too, matching the final compile below --
    REM it defines _REENTRANT during compilation, not just at link time,
    REM so without it here g++ rejects this .gch as stale on every build
    REM and silently re-parses Processing.h from source instead (caught
    REM by actually building this package and checking with the
    REM -Winvalid-pch warning flag -- it's not a build failure, the
    REM caching just quietly stops working).
    call :run_build_step "header precompile" g++ -std=c++2c -I"%ENGINE_DIR%include" %DEFINES% -pthread -x c++-header "%ENGINE_DIR%include\\Processing.h" -o "%PCH_FILE%"
    if errorlevel 1 exit /b 1
)

call :run_build_step "sketch compile" g++ -std=c++2c -I"%ENGINE_DIR%include" %DEFINES% %SOURCES% -L"%LIB_DIR%" -lprocessing_cpp -lglfw3 -lglew32 -lopengl32 -lglu32 -lcomdlg32 -lshell32 -lole32 -luuid -mwindows -pthread -o "%PROJECT_DIR%\\.processing-cpp-build.exe"
if errorlevel 1 exit /b 1

echo Running...
"%PROJECT_DIR%\\.processing-cpp-build.exe"
'''


DRAGDROP_GITIGNORE = '''\
# Generated by run.sh / run.bat -- safe to delete, will be rebuilt
# automatically on the next run. Not meant to be committed: the engine
# archive is rebuilt in seconds, and the precompiled header in
# particular is 100MB+, which is a bad thing to put in git history.
lib/
include/Processing.h.gch
'''

VSCODE_TASKS_JSON = '''\
{
    // Lets you build-and-run with Ctrl+Shift+B (Cmd+Shift+B on macOS)
    // instead of typing ./processing-cpp/run.sh in a terminal.
    //
    // This file belongs in YOUR project's .vscode/ folder, not inside
    // processing-cpp/ -- VS Code only reads .vscode/tasks.json at the
    // workspace root. Copy this file there:
    //
    //   mkdir -p .vscode
    //   cp processing-cpp/vscode-task/tasks.json .vscode/tasks.json
    //
    // It just calls run.sh / run.bat, the same script the README's
    // command-line instructions use -- there's no separate build logic
    // here to keep in sync with anything.
    "version": "2.0.0",
    "tasks": [
        {
            "label": "Run processing-cpp sketch",
            "type": "shell",
            "command": "${workspaceFolder}/processing-cpp/run.sh",
            "windows": {
                "command": "${workspaceFolder}\\\\processing-cpp\\\\run.bat"
            },
            "group": {
                "kind": "build",
                "isDefault": true
            },
            "presentation": {
                "reveal": "always",
                "panel": "shared",
                "clear": true
            },
            "problemMatcher": ["$gcc"]
        }
    ]
}
'''


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--out", type=Path, default=None,
        help="Where to write the release. A directory ending in .zip writes "
             "a zip there; anything else is treated as a folder to write "
             "unzipped, as if --no-zip were also given. "
             "Default: dist/processing-cpp-plugandplay.zip"
    )
    parser.add_argument(
        "--no-zip", action="store_true",
        help="Leave the generated folder unzipped (for inspecting the output)"
    )
    args = parser.parse_args()

    if args.out and str(args.out).endswith(".zip"):
        zip_path = args.out
        folder = zip_path.parent / zip_path.stem
    elif args.out:
        folder = args.out
        zip_path = None
    else:
        folder = DIST_DIR / "processing-cpp-plugandplay"
        zip_path = DIST_DIR / "processing-cpp-plugandplay.zip"

    generate(folder)

    if args.no_zip:
        zip_path = None

    if zip_path:
        zip_directory(folder, zip_path)
        shutil.rmtree(folder)
        print(f"Wrote release: {zip_path}")
    else:
        print(f"Wrote unzipped release folder: {folder}")


if __name__ == "__main__":
    main()
