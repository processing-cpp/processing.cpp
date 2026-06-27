#include <functional>
#include <string>

void noCaptureLambda() {
    auto greet = []() { return 42; };
    int result = greet();
}
void explicitCaptureLambda() {
    int x = 10;
    auto byValue = [x]() { return x + 1; };
    auto byRef    = [&x]() { x = x + 1; return x; };
    int a = byValue();
    int b = byRef();
}
int lambdaPassedAsArgument(std::function<int(int)> callback, int input) {
    return callback(input);
}
void useLambdaPassedAsArgument() {
    int result = lambdaPassedAsArgument([](int n) { return n * 2; }, 21);
}
void lambdaWithExplicitReturnType() {
    auto divide = [](int a, int b) -> double {
        return (double)a / (double)b;
    };
    double result = divide(7, 2);
}
template<typename T>
class Box {
public:
    T value;
    T get() const { return value; }
};
template<typename K, typename V>
class Pair {
public:
    K key;
    V value;
    K getKey() const { return key; }
    V getValue() const { return value; }
};
void useTemplateClasses() {
    Box<int> b;
    b.value = 5;
    int boxed = b.get();
    Pair<int, std::string> p;
    p.key = 1;
    p.value = "one";
    int k = p.getKey();
    std::string v = p.getValue();
}
int someFunc(int a, int b) {
    return a + b;
}
void useRawFunctionPointer() {
    int (*funcPtr)(int, int) = someFunc;
    int result = funcPtr(3, 4);
}
void apply(std::function<void(int)> callback) {
    callback(99);
}
void useStdFunctionParam() {
    std::function<void(int)> printer = [](int n) { (void)n; };
    apply(printer);
}
namespace MyNamespace {
    int someValue = 7;
    int someFunction() {
        return someValue * 2;
    }
}
void callNamespacedFunctionQualified() {
    int result = MyNamespace::someFunction();
}
using namespace MyNamespace;
void callNamespacedFunctionUnqualified() {
    int result = someFunction();
}
