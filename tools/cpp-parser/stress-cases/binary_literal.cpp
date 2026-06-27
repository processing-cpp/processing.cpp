// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly, no occurrences
//
// C++14 binary integer literals ("0b1010") are not recognized by the
// lexer's numeric-literal scanning, which only special-cases a leading
// "0x"/"0X" (hex) before falling through to ordinary decimal digit
// scanning -- "0b1010" gets lexed as the integer "0" followed by a
// separate identifier "b1010", which then fails to parse as a
// continuation of anything valid.
void f() {
    int x = 0b1010;
}
