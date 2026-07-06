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
    (out_dir / "examples" / "CMakeLists.txt").write_text(EXAMPLES_CMAKELISTS_TXT)

    # The version baked into project(processing_cpp VERSION ...) -- and
    # from there, automatically, into PROJECT_VERSION, which
    # write_basic_package_version_file() reads further down in
    # CMAKELISTS_TXT. Pulled from mode.properties (the same version
    # CppMode itself reports) rather than a separately hardcoded number,
    # so this package's version can't silently drift from the real one.
    version = get_library_version()
    cmakelists = CMAKELISTS_TXT.replace("__PROCESSING_CPP_VERSION__", version) + (
        "\noption(PROCESSING_CPP_BUILD_EXAMPLES \"Build examples/\" ON)\n"
        "if(PROCESSING_CPP_BUILD_EXAMPLES)\n"
        "    add_subdirectory(examples)\n"
        "endif()\n"
    )
    (out_dir / "CMakeLists.txt").write_text(cmakelists)
    (out_dir / "processing_cpp-config.cmake.in").write_text(PACKAGE_CONFIG_CMAKE_IN)
    (out_dir / "README.md").write_text(README_MD)

    # Covers the build/ directory someone gets if they configure/build
    # this package standalone (the README's own "Examples" section tells
    # them to run cmake -S . -B build right here) -- without this, that
    # directory is one `git add .` away from landing inside whatever repo
    # this folder gets vendored into.
    (out_dir / ".gitignore").write_text(CMAKE_GITIGNORE)

    shutil.copy2(REPO_ROOT / "LICENSE", out_dir / "LICENSE")

    print(f"Generated CMake package: {out_dir}")
    print(f"  version {version}")
    print(f"  include/ ({len(ENGINE_HEADERS)} headers), "
          f"src/ ({len(ENGINE_SOURCES)} engine source files), "
          f"CMakeLists.txt, processing_cpp-config.cmake.in, README.md, .gitignore, examples/, LICENSE")


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
page you got this from, or check the project's repository. That one
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
    GIT_REPOSITORY <url of your processing-cpp git repo>
    GIT_TAG main
)
FetchContent_MakeAvailable(processing_cpp)

