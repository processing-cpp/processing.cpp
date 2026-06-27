// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly for
// any "using Name = Type;" pattern, no occurrences (already separately
// noted in EnumScopeExtractor's javadoc as a known, deliberately-
// unaddressed gap -- this file makes it a permanent regression-tracking
// case too, not just a code comment)
//
// "using IntPtr = int*;" (a C++11 type-alias declaration, the modern
// replacement for "typedef int* IntPtr;") fails outright. The parser
// only recognizes "using" in its using-NAMESPACE form
// ("using namespace Foo;"); the type-alias form isn't handled at all.
using IntPtr = int*;
