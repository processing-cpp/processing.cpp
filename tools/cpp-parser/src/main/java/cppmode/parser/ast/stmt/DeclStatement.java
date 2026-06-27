package cppmode.parser.ast.stmt;

import cppmode.parser.Token;
import cppmode.parser.ast.TypeRef;
import cppmode.parser.ast.expr.Expr;

import java.util.List;

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
 */
/**
 * A local-scope variable declaration statement.
 *
 * isStatic/isConst added after a real bug was found via adversarial
 * stress-case probing: "static int x = 5;" as a LOCAL variable (inside
 * a function body) failed to parse at all -- "static" was only ever
 * consumed by the TOP-LEVEL declaration path
 * (parseOneTopLevelDeclarator's caller), never by
 * parseDeclStatementsDesugared (the statement/local-scope path). This
 * record had no field to even HOLD that information before this fix,
 * which is also why the identical, separately-documented "local const
 * loses its const-ness" gap (see DECISION_two_parser_implementations.md)
 * existed -- both are the same underlying problem (this record only
 * ever modeled "no qualifier" local declarations), fixed together here
 * rather than patching one and leaving the other as a known gap with no
 * evidence-based reason to treat them differently once the real fix was
 * already in hand.
 */
public record DeclStatement(
    TypeRef type,
    String name,
    List<Expr> arrayDims,
    Expr initializer,
    boolean isStatic,
    boolean isConst,
    int line,
    int col,
    List<Token> leadingComments
) implements Statement {
}
