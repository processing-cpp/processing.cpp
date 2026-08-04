# processing.cpp

C++ Mode for Processing. Write sketches with `setup()` and `draw()` inside the Processing IDE, or drop the engine into your own C++ project and use the same API from your own editor and build system.

[![License: LGPL v2.1](https://img.shields.io/badge/License-LGPL%20v2.1-blue.svg)](LICENSE)
[![Docs](https://img.shields.io/badge/docs-processing--cpp.github.io-orange)](https://processing-cpp.github.io)

---

## Two ways to use it

### 1. Inside the Processing IDE

If you know Processing, this is the fastest way in. Install C++ Mode through the Contribution Manager, select it from the mode dropdown, and write sketches exactly like you normally would. They compile to native C++ instead of Java, but the workflow is identical.

**Install:**

1. Open Processing
2. Click the mode dropdown (top right, says **Java** by default) → **Add Mode...**
3. Search for **C++ Mode** and click Install
4. Restart Processing, select **C++** from the mode dropdown

```cpp
void setup() {
    size(640, 360);
}

void draw() {
    background(20);
    fill(255, 140, 0);
    circle(mouseX, mouseY, 40);
}
```

### 2. As a standalone header library

Use the Processing-style API in any C++ project without opening the Processing IDE. Two packaging options:

**Drag-and-drop** — unzip, drop two files next to your source, compile:

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
}
```

```bash
g++ -std=c++17 main.cpp Processing.cpp -o sketch && ./sketch
```

**CMake** — add as a subdirectory and link:

```cmake
add_subdirectory(processing-cpp)
target_link_libraries(your_target PRIVATE processing_cpp)
```

---

## Downloads

Get the latest release from the [releases page](https://github.com/processing-cpp/processing.cpp/releases/latest):

- `processing-cpp-dragdrop.zip` — drag-and-drop version
- `processing-cpp-cmake.zip` — CMake version

---

## Requirements

### Windows

MSYS2 with MinGW 64-bit. Install once:

```bash
pacman -S --needed mingw-w64-x86_64-gcc mingw-w64-x86_64-glfw mingw-w64-x86_64-glew
```

Always build from the **MSYS2 MinGW 64-bit** terminal, not PowerShell or Command Prompt.

### macOS

Xcode command line tools and Homebrew:

```bash
xcode-select --install
brew install glfw glew
```

### Linux

```bash
# Ubuntu / Debian
sudo apt install g++ libglfw3-dev libglew-dev

# Arch
sudo pacman -S gcc glfw glew
```

---

## Documentation

Full reference and tutorials at [processing-cpp.github.io](https://processing-cpp.github.io)

- [Getting Started](https://processing-cpp.github.io/gettingstarted) — first sketch in the IDE
- [Drag-and-Drop Setup](https://processing-cpp.github.io/downloads/dragdrop-setup.html) — full Windows/macOS/Linux walkthrough
- [CMake Setup](https://processing-cpp.github.io/downloads/cmake-setup.html) — full Windows/macOS/Linux walkthrough
- [Reference](https://processing-cpp.github.io/reference) — full API reference
- [Examples](https://processing-cpp.github.io/examples) — 100+ example sketches

---

## Contributing

Contributions are welcome. Open an issue or pull request on [GitHub](https://github.com/processing-cpp/processing.cpp).

---

## License

[LGPL-2.1](LICENSE)