add_executable(my_sketch main.cpp)
target_link_libraries(my_sketch PRIVATE processing_cpp)
```

This only works once this package's contents live in a git repo somewhere
reachable by URL -- FetchContent clones it the same way `git clone` would.
If this folder hasn't been pushed anywhere yet, use one of the other two
options below instead in the meantime.

### Installed system-wide

```sh
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j
sudo cmake --install build
```

```cmake
find_package(processing_cpp REQUIRED)
target_link_libraries(my_sketch PRIVATE processing_cpp::processing_cpp)
```

Note the `processing_cpp::` prefix here -- it's different from the other
two methods above, which both use the bare `processing_cpp` name. That's
intentional, not an inconsistency to work around: CMake convention
namespaces targets that come from an installed, `find_package`-located
package specifically so they can't collide with some other unrelated
package's target of the same name on your system; `add_subdirectory` and
`FetchContent` build the target directly in your own project, where that
collision risk doesn't apply, so the plain name is fine there.

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

If you'd rather not install the prebuilt packages above, configure with
`-DPROCESSING_CPP_FETCH_DEPS=ON` and CMake will build GLFW and GLEW from
source automatically as part of your build. This is off by default
because building two extra libraries from source on someone's first
`cmake configure` can be a surprising thing to have happen silently.

Worth knowing before relying on this: building GLFW from source still
needs *something* installed on Linux -- either the X11 development
headers (e.g. Ubuntu/Debian's `xorg-dev` package, which pulls in
`libxrandr-dev`, `libxinerama-dev`, `libxcursor-dev`, `libxi-dev`) or, if
you set `-DGLFW_BUILD_WAYLAND=ON` yourself, the Wayland equivalents
(`libwayland-dev`, `libxkbcommon-dev`, `wayland-protocols`). This
`CMakeLists.txt` builds for X11 by default when fetching from source,
since it's the more universally available baseline on Linux, but X11's
own dev headers are still a real, separate thing to have installed --
`PROCESSING_CPP_FETCH_DEPS=ON` fetches GLFW's *source*, not a magic
zero-dependency build. If you hit a build error inside `_deps/glfw3_fetched-src`,
installing the prebuilt `libglfw3-dev`/`libglew-dev` packages directly
(above) is simpler than chasing down GLFW's own build dependencies.

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

## License

The engine is licensed under the **GNU Lesser General Public License
v2.1** (see `LICENSE`). LGPL is meant to allow linking from proprietary
software, unlike plain GPL, but it does come with real obligations --
notably around static linking. `processing_cpp` is a `STATIC` library in
this `CMakeLists.txt`, meaning it gets compiled into your sketch's
binary directly rather than loaded as a separate shared library. LGPL
2.1 requires that anyone you distribute that binary to be able to relink
it against a modified version of the engine -- in practice, that means
making the engine's object files (or this source) available alongside
your binary, not just the binary itself. Building `processing_cpp` as a
`SHARED` library instead would change this; that's not how this
`CMakeLists.txt` is currently set up, but it's a legitimate way to avoid
the static-linking obligation if it matters for your project.

This isn't legal advice, and the specifics depend on how you're
distributing your project -- read `LICENSE` itself, and talk to an actual
lawyer if it matters for what you're shipping. It's flagged here mainly
so it doesn't come as a surprise after the fact.

`include/stb_image.h`, `stb_image_write.h`, and `stb_truetype.h` are
bundled third-party libraries (by Sean Barrett and contributors), not
part of the engine -- they're each dual-licensed under MIT or public
domain (your choice), which is unrestricted enough that it doesn't add
anything beyond what's already true of the LGPL 2.1 engine itself. Their
full license text is included at the bottom of each of those files.

## Where this comes from

This release is built by `scripts/generate_cmake.py` in the main CppMode
repo, and it's generated directly from that repo's real engine source
(`src/Processing.h`, `src/Processing.cpp`) -- the very same code CppMode's
Processing IDE plugin compiles your sketches against.
'''

CMAKELISTS_TXT = '''\
cmake_minimum_required(VERSION 3.16)
project(processing_cpp VERSION __PROCESSING_CPP_VERSION__ LANGUAGES CXX)

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

# Needed early -- CMAKE_INSTALL_INCLUDEDIR (used a few lines down, in
# the INSTALL_INTERFACE include path) only exists after this is included.
# The actual install() calls that need it are further below; this just
# has to come before the first place that variable gets read.
include(GNUInstallDirs)

add_library(processing_cpp STATIC
    src/Processing.cpp
    src/Processing_defaults.cpp
)

target_include_directories(processing_cpp PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}/processing_cpp>
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
        if(UNIX AND NOT APPLE)
            # Building GLFW from source pulls in its own build-time
            # dependency on wayland-scanner if Wayland support is left
            # on -- not something every machine has installed, and not
            # something this script can silently install for you the
            # way it can FetchContent a git repo. X11 is the more
            # universally available baseline on Linux, so that's what
            # gets built when fetching from source; a machine with
            # GLFW/GLEW already installed via find_package/pkg-config
            # never hits this at all; it only applies in the fallback.
            set(GLFW_BUILD_WAYLAND OFF CACHE BOOL "" FORCE)
            set(GLFW_BUILD_X11 ON CACHE BOOL "" FORCE)
        endif()
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

# -----------------------------------------------------------------------------
# install + export, so `cmake --install` followed by `find_package(processing_cpp)`
# from a separate project actually works -- this is what the README's
# "Installed system-wide" section depends on. GNUInstallDirs is already
# included near the top of this file (CMAKE_INSTALL_INCLUDEDIR is needed
# there, before this point).
# -----------------------------------------------------------------------------
include(CMakePackageConfigHelpers)

# Only install/export processing_cpp's own artifacts when this
# CMakeLists.txt is the top-level project being configured directly --
# i.e. someone built/installed this package on its own, the scenario the
# README's "Installed system-wide" section describes. When this folder
# is pulled in via add_subdirectory() from a parent project instead, skip
# all of this: without the guard, the PARENT project's own
# `cmake --install` would also silently install processing_cpp's headers
# and library into the parent's install tree, alongside (and easily
# mistaken for part of) the parent's own install output -- confirmed by
# actually vendoring this package and running cmake --install on the
# parent without the guard before adding it.
#
# CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR is the portable way
# to detect this on CMake 3.16 (the minimum this file declares);
# PROJECT_IS_TOP_LEVEL only exists from CMake 3.21 onward.
if(CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR)
    install(TARGETS processing_cpp
        EXPORT processing_cpp-targets
        LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
        ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
        RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
    )
    install(DIRECTORY include/ DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/processing_cpp)
    install(EXPORT processing_cpp-targets
        FILE processing_cpp-targets.cmake
        NAMESPACE processing_cpp::
        DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/processing_cpp
    )

    configure_package_config_file(
        "${CMAKE_CURRENT_SOURCE_DIR}/processing_cpp-config.cmake.in"
        "${CMAKE_CURRENT_BINARY_DIR}/processing_cpp-config.cmake"
        INSTALL_DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/processing_cpp
    )
    write_basic_package_version_file(
        "${CMAKE_CURRENT_BINARY_DIR}/processing_cpp-config-version.cmake"
        VERSION ${PROJECT_VERSION}
        COMPATIBILITY SameMajorVersion
    )
    install(FILES
        "${CMAKE_CURRENT_BINARY_DIR}/processing_cpp-config.cmake"
        "${CMAKE_CURRENT_BINARY_DIR}/processing_cpp-config-version.cmake"
        DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/processing_cpp
    )
endif()

# find_package(processing_cpp) resolves to processing_cpp::processing_cpp
# (the namespaced form CMake convention expects), as well as the plain
# processing_cpp target also used by add_subdirectory/FetchContent --
# both names point at the same library, so the README's
# target_link_libraries(... processing_cpp) line works regardless of
# which of the three installation methods was used.
add_library(processing_cpp::processing_cpp ALIAS processing_cpp)
'''

PACKAGE_CONFIG_CMAKE_IN = '''\
@PACKAGE_INIT@

include(CMakeFindDependencyMacro)
find_dependency(OpenGL)

include("${CMAKE_CURRENT_LIST_DIR}/processing_cpp-targets.cmake")

check_required_components(processing_cpp)
'''

EXAMPLES_CMAKELISTS_TXT = '''\
add_executable(bouncing_ball bouncing_ball.cpp)
target_link_libraries(bouncing_ball PRIVATE processing_cpp)

add_executable(embedded_sketch embedding/main.cpp embedding/app.cpp)
target_link_libraries(embedded_sketch PRIVATE processing_cpp)
'''

CMAKE_GITIGNORE = '''\
# Generated by CMake when this package is configured/built standalone
# (e.g. by following the "Examples" section of this README directly
# inside this folder). Safe to delete; cmake -S . -B build recreates it.
build/
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
