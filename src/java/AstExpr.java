package processing.mode.cpp;

import java.util.List;

/*
 * Consolidated expression AST node types -- formerly 18 separate files
 * under tools/cpp-parser's ast/expr/ package. Merged into one file to
 * reduce src/java/'s file count; every type here is otherwise unchanged,
 * just package-private now (no "public" modifier) instead of public,
 * since nothing outside this package ever references them -- Parser,
 * CodeGen, and the passes/ files all live in this same flat package.
 */



/**
 * Root of the expression AST hierarchy. Every concrete expression node lives in
 * this package and implements this interface, in addition to {@link Node} for
 * position/comment tracking.
 *
 * Confirmed shapes (see project corpus-validation notes for the reasoning behind
 * each one -- this list is the consolidated result of walking LSystem, Mandelbrot,
 * Button, Handles, and two rounds of synthetic kitchen-sink fixtures):
 *
 *  Literal, Identifier, ScopedName, BinaryExpr, UnaryExpr, PostfixExpr,
 *  AssignExpr, CallExpr, MemberAccessExpr, IndexExpr, CastExpr, TernaryExpr,
 *  NewExpr, ArrayNewExpr, LambdaExpr, InitializerListExpr
 */

sealed interface Expr extends Node
    permits Literal, Identifier, ScopedName, BinaryExpr, UnaryExpr, PostfixExpr,
            AssignExpr, CallExpr, MemberAccessExpr, IndexExpr, CastExpr,
            TernaryExpr, NewExpr, ArrayNewExpr, LambdaExpr, InitializerListExpr {
}





/**
 * A literal value: int, float, string, char, or bool.
 *
 * The raw lexer text is preserved verbatim in {@code text} (including quotes for
 * strings, the leading 0x for hex, suffixes like f/L/u, escape sequences as
 * written) rather than being eagerly converted to a Java int/double/etc. Parsing
 * the actual numeric/string value is a later concern (codegen just re-emits the
 * text; a semantic pass that needs the real value can parse {@code text} itself).
 * This avoids the parser making lossy decisions about e.g. whether "0xFF" should
 * become an int or a long -- that's not the parser's call to make.
 */

