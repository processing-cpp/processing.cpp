#include "Processing.h"
#include <cstdlib>
#include <string>

// Forward declare so we can set it before run()
// It's defined in Processing.cpp at global scope
namespace Processing { extern std::string g_sketchName; }

#ifdef _WIN32
#include <windows.h>
int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int) {
#ifdef PROCESSING_SKETCH_NAME
    Processing::g_sketchName = PROCESSING_SKETCH_NAME;
#endif
    Processing::run();
    return 0;
}
#else
int main(int argc, char** argv) {
#ifdef PROCESSING_SKETCH_NAME
    Processing::g_sketchName = PROCESSING_SKETCH_NAME;
#endif
    for (int i = 1; i < argc; i++)
        if (std::string(argv[i]) == "--debug")
            Processing::enableDebugConsole();
    Processing::run();
    return 0;
}
#endif
