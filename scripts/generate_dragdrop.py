#!/usr/bin/env python3
"""
generate_dragdrop.py -- builds the drag-and-drop release of processing-cpp:
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
    scripts/generate_dragdrop.py                # writes dist/processing-cpp-dragdrop.zip
    scripts/generate_dragdrop.py --no-zip        # leave the folder unzipped, for inspection
    scripts/generate_dragdrop.py --out PATH      # write the zip somewhere else

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

    # VS Code task. This can't be shipped directly as .vscode/tasks.json
    # inside this folder -- VS Code only reads .vscode/ at the workspace
    # ROOT, which is one level up (wherever the user's own main.cpp lives),
    # not inside processing-cpp/ itself. So it ships here, with the README
    # telling the user to copy this one file up to their own .vscode/.
    vscode_dir = out_dir / "vscode-task"
    vscode_dir.mkdir(exist_ok=True)
    (vscode_dir / "tasks.json").write_text(VSCODE_TASKS_JSON)

    shutil.copy2(REPO_ROOT / "LICENSE", out_dir / "LICENSE")

    print(f"Generated drag-and-drop package: {out_dir}")
    print(f"  include/ ({len(ENGINE_HEADERS)} headers), "
          f"src/ ({len(ENGINE_SOURCES)} engine source files), "
          f"README.md, run.sh, run.bat, vscode-task/, examples/, LICENSE")


# =============================================================================
# README.md -- written for a human who just unzipped this folder and has
# never seen processing-cpp before. Leads with the one command that
# matters (./run.sh); everything else is here for when they want more.
# =============================================================================

README_MD = '''\
# processing-cpp (drag-and-drop)

This is [Processing](https://processing.org)'s API -- `size()`, `ellipse()`,
`mouseX`, `draw()`, and the rest of it -- implemented natively in C++.
There's no Processing IDE involved, no `.pde` files, no transpiler, and no
build system to set up. You unzip this folder, write a `.cpp` file next to
it, and run one script.

If you're already building your project with CMake, you probably want the
separate CMake release instead -- look for `processing-cpp-cmake.zip` on
the same page you got this from, or in the
[CppMode repo](https://github.com/processing-cpp/processing.cpp). This
folder doesn't need CMake at all, and isn't meant to be used with it.

## Quick start

1. Unzip this folder into your project, however you like -- as
   `processing-cpp/` sitting next to your own code is the usual way.
2. Write a sketch (see below, or copy `examples/bouncing_ball.cpp` to get
   started).
3. Run it:

   ```sh
   ./processing-cpp/run.sh
   ```

That's the entire workflow. `run.sh` finds your `.cpp` file, compiles it
against the engine, links it, and runs the result, in one step. The first
time you run it, it also compiles the engine itself, which takes about
10-15 seconds; every run after that reuses the compiled engine and only
has to rebuild your own file, so it's fast.

On Windows, run `run.bat` the same way (from an MSYS2 shell, or by
double-clicking it).

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
[Processing reference](https://processing.org/reference) -- `background()`,
`fill()`, `circle()`, `mouseX`, `width`, `height` -- is available as a
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
else, just list them:

```sh
./processing-cpp/run.sh main.cpp app.cpp
```

## What's actually happening under the hood

`run.sh` isn't doing anything magic -- it runs the same command you'd type
by hand, with the right flags already filled in for your operating system:

```sh
g++ -std=c++17 -I include main.cpp \\
    src/Processing.cpp src/Processing_defaults.cpp \\
    -DPROCESSING_HAS_STB_IMAGE -DPROCESSING_HAS_STB_TRUETYPE \\
    -lglfw -lGLEW -lGL -lGLU -lm -pthread \\
    -o my_sketch && ./my_sketch
```

On macOS, the link line is
`-lglfw -lGLEW -framework OpenGL -framework Cocoa -framework IOKit -framework CoreVideo`
instead. On Windows with MSYS2, it's
`-lglfw3 -lglew32 -lopengl32 -lglu32 -lcomdlg32 -lshell32 -lole32 -luuid -mwindows -pthread -D_USE_MATH_DEFINES`.
This is mostly useful if you want to wire processing-cpp into your own
Makefile, editor build task, or something other than `run.sh` -- you don't
need to read this section to just use the library.

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
  little more in it -- an orange ball bouncing around the window, space
  bar reverses it. Try it with:

  ```sh
  ./processing-cpp/run.sh processing-cpp/examples/bouncing_ball.cpp
  ```

- **`examples/embedding/`** shows how to drop a sketch into a project
  that already exists, without the rest of that project ever needing to
  know GLFW, GLEW, or `PApplet` exist. The `PApplet` subclass and the
  `#include "Processing.h"` live only inside `app.cpp`; `main.cpp` -- and
  by extension the rest of a real project -- only sees `app.h`'s plain
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
already do -- it just calls `run.sh` (or `run.bat` on Windows) for you,
so there's nothing here that can drift out of sync with the plain
command-line instructions above.

## Where this comes from

This release is built by `scripts/generate_dragdrop.py` in the main
[CppMode repo](https://github.com/processing-cpp/processing.cpp), and it's
generated directly from that repo's real engine source
(`src/Processing.h`, `src/Processing.cpp`) -- the very same code CppMode's
Processing IDE plugin compiles your sketches against. If you got this
folder somewhere other than that repo, it's a snapshot of the engine at
some point in time; check the repo for anything newer.

If you're curious how this relates to writing a sketch inside the actual
Processing IDE with CppMode installed: the IDE's transpiler takes a
Processing-style sketch (free-standing `setup()` and `draw()` functions,
no class) and mechanically rewrites it into exactly the
`struct Sketch : public PApplet { ... }; sketch.run();` shape shown above.
There's no hidden behavior in that translation -- writing that shape
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
# The engine itself is compiled once and cached in processing-cpp/lib/ --
# only your own file(s) get recompiled on later runs, unless the engine
# source in this folder changes (e.g. you updated the package).
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

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
        ;;
    Linux)
        GL_LIBS=(-lglfw -lGLEW -lGL -lGLU -lm -pthread)
        ;;
    *)
        echo "error: unrecognized OS '$OS' -- on Windows, use run.bat instead" >&2
        exit 1
        ;;
esac

ENGINE_DIR="$SCRIPT_DIR"
LIB_DIR="$ENGINE_DIR/lib"
LIB_A="$LIB_DIR/libprocessing_cpp.a"
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
    g++ -std=c++17 -O2 -c -I"$ENGINE_DIR/include" \\
        -DPROCESSING_HAS_STB_IMAGE -DPROCESSING_HAS_STB_TRUETYPE \\
        "$ENGINE_DIR/src/Processing.cpp" -o "$LIB_DIR/Processing.o"
    g++ -std=c++17 -O2 -c -I"$ENGINE_DIR/include" \\
        -DPROCESSING_HAS_STB_IMAGE -DPROCESSING_HAS_STB_TRUETYPE \\
        "$ENGINE_DIR/src/Processing_defaults.cpp" -o "$LIB_DIR/Processing_defaults.o"
    ar rcs "$LIB_A" "$LIB_DIR/Processing.o" "$LIB_DIR/Processing_defaults.o"
    rm -f "$LIB_DIR/Processing.o" "$LIB_DIR/Processing_defaults.o"
fi

OUT="$PROJECT_DIR/.processing-cpp-build"
g++ -std=c++17 -I"$ENGINE_DIR/include" "${SOURCES[@]}" \\
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
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%\\.."
set "PROJECT_DIR=%CD%"

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

set "ENGINE_DIR=%SCRIPT_DIR%"
set "LIB_DIR=%ENGINE_DIR%lib"
set "LIB_A=%LIB_DIR%\\libprocessing_cpp.a"
if not exist "%LIB_DIR%" mkdir "%LIB_DIR%"

set NEED_ENGINE_BUILD=0
if not exist "%LIB_A%" set NEED_ENGINE_BUILD=1

if %NEED_ENGINE_BUILD%==1 (
    echo Compiling engine ^(first run; ~10-15s^)...
    g++ -std=c++17 -O2 -c -I"%ENGINE_DIR%include" -DPROCESSING_HAS_STB_IMAGE -DPROCESSING_HAS_STB_TRUETYPE -D_USE_MATH_DEFINES "%ENGINE_DIR%src\\Processing.cpp" -o "%LIB_DIR%\\Processing.o"
    if errorlevel 1 exit /b 1
    g++ -std=c++17 -O2 -c -I"%ENGINE_DIR%include" -DPROCESSING_HAS_STB_IMAGE -DPROCESSING_HAS_STB_TRUETYPE -D_USE_MATH_DEFINES "%ENGINE_DIR%src\\Processing_defaults.cpp" -o "%LIB_DIR%\\Processing_defaults.o"
    if errorlevel 1 exit /b 1
    ar rcs "%LIB_A%" "%LIB_DIR%\\Processing.o" "%LIB_DIR%\\Processing_defaults.o"
    del "%LIB_DIR%\\Processing.o" "%LIB_DIR%\\Processing_defaults.o"
)

g++ -std=c++17 -I"%ENGINE_DIR%include" %SOURCES% -L"%LIB_DIR%" -lprocessing_cpp -lglfw3 -lglew32 -lopengl32 -lglu32 -lcomdlg32 -lshell32 -lole32 -luuid -mwindows -pthread -D_USE_MATH_DEFINES -o "%PROJECT_DIR%\\.processing-cpp-build.exe"
if errorlevel 1 exit /b 1

echo Running...
"%PROJECT_DIR%\\.processing-cpp-build.exe"
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
             "Default: dist/processing-cpp-dragdrop.zip"
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
        folder = DIST_DIR / "processing-cpp-dragdrop"
        zip_path = DIST_DIR / "processing-cpp-dragdrop.zip"

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
