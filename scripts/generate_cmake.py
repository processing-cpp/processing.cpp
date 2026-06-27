#!/usr/bin/env python3
"""
generate_cmake.py -- builds the CMake release of processing-cpp: a zip
containing the engine packaged as a normal CMake target, plus a README
covering find_package, add_subdirectory, and FetchContent. No g++
instructions anywhere in this release -- that's a separate audience,
covered by generate_dragdrop.py instead.

Pulls the engine straight from this repo's real src/ (the same
Processing.h/Processing.cpp that CppMode's IDE plugin compiles sketches
against) -- there's no separate hand-maintained copy of the engine
anywhere. Run this again any time src/ changes and the release is back
in sync.

Usage:
    scripts/generate_cmake.py               # writes dist/processing-cpp-cmake.zip
    scripts/generate_cmake.py --no-zip       # leave the folder unzipped, for inspection
    scripts/generate_cmake.py --out PATH     # write the zip somewhere else

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
    (out_dir / "examples" / "CMakeLists.txt").write_text(EXAMPLES_CMAKELISTS_TXT)

    cmakelists = CMAKELISTS_TXT + (
        "\noption(PROCESSING_CPP_BUILD_EXAMPLES \"Build examples/\" ON)\n"
        "if(PROCESSING_CPP_BUILD_EXAMPLES)\n"
        "    add_subdirectory(examples)\n"
        "endif()\n"
    )
    (out_dir / "CMakeLists.txt").write_text(cmakelists)
    (out_dir / "README.md").write_text(README_MD)

    shutil.copy2(REPO_ROOT / "LICENSE", out_dir / "LICENSE")

    print(f"Generated CMake package: {out_dir}")
    print(f"  include/ ({len(ENGINE_HEADERS)} headers), "
          f"src/ ({len(ENGINE_SOURCES)} engine source files), "
          f"CMakeLists.txt, README.md, examples/, LICENSE")


# =============================================================================
# README.md -- written for a human who already knows CMake and wants to
# know exactly which of the three ways to depend on this fits their setup.
# =============================================================================

README_MD = '''\
# processing-cpp (CMake package)

This is [Processing](https://processing.org)'s API -- `size()`, `ellipse()`,
`mouseX`, `draw()`, and the rest of it -- implemented natively in C++,
packaged as a normal CMake target. There's no Processing IDE involved, no
`.pde` files, and no transpiler.

If you're not using CMake, there's a separate drag-and-drop release built
for that instead -- look for `processing-cpp-dragdrop.zip` on the same
page you got this from, or in the
[CppMode repo](https://github.com/processing-cpp/processing.cpp). That one
needs no build system at all; this one assumes you already have CMake.

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

You inherit from `PApplet`, override whichever lifecycle methods you need,
and call `.run()` in `main()`. Everything in the
[Processing reference](https://processing.org/reference) is available as
a member you inherit, so it's unqualified inside your overrides -- no
`Processing::` prefix needed there. A helper class that *isn't* a
`PApplet` (e.g. a `Particle`) needs one `using namespace Processing;` near
the top of its own file to call these -- `examples/embedding/` shows this.

## Adding it to your project

Pick whichever of these matches how you manage dependencies. All three
end up giving you the same `processing_cpp` CMake target, with
GLFW/GLEW/OpenGL discovery and every platform-specific link flag
(`-framework OpenGL` on macOS, `-mwindows` on Windows, and so on) already
attached to it -- so the rest of your `CMakeLists.txt` doesn't need to
know any of that exists.

### Vendored: copy this folder into your repo

```cmake
add_subdirectory(processing-cpp)
target_link_libraries(my_sketch PRIVATE processing_cpp)
```

### FetchContent: no copying, CMake fetches it for you

```cmake
include(FetchContent)
FetchContent_Declare(processing_cpp
    GIT_REPOSITORY https://github.com/processing-cpp/processing-cpp-lib.git
    GIT_TAG main
)
FetchContent_MakeAvailable(processing_cpp)

add_executable(my_sketch main.cpp)
target_link_libraries(my_sketch PRIVATE processing_cpp)
```

### Installed system-wide

```sh
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j
sudo cmake --install build
```

```cmake
find_package(processing_cpp REQUIRED)
target_link_libraries(my_sketch PRIVATE processing_cpp)
```

## Dependencies

GLFW3, GLEW, OpenGL, and a C++17 compiler. CMake looks for them with
`find_package`/`pkg-config` first, so if you already have them installed,
nothing else happens:

```sh
# Ubuntu / Debian
sudo apt install libglfw3-dev libglew-dev

# Arch
sudo pacman -S glfw glew

# macOS (Homebrew)
brew install glfw glew

# Windows, via MSYS2
pacman -S mingw-w64-x86_64-glfw mingw-w64-x86_64-glew
```

If you'd rather not install them yourself, configure with
`-DPROCESSING_CPP_FETCH_DEPS=ON` and CMake will build GLFW and GLEW from
source automatically as part of your build. This is off by default
because building two extra libraries from source on someone's first
`cmake configure` can be a surprising thing to have happen silently.

## The two examples included here

```sh
cmake -S . -B build
cmake --build build -j
./build/examples/bouncing_ball
```

- **`examples/bouncing_ball.cpp`** is the sketch shown above, with a
  little more in it -- an orange ball bouncing around the window.
- **`examples/embedding/`** shows how to drop a sketch into a project
  that already exists, without the rest of that project ever needing to
  know GLFW, GLEW, or `PApplet` exist. The `PApplet` subclass and the
  `#include "Processing.h"` live only inside `app.cpp`; `main.cpp` only
  sees `app.h`'s plain `run_particle_view()` function. Built as
  `embedded_sketch` by the same `cmake --build` above.

## Where this comes from

This release is built by `scripts/generate_cmake.py` in the main
[CppMode repo](https://github.com/processing-cpp/processing.cpp), and it's
generated directly from that repo's real engine source
(`src/Processing.h`, `src/Processing.cpp`) -- the very same code CppMode's
Processing IDE plugin compiles your sketches against.
'''

CMAKELISTS_TXT = '''\
cmake_minimum_required(VERSION 3.16)
project(processing_cpp LANGUAGES CXX)

# Usage from a parent project:
#
#   add_subdirectory(processing-cpp)
#   target_link_libraries(my_sketch PRIVATE processing_cpp)
#
# processing_cpp is a normal CMake target -- GLFW/GLEW/OpenGL discovery
# and every platform-specific link flag (-framework OpenGL on macOS,
# -mwindows on Windows, etc.) are already attached to it.
#
# This file is regenerated from CppMode's real engine source by
# scripts/generate_cmake.py. Don't hand-edit a copy of this folder;
# re-run that script instead.

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(processing_cpp STATIC
    src/Processing.cpp
    src/Processing_defaults.cpp
)

target_include_directories(processing_cpp PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_compile_definitions(processing_cpp PUBLIC
    PROCESSING_HAS_STB_IMAGE
    PROCESSING_HAS_STB_TRUETYPE
)

target_compile_features(processing_cpp PUBLIC cxx_std_17)

option(PROCESSING_CPP_FETCH_DEPS "Build GLFW/GLEW from source if missing" OFF)

find_package(OpenGL REQUIRED)
find_package(glfw3 QUIET CONFIG)
find_package(GLEW QUIET CONFIG)

if(NOT glfw3_FOUND OR NOT GLEW_FOUND)
    find_package(PkgConfig QUIET)
    if(PkgConfig_FOUND)
        if(NOT glfw3_FOUND)
            pkg_check_modules(PC_GLFW QUIET glfw3)
        endif()
        if(NOT GLEW_FOUND)
            pkg_check_modules(PC_GLEW QUIET glew)
        endif()
    endif()
endif()

set(_have_glfw FALSE)
set(_have_glew FALSE)

if(glfw3_FOUND)
    target_link_libraries(processing_cpp PUBLIC glfw)
    set(_have_glfw TRUE)
elseif(PC_GLFW_FOUND)
    target_link_libraries(processing_cpp PUBLIC ${PC_GLFW_LIBRARIES})
    target_include_directories(processing_cpp PUBLIC ${PC_GLFW_INCLUDE_DIRS})
    target_link_directories(processing_cpp PUBLIC ${PC_GLFW_LIBRARY_DIRS})
    set(_have_glfw TRUE)
endif()

if(GLEW_FOUND)
    target_link_libraries(processing_cpp PUBLIC GLEW::GLEW)
    set(_have_glew TRUE)
elseif(PC_GLEW_FOUND)
    target_link_libraries(processing_cpp PUBLIC ${PC_GLEW_LIBRARIES})
    target_include_directories(processing_cpp PUBLIC ${PC_GLEW_INCLUDE_DIRS})
    target_link_directories(processing_cpp PUBLIC ${PC_GLEW_LIBRARY_DIRS})
    set(_have_glew TRUE)
endif()

if((NOT _have_glfw OR NOT _have_glew) AND PROCESSING_CPP_FETCH_DEPS)
    include(FetchContent)
    if(NOT _have_glfw)
        set(GLFW_BUILD_DOCS OFF CACHE BOOL "" FORCE)
        set(GLFW_BUILD_TESTS OFF CACHE BOOL "" FORCE)
        set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
        FetchContent_Declare(glfw3_fetched
            GIT_REPOSITORY https://github.com/glfw/glfw.git GIT_TAG 3.4 GIT_SHALLOW TRUE)
        FetchContent_MakeAvailable(glfw3_fetched)
        target_link_libraries(processing_cpp PUBLIC glfw)
        set(_have_glfw TRUE)
    endif()
    if(NOT _have_glew)
        FetchContent_Declare(glew_fetched
            GIT_REPOSITORY https://github.com/Perlmint/glew-cmake.git
            GIT_TAG glew-cmake-2.2.0 GIT_SHALLOW TRUE)
        set(glew-cmake_BUILD_SHARED OFF CACHE BOOL "" FORCE)
        FetchContent_MakeAvailable(glew_fetched)
        target_link_libraries(processing_cpp PUBLIC libglew_static)
        set(_have_glew TRUE)
    endif()
endif()

if(NOT _have_glfw OR NOT _have_glew)
    message(FATAL_ERROR
        "processing_cpp: GLFW and/or GLEW not found.\\n"
        "  Ubuntu/Debian : sudo apt install libglfw3-dev libglew-dev\\n"
        "  Arch          : sudo pacman -S glfw glew\\n"
        "  macOS         : brew install glfw glew\\n"
        "  Windows(MSYS2): pacman -S mingw-w64-x86_64-glfw mingw-w64-x86_64-glew\\n"
        "Or configure with -DPROCESSING_CPP_FETCH_DEPS=ON to build them from source."
    )
endif()

target_link_libraries(processing_cpp PUBLIC OpenGL::GL)

if(WIN32)
    target_link_libraries(processing_cpp PUBLIC opengl32 glu32 comdlg32 shell32 ole32 uuid)
    target_compile_definitions(processing_cpp PUBLIC _USE_MATH_DEFINES)
elseif(APPLE)
    target_link_libraries(processing_cpp PUBLIC
        "-framework OpenGL" "-framework Cocoa" "-framework IOKit" "-framework CoreVideo")
else()
    find_library(GLU_LIBRARY GLU)
    if(GLU_LIBRARY)
        target_link_libraries(processing_cpp PUBLIC ${GLU_LIBRARY})
    endif()
    target_link_libraries(processing_cpp PUBLIC m pthread)
endif()

if(WIN32 AND NOT MSVC)
    target_link_options(processing_cpp PUBLIC -mwindows)
endif()
'''

EXAMPLES_CMAKELISTS_TXT = '''\
add_executable(bouncing_ball bouncing_ball.cpp)
target_link_libraries(bouncing_ball PRIVATE processing_cpp)

add_executable(embedded_sketch embedding/main.cpp embedding/app.cpp)
target_link_libraries(embedded_sketch PRIVATE processing_cpp)
'''


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--out", type=Path, default=None,
        help="Where to write the release. A path ending in .zip writes a "
             "zip there; anything else is treated as a folder to write "
             "unzipped, as if --no-zip were also given. "
             "Default: dist/processing-cpp-cmake.zip"
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
        folder = DIST_DIR / "processing-cpp-cmake"
        zip_path = DIST_DIR / "processing-cpp-cmake.zip"

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
