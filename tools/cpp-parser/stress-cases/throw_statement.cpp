// EXPECT: FAIL
// CATEGORY: parser-gap, SILENT-MISPARSE-RISK (partially fixed)
// FOUND: deliberate adversarial probing, not from any real sketch
// CORPUS EVIDENCE: zero -- checked the real 131-file corpus directly, no occurrences
//
// "throw" has NO dedicated dispatch case in parseStatement() (unlike
// every other statement keyword -- if/for/while/return/break/continue/
// try/delete each get one) and no ThrowStatement AST node exists at
// all. "throw <expr>;" fails cleanly with a ParseException (good,
// fail-fast) since the expression parser eventually hits something it
// can't handle.
//
// "throw;" (bare rethrow, no expression) is a MORE SERIOUS case: it
// used to silently misparse as ExprStatement[Identifier("throw")] --
// "throw" treated as an ordinary variable name, not a keyword at all --
// because "throw" was ALSO missing from STATEMENT_KEYWORDS (the
// exclusion set looksLikeDeclaration() checks first, added earlier in
// this project specifically to prevent statement keywords from being
// misread as bogus type names). FIXED that part: "throw" added to
// STATEMENT_KEYWORDS, confirmed via the real 131-file corpus sweep
// (still 131/131, no regression) before and after.
//
// NOT fully fixed: even with STATEMENT_KEYWORDS corrected, "throw"
// still has no real dispatch case in parseStatement(), so it falls
// through to parseExprStatement()'s primary-expression fallback, which
// still accepts a bare KEYWORD token as if it were an identifier --
// meaning "throw;" alone (with nothing fitting a declaration shape
// after it) still misparses the same way. A complete fix needs a real
// ThrowStatement node added to the Statement sealed interface (a
// non-trivial change -- every exhaustive Statement-handling site, e.g.
// in CodeGen, needs the new case too) -- deliberately NOT built here
// given zero corpus evidence either real sketch needs throw at all.
// This file's marked EXPECT: FAIL based on the throw-WITH-VALUE line
// below, which does fail cleanly; the bare "throw;" risk is recorded
// here in prose since this PASS/FAIL mechanism can't directly capture
// "fails to throw an exception but produces a wrong AST" on its own.
void f() {
    throw 5;
}
