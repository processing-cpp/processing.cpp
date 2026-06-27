package cppmode.parser.ast.decl;

import cppmode.parser.Token;
import cppmode.parser.ast.Param;
import cppmode.parser.ast.TypeRef;
import cppmode.parser.ast.expr.Expr;
import cppmode.parser.ast.stmt.Block;

import java.util.List;

/**
 * A function declaration/definition. This single node shape covers every
 * function-like form confirmed in the corpus:
 *
 *  - free functions ("void setup() { ... }", "static int staticFreeFunction(int n)")
 *  - methods, including one-liner setters ("void useRule(std::string r_) { rule = r_; }")
 *  - virtual methods and virtual destructors ("virtual std::string iterate(...)",
 *    "virtual ~LSystem() {}")
 *  - override methods ("std::string iterate(...) override { ... }")
 *  - const methods ("float getX() const { return x; }")
 *  - constructors, including with an initializer list
 *    ("Handle(int x) : x_(x), y_(0) { }") -- isConstructor is true and
 *    returnType is null in this case
 *  - destructors ("virtual ~LSystem() {}") -- isDestructor is true,
 *    returnType is null
 *  - operator overloads ("bool operator==(const Handle&amp; other) const { ... }")
 *    -- name is the literal text "operator==", no separate node needed
 *  - template functions ("template&lt;typename T&gt; T myMax(T a, T b) { ... }")
 *    -- templateParams is non-empty
 *
 * Note on isConstructor/isDestructor vs returnType: constructors and
 * destructors have no return type in C++ syntax at all (not even "void") --
 * returnType is null for both, and the parser distinguishes "this is a
 * constructor" by the function name matching the enclosing class name with
 * no return type present, and "this is a destructor" by a leading "~".
 * These flags exist so later passes don't need to re-derive that from name
 * matching every time.
 */
public record FunctionDecl(
    TypeRef returnType,         // null for constructors/destructors
    String name,                // includes "operator==" style names verbatim
    List<String> templateParams,
    List<Param> params,
    List<ConstructorInit> initializerList,  // empty unless isConstructor
    Block body,                 // null for a declaration with no definition (rare in corpus, supported regardless)
    boolean isConstructor,
    boolean isDestructor,
    boolean isVirtual,
    boolean isOverride,
    boolean isConst,
    boolean isStatic,
    int line,
    int col,
    List<Token> leadingComments
) implements TopLevelItem {

    /** One "member(args...)" entry in a constructor's initializer list. */
    public record ConstructorInit(String memberName, List<Expr> args) {
    }
}
