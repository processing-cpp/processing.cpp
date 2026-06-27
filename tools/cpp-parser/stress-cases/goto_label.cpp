// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly, no occurrences
//
// "goto label;" and label declarations ("label:") are not recognized as
// statement forms at all -- there is no GotoStatement or LabelStatement
// node in the AST, and the parser has no dispatch case for either shape.
// goto is rare/discouraged in modern C++ and Processing sketches in
// particular have no reason to use it, but it's real, valid syntax.
void f() {
    goto label;
    label:
    return;
}
