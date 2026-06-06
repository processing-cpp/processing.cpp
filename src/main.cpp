#include "Processing.h"

#ifdef _WIN32
#include <windows.h>
int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int) {
    Processing::run();
    return 0;
}
#else
#include <string>
int main(int argc, char** argv) {
    for (int i = 1; i < argc; i++)
        if (std::string(argv[i]) == "--debug")
            Processing::enableDebugConsole();
    Processing::run();
    return 0;
}
#endif
