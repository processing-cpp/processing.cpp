// control_flow.cpp -- ternary, switch/case, do-while, continue, try/catch
int ternaryBasic(int a, int b) {
    return a > b ? a : b;
}
int ternaryNested(int a, int b, int c) {
    return a > b ? a : (b > c ? b : c);
}
void printArgValue(int v) {}
void ternaryAsArgument(int x) {
    printArgValue(x > 0 ? x : -x);
}
int ternaryAsArrayIndex(int* arr, int flag, int a, int b) {
    return arr[flag ? a : b];
}
int switchOnIntWithFallthrough(int code) {
    int result = 0;
    switch (code) {
        case 1:
            result += 1;
            // fallthrough
        case 2:
            result += 2;
            break;
        case 3:
            result += 3;
            break;
        default:
            result = -1;
            break;
    }
    return result;
}
int switchOnChar(char c) {
    switch (c) {
        case 'a': return 1;
        case 'b': return 2;
        default:  return 0;
    }
}
int doWhileLoop(int n) {
    int count = 0;
    int i = 0;
    do {
        count += i;
        i++;
    } while (i < n);
    return count;
}
int continueInsideFor(int n) {
    int sum = 0;
    for (int i = 0; i < n; i++) {
        if (i % 2 == 0) continue;
        sum += i;
    }
    return sum;
}
int continueInsideWhile(int n) {
    int sum = 0;
    int i = 0;
    while (i < n) {
        i++;
        if (i == 3) continue;
        sum += i;
    }
    return sum;
}
void tryCatchPassthrough() {
    try {
        int x = 1 / 1;
    } catch (...) {
    }
}
// arrays_and_pointers.cpp -- fixed-size arrays, array-new/delete
void fixedSizeArrayDeclarations() {
    int arr[10];
    int initialized[5] = {1, 2, 3, 4, 5};
    int grid[3][3];
    for (int i = 0; i < 5; i++) arr[i] = initialized[i];
    for (int row = 0; row < 3; row++)
        for (int col = 0; col < 3; col++)
            grid[row][col] = row * 3 + col;
}
void arrayNewAndDelete() {
    int* p = new int[10];
    for (int i = 0; i < 10; i++) p[i] = i * i;
    delete[] p;
}
void plainNewAndDelete() {
    int* single = new int(42);
    delete single;
}
// type_system.cpp -- enums, templates, references, const, static, range-for
enum Color { RED, GREEN, BLUE };
enum class Direction { UP, DOWN };
void useEnums() {
    Color c = RED;
    Direction d = Direction::UP;
    if (c == GREEN) d = Direction::DOWN;
}
template<typename T>
T myMax(T a, T b) {
    return a > b ? a : b;
}
void useTemplateFunction() {
    int   i = myMax(3, 7);
    float f = myMax(1.5f, 2.5f);
}
void modifyByReference(int& val) {
    val = 5;
}
void showConstReference(const std::string& s) {}
struct PointWithGetter {
    float x, y;
    float getX() const { return x; }
};
const int LIMIT = 100;
struct Counter {
    static int count;
    void increment() { count++; }
};
int Counter::count = 0;
static int staticFreeFunction(int n) {
    return n * 2;
}
void withDefaultParam(int x, int y = 5) {
    int sum = x + y;
}
struct Point {
    float x, y;
};

// Multi-line enum -- the exact shape the original writeSketch() needed
// careful brace-depth tracking for (a naive per-line scan would only
// capture the opening line and lose every enumerator below it).
enum class CardSuit {
    HEARTS,
    DIAMONDS,
    CLUBS,
    SPADES
};
