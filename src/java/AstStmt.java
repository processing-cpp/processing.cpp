package processing.mode.cpp;

import java.util.List;

/*
 * Consolidated statement AST node types -- formerly 17 separate files
 * under tools/cpp-parser's ast/stmt/ package. Merged for the same reason
 * as AstExpr.java; see its header comment.
 */



/**
 * Root of the statement AST hierarchy. Node set confirmed by walking the full
 * test corpus (LSystem, Mandelbrot, Button, Handles, plus two synthetic
 * kitchen-sink batches covering control flow, arrays, OOP features, lambdas,
 * templates, function pointers, and namespaces).
 *
 * Note on if/else-if chains: there is no separate "ElseIfStatement" -- an
 * "else if (...)" is just an IfStatement whose elseBranch is itself another
 * IfStatement, the standard recursive-descent representation. Arbitrary-length
 * chains (confirmed up to 5-way in the LSystem render() method) fall out of
 * this for free with no special grammar rule.
 */

sealed interface Statement extends Node
    permits ExprStatement, DeclStatement, Block, IfStatement, ForStatement,
            RangeForStatement, WhileStatement, DoWhileStatement, SwitchStatement,
            ReturnStatement, BreakStatement, ContinueStatement, TryStatement,
            DeleteStatement {
}





/** A brace-delimited sequence of statements: "{ stmt; stmt; ... }". */

