package processing.mode.cpp;

import java.util.List;

/*
 * Consolidated declaration AST node types -- formerly 10 separate files
 * under tools/cpp-parser's ast/decl/ package. Merged for the same reason
 * as AstExpr.java; see its header comment.
 */



/**
 * Marker for anything that can appear directly inside a CompilationUnit:
 * a VariableDecl, FunctionDecl, TypeDef, EnumDecl, NamespaceDecl, or
 * UsingNamespaceDecl. Also reused as the "member of a class/struct body"
 * marker (TypeDef.members), since the corpus confirms the same shapes
 * (field VariableDecl, method/constructor/destructor FunctionDecl, nested
 * TypeDef) appear in both top-level and class-body positions identically.
 */

sealed interface TopLevelItem extends Node
    permits VariableDecl, FunctionDecl, TypeDef, EnumDecl, NamespaceDecl,
            UsingNamespaceDecl, PreprocessorLine, TopLevelStatement {
}




/** The root AST node: an entire parsed source file as a list of top-level items. */

record CompilationUnit(List<TopLevelItem> items) {
}





/**
 * A top-level or class-field variable declaration, e.g. "int steps = 0;",
 * "std::string axiom;", "PenroseSnowflakeLSystem ps;", "static int count;".
 *
 * Per the multi-declarator desugaring decision (see DeclStatement's notes,
 * which apply identically here): "int rectX, rectY;" and "color rectColor,
 * circleColor, baseColor;" both desugar into multiple single-name
 * VariableDecl nodes at parse time, sharing one TypeRef each, confirmed
 * necessary at both top-level scope (Button fixture) and inside a class
 * body (Handles fixture's Handle class fields).
 *
 * isConst/isStatic confirmed needed by the type_system kitchen-sink batch
 * ("const int LIMIT = 100;", "static int count;").
 */

record VariableDecl(
    TypeRef type,
    String name,
    List<Expr> arrayDims,
    Expr initializer,
    boolean isConst,
    boolean isStatic,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements TopLevelItem {
}





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

record FunctionDecl(
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
    List<CppLexerToken> leadingComments
) implements TopLevelItem {

    /** One "member(args...)" entry in a constructor's initializer list. */
    public record ConstructorInit(String memberName, List<Expr> args) {
    }
}





/**
 * A class or struct declaration: "class Name [: public Base1, public Base2] { members }"
 * or "struct Name { members }".
 *
 * kind distinguishes "class" vs "struct" textually -- confirmed by the corpus
 * that both forms are used (LSystem/Handle as class, Point/Counter as struct)
 * but behave identically in every other respect for parsing purposes, so one
 * node with a kind field is sufficient (no separate ClassDecl/StructDecl).
 *
 * baseClasses is a List, not a nullable single name -- confirmed necessary by
 * the multiple-inheritance fixture ("class Sprite : public Movable, public
 * Drawable"). Empty list for no inheritance.
 *
 * templateParams is empty for non-template types; confirmed necessary by
 * "template&lt;typename T&gt; class Box" and the two-parameter
 * "template&lt;typename K, typename V&gt; class Pair" form.
 */

record TypeDef(
    String kind,           // "class" or "struct"
    String name,
    List<String> templateParams,
    List<String> baseClasses,
    List<TopLevelItem> members,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements TopLevelItem {
}





/**
 * "enum Name { VALUE1, VALUE2, ... };" (isScoped=false) or
 * "enum class Name { VALUE1, VALUE2 };" (isScoped=true).
 *
 * isScoped is a real semantic difference, not just cosmetic -- confirmed by
 * the corpus that unscoped enum values are usable bare ("Color c = RED;")
 * while scoped enum values require qualification ("Direction d =
 * Direction::UP;"). A later name-resolution pass needs this flag to know
 * whether an enum's values pollute the enclosing scope or not; the parser
 * itself doesn't enforce the distinction, it just records which form was
 * written.
 */

record EnumDecl(
    String name,
    boolean isScoped,
    List<String> values,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements TopLevelItem {
}





/**
 * "namespace Name { items }".
 *
 * NOTE (carried over from the synthetic test-fixture brief): real .pde-style
 * CppMode sketches have not been confirmed to ever use namespaces. This node
 * exists so the parser doesn't choke if one appears, but semantic-pass
 * support for namespace-aware name resolution is deliberately deprioritized
 * until real sketch input demonstrates the need. Parse-only support for now.
 */

record NamespaceDecl(
    String name,
    List<TopLevelItem> items,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements TopLevelItem {
}





/** "using namespace Name;". Same low-priority/parse-only status as NamespaceDecl. */

record UsingNamespaceDecl(
    String name,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements TopLevelItem {
}





/**
 * An opaque preprocessor directive line ("#include ...", "#define ...", etc.),
 * passed through verbatim per the original grammar-scope decision: the parser
 * never deeply parses preprocessor directives, only preserves their raw text
 * and position so codegen can re-emit them unchanged in the right place.
 */

record PreprocessorLine(
    String rawText,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements TopLevelItem {
}





/**
 * Wraps a bare statement appearing directly at file (top) scope -- confirmed
 * necessary by real Processing "static mode" sketches in the example corpus
 * (e.g. Coordinates.pde), which have no setup()/draw() at all and consist of
 * a flat sequence of statements executed once, top to bottom. A call like
 * "size(640, 360);" at true top level doesn't fit any other TopLevelItem
 * shape (it's not a declaration), so it's wrapped here instead, carrying
 * the underlying Statement unchanged.
 *
 * This was NOT anticipated by the original corpus walk -- all four
 * hand-picked example sketches happened to use active mode with explicit
 * setup()/draw(). Found only by running the parser against the full real
 * example corpus.
 */

record TopLevelStatement(
    Statement statement,
    int line,
    int col,
    List<CppLexerToken> leadingComments
) implements TopLevelItem {
}
