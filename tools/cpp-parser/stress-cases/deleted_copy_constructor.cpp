// EXPECT: FAIL
// CATEGORY: parser-gap
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly, no occurrences
//
// "= delete;" on a special member function (here, a copy constructor --
// a common real-world idiom to make a class non-copyable) fails. Same
// root cause as the already-recorded defaulted_destructor.cpp case
// ("= default;"): the parser expects a real function body ("{ ... }")
// or a bare ";" after a constructor's parameter list, never "= delete"
// or "= default".
class A {
    A(const A&) = delete;
};
