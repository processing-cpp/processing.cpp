#include "Processing.h"
#include <string>
#include <cstdlib>

extern std::string g_sketchName;

#ifdef _WIN32
#include <windows.h>
int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int) {
    // Get sketch name from env var (set by launcher) or compile-time define
    const char* envName = std::getenv("PROCESSING_SKETCH_NAME");
    if (envName && envName[0]) {
        g_sketchName = envName;
    }
#ifdef PROCESSING_SKETCH_NAME
    else {
        g_sketchName = PROCESSING_SKETCH_NAME;
    }
#endif
    Processing::run();
    return 0;
}
#else
int main(int argc, char** argv) {
    const char* envName = std::getenv("PROCESSING_SKETCH_NAME");
    if (envName && envName[0]) {
        g_sketchName = envName;
    }
#ifdef PROCESSING_SKETCH_NAME
    else {
        g_sketchName = PROCESSING_SKETCH_NAME;
    }
#endif
    for (int i = 1; i < argc; i++)
        if (std::string(argv[i]) == "--debug")
            Processing::enableDebugConsole();
    Processing::run();
    return 0;
}
#endif
