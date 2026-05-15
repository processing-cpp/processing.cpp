#include "Processing.h"
#include <string>

int main(int argc, char** argv) {
    for (int i = 1; i < argc; i++)
        if (std::string(argv[i]) == "--debug")
            Processing::enableDebugConsole();
    Processing::run();
    return 0;
}
