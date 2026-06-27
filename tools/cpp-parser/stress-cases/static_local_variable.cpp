// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: deliberate adversarial probing -- a real, structural bug
// FIXED: DeclStatement had no isStatic/isConst fields at all (closing
//        this gap also closed the separately-documented local-const
//        gap in the same change); looksLikeDeclaration() and
//        parseDeclStatementsDesugared() now both consume a leading
//        "static"/"const" before attempting to parse a type, mirroring
//        the top-level declaration path's existing tolerance.
//
// "static" on a LOCAL variable (inside a function body) -- a real,
// common, idiomatic C++ pattern (one-time initialization, simple
// memoization/caching) -- previously failed to parse at all. This was a
// genuinely different and more consequential bug than most stress-cases
// entries: not an exotic or rarely-used construct, just an ordinary
// thing real code writes constantly, that happened to never be
// confirmed by any real example sketch's specific code paths.
int counter() {
    static int x = 5;
    static const int y = 10;
    x++;
    return x + y;
}