record Block(
    List<Statement> statements,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/**
 * An expression used as a statement, terminated by ";": assignments
 * ("production = iterate(...);"), bare calls ("ps.render();"), increments
 * ("generations++;"), etc.
 */

record ExprStatement(
    Expr expr,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/**
 * A single local variable declaration used as a statement, e.g. "int x = 5;",
 * "float w = 4;", "Handle* h = handles.get(i);".
 *
 * Per the corpus-validation decision on multi-declarator statements (e.g.
 * "int rectX, rectY;", "bool over, press;"), the parser desugars comma-separated
 * declarator lists into multiple single-name DeclStatement nodes at parse time
 * -- each sharing the same TypeRef but otherwise independent. This keeps every
 * downstream semantic pass and the codegen stage working with a uniform single-
 * name shape rather than needing to handle a list-of-declarators case everywhere.
 * If exact source round-trip formatting of comma-lists is ever needed by codegen,
 * that's recovered via a sourceGroupId correlation field, not by changing this
 * node's shape -- not implemented yet since nothing has required it so far.
 *
 * @param arrayDims  empty for scalars; one Expr per dimension for fixed-size
 *                    C-style arrays (e.g. "int grid[3][3];" has two entries).
 *                    An entry may itself be null-equivalent (use a Literal of
 *                    empty text, or omit by convention -- TBD by the parser
 *                    implementation) for an array with no explicit constant
 *                    size when accompanied by an initializer list; not yet
 *                    exercised by the corpus, flagged for when it is.
 *
 * isStatic/isConst added after a real bug was found via adversarial
 * stress-case probing: "static int x = 5;" as a LOCAL variable (inside
 * a function body) failed to parse at all -- "static" was only ever
 * consumed by the TOP-LEVEL declaration path, never by
 * parseDeclStatementsDesugared (the statement/local-scope path). This
 * record had no field to even HOLD that information before this fix,
 * which is also why the separately-documented "local const loses its
 * const-ness" gap existed -- both are the same underlying problem (this
 * record only ever modeled "no qualifier" local declarations), fixed
 * together here.
 */
record DeclStatement(
    TypeRef type,
    String name,
    List<Expr> arrayDims,
    Expr initializer,
    boolean isStatic,
    boolean isConst,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/**
 * "if (condition) thenBranch [else elseBranch]".
 *
 * elseBranch is null when absent. An "else if (...)" chain is represented as
 * elseBranch being another IfStatement directly (no separate ElseIfStatement
 * node) -- confirmed sufficient for arbitrary-length chains by the LSystem
 * render() method's 5-way else-if chain.
 *
 * thenBranch/elseBranch are themselves Statement, which permits both the brace
 * form ("if (x) { ... }") and the single-statement-no-braces form
 * ("if (i % 2 == 0) continue;") confirmed present in the corpus -- both parse
 * to the same shape, just with elseBranch/thenBranch being a Block in one case
 * and a single non-Block Statement in the other.
 */

record IfStatement(
    Expr condition,
    Statement thenBranch,
    Statement elseBranch,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/**
 * The classic 3-part for loop: "for (init; condition; update) body".
 *
 * init is a Statement (typically a DeclStatement, e.g. "int i = 0", or an
 * ExprStatement) rather than a bare Expr, since the corpus shows the init
 * clause is itself a declaration in every real occurrence -- modeling it as
 * Statement keeps this uniform with how declarations are represented
 * everywhere else rather than inventing a separate inline-decl shape just
 * for this one grammar position. update is a plain Expr (e.g. "i++"),
 * matched against the corpus's exclusively-expression update clauses.
 *
 * Distinct from {@link RangeForStatement} ("for (auto&amp; x : items)"), a
 * separate grammar form confirmed present in the corpus's synthetic fixtures.
 */

record ForStatement(
    Statement init,
    Expr condition,
    Expr update,
    Statement body,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/**
 * Range-based for loop: "for (Type&amp; name : iterableExpr) body", e.g.
 * "for (auto&amp; item : items) { ... }". Confirmed as a distinct grammar form
 * from the classic 3-part ForStatement by the synthetic kitchen-sink fixture.
 */

record RangeForStatement(
    TypeRef declType,
    String declName,
    boolean isReference,
    Expr iterableExpr,
    Statement body,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/** "while (condition) body". */

record WhileStatement(
    Expr condition,
    Statement body,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/** "do body while (condition);". */

record DoWhileStatement(
    Statement body,
    Expr condition,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/**
 * A single "case" (or "default") clause within a switch statement.
 *
 * @param matchValue  the case's constant expression (e.g. a Literal); null for
 *                     the "default:" clause
 * @param body        the statements under this case, up to (but not including)
 *                     the next case/default/closing brace. Fallthrough (a case
 *                     with no "break;" at the end of its body) is simply
 *                     represented as-is -- confirmed by the corpus's
 *                     switchOnIntWithFallthrough fixture, where case 1 falls
 *                     into case 2 with no special node needed; the absence of
 *                     a trailing BreakStatement in body is itself the
 *                     fallthrough signal for any later pass that cares.
 */

record SwitchCase(Expr matchValue, List<Statement> body) {
}





/**
 * "switch (subject) { case ...: ...; case ...: ...; default: ...; }".
 * Confirmed by the corpus to work uniformly whether switching on an int or a
 * char subject -- the grammar doesn't need to know the subject's type, that's
 * purely a later type-checking concern.
 */

record SwitchStatement(
    Expr subject,
    List<SwitchCase> cases,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/**
 * "return expr;" or bare "return;" (value is null for the latter -- needed by
 * void-returning functions/methods, e.g. early-return patterns not yet seen
 * in the corpus but required by the grammar's own completeness regardless).
 */

record ReturnStatement(
    Expr value,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/** "break;" -- exits the nearest enclosing loop or switch. */

record BreakStatement(
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/** "continue;" -- skips to the next iteration of the nearest enclosing loop. */

record ContinueStatement(
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}





/**
 * "try { ... } catch (...) { ... } [catch (...) { ... } ...]".
 * Pass-through parsing only, per the original grammar-scope decision --
 * the parser doesn't attempt to model exception-handling semantics.
 */

record TryStatement(
    Block tryBlock,
    List<CatchClause> catchClauses,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}




/**
 * A single "catch" clause. exceptionType/varName are both null for the
 * catch-all "catch (...)" form confirmed in the corpus (tryCatchPassthrough
 * fixture); a typed catch ("catch (const std::exception&amp; e)") is not yet
 * exercised by any fixture but supported by the grammar regardless, since
 * pass-through parsing of try/catch was always the scope, not just the
 * catch-all form specifically.
 */

record CatchClause(TypeRef exceptionType, String varName, Block body, boolean isCatchAll) {
}





/**
 * "delete expr;" or "delete[] expr;" -- isArray distinguishes the two forms,
 * both confirmed present in the corpus (arrayNewAndDelete / plainNewAndDelete
 * fixtures). Modeled as a statement rather than an expression since "delete"
 * is always used as a bare statement in every occurrence seen, and C++ itself
 * treats it as an expression of type void in a context that's only ever used
 * statement-like in practice -- no corpus evidence of "delete" appearing
 * nested inside another expression, so the simpler statement-only shape is
 * used unless a counterexample shows up.
 */

record DeleteStatement(
    Expr target,
    boolean isArray,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements Statement {
}
