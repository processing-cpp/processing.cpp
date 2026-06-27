// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: confirmed working via direct probing while investigating
// function_pointer_parameter.cpp's gap -- this is the positive control
// proving function pointers themselves are NOT broken in general, only
// the parameter-position case is.
//
// Function-pointer-typed local variables are a real, deliberately
// supported feature from earlier in this project's architecture. This
// exact shape must keep working.
void f() {
    int (*callback)(int, int);
}