record Literal(
    Kind kind,
    String text,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {

    public enum Kind { INT, FLOAT, STRING, CHAR, BOOL }
}





/** A bare identifier reference, e.g. "x", "drawLength", "production". */

record Identifier(
    String name,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * A "::"-qualified name, e.g. "std::string", "MyNamespace::someFunction",
 * "Direction::UP". Confirmed as its own node (rather than just baking "::" into
 * raw identifier text) since semantic passes care about namespace/scope
 * structure separately from a plain identifier reference -- e.g. resolving
 * "Direction::UP" needs to know "Direction" is the scope and "UP" is the member,
 * not just see one opaque token "Direction::UP".
 *
 * @param parts  the dot-free, "::"-separated segments in order, e.g.
 *               ["std", "string"] or ["MyNamespace", "someFunction"]
 */

record ScopedName(
    List<String> parts,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {

    public String joined() {
        return String.join("::", parts);
    }
}





/**
 * A binary operator expression: arithmetic (+ - * / %), comparison
 * (== != &lt; &gt; &lt;= &gt;=), logical (&amp;&amp; ||), bitwise (&amp; | ^ &lt;&lt; &gt;&gt;),
 * and compound-assignment (+= -= *= /= %= &amp;= |= ^= &lt;&lt;= &gt;&gt;=) all share this
 * shape -- the operator is carried as raw text rather than an enum, since the
 * grammar treats them identically (same precedence-climbing parse logic) and an
 * enum would need a near-1:1 mirror of every operator string with no real
 * structural benefit.
 *
 * Plain "=" assignment is intentionally NOT here -- see {@link AssignExpr},
 * which is its own node because assignment is right-associative and can itself
 * appear as the right-hand side of another assignment (confirmed by the corpus's
 * "circleOver = rectOver = false;" chained-assignment case), a property compound
 * operators don't need to share.
 */

record BinaryExpr(
    String op,
    Expr left,
    Expr right,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * A prefix unary operator expression: negation (-x), logical-not (!x),
 * address-of (&amp;x), bitwise-not (~x), and prefix increment/decrement
 * (++x, --x).
 *
 * Postfix increment/decrement (x++, x--) is a separate node, {@link PostfixExpr},
 * since it has different precedence/associativity and applies to the left rather
 * than wrapping to the right.
 */

record UnaryExpr(
    String op,
    Expr operand,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/** A postfix increment/decrement expression: "x++" or "x--". */

record PostfixExpr(
    String op,
    Expr operand,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * Plain "=" assignment. Right-associative and usable as the right-hand side of
 * another AssignExpr -- confirmed necessary by the corpus's chained-assignment
 * case ("circleOver = rectOver = false;").
 *
 * {@code target} may be an Identifier, a MemberAccessExpr, or an IndexExpr --
 * confirmed by the corpus that array-index ("pixels[i + j * width] = ...") and
 * (implicitly) member-access targets both need to be valid assignment LHS forms,
 * not just plain identifiers. The parser does not restrict which Expr subtype
 * appears here at parse time; rejecting non-lvalue targets (e.g. assigning to a
 * literal) is left to a later semantic check if one is ever needed, not encoded
 * in the grammar itself.
 */

record AssignExpr(
    Expr target,
    Expr value,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * A function call: "callee(args...)".
 *
 * Deliberately also covers the "bare construction call" pattern confirmed in
 * the corpus -- "ps = PenroseSnowflakeLSystem();" and
 * "handles = ArrayList&lt;Handle&gt;();" both parse as an ordinary CallExpr whose
 * callee happens to be a type name rather than a function name. The parser
 * cannot tell these apart without a symbol table (a free function and a
 * same-named type constructor are syntactically identical at this stage), so
 * disambiguating "is this a real call or a construction" is left to a later
 * semantic pass, not encoded as a separate node here. See project notes on why
 * a dedicated ConstructExpr node was considered and rejected.
 *
 * isBraceInit added for brace-init of a templated type
 * ("Rect<float>{x, y, w, h}"), distinct from paren-init/ordinary calls.
 */

record CallExpr(
    Expr callee,
    List<Expr> args,
    boolean isBraceInit,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
    CallExpr(Expr callee, List<Expr> args, int line, int col, List<CppLexerToken> leadingComments) {
        this(callee, args, false, line, col, leadingComments);
    }
}





/**
 * Member access via "." or "->": "object.member" or "pointer->member".
 *
 * Confirmed by the corpus to need an explicit isArrow flag (rather than two
 * separate node types) since both forms compose identically in every other
 * respect, including chaining arbitrarily ("others->get(i)->locked",
 * "handles.get(i)->releaseEvent()") and mixing dot/arrow within one chain.
 */

record MemberAccessExpr(
    Expr target,
    String memberName,
    boolean isArrow,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * Array/container subscript: "target[index]".
 *
 * Covers both real array indexing and std::string character indexing
 * uniformly -- the corpus showed "production[i]" (string char access) and
 * "pixels[i + j * width]" (array access, also confirmed valid as an
 * AssignExpr target) using identical syntax. Disambiguating "is this a
 * string or an array" is a type-resolution concern for a later pass, not
 * something the grammar needs to know.
 */

record IndexExpr(
    Expr target,
    Expr index,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * A C-style cast: "(TargetType)expr", e.g. "(int)production.length()".
 * Confirmed as common in the real corpus (appears repeatedly in LSystem).
 * Only the C-style parenthesized-type form is supported -- static_cast/
 * dynamic_cast/etc. are out of scope per the original grammar-scope decision.
 */

record CastExpr(
    TypeRef targetType,
    Expr expr,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * The ternary conditional: "cond ? thenExpr : elseExpr".
 * Confirmed by the kitchen-sink fixture to nest (a ? b : (c ? d : e)) and to
 * appear as a call argument and as an array index -- no special handling
 * needed for those positions beyond ordinary expression-grammar composition.
 */

record TernaryExpr(
    Expr condition,
    Expr thenExpr,
    Expr elseExpr,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * Heap allocation of a single object: "new Type(args...)", e.g.
 * "new Handle(width / 2, 10 + i * 15, ...)" or "new int(42)".
 *
 * Distinct from {@link ArrayNewExpr} ("new Type[size]") and from the bare
 * construction-call pattern handled by {@link CallExpr} (no "new" keyword,
 * e.g. "PenroseSnowflakeLSystem()") -- all three are different syntactic forms
 * confirmed present in the real corpus.
 */

record NewExpr(
    TypeRef type,
    List<Expr> args,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * Heap allocation of an array: "new Type[sizeExpr]", e.g. "new int[10]".
 * Paired at the statement/codegen level with "delete[] ptr;" (a flag on the
 * delete statement, not on this node -- allocation and deallocation are
 * separate statements in the source and the parser doesn't try to link them).
 */

record ArrayNewExpr(
    TypeRef elementType,
    Expr sizeExpr,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * A lambda expression: "[captures](params) [-> ReturnType] { body }".
 *
 * returnType is null when the trailing "-> Type" is omitted (the common case --
 * confirmed by the corpus that most lambdas skip it and rely on return-type
 * deduction; the parser doesn't perform deduction itself, it simply records
 * "no explicit return type was written").
 */

record LambdaExpr(
    List<Capture> captures,
    List<Param> params,
    TypeRef returnType,
    boolean isMutable,
    Block body,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}





/**
 * A brace-enclosed initializer list: "{1, 2, 3, 4, 5}", used as an array
 * initializer (confirmed by the corpus's "int initialized[5] = {1,2,3,4,5};"
 * fixture). Not yet confirmed for other contexts (e.g. aggregate
 * initialization of a struct) but the shape generalizes trivially if needed.
 */

record InitializerListExpr(
    List<Expr> elements,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Expr {
}



/**
 * A single entry in a lambda capture list: a captured variable name and
 * whether it's captured by reference ([&amp;x]) or by value ([x]).
 */

record Capture(String name, boolean byRef) {
}

