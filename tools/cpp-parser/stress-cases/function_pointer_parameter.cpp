// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly for
// a function-pointer-typed PARAMETER specifically, no occurrences
//
// A function-pointer type used as a FUNCTION PARAMETER
// ("void f(int (*callback)(int, int))") fails -- confirmed via direct
// probing that this is specifically a parameter-parsing gap, not a
// general function-pointer gap: the exact same declarator shape as a
// LOCAL VARIABLE ("int (*callback)(int, int);" inside a function body)
// already works correctly and is a confirmed, real, supported feature
// of this project (function-pointer variables, noted in the original
// architecture docs). parseParam's declarator grammar simply never
// learned the "(*name)(...)" function-pointer-parameter shape that the
// statement/top-level declarator parsers already know.
void f(int (*callback)(int, int)) {}
