package cppmode.parser;

import cppmode.parser.ast.FunctionPointerType;
import cppmode.parser.ast.FunctionSignatureType;
import cppmode.parser.ast.NamedType;
import cppmode.parser.ast.Param;
import cppmode.parser.ast.TypeRef;
import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.ast.decl.EnumDecl;
import cppmode.parser.ast.decl.FunctionDecl;
import cppmode.parser.ast.decl.NamespaceDecl;
import cppmode.parser.ast.decl.TopLevelItem;
import cppmode.parser.ast.decl.TypeDef;
import cppmode.parser.ast.decl.UsingNamespaceDecl;
import cppmode.parser.ast.decl.VariableDecl;
import cppmode.parser.ast.expr.ArrayNewExpr;
import cppmode.parser.ast.expr.AssignExpr;
import cppmode.parser.ast.expr.BinaryExpr;
import cppmode.parser.ast.expr.CallExpr;
import cppmode.parser.ast.expr.Capture;
import cppmode.parser.ast.expr.CastExpr;
import cppmode.parser.ast.expr.Expr;
import cppmode.parser.ast.expr.Identifier;
import cppmode.parser.ast.expr.IndexExpr;
import cppmode.parser.ast.expr.InitializerListExpr;
import cppmode.parser.ast.expr.LambdaExpr;
import cppmode.parser.ast.expr.Literal;
import cppmode.parser.ast.expr.MemberAccessExpr;
import cppmode.parser.ast.expr.NewExpr;
import cppmode.parser.ast.expr.PostfixExpr;
import cppmode.parser.ast.expr.ScopedName;
import cppmode.parser.ast.expr.TernaryExpr;
import cppmode.parser.ast.expr.UnaryExpr;
import cppmode.parser.ast.stmt.Block;
import cppmode.parser.ast.stmt.BreakStatement;
import cppmode.parser.ast.stmt.CatchClause;
import cppmode.parser.ast.stmt.ContinueStatement;
import cppmode.parser.ast.stmt.DeclStatement;
import cppmode.parser.ast.stmt.DeleteStatement;
import cppmode.parser.ast.stmt.DoWhileStatement;
import cppmode.parser.ast.stmt.ExprStatement;
import cppmode.parser.ast.stmt.ForStatement;
import cppmode.parser.ast.stmt.IfStatement;
import cppmode.parser.ast.stmt.RangeForStatement;
import cppmode.parser.ast.stmt.ReturnStatement;
import cppmode.parser.ast.stmt.Statement;
import cppmode.parser.ast.stmt.SwitchCase;
import cppmode.parser.ast.stmt.SwitchStatement;
import cppmode.parser.ast.stmt.TryStatement;
import cppmode.parser.ast.stmt.WhileStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for CppMode's Java-flavored-C++ grammar subset.
 *
 * Design notes:
 *  - Comments are consumed by skipComments() and attached as leadingComments
 *    on the next real node produced, per the Lexer's comment-as-first-class-
 *    token design (see Lexer notes). This is the direct replacement for the
 *    old blankCommentsAndLiterals regex pass.
 *  - Fail-fast: the first parse error throws ParseException immediately.
 *    No error recovery is attempted (see ParseException's notes for why).
 *  - Multi-declarator statements ("int rectX, rectY;") are desugared into
 *    multiple single-name nodes at this layer -- see parseVariableDeclTail.
 */
public final class Parser {

    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static CompilationUnit parse(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        return new Parser(tokens).parseCompilationUnit();
    }

    /** Test-only entry point: parses a single TypeRef from source text in isolation. */
    static TypeRef parseTypeRefFromString(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        return new Parser(tokens).parseTypeRef();
    }

    // ----- Core cursor helpers -----------------------------------------
    //
    // peek()/peek(ahead)/advance() transparently skip over comment tokens.
    // This is deliberate and fixes a real bug class found via real-corpus
    // testing: a line comment can legally appear in the MIDDLE of a
    // multi-line expression (e.g. a function call whose arguments are each
    // commented, confirmed real by Move_Eye.pde's
    // "camera(x, y, z, // eyeX, eyeY, eyeZ \n ...)"). The original design
    // only collected leading comments at statement/declaration boundaries
    // via consumeLeadingComments(), which left every OTHER parse point
    // (inside parseArgList, parsePostfix, anywhere mid-expression) just as
    // comment-blind as the regex passes this parser exists to replace.
    // Routing comment-skipping through the cursor primitives themselves
    // fixes every call site at once instead of needing a fix at each one.
    //
    // consumeLeadingComments() still exists and is still called explicitly
    // at statement/declaration start -- it now serves only to COLLECT the
    // comment tokens for attachment to the next node's leadingComments
    // field, not to make parsing work correctly (that's now peek/advance's
    // job). It works correctly alongside this since skipCommentsAt() is
    // idempotent -- calling it twice in a row from the same position is a
    // no-op the second time.

    private void skipCommentsAt() {
        while (pos < tokens.size()
               && (tokens.get(pos).type() == TokenType.LINE_COMMENT
                   || tokens.get(pos).type() == TokenType.BLOCK_COMMENT)) {
            pos++;
        }
    }

    private Token peek() {
        skipCommentsAt();
        return tokens.get(pos);
    }

    private Token peek(int ahead) {
        // Walk forward from pos, skipping comment tokens, to find the
        // "ahead"-th real (non-comment) token after the current position.
        skipCommentsAt();
        int p = pos;
        int remaining = ahead;
        while (remaining > 0 && p < tokens.size() - 1) {
            p++;
            while (p < tokens.size() && (tokens.get(p).type() == TokenType.LINE_COMMENT
                                          || tokens.get(p).type() == TokenType.BLOCK_COMMENT)) {
                p++;
            }
            remaining--;
        }
        return p < tokens.size() ? tokens.get(p) : tokens.get(tokens.size() - 1);
    }

    private Token advance() {
        skipCommentsAt();
        Token t = tokens.get(pos);
        if (t.type() != TokenType.EOF) pos++;
        return t;
    }

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private boolean checkKeyword(String kw) {
        return peek().isKeyword(kw);
    }

    private boolean checkPunct(String p) {
        return peek().isPunct(p);
    }

    private boolean checkOp(String op) {
        return peek().isOp(op);
    }

    /** True if the current token is the integer literal "0" specifically
     * (not any other literal or expression) -- used to recognize the
     * pure-virtual specifier "= 0" precisely, without accidentally
     * matching some other "= <expr>" shape. */
    private boolean checkLiteralZero() {
        Token t = peek();
        return t.type() == TokenType.INT_LITERAL && t.text().equals("0");
    }

    private boolean matchKeyword(String kw) {
        if (checkKeyword(kw)) { advance(); return true; }
        return false;
    }

    private boolean matchPunct(String p) {
        if (checkPunct(p)) { advance(); return true; }
        return false;
    }

    private boolean matchOp(String op) {
        if (checkOp(op)) { advance(); return true; }
        return false;
    }

    private Token expectPunct(String p) {
        if (checkPunct(p)) return advance();
        throw error("expected '" + p + "' but found '" + peek().text() + "'");
    }

    private Token expectOp(String op) {
        if (checkOp(op)) return advance();
        throw error("expected '" + op + "' but found '" + peek().text() + "'");
    }

    private Token expectKeyword(String kw) {
        if (checkKeyword(kw)) return advance();
        throw error("expected '" + kw + "' but found '" + peek().text() + "'");
    }

    /**
     * Set of KEYWORD-classified tokens that are NOT actually reserved
     * words in real Processing/Java/C++ -- they're lexed as keywords
     * purely so they can be recognized as TYPE names in a type-name
     * position (see Lexer's KEYWORDS set and its own comment on this),
     * but that classification incorrectly also blocked them from ever
     * being used as a declarator/variable NAME, which real Processing
     * code is free to do.
     *
     * Found via a real sketch (RayTracer.pde's "Vec3 color;" struct
     * field -- "color" the Processing pseudo-type, used as an ordinary
     * field name, which is completely legal Processing/C++ and not
     * something the language itself reserves) hitting
     * "expected identifier but found 'color'" outright.
     *
     * Deliberately a narrow, explicit allow-list -- NOT a general
     * relaxation letting any KEYWORD stand in for an identifier, which
     * would incorrectly let real reserved words ("int", "if", "class",
     * etc.) be used as names too. Only pseudo-type keywords that this
     * project itself introduced for type-name recognition belong here.
     */
    private static final java.util.Set<String> PSEUDO_TYPE_KEYWORDS_USABLE_AS_NAMES = java.util.Set.of(
        "color"
    );

    private Token expectIdentifier() {
        if (check(TokenType.IDENTIFIER)) return advance();
        if (check(TokenType.KEYWORD) && PSEUDO_TYPE_KEYWORDS_USABLE_AS_NAMES.contains(peek().text())) {
            return advance();
        }
        throw error("expected identifier but found '" + peek().text() + "'");
    }

    private ParseException error(String message) {
        Token t = peek();
        return new ParseException(message, t.line(), t.col());
    }

    /**
     * Consumes and discards any whitespace-adjacent comment tokens immediately
     * preceding the current position, returning them so the caller can attach
     * them as leadingComments on the node it's about to build. Must be called
     * at the start of every "parse one item/statement/declaration" entry point
     * so comments are never silently dropped nor mistaken for code.
     */
    /**
     * Consumes and collects any comment tokens sitting at the current raw
     * position, for attachment to the next node's leadingComments field.
     *
     * Deliberately reads tokens.get(pos) directly rather than calling
     * peek()/check() -- those now transparently skip comment tokens (see
     * the cursor-helpers notes above, added after the Move_Eye.pde bug),
     * so they can never see a comment to report back here. This method is
     * the one place that still needs to look at raw, unfiltered token-
     * stream position.
     */
    private List<Token> consumeLeadingComments() {
        List<Token> comments = new ArrayList<>();
        while (pos < tokens.size()
               && (tokens.get(pos).type() == TokenType.LINE_COMMENT
                   || tokens.get(pos).type() == TokenType.BLOCK_COMMENT)) {
            comments.add(tokens.get(pos));
            pos++;
        }
        return comments;
    }

    // ----- Entry point ---------------------------------------------------

    public CompilationUnit parseCompilationUnit() {
        List<TopLevelItem> items = new ArrayList<>();
        consumeLeadingComments(); // leading file-level comments currently dropped
                                   // at EOF if nothing follows; fine for now
        while (!isAtEnd()) {
            List<Token> comments = consumeLeadingComments();
            if (isAtEnd()) break;
            items.addAll(parseTopLevelItem(comments));
        }
        return new CompilationUnit(items);
    }

    /**
     * Dispatches on lookahead to the appropriate top-level (or class-member,
     * since both positions share this same dispatch per TopLevelItem's design
     * notes) sub-parser. Returns a List since a single source statement can
     * desugar into multiple VariableDecl nodes (multi-declarator decls,
     * confirmed at both top-level and class-member scope by the Button and
     * Handles fixtures respectively) -- every other branch returns a
     * single-element list.
     */
    private List<TopLevelItem> parseTopLevelItem(List<Token> leadingComments) {
        if (check(TokenType.PREPROCESSOR_DIRECTIVE)) {
            Token t = advance();
            return List.of(new cppmode.parser.ast.decl.PreprocessorLine(t.text(), t.line(), t.col(), leadingComments));
        }

        List<String> templateParams = List.of();
        if (checkKeyword("template")) {
            templateParams = parseTemplateParamList();
            consumeLeadingComments();
        }

        if (checkKeyword("class") || checkKeyword("struct")) {
            return List.of(parseTypeDef(leadingComments, templateParams));
        }
        if (checkKeyword("enum")) {
            return List.of(parseEnumDecl(leadingComments));
        }
        if (checkKeyword("namespace")) {
            return List.of(parseNamespaceDecl(leadingComments));
        }
        if (checkKeyword("using") && peek(1).isKeyword("namespace")) {
            return List.of(parseUsingNamespaceDecl(leadingComments));
        }
        // Destructor: "~Name() { ... }" or "virtual ~Name() { ... }" -- only
        // valid inside a class body in real C++, but the parser doesn't
        // enforce that positional rule; a later semantic pass can if it
        // matters. Must look past an optional leading "virtual", same as
        // the parseClassMember dispatch.
        if (checkOp("~") || (checkKeyword("virtual") && peek(1).isOp("~"))) {
            return List.of(parseFunctionOrConstructorOrDestructor(leadingComments, templateParams));
        }

        return parseFunctionOrVariable(leadingComments, templateParams, true);
    }

    /** "template<typename T, typename U, ...>" -- returns just the param names. */
    private List<String> parseTemplateParamList() {
        expectKeyword("template");
        expectOp("<");
        List<String> params = new ArrayList<>();
        if (!checkOp(">")) {
            params.add(parseTemplateParamName());
            while (matchPunct(",")) {
                params.add(parseTemplateParamName());
            }
        }
        if (checkOp(">>")) {
            splitTrailingShiftIntoTwoCloseAngles();
        }
        expectOp(">");
        return params;
    }

    private String parseTemplateParamName() {
        if (!matchKeyword("typename")) {
            matchKeyword("class"); // "template<class T>" is also valid C++, accept both spellings
        }
        return expectIdentifier().text();
    }

    /**
     * Parses a class/struct declaration: "class Name [: public Base1, ...] { members };".
     * "public"/"private"/"protected" access specifiers within the body are
     * consumed and discarded (not modeled in the AST -- not yet confirmed
     * necessary by any semantic pass; if member visibility ever matters,
     * this is the place to start tracking it).
     */
    private TypeDef parseTypeDef(List<Token> leadingComments, List<String> templateParams) {
        Token start = peek();
        String kind = checkKeyword("class") ? "class" : "struct";
        advance();
        String name = expectIdentifier().text();

        List<String> baseClasses = new ArrayList<>();
        if (matchPunct(":")) {
            baseClasses.add(parseBaseClassEntry());
            while (matchPunct(",")) {
                baseClasses.add(parseBaseClassEntry());
            }
        }

        expectPunct("{");
        List<TopLevelItem> members = new ArrayList<>();
        while (!checkPunct("}")) {
            if (isAtEnd()) throw error("unexpected end of input inside class/struct body for '" + name + "'");
            List<Token> comments = consumeLeadingComments();
            if (checkPunct("}")) break;
            if (matchKeyword("public") || matchKeyword("private") || matchKeyword("protected")) {
                expectPunct(":");
                continue;
            }
            members.addAll(parseClassMember(comments, name));
        }
        expectPunct("}");
        // The trailing ';' after a class/struct body is standard C++, but
        // confirmed OPTIONAL in real .pde input -- Scrollbar.pde's
        // "class HScrollbar { ... }" has no trailing ';' at all. Processing's
        // own preprocessing tolerates this; this parser does too rather than
        // hard-requiring strict C++ grammar here.
        matchPunct(";");

        return new TypeDef(kind, name, templateParams, baseClasses, members, start.line(), start.col(), leadingComments);
    }

    /** "public BaseName" / "private BaseName" / "protected BaseName" / bare "BaseName". */
    private String parseBaseClassEntry() {
        matchKeyword("public");
        if (!checkKeyword("public")) {
            matchKeyword("private");
            matchKeyword("protected");
        }
        return parseQualifiedTypeName();
    }

    /**
     * A class member is almost always parseTopLevelItem's domain (field decl,
     * method, nested type) -- but constructors/destructors named after the
     * enclosing class need special handling parseTopLevelItem's generic
     * function/variable dispatch can't do alone (no return type at all,
     * possible initializer list), so this wraps that in enclosing-class
     * context.
     */
    private List<TopLevelItem> parseClassMember(List<Token> leadingComments, String enclosingClassName) {
        if (check(TokenType.PREPROCESSOR_DIRECTIVE)) {
            Token t = advance();
            return List.of(new cppmode.parser.ast.decl.PreprocessorLine(t.text(), t.line(), t.col(), leadingComments));
        }
        List<String> templateParams = List.of();
        if (checkKeyword("template")) {
            templateParams = parseTemplateParamList();
            consumeLeadingComments();
        }
        if (checkKeyword("class") || checkKeyword("struct")) {
            return List.of(parseTypeDef(leadingComments, templateParams));
        }
        if (checkKeyword("enum")) {
            return List.of(parseEnumDecl(leadingComments));
        }
        // Destructor dispatch: "~Name() {...}" or "virtual ~Name() {...}" --
        // must look one token past an optional leading "virtual", since that
        // keyword (confirmed common on destructors, e.g. "virtual ~LSystem()")
        // precedes the '~' that would otherwise be the dispatch signal.
        if (checkOp("~") || (checkKeyword("virtual") && peek(1).isOp("~"))) {
            return List.of(parseFunctionOrConstructorOrDestructor(leadingComments, templateParams));
        }
        // Constructor dispatch: identifier matching the enclosing class name,
        // directly followed by '(' with no return type preceding it. Also
        // allow an optional leading "virtual" for symmetry, though
        // constructors can't actually be virtual in real C++ -- tolerated
        // here rather than rejected, since enforcing that isn't this parser's
        // job (see grammar-scope notes on not modeling full C++ semantics).
        if (check(TokenType.IDENTIFIER) && peek().text().equals(enclosingClassName) && peek(1).isPunct("(")) {
            return List.of(parseFunctionOrConstructorOrDestructor(leadingComments, templateParams));
        }
        return parseFunctionOrVariable(leadingComments, templateParams, false);
    }

    /**
     * Parses a destructor ("~Name() { ... }") or a constructor (handled here
     * too since both share the "no return type, name/~name immediately
     * followed by '('" shape, including the optional virtual/override/const
     * qualifiers and, for constructors, an initializer list).
     */
    private FunctionDecl parseFunctionOrConstructorOrDestructor(List<Token> leadingComments, List<String> templateParams) {
        Token start = peek();
        boolean isVirtual = matchKeyword("virtual");
        boolean isDestructor = matchOp("~");
        String name = expectIdentifier().text();
        if (isDestructor) name = "~" + name;

        List<Param> params = parseParamList();

        boolean isConst = matchKeyword("const");
        boolean isOverride = matchKeyword("override");
        // order-flexible: also accept override before const, just in case
        if (!isConst) isConst = matchKeyword("const");

        List<FunctionDecl.ConstructorInit> initList = new ArrayList<>();
        if (!isDestructor && matchPunct(":")) {
            initList.add(parseConstructorInitEntry());
            while (matchPunct(",")) {
                initList.add(parseConstructorInitEntry());
            }
        }

        // Pure-virtual specifier ("= 0"), e.g. "virtual ~A() = 0;" -- a
        // real, valid C++ idiom for ensuring polymorphic deletion through
        // an abstract base. Found via a realistic builder-pattern sketch
        // using "virtual void draw() = 0;" on an abstract base class --
        // confirmed real, ordinary OOP, not exotic. Must be checked
        // BEFORE the body/";" check below, since "= 0;" replaces both.
        boolean isPureVirtual = false;
        if (checkOp("=")) {
            int save = pos;
            advance();
            if (checkLiteralZero()) {
                advance();
                isPureVirtual = true;
            } else {
                pos = save;
            }
        }

        Block body = checkPunct("{") ? parseBlock() : null;
        if (body == null && !isPureVirtual) expectPunct(";");
        else if (isPureVirtual) expectPunct(";");

        return new FunctionDecl(null, name, templateParams, params, initList, body,
            !isDestructor, isDestructor, isVirtual, isOverride, isConst, false, isPureVirtual,
            start.line(), start.col(), leadingComments);
    }

    private FunctionDecl.ConstructorInit parseConstructorInitEntry() {
        String memberName = expectIdentifier().text();
        expectPunct("(");
        List<Expr> args = new ArrayList<>();
        if (!checkPunct(")")) {
            args.add(parseExpr());
            while (matchPunct(",")) {
                args.add(parseExpr());
            }
        }
        expectPunct(")");
        return new FunctionDecl.ConstructorInit(memberName, args);
    }

    /**
     * Handles every remaining top-level/class-member form: an ordinary
     * function declaration/definition, an operator overload, or a variable
     * declaration (possibly multi-declarator, desugared into multiple
     * VariableDecl nodes sharing one TypeRef).
     *
     * Disambiguation: parse [virtual] [static] [const-is-not-valid-here]
     * returnType, then a name (ordinary identifier OR "operator" followed by
     * an operator token, per the operator-overload fixture). If '(' follows
     * the name, it's a function; otherwise it's a variable declaration
     * (with possible comma-separated additional declarators).
     *
     * @param isTopLevel  true when called from true file scope
     *                     (parseTopLevelItem), false when called from inside
     *                     a class/struct body (parseClassMember). Gates the
     *                     bare-statement (Processing static-mode) fallback,
     *                     which only makes sense at file scope -- a class
     *                     member is always a declaration, never a bare
     *                     statement. Without this gate, the fallback was
     *                     reachable from inside class bodies too, which
     *                     caused a real bug: "bool operator==(...) const {...}"
     *                     as a class member was misjudged as "not a
     *                     declaration" by the fallback's lookahead and
     *                     incorrectly routed into statement parsing instead
     *                     (found via the oop_features.cpp fixture).
     */
    private List<TopLevelItem> parseFunctionOrVariable(List<Token> leadingComments, List<String> templateParams, boolean isTopLevel) {
        Token start = peek();

        // Bare top-level statement fallback (Processing "static mode" sketches,
        // confirmed real by the example corpus's Coordinates.pde -- a flat
        // sequence of statements like "size(640, 360);" with no enclosing
        // setup()/draw() at all). Distinguished from a real declaration by
        // lookahead: a declaration is "TypeName Identifier ...", whereas a
        // bare call statement is "identifier(" with nothing in between. This
        // check must run before any of virtual/static/const are consumed,
        // since none of those prefix a bare statement. Only ever applies at
        // true top level -- see isTopLevel notes above.
        if (isTopLevel && !looksLikeTopLevelDeclarationOrFunction()) {
            Statement stmt = parseStatement(List.of());
            return List.of(new cppmode.parser.ast.decl.TopLevelStatement(stmt, start.line(), start.col(), leadingComments));
        }

        boolean isVirtual = matchKeyword("virtual");
        boolean isStatic = matchKeyword("static");
        boolean isConst = matchKeyword("const");
        if (!isStatic) isStatic = matchKeyword("static"); // tolerate either order

        // Raw function-pointer variable declaration: "Type (*name)(Params) = init;"
        if (looksLikeFunctionPointerVariable()) {
            TypeRef returnType = parseTypeRef();
            NameAndFunctionPointerType decl = parseFunctionPointerDeclaratorTail(returnType);
            Expr initializer = null;
            if (matchOp("=")) {
                initializer = parseExpr();
            }
            expectPunct(";");
            return List.of(new VariableDecl(decl.type(), decl.name(), List.of(), initializer,
                isConst, isStatic, start.line(), start.col(), leadingComments));
        }

        TypeRef type = parseTypeRef();
        String name = parseFunctionOrVariableName();

        if (checkPunct("(") && !looksLikeFunctionPointerDeclarator() && looksLikeParamList()) {
            List<Param> params = parseParamList();
            boolean isOverride = false;
            boolean isMethodConst = false;
            // trailing const/override may appear in either order
            for (int i = 0; i < 2; i++) {
                if (matchKeyword("const")) { isMethodConst = true; continue; }
                if (matchKeyword("override")) { isOverride = true; continue; }
            }
            // Trailing return type: "auto f() -> int" or "auto f() -> MyType"
            // -- the "->" after the parameter list specifies the actual return
            // type when the declared return type is "auto". "->" is classified
            // as PUNCTUATION in this lexer (see Lexer.PUNCT_SYMBOLS), so must
            // be checked with checkPunct, not checkOp.
            if (checkPunct("->")) {
                advance();
                parseTypeRef(); // consume and discard the specified return type
            }
            // Pure-virtual specifier ("= 0"), e.g. "virtual void draw() = 0;"
            // -- a real, ordinary OOP idiom for declaring an abstract
            // method on a base class, found via a realistic builder-
            // pattern sketch. Must be checked BEFORE the body/";" check
            // below, since "= 0;" replaces both.
            boolean isPureVirtual = false;
            if (checkOp("=")) {
                int save = pos;
                advance();
                if (checkLiteralZero()) {
                    advance();
                    isPureVirtual = true;
                } else {
                    pos = save;
                }
            }
            Block body = checkPunct("{") ? parseBlock() : null;
            if (body == null) expectPunct(";");
            FunctionDecl fn = new FunctionDecl(type, name, templateParams, params, List.of(), body,
                false, false, isVirtual, isOverride, isMethodConst, isStatic, isPureVirtual,
                start.line(), start.col(), leadingComments);
            return List.of(fn);
        }

        // Variable declaration, possibly multi-declarator.
        List<TopLevelItem> result = new ArrayList<>();
        result.add(parseOneTopLevelDeclarator(type, name, isConst, isStatic, start, leadingComments));
        while (matchPunct(",")) {
            // Each declarator after the first comma can carry its OWN
            // leading "*"/"&" markers, independent of every other
            // declarator on the line -- real, standard C++ syntax
            // ("int* a, b;" -- only a is a pointer; "int* a, *b;" --
            // both are pointers, the "*" repeated per declarator).
            // Confirmed real and necessary by Scrollbar.pde's own
            // corrected declaration ("HScrollbar* hs1, * hs2;").
            //
            // IMPORTANT: build each declarator's type from the base
            // type's NAME/TEMPLATE-ARGS/CONST ONLY -- never reuse
            // "type"'s own pointerDepth/isReference, which already
            // belongs EXCLUSIVELY to the first declarator (parseTypeRef
            // consumes a "*"/"&" immediately after the base type name,
            // before the first declarator's name is even parsed). A
            // first attempt at this fix added each subsequent
            // declarator's marker count ON TOP of the shared type's
            // existing pointerDepth, which produced "HScrollbar**" for
            // the second declarator in "HScrollbar* hs1, * hs2;"
            // instead of the correct "HScrollbar*" -- caught by
            // checking the RENDERED OUTPUT, not just parse success;
            // "does this parse" and "is the resulting AST actually
            // correct" are different questions, the same lesson from
            // the return-statement misparse bug found much earlier in
            // this project.
            int extraPointerDepth = 0;
            boolean extraIsReference = false;
            while (checkOp("*") || checkOp("&")) {
                if (matchOp("*")) extraPointerDepth++;
                else { matchOp("&"); extraIsReference = true; }
            }
            String nextName = expectIdentifier().text();
            TypeRef declaratorType = type;
            if (type instanceof NamedType nt) {
                declaratorType = new NamedType(nt.baseName(), nt.templateArgs(),
                    extraPointerDepth, extraIsReference, nt.isConst());
            }
            result.add(parseOneTopLevelDeclarator(declaratorType, nextName, isConst, isStatic, start, List.of()));
        }
        expectPunct(";");
        return result;
    }

    /**
     * Lookahead-only: determines whether the upcoming tokens form a real
     * top-level declaration or function definition (possibly preceded by
     * virtual/static/const) rather than a bare statement. A declaration has
     * the shape "[virtual] [static] [const] TypeName Identifier ..."; a bare
     * statement -- the Processing static-mode case -- starts with an
     * identifier immediately followed by something that isn't another
     * identifier (most commonly '(' for a call, or an assignment operator
     * for a plain assignment to an already-declared global).
     */
    private boolean looksLikeTopLevelDeclarationOrFunction() {
        int save = pos;
        try {
            matchKeyword("virtual");
            matchKeyword("static");
            matchKeyword("const");
            matchKeyword("static"); // tolerate either order, mirroring the real parse path

            if (looksLikeFunctionPointerVariable()) return true;

            if (!(check(TokenType.IDENTIFIER) || check(TokenType.KEYWORD))) return false;
            TypeRef type;
            try {
                type = parseTypeRef();
            } catch (ParseException e) {
                return false;
            }
            // "operator==" style names: 'operator' is lexed as a KEYWORD.
            // Two cases reach here:
            //  (a) "operator" itself was parsed as the bare "type" (when
            //      there's no separate return type before it -- not
            //      actually valid C++ for operator overloads but tolerated
            //      defensively), OR
            //  (b) more commonly, a real return type was already consumed
            //      ("bool", "Handle", etc.) and the CURRENT token is now
            //      "operator" itself, e.g. "bool operator==(...)" --
            //      confirmed real and previously missed by the
            //      oop_features.cpp fixture's Handle::operator==.
            // Either way, this is unambiguously a declaration.
            if ((type instanceof NamedType nt && nt.baseName().equals("operator"))
                || checkKeyword("operator")) {
                return true;
            }
            return check(TokenType.IDENTIFIER);
        } finally {
            pos = save;
        }
    }

    /**
     * Parses an ordinary name, "operator" followed by an operator/comparison
     * token (e.g. "operator=="), or a "::"-qualified name for out-of-class
     * static member definitions (e.g. "Counter::count" in
     * "int Counter::count = 0;", confirmed real by the kitchen-sink fixture).
     */
    private String parseFunctionOrVariableName() {
        if (checkKeyword("operator")) {
            Token opKw = advance();
            // Special multi-token operator names:
            // operator[]  -- subscript
            // operator()  -- function call
            // operator new / operator delete
            // operator== / operator+ / etc. -- single-token symbols
            if (checkPunct("[")) {
                advance(); // '['
                expectPunct("]");
                return opKw.text() + "[]";
            }
            if (checkPunct("(")) {
                advance(); // '('
                expectPunct(")");
                return opKw.text() + "()";
            }
            if (checkKeyword("new") || checkKeyword("delete")) {
                Token kw = advance();
                return opKw.text() + " " + kw.text();
            }
            // Cast operator: "operator int", "operator float", "operator bool", etc.
            // ONLY fire for actual C++ primitive type keywords, not arbitrary
            // keywords that happen to follow "operator" in other contexts.
            // Using a small explicit set prevents keywords like "color", "auto",
            // "return", etc. from being misidentified as cast targets.
            if (check(TokenType.KEYWORD) && CAST_OPERATOR_TYPE_KEYWORDS.contains(peek().text())) {
                Token castTok = advance();
                return opKw.text() + " " + castTok.text();
            }
            // Also handle identifier type names used as cast targets (e.g.
            // "operator MyType()" where MyType is a user-defined class).
            // Only safe when the next token is an identifier (not a keyword),
            // so we don't misidentify keyword-named things like "color".
            if (check(TokenType.IDENTIFIER)) {
                Token castTok = advance();
                return opKw.text() + " " + castTok.text();
            }
            Token opTok = advance(); // ordinary single-token operator, e.g. "==", "+", "<<"
            return opKw.text() + opTok.text();
        }
        // Cast-operator case: "operator" was already consumed by parseTypeRef()
        // as NamedType("operator"), leaving a keyword type like "bool"/"int" as
        // the current token. "operator bool()" and "operator int()" reach here.
        // Guard against mistakenly eating real qualifiers, storage specifiers, OR
        // Processing-specific keywords like "color" that are legitimately used as
        // method names (e.g. "Builder& color(int r, int g, int b)" in the
        // builder pattern) -- these must fall through to expectIdentifier() below.
        if (check(TokenType.KEYWORD) && !checkKeyword("const") && !checkKeyword("override")
                && !checkKeyword("virtual") && !checkKeyword("static")
                && !checkKeyword("inline") && !checkKeyword("explicit")
                && !PSEUDO_TYPE_KEYWORDS_USABLE_AS_NAMES.contains(peek().text())) {
            Token castTok = advance();
            String suffix = "";
            while (checkOp("*") || checkOp("&")) suffix += advance().text();
            return "operator " + castTok.text() + suffix;
        }
        String name = expectIdentifier().text();
        while (checkPunct("::")) {
            advance();
            name += "::" + expectIdentifier().text();
        }
        return name;
    }

    private VariableDecl parseOneTopLevelDeclarator(TypeRef type, String name, boolean isConst, boolean isStatic,
                                                     Token start, List<Token> leadingComments) {
        List<Expr> dims = parseOptionalArrayDims();
        Expr initializer = null;
        if (matchOp("=")) {
            initializer = checkPunct("{") ? parseInitializerList() : parseExpr();
        } else if (checkPunct("(")) {
            initializer = parseDirectInitAsCall(name);
        } else if (checkPunct("{")) {
            // Brace-direct-init: "Type name{args};" -- the unambiguous,
            // most-vexing-parse-proof sibling of paren-direct-init
            // ("Type name(args);"). Confirmed necessary by this project's
            // own CodeGen, which deliberately renders the self-named-
            // CallExpr direct-init shape using braces rather than parens
            // (see CodeGen.emitDeclaratorTail's notes on why) -- without
            // this branch, the parser couldn't read its own generated
            // output back, which a round-trip test caught immediately.
            // Represented with the exact same CallExpr shape as the paren
            // form (callee = an Identifier matching the declarator's own
            // name), since semantically they mean the same thing and
            // CodeGen already knows how to render that shape as braces.
            initializer = parseDirectInitAsCall(name);
        }
        return new VariableDecl(type, name, dims, initializer, isConst, isStatic,
            start.line(), start.col(), leadingComments);
    }

    /**
     * Lookahead-only: from the "(" that follows a name (in
     * parseFunctionOrVariable, deciding between "this is a function decl"
     * and "this is direct-init construction syntax"), determines whether the
     * parenthesized content actually looks like a parameter list rather than
     * a constructor-call argument list.
     *
     * Confirmed necessary by Arctangent.pde's "Eye e1(250, 16, 120);" --
     * direct-init with literal constructor arguments. A real parameter list
     * is either empty ("()") or starts with something type-shaped followed
     * by a parameter name; an argument list starting with a literal,
     * a unary operator, or anything else that can't start a TypeRef is
     * unambiguously NOT a parameter list.
     */
    private boolean looksLikeParamList() {
        int save = pos;
        try {
            expectPunct("(");
            if (checkPunct(")")) return true; // "()" -- ambiguous but treated as a function with no params, matching prior behavior
            if (!(check(TokenType.IDENTIFIER) || check(TokenType.KEYWORD))) return false;
            try {
                parseTypeRef();
            } catch (ParseException e) {
                return false;
            }
            // A real parameter needs a name after its type (or a function-
            // pointer declarator's own "(" -- not modeled here since no
            // corpus fixture nests a function-pointer param inside a
            // direct-init-ambiguous position; flagged as a known gap if it
            // ever occurs).
            return check(TokenType.IDENTIFIER);
        } finally {
            pos = save;
        }
    }

    private List<Param> parseParamList() {
        expectPunct("(");
        List<Param> params = new ArrayList<>();
        if (!checkPunct(")")) {
            params.add(parseParam());
            while (matchPunct(",")) {
                params.add(parseParam());
            }
        }
        expectPunct(")");
        return params;
    }

    /**
     * Lookahead for the raw function-pointer VARIABLE form specifically
     * (distinct from looksLikeFunctionPointerDeclarator, which checks
     * starting from the "(" itself once a return type has already been
     * consumed) -- this version starts from before the return type, so it
     * must first skip over a tentative TypeRef.
     */
    private boolean looksLikeFunctionPointerVariable() {
        int save = pos;
        try {
            if (!(check(TokenType.IDENTIFIER) || check(TokenType.KEYWORD))) return false;
            try {
                parseTypeRef();
            } catch (ParseException e) {
                return false;
            }
            return looksLikeFunctionPointerDeclarator();
        } finally {
            pos = save;
        }
    }

    private EnumDecl parseEnumDecl(List<Token> leadingComments) {
        Token start = expectKeyword("enum");
        boolean isScoped = matchKeyword("class");
        String name = expectIdentifier().text();
        expectPunct("{");
        List<String> values = new ArrayList<>();
        if (!checkPunct("}")) {
            values.add(expectIdentifier().text());
            while (matchPunct(",")) {
                if (checkPunct("}")) break; // tolerate a trailing comma
                values.add(expectIdentifier().text());
            }
        }
        expectPunct("}");
        expectPunct(";");
        return new EnumDecl(name, isScoped, values, start.line(), start.col(), leadingComments);
    }

    private NamespaceDecl parseNamespaceDecl(List<Token> leadingComments) {
        Token start = expectKeyword("namespace");
        String name = expectIdentifier().text();
        expectPunct("{");
        List<TopLevelItem> items = new ArrayList<>();
        while (!checkPunct("}")) {
            if (isAtEnd()) throw error("unexpected end of input inside namespace '" + name + "'");
            List<Token> comments = consumeLeadingComments();
            if (checkPunct("}")) break;
            items.addAll(parseTopLevelItem(comments));
        }
        expectPunct("}");
        return new NamespaceDecl(name, items, start.line(), start.col(), leadingComments);
    }

    private UsingNamespaceDecl parseUsingNamespaceDecl(List<Token> leadingComments) {
        Token start = expectKeyword("using");
        expectKeyword("namespace");
        String name = parseQualifiedTypeName();
        expectPunct(";");
        return new UsingNamespaceDecl(name, start.line(), start.col(), leadingComments);
    }

    // ----- Type references ------------------------------------------------

    /**
     * Parses a TypeRef in any position: variable type, return type, param
     * type, template argument, cast target type, etc.
     *
     * Handles, in order:
     *  1. An optional leading "const".
     *  2. The raw C-style function-pointer special case: "Type (*)(Params)".
     *     Detected by lookahead -- after parsing the return type, if we see
     *     "(" "*" we know this is a function pointer declarator, not an
     *     ordinary parenthesized expression (callers parsing a VariableDecl
     *     pass the name through separately -- see parseFunctionPointerDecl
     *     for the full "Type (*name)(Params)" variable-declaration form).
     *  3. An ordinary dotted/"::"-qualified base name (e.g. "std::string").
     *  4. An optional "&lt;...&gt;" template argument list, where each argument
     *     is itself parsed as either a TypeRef or -- specifically inside
     *     std::function's argument list -- a bare function signature
     *     "ReturnType(ParamType, ...)" with no name and no "(*)" (see
     *     tryParseFunctionSignatureArg).
     *  5. Any number of trailing '*' (pointerDepth) and at most one trailing
     *     '&amp;' (isReference).
     */
    private TypeRef parseTypeRef() {
        boolean isConst = matchKeyword("const");

        String baseName = parseQualifiedTypeName();

        List<TypeRef> templateArgs = List.of();
        if (checkOp("<")) {
            templateArgs = parseTemplateArgList();
        }

        int pointerDepth = 0;
        while (matchOp("*")) {
            pointerDepth++;
        }
        boolean isReference = matchOp("&");

        return new NamedType(baseName, templateArgs, pointerDepth, isReference, isConst);
    }

    /**
     * Parses a "::"-joined sequence of identifiers/keywords-used-as-typenames,
     * e.g. "std::string", "MyNamespace::Handle", or a single plain name like
     * "int" or "Handle". Accepts KEYWORD tokens too (not just IDENTIFIER)
     * since C++ builtin type keywords ("int", "float", "bool", "void", "auto",
     * "char", etc.) are lexed as KEYWORD, and "color" (a Processing-flavored
     * pseudo-keyword, see Lexer notes) needs the same treatment.
     */
    /**
     * Parses a "::"-joined sequence of identifiers/keywords-used-as-typenames,
     * e.g. "std::string", "MyNamespace::Handle", or a single plain name like
     * "int" or "Handle". Also handles compound built-in integer type
     * keywords -- "signed"/"unsigned"/"long"/"short" composing with a
     * following base type keyword, e.g. "signed char", "unsigned int",
     * "long long", "unsigned long long" -- confirmed real and previously
     * missed entirely by the original corpus walk (found via real-corpus
     * testing on Datatype_Conversion.pde's "signed char b;").
     */
    private String parseQualifiedTypeName() {
        StringBuilder sb = new StringBuilder();
        sb.append(parseTypeNameSegment());

        // Compound integer-type keyword combinations: greedily consume any
        // run of signed/unsigned/long/short/int/char that follows, since
        // these only ever combine with each other (never with a separate
        // user type name in the same position) -- e.g. "unsigned long long
        // int" is valid C++, "unsigned long long ArrayList" never occurs.
        while ((check(TokenType.KEYWORD))
               && isIntegerTypeModifierOrBase(peek().text())
               && isIntegerTypeModifierOrBase(sb.toString())) {
            sb.append(' ').append(parseTypeNameSegment());
        }

        while (checkPunct("::")) {
            advance();
            sb.append("::").append(parseTypeNameSegment());
        }
        return sb.toString();
    }

    private static final java.util.Set<String> INTEGER_TYPE_WORDS = java.util.Set.of(
        "signed", "unsigned", "long", "short", "int", "char"
    );

    /** True if every space-separated word in s is one of the integer-type-modifier/base words. */
    private boolean isIntegerTypeModifierOrBase(String s) {
        for (String word : s.split(" ")) {
            if (!INTEGER_TYPE_WORDS.contains(word)) return false;
        }
        return true;
    }

    private String parseTypeNameSegment() {
        Token t = peek();
        if (t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD) {
            advance();
            return t.text();
        }
        throw error("expected a type name but found '" + t.text() + "'");
    }

    /**
     * Parses "&lt;Arg1, Arg2, ...&gt;" where each Arg is either an ordinary
     * TypeRef or a bare function-signature (for std::function&lt;Ret(Params)&gt;).
     */
    private List<TypeRef> parseTemplateArgList() {
        expectOp("<");
        List<TypeRef> args = new ArrayList<>();
        if (!checkOp(">")) {
            args.add(parseTemplateArg());
            while (matchPunct(",")) {
                args.add(parseTemplateArg());
            }
        }
        // Note: ">>"  closing two nested template lists at once (e.g.
        // "Pair<int, ArrayList<T>>") is lexed as a single ">>" OPERATOR token
        // by the lexer's longest-match rule, not as two separate '>' tokens --
        // the corpus's "template angle brackets vs shift/comparison ambiguity"
        // smoke test confirmed ">>" lexes as one token. We must special-case
        // that here: a closing ">>" needs to close *this* list and leave a
        // single '>' behind for the enclosing list to consume.
        if (checkOp(">>")) {
            splitTrailingShiftIntoTwoCloseAngles();
        }
        expectOp(">");
        return args;
    }

    /**
     * Rewrites the current ">>" token into two consecutive ">" tokens in the
     * token stream, so nested template arg lists each consume exactly one
     * '>' as their closer. This mutates the token list in place rather than
     * re-lexing, which is simpler than threading "are we inside a template
     * list" state back into the lexer (the lexer has no such context, and
     * shouldn't need one -- this is a parser-level concern only).
     */
    private void splitTrailingShiftIntoTwoCloseAngles() {
        Token shift = tokens.get(pos);
        Token first = new Token(TokenType.OPERATOR, ">", shift.line(), shift.col());
        Token second = new Token(TokenType.OPERATOR, ">", shift.line(), shift.col() + 1);
        tokens.set(pos, first);
        tokens.add(pos + 1, second);
    }

    private TypeRef parseTemplateArg() {
        TypeRef maybeReturnType = parseTypeRef();
        // If a '(' follows immediately, this was actually the return type of a
        // bare function-signature template arg (std::function<void(int)>),
        // not a complete type by itself -- reinterpret.
        if (checkPunct("(")) {
            return parseFunctionSignatureTail(maybeReturnType);
        }
        return maybeReturnType;
    }

    /** Parses "(ParamType, ...)" given an already-parsed return type, producing a FunctionSignatureType. */
    private FunctionSignatureType parseFunctionSignatureTail(TypeRef returnType) {
        expectPunct("(");
        List<TypeRef> paramTypes = new ArrayList<>();
        if (!checkPunct(")")) {
            paramTypes.add(parseTypeRef());
            while (matchPunct(",")) {
                paramTypes.add(parseTypeRef());
            }
        }
        expectPunct(")");
        return new FunctionSignatureType(returnType, paramTypes);
    }

    /**
     * Detects whether the upcoming tokens form a raw C-style function pointer
     * declarator: "(" "*" IDENTIFIER ")" "(" -- used by declaration parsing to
     * decide whether a "(" right after a type belongs to this special form
     * rather than being parsed as part of an ordinary VariableDecl. Lookahead
     * only; does not consume.
     */
    private boolean looksLikeFunctionPointerDeclarator() {
        return checkPunct("(") && peek(1).isOp("*")
            && peek(2).type() == TokenType.IDENTIFIER
            && peek(3).isPunct(")");
    }

    /**
     * Parses the remainder of "Type (*name)(ParamTypes...)" given an
     * already-parsed return type, returning the declared variable's name and
     * its FunctionPointerType. Confirmed necessary by the corpus's
     * useRawFunctionPointer fixture.
     */
    private NameAndFunctionPointerType parseFunctionPointerDeclaratorTail(TypeRef returnType) {
        expectPunct("(");
        expectOp("*");
        String name = expectIdentifier().text();
        expectPunct(")");
        expectPunct("(");
        List<TypeRef> paramTypes = new ArrayList<>();
        if (!checkPunct(")")) {
            paramTypes.add(parseTypeRef());
            while (matchPunct(",")) {
                paramTypes.add(parseTypeRef());
            }
        }
        expectPunct(")");
        return new NameAndFunctionPointerType(name, new FunctionPointerType(returnType, paramTypes));
    }

    private record NameAndFunctionPointerType(String name, FunctionPointerType type) {
    }

    // ----- Expressions (precedence climbing, low to high) -----------------
    //
    // assignment  -> ternary (("=") assignment)?              [right-assoc]
    // ternary     -> logicalOr ("?" expr ":" ternary)?
    // logicalOr   -> logicalAnd ("||" logicalAnd)*
    // logicalAnd  -> bitOr ("&&" bitOr)*
    // bitOr       -> bitXor ("|" bitXor)*
    // bitXor      -> bitAnd ("^" bitAnd)*
    // bitAnd      -> equality ("&" equality)*
    // equality    -> relational (("=="|"!=") relational)*
    // relational  -> shift (("<"|">"|"<="|">=") shift)*
    // shift       -> additive (("<<"|">>") additive)*
    // additive    -> multiplicative (("+"|"-") multiplicative)*
    // multiplicative -> unary (("*"|"/"|"%") unary)*
    // unary       -> ("-"|"!"|"&"|"~"|"++"|"--") unary | postfix
    // postfix     -> primary (call | member | index | "++" | "--")*
    // primary     -> literal | identifier | scopedName | "(" expr ")"
    //              | lambda | new | cast | initializerList

    private static final java.util.Set<String> COMPOUND_ASSIGN_OPS = java.util.Set.of(
        "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>="
    );

    Expr parseExpr() {
        return parseAssignment();
    }

    private Expr parseAssignment() {
        Expr left = parseTernary();
        if (checkOp("=")) {
            Token t = advance();
            Expr right = parseAssignment(); // right-associative
            return new AssignExpr(left, right, t.line(), t.col(), List.of());
        }
        if (peek().type() == TokenType.OPERATOR && COMPOUND_ASSIGN_OPS.contains(peek().text())) {
            Token t = advance();
            Expr right = parseAssignment();
            return new BinaryExpr(t.text(), left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private Expr parseTernary() {
        Expr cond = parseLogicalOr();
        if (checkPunct("?")) {
            Token t = advance();
            Expr thenExpr = parseExpr();
            expectPunct(":");
            Expr elseExpr = parseTernary();
            return new TernaryExpr(cond, thenExpr, elseExpr, t.line(), t.col(), List.of());
        }
        return cond;
    }

    private Expr parseLogicalOr() {
        Expr left = parseLogicalAnd();
        while (checkOp("||")) {
            Token t = advance();
            Expr right = parseLogicalAnd();
            left = new BinaryExpr("||", left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private Expr parseLogicalAnd() {
        Expr left = parseBitOr();
        while (checkOp("&&")) {
            Token t = advance();
            Expr right = parseBitOr();
            left = new BinaryExpr("&&", left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private Expr parseBitOr() {
        Expr left = parseBitXor();
        while (checkOp("|")) {
            Token t = advance();
            Expr right = parseBitXor();
            left = new BinaryExpr("|", left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private Expr parseBitXor() {
        Expr left = parseBitAnd();
        while (checkOp("^")) {
            Token t = advance();
            Expr right = parseBitAnd();
            left = new BinaryExpr("^", left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private Expr parseBitAnd() {
        Expr left = parseEquality();
        while (checkOp("&")) {
            Token t = advance();
            Expr right = parseEquality();
            left = new BinaryExpr("&", left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private Expr parseEquality() {
        Expr left = parseRelational();
        while (checkOp("==") || checkOp("!=")) {
            Token t = advance();
            Expr right = parseRelational();
            left = new BinaryExpr(t.text(), left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private Expr parseRelational() {
        Expr left = parseShift();
        while (checkOp("<") || checkOp(">") || checkOp("<=") || checkOp(">=")) {
            Token t = advance();
            Expr right = parseShift();
            left = new BinaryExpr(t.text(), left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private Expr parseShift() {
        Expr left = parseAdditive();
        while (checkOp("<<") || checkOp(">>")) {
            Token t = advance();
            Expr right = parseAdditive();
            left = new BinaryExpr(t.text(), left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private Expr parseAdditive() {
        Expr left = parseMultiplicative();
        while (checkOp("+") || checkOp("-")) {
            Token t = advance();
            Expr right = parseMultiplicative();
            left = new BinaryExpr(t.text(), left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private Expr parseMultiplicative() {
        Expr left = parseUnary();
        while (checkOp("*") || checkOp("/") || checkOp("%")) {
            Token t = advance();
            Expr right = parseUnary();
            left = new BinaryExpr(t.text(), left, right, t.line(), t.col(), List.of());
        }
        return left;
    }

    private static final java.util.Set<String> UNARY_OPS = java.util.Set.of("-", "!", "&", "~", "+", "*");

    private Expr parseUnary() {
        if (checkOp("++") || checkOp("--")) {
            Token t = advance();
            Expr operand = parseUnary();
            return new UnaryExpr(t.text(), operand, t.line(), t.col(), List.of());
        }
        if (peek().type() == TokenType.OPERATOR && UNARY_OPS.contains(peek().text())) {
            Token t = advance();
            Expr operand = parseUnary();
            return new UnaryExpr(t.text(), operand, t.line(), t.col(), List.of());
        }
        if (checkKeyword("new")) {
            return parseNew();
        }
        if (checkKeyword("delete")) {
            // delete is modeled as a statement (DeleteStatement) per the AST
            // design notes -- it should never be reached from expression
            // context. If it is, that's a real grammar gap, not something to
            // silently paper over.
            throw error("'delete' is only valid as a statement, not inside an expression");
        }
        // C-style cast: "(" TypeName ")" expr -- only recognized when the
        // parenthesized content is unambiguously a type (an identifier/keyword
        // optionally followed by '*'/'&'/template-args, then immediately ')'),
        // and is followed by something that can start an expression. This
        // disambiguates from a plain parenthesized expression like "(a + b)".
        if (checkPunct("(") && looksLikeCast()) {
            return parseCast();
        }
        return parsePostfix();
    }

    /**
     * Lookahead-only check for whether the upcoming "(...)" is a C-style cast
     * rather than a parenthesized expression. We tentatively try parsing a
     * TypeRef starting just after "(" and require it to be immediately
     * followed by ")" and then something that looks like the start of a unary
     * expression (identifier, literal, "(", unary operator). This is a
     * backtracking lookahead -- cheap here since types are short and this
     * only runs when we've already seen "(".
     */
    private boolean looksLikeCast() {
        int save = pos;
        try {
            if (!matchPunct("(")) return false;
            // Must look like a type: starts with an identifier or type-keyword.
            if (!(check(TokenType.IDENTIFIER) || check(TokenType.KEYWORD))) return false;
            TypeRef ignored;
            try {
                ignored = parseTypeRef();
            } catch (ParseException e) {
                return false;
            }
            if (!checkPunct(")")) return false;
            advance(); // consume ')'
            // What follows a real cast must be able to start a unary expression.
            return canStartExpression(peek());
        } finally {
            pos = save;
        }
    }

    private boolean canStartExpression(Token t) {
        if (t.type() == TokenType.IDENTIFIER || t.type() == TokenType.INT_LITERAL
            || t.type() == TokenType.FLOAT_LITERAL || t.type() == TokenType.STRING_LITERAL
            || t.type() == TokenType.CHAR_LITERAL || t.type() == TokenType.BOOL_LITERAL) {
            return true;
        }
        if (t.isPunct("(")) return true;
        if (t.isOp("-") || t.isOp("!") || t.isOp("&") || t.isOp("~") || t.isOp("++") || t.isOp("--")) return true;
        if (t.isKeyword("new")) return true;
        return false;
    }

    private Expr parseCast() {
        Token start = peek();
        expectPunct("(");
        TypeRef targetType = parseTypeRef();
        expectPunct(")");
        Expr expr = parseUnary();
        return new CastExpr(targetType, expr, start.line(), start.col(), List.of());
    }

    private Expr parseNew() {
        Token start = expectKeyword("new");
        TypeRef type = parseTypeRef();
        if (matchPunct("[")) {
            Expr sizeExpr = parseExpr();
            expectPunct("]");
            return new ArrayNewExpr(type, sizeExpr, start.line(), start.col(), List.of());
        }
        List<Expr> args = new ArrayList<>();
        if (matchPunct("(")) {
            if (!checkPunct(")")) {
                args.add(parseExpr());
                while (matchPunct(",")) {
                    args.add(parseExpr());
                }
            }
            expectPunct(")");
        }
        return new NewExpr(type, args, start.line(), start.col(), List.of());
    }

    private Expr parsePostfix() {
        Expr expr = parsePrimary();
        while (true) {
            if (checkPunct(".") || checkPunct("->")) {
                boolean isArrow = checkPunct("->");
                Token t = advance();
                String member = expectIdentifierOrKeywordName();
                // Sibling fix to the std::vector<int>(...) bug found via
                // RealHeaderStressTest: a member name can ALSO be
                // followed by explicit template arguments before the
                // call parens ("obj.method<int>(5)" -- calling a
                // template member function with an explicit type
                // argument, since the argument types alone aren't always
                // enough for the compiler to deduce it). Without this
                // check, "obj.method<int>(5)" misparses the same way
                // "std::vector<int>(rows)" did before that fix: as
                // "obj.method" followed by an unrelated
                // "< int > (5)" comparison chain. Folded the member name
                // and its template args into one combined member-name
                // string, consistent with how a templated callee
                // Identifier already folds its name and args together
                // elsewhere in this parser.
                if (checkOp("<") && looksLikeTemplatedConstructionCallee()) {
                    List<TypeRef> templateArgs = parseTemplateArgList();
                    member = member + "<" + renderTemplateArgs(templateArgs) + ">";
                }
                expr = new MemberAccessExpr(expr, member, isArrow, t.line(), t.col(), List.of());
            } else if (checkPunct("(")) {
                Token t = peek();
                List<Expr> args = parseArgList();
                expr = new CallExpr(expr, args, t.line(), t.col(), List.of());
            } else if (checkPunct("{") && expr instanceof Identifier id && id.name().contains("<")) {
                // Brace-init of a templated type, e.g. "Rect<float>{x, y, w, h}"
                // -- only reachable when the callee Identifier already has
                // template args folded into its name (confirmed by
                // looksLikeTemplatedConstructionCallee's matching '{'
                // check), so this can't misfire on an ordinary identifier
                // immediately followed by an unrelated block.
                Token t = peek();
                InitializerListExpr init = (InitializerListExpr) parseInitializerList();
                expr = new CallExpr(expr, init.elements(), true, t.line(), t.col(), List.of());
            } else if (checkPunct("[")) {
                Token t = advance();
                Expr index = parseExpr();
                expectPunct("]");
                expr = new IndexExpr(expr, index, t.line(), t.col(), List.of());
            } else if (checkOp("++") || checkOp("--")) {
                Token t = advance();
                expr = new PostfixExpr(t.text(), expr, t.line(), t.col(), List.of());
            } else {
                break;
            }
        }
        return expr;
    }

    /**
     * Member names are usually identifiers but may be a few keyword-shaped
     * tokens too in practice (e.g. nothing in-scope right now requires this,
     * but kept permissive since C++ member names are never actual keywords
     * in valid code -- this mainly just defers to expectIdentifier).
     */
    private String expectIdentifierOrKeywordName() {
        return expectIdentifier().text();
    }

    private List<Expr> parseArgList() {
        expectPunct("(");
        List<Expr> args = new ArrayList<>();
        if (!checkPunct(")")) {
            args.add(parseExpr());
            while (matchPunct(",")) {
                args.add(parseExpr());
            }
        }
        expectPunct(")");
        return args;
    }

    private Expr parsePrimary() {
        Token t = peek();

        switch (t.type()) {
            case INT_LITERAL -> { advance(); return new Literal(Literal.Kind.INT, t.text(), t.line(), t.col(), List.of()); }
            case FLOAT_LITERAL -> { advance(); return new Literal(Literal.Kind.FLOAT, t.text(), t.line(), t.col(), List.of()); }
            case STRING_LITERAL -> { advance(); return new Literal(Literal.Kind.STRING, t.text(), t.line(), t.col(), List.of()); }
            case CHAR_LITERAL -> { advance(); return new Literal(Literal.Kind.CHAR, t.text(), t.line(), t.col(), List.of()); }
            case BOOL_LITERAL -> { advance(); return new Literal(Literal.Kind.BOOL, t.text(), t.line(), t.col(), List.of()); }
            default -> { /* fall through below */ }
        }

        if (checkPunct("[")) {
            // lambda capture-list start
            return parseLambda();
        }
        if (checkPunct("(")) {
            advance();
            Expr inner = parseExpr();
            expectPunct(")");
            return inner;
        }
        if (checkPunct("{")) {
            return parseInitializerList();
        }
        if (t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD) {
            // Could be a plain identifier, the start of a "::"-qualified
            // ScopedName, or a bare templated-construction-call callee like
            // "ArrayList<Handle>()" / "PenroseSnowflakeLSystem()" (the latter
            // with no template args at all). Confirmed by the corpus as the
            // no-"new" construction idiom (see CallExpr's design notes).
            //
            // The tricky case is the templated form: "ArrayList<Handle>()"
            // is syntactically ambiguous with "a < b > ()" (comparisons
            // chained with an empty-paren expression, which isn't valid
            // anyway, but the grammar can't assume that). We resolve it with
            // a backtracking lookahead: if a '<' follows the identifier AND
            // the content up to a matching '>' parses as a template-arg-like
            // list AND is immediately followed by '(', treat the whole
            // "Name<Args>" as one callee identifier whose text includes the
            // template args verbatim -- semantic resolution (is this really
            // a constructor call) is deferred to a later pass either way,
            // consistent with how the untemplated bare-call case already
            // works.
            String first = t.text();
            advance();
            if (checkPunct("::")) {
                List<String> parts = new ArrayList<>();
                parts.add(first);
                while (matchPunct("::")) {
                    parts.add(parseTypeNameSegment());
                }
                // BUG FIX, found via RealHeaderStressTest against the real
                // engine headers (Game_Of_Life.pde's real
                // "cells.resize(cols, std::vector<int>(rows));"): this
                // branch used to return immediately as a plain ScopedName
                // the moment it saw "::", WITHOUT ever checking for a
                // following "<Args>(" templated-construction-call suffix
                // -- unlike the bare-identifier case just below (line
                // ~1342 in this method), which already had this check.
                // This meant "std::vector<int>(rows)" was parsed as
                // "std::vector" (a ScopedName) followed by a SEPARATE,
                // unrelated "< int > (rows)" comparison-chain expression
                // -- confirmed directly: g++ rejected the resulting
                // "((std::vector < int) > rows)" with "template argument
                // 1 is invalid". A bare bare "Foo<int>(...)" (no "::")
                // already worked correctly; only the "::"-qualified form
                // (which is exactly what every "std::"-prefixed standard
                // container construction looks like) had this gap.
                String joined = String.join("::", parts);
                if (checkOp("<") && looksLikeTemplatedConstructionCallee()) {
                    List<TypeRef> templateArgs = parseTemplateArgList();
                    String rendered = joined + "<" + renderTemplateArgs(templateArgs) + ">";
                    return new Identifier(rendered, t.line(), t.col(), List.of());
                }
                return new ScopedName(parts, t.line(), t.col(), List.of());
            }
            if (checkOp("<") && looksLikeTemplatedConstructionCallee()) {
                List<TypeRef> templateArgs = parseTemplateArgList();
                String rendered = first + "<" + renderTemplateArgs(templateArgs) + ">";
                return new Identifier(rendered, t.line(), t.col(), List.of());
            }
            return new Identifier(first, t.line(), t.col(), List.of());
        }

        throw error("unexpected token '" + t.text() + "' while parsing an expression");
    }

    private String renderTemplateArgs(List<TypeRef> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(args.get(i).describe());
        }
        return sb.toString();
    }

    /**
     * Lookahead-only: from just before the '<' following an identifier,
     * determines whether this is a templated-construction-call callee
     * ("Name<Args>(" ) rather than a less-than comparison. Tries to parse a
     * template-arg list and checks that '(' immediately follows. Restores
     * position regardless.
     */
    private boolean looksLikeTemplatedConstructionCallee() {
        int save = pos;
        try {
            try {
                parseTemplateArgList();
            } catch (ParseException e) {
                return false;
            }
            // Brace-init of a templated type ("Rect<float>{x, y, w, h}")
            // is real, ordinary modern C++ -- found via a realistic
            // quadtree sketch using "new QuadNode(Rect<float>{...})".
            // This check previously only recognized the paren-init form
            // ("Name<Args>(...)"), the exact same gap (fixed elsewhere
            // for std::vector<int>(...) and obj.method<int>(...)) just
            // for brace syntax instead of parens.
            return checkPunct("(") || checkPunct("{");
        } finally {
            pos = save;
        }
    }

    private Expr parseInitializerList() {
        Token start = expectPunct("{");
        List<Expr> elements = new ArrayList<>();
        if (!checkPunct("}")) {
            elements.add(parseExpr());
            while (matchPunct(",")) {
                elements.add(parseExpr());
            }
        }
        expectPunct("}");
        return new InitializerListExpr(elements, start.line(), start.col(), List.of());
    }

    /**
     * Parses a lambda: "[captures](params) [-> ReturnType] { body }".
     * Capture forms confirmed by the corpus: "[]" (none), "[x]" (by value),
     * "[&x]" (by reference).
     */
    private Expr parseLambda() {
        Token start = expectPunct("[");
        List<Capture> captures = new ArrayList<>();
        if (!checkPunct("]")) {
            captures.add(parseCapture());
            while (matchPunct(",")) {
                captures.add(parseCapture());
            }
        }
        expectPunct("]");

        List<Param> params = new ArrayList<>();
        expectPunct("(");
        if (!checkPunct(")")) {
            params.add(parseParam());
            while (matchPunct(",")) {
                params.add(parseParam());
            }
        }
        expectPunct(")");

        TypeRef returnType = null;
        if (matchPunct("->")) {
            returnType = parseTypeRef();
        }

        Block body = parseBlock();
        return new LambdaExpr(captures, params, returnType, body, start.line(), start.col(), List.of());
    }

    private Capture parseCapture() {
        // Capture-ALL forms ("[=]" -- everything by value, "[&]" --
        // everything by reference) are real, common, idiomatic lambda
        // syntax that this method never recognized at all -- it
        // unconditionally called expectIdentifier(), assuming every
        // capture is a named identifier, with no check for a bare "="
        // or a lone "&" (not followed by a name) first.
        //
        // Represented using the EXISTING Capture(name, byRef) shape, no
        // AST change needed: "[&]" is byRef=true with an empty name
        // (CodeGen's existing "if (byRef) append('&'); append(name);"
        // rendering already produces exactly "&" for this with zero
        // changes); "[=]" is byRef=false with name="=" (renders the
        // literal "=" token, matching real C++ syntax exactly, since
        // CodeGen always appends cap.name() verbatim).
        if (checkOp("=")) {
            advance();
            return new Capture("=", false);
        }
        if (checkOp("&") && peek(1).text().equals("]")) {
            advance();
            return new Capture("", true);
        }
        boolean byRef = matchOp("&");
        String name = expectIdentifier().text();
        return new Capture(name, byRef);
    }

    /** Parses a single "Type name [= default]" parameter, shared by function decls and lambdas. */
    /**
     * Parses a single "Type name [= default]" parameter, shared by function
     * decls and lambdas. Also handles the C-style array-parameter form
     * ("int data[]"), confirmed real by Pie_Chart.pde's
     * "void pieChart(float diameter, int data[], int length)" -- per real
     * C++ semantics, an array parameter decays to a pointer, so this is
     * represented by bumping the TypeRef's pointerDepth by one rather than
     * adding a separate array-dims field to Param (which would need to mean
     * something different from -- and easily confusable with -- the fixed-
     * size array dims already tracked on VariableDecl/DeclStatement).
     */
    private Param parseParam() {
        TypeRef type = parseTypeRef();
        String name = expectIdentifier().text();
        // C-style array parameter suffixes: "float m[]", "float m[3]",
        // "float m[3][3]", "float m[][3]", etc.
        // The first pair decays to a pointer (bump pointerDepth).
        // Inner pairs (2nd onwards) record their sizes for correct codegen:
        // "float m[3][3]" must render as "float (*m)[3]", not "float* m",
        // because m[i][j] on float* gives "float[int]" -- invalid.
        List<Integer> innerDims = new ArrayList<>();
        if (checkPunct("[")) {
            boolean first = true;
            while (checkPunct("[")) {
                advance(); // '['
                Integer dimSize = null;
                if (!checkPunct("]")) {
                    // grab the size if it's a simple integer literal
                    if (peek().type() == TokenType.INT_LITERAL) {
                        try { dimSize = Integer.parseInt(peek().text()); } catch (NumberFormatException ignored) {}
                    }
                    parseExpr(); // consume the dimension expr regardless
                }
                expectPunct("]");
                if (!first) innerDims.add(dimSize != null ? dimSize : 0);
                first = false;
            }
            type = bumpPointerDepth(type);
        }
        Expr defaultValue = null;
        if (matchOp("=")) {
            defaultValue = parseExpr();
        }
        return new Param(type, name, defaultValue, innerDims);
    }

    private TypeRef bumpPointerDepth(TypeRef type) {
        if (type instanceof NamedType nt) {
            return new NamedType(nt.baseName(), nt.templateArgs(), nt.pointerDepth() + 1, nt.isReference(), nt.isConst());
        }
        // Function-pointer/function-signature types can't sensibly gain a
        // C-style array-parameter pointer bump this way; not encountered by
        // any corpus fixture in this position, so left as-is rather than
        // guessing at a shape nothing has confirmed yet.
        return type;
    }

    // ----- Statements -------------------------------------------------------

    /**
     * Parses a brace-delimited block. Handles multi-declarator desugaring
     * directly here (e.g. "int boxx, boxy;" inside a method body, confirmed
     * present in the Handles fixture): a single source statement with N
     * comma-separated declarators becomes N consecutive DeclStatement nodes
     * in this block's statement list. This is the right place to do the
     * splicing since Block is the only structure that owns a List<Statement>
     * it can freely expand -- parseStatement itself can only return one node.
     */
    private Block parseBlock() {
        Token start = expectPunct("{");
        List<Statement> statements = new ArrayList<>();
        while (!checkPunct("}")) {
            if (isAtEnd()) {
                throw error("unexpected end of input while looking for closing '}'");
            }
            List<Token> comments = consumeLeadingComments();
            if (checkPunct("}")) break;
            statements.addAll(parseStatementOrMultiDecl(comments));
        }
        expectPunct("}");
        return new Block(statements, start.line(), start.col(), List.of());
    }

    /**
     * Returns one or more statements: more than one only when this was a
     * multi-declarator local declaration ("int x, y;"), which desugars into
     * one DeclStatement per declarator, all sharing the same TypeRef.
     */
    private List<Statement> parseStatementOrMultiDecl(List<Token> leadingComments) {
        if (looksLikeDeclaration()) {
            return new ArrayList<Statement>(parseDeclStatementsDesugared(leadingComments));
        }
        return List.of(parseStatement(leadingComments));
    }

    /**
     * Dispatches on lookahead to the correct single-statement parser.
     * Declarations are handled by the caller (parseStatementOrMultiDecl)
     * before this is reached in the common case, but this method still
     * handles the declaration form too for callers that only ever expect
     * exactly one declarator (if/for/while bodies that are a single
     * statement with no braces, e.g. "if (x) int y = 1;" -- unusual but
     * grammatically legal, and not distinguishable from the multi-decl case
     * until we've already looked).
     */
    private Statement parseStatement(List<Token> leadingComments) {
        if (checkPunct("{")) {
            Block b = parseBlock();
            return new Block(b.statements(), b.line(), b.col(), leadingComments);
        }
        if (checkKeyword("if")) return parseIf(leadingComments);
        if (checkKeyword("for")) return parseForOrRangeFor(leadingComments);
        if (checkKeyword("while")) return parseWhile(leadingComments);
        if (checkKeyword("do")) return parseDoWhile(leadingComments);
        if (checkKeyword("switch")) return parseSwitch(leadingComments);
        if (checkKeyword("return")) return parseReturn(leadingComments);
        if (checkKeyword("break")) return parseBreak(leadingComments);
        if (checkKeyword("continue")) return parseContinue(leadingComments);
        if (checkKeyword("try")) return parseTry(leadingComments);
        if (checkKeyword("delete")) return parseDelete(leadingComments);
        if (looksLikeDeclaration()) {
            List<DeclStatement> decls = parseDeclStatementsDesugared(leadingComments);
            if (decls.size() > 1) {
                throw error("multiple comma-separated declarators are not supported in a single-statement context (e.g. directly inside an 'if'/'for'/'while' with no braces)");
            }
            return decls.get(0);
        }
        return parseExprStatement(leadingComments);
    }

    private Statement parseIf(List<Token> leadingComments) {
        Token start = expectKeyword("if");
        expectPunct("(");
        Expr cond = parseExpr();
        expectPunct(")");
        Statement thenBranch = parseStatement(consumeLeadingComments());
        Statement elseBranch = null;
        int save = pos;
        consumeLeadingComments();
        if (checkKeyword("else")) {
            advance();
            elseBranch = parseStatement(consumeLeadingComments());
        } else {
            pos = save; // no else; rewind past any comments consumed speculatively
        }
        return new IfStatement(cond, thenBranch, elseBranch, start.line(), start.col(), leadingComments);
    }

    /**
     * Disambiguates "for (init; cond; update) body" from
     * "for (Type name : iterable) body" by lookahead.
     */
    private Statement parseForOrRangeFor(List<Token> leadingComments) {
        Token start = expectKeyword("for");
        expectPunct("(");
        if (looksLikeRangeFor()) {
            TypeRef declType = parseTypeRef();
            boolean isReference = false;
            if (declType instanceof NamedType nt && nt.isReference()) {
                isReference = true;
                declType = new NamedType(nt.baseName(), nt.templateArgs(), nt.pointerDepth(), false, nt.isConst());
            }
            String declName = expectIdentifier().text();
            expectPunct(":");
            Expr iterable = parseExpr();
            expectPunct(")");
            Statement body = parseStatement(consumeLeadingComments());
            return new RangeForStatement(declType, declName, isReference, iterable, body,
                start.line(), start.col(), leadingComments);
        }

        Statement init = null;
        if (!checkPunct(";")) {
            if (looksLikeDeclaration()) {
                List<DeclStatement> decls = parseDeclStatementsDesugared(List.of());
                if (decls.size() > 1) {
                    throw error("multiple comma-separated declarators are not supported in a for-loop init clause");
                }
                init = decls.get(0);
            } else {
                Token t = peek();
                Expr e = parseExpr();
                expectPunct(";");
                init = new ExprStatement(e, t.line(), t.col(), List.of());
            }
        } else {
            advance(); // consume the bare ';'
        }
        Expr cond = checkPunct(";") ? null : parseExpr();
        expectPunct(";");
        Expr update = checkPunct(")") ? null : parseExpr();
        expectPunct(")");
        Statement body = parseStatement(consumeLeadingComments());
        return new ForStatement(init, cond, update, body, start.line(), start.col(), leadingComments);
    }

    /**
     * Lookahead-only: from just after "for (", determine whether this is a
     * range-for by tentatively parsing a TypeRef + identifier and checking
     * whether ':' (not ';') follows. Restores position regardless.
     */
    private boolean looksLikeRangeFor() {
        int save = pos;
        try {
            if (!(check(TokenType.IDENTIFIER) || check(TokenType.KEYWORD))) return false;
            try {
                parseTypeRef();
            } catch (ParseException e) {
                return false;
            }
            if (!check(TokenType.IDENTIFIER)
                && !(check(TokenType.KEYWORD) && PSEUDO_TYPE_KEYWORDS_USABLE_AS_NAMES.contains(peek().text()))) {
                return false;
            }
            advance();
            return checkPunct(":");
        } finally {
            pos = save;
        }
    }

    private Statement parseWhile(List<Token> leadingComments) {
        Token start = expectKeyword("while");
        expectPunct("(");
        Expr cond = parseExpr();
        expectPunct(")");
        Statement body = parseStatement(consumeLeadingComments());
        return new WhileStatement(cond, body, start.line(), start.col(), leadingComments);
    }

    private Statement parseDoWhile(List<Token> leadingComments) {
        Token start = expectKeyword("do");
        Statement body = parseStatement(consumeLeadingComments());
        expectKeyword("while");
        expectPunct("(");
        Expr cond = parseExpr();
        expectPunct(")");
        expectPunct(";");
        return new DoWhileStatement(body, cond, start.line(), start.col(), leadingComments);
    }

    private Statement parseSwitch(List<Token> leadingComments) {
        Token start = expectKeyword("switch");
        expectPunct("(");
        Expr subject = parseExpr();
        expectPunct(")");
        expectPunct("{");
        List<SwitchCase> cases = new ArrayList<>();
        while (!checkPunct("}")) {
            consumeLeadingComments();
            if (checkPunct("}")) break;
            Expr matchValue;
            if (matchKeyword("case")) {
                matchValue = parseExpr();
            } else {
                expectKeyword("default");
                matchValue = null;
            }
            expectPunct(":");
            List<Statement> body = new ArrayList<>();
            while (!checkKeyword("case") && !checkKeyword("default") && !checkPunct("}")) {
                List<Token> comments = consumeLeadingComments();
                if (checkKeyword("case") || checkKeyword("default") || checkPunct("}")) break;
                body.addAll(parseStatementOrMultiDecl(comments));
            }
            cases.add(new SwitchCase(matchValue, body));
        }
        expectPunct("}");
        return new SwitchStatement(subject, cases, start.line(), start.col(), leadingComments);
    }

    private Statement parseReturn(List<Token> leadingComments) {
        Token start = expectKeyword("return");
        Expr value = checkPunct(";") ? null : parseExpr();
        expectPunct(";");
        return new ReturnStatement(value, start.line(), start.col(), leadingComments);
    }

    private Statement parseBreak(List<Token> leadingComments) {
        Token start = expectKeyword("break");
        expectPunct(";");
        return new BreakStatement(start.line(), start.col(), leadingComments);
    }

    private Statement parseContinue(List<Token> leadingComments) {
        Token start = expectKeyword("continue");
        expectPunct(";");
        return new ContinueStatement(start.line(), start.col(), leadingComments);
    }

    private Statement parseTry(List<Token> leadingComments) {
        Token start = expectKeyword("try");
        Block tryBlock = parseBlock();
        List<CatchClause> clauses = new ArrayList<>();
        while (matchKeyword("catch")) {
            expectPunct("(");
            CatchClause clause;
            if (matchPunct("...")) {
                expectPunct(")");
                Block body = parseBlock();
                clause = new CatchClause(null, null, body, true);
            } else {
                TypeRef exType = parseTypeRef();
                String varName = check(TokenType.IDENTIFIER) ? expectIdentifier().text() : null;
                expectPunct(")");
                Block body = parseBlock();
                clause = new CatchClause(exType, varName, body, false);
            }
            clauses.add(clause);
        }
        return new TryStatement(tryBlock, clauses, start.line(), start.col(), leadingComments);
    }

    private Statement parseDelete(List<Token> leadingComments) {
        Token start = expectKeyword("delete");
        boolean isArray = false;
        if (checkPunct("[")) {
            advance();
            expectPunct("]");
            isArray = true;
        }
        Expr target = parseExpr();
        expectPunct(";");
        return new DeleteStatement(target, isArray, start.line(), start.col(), leadingComments);
    }

    private Statement parseExprStatement(List<Token> leadingComments) {
        Token start = peek();
        Expr expr = parseExpr();
        expectPunct(";");
        return new ExprStatement(expr, start.line(), start.col(), leadingComments);
    }

    /**
     * Lookahead-only: determines whether the upcoming tokens form a variable
     * declaration ("Type name ...") rather than an expression-statement.
     * Tries to parse a TypeRef followed by an identifier; if that succeeds
     * and is followed by '=', ';', '[', ',', or '(' (direct-init), it's
     * treated as a declaration. Restores position regardless of outcome.
     *
     * This disambiguation is necessary because both "int x = 5;"
     * (declaration) and "x = 5;" (plain assignment expression statement)
     * start with an identifier-shaped token; the grammar must decide which
     * without a symbol table -- same category of ambiguity documented on
     * CallExpr for bare construction calls.
     */
    /** Keywords that introduce a statement form and can NEVER be the start
     * of a type name. Checked first and unconditionally in
     * looksLikeDeclaration, because parseTypeRef/parseQualifiedTypeName
     * have no concept of "which keywords are valid type names" -- they
     * accept ANY KEYWORD-or-IDENTIFIER token as a type name segment (this
     * is intentional and correct for real type keywords like "int",
     * "auto", "color", etc., see parseTypeNameSegment's notes -- the bug
     * is that nothing ever excluded the OTHER kind of keyword, the ones
     * that start an entirely different statement form).
     *
     * Found via PipelineCompositionTest's real g++ check, not by the
     * parser's own corpus sweep: "return foo(1, 2);" was being parsed as
     * a DeclStatement (treating "return" as a bogus type name, "foo" as
     * the declared name, and "(1, 2)" as a direct-init argument list),
     * not a ReturnStatement -- producing a syntactically-different-but-
     * still-valid-shaped AST that round-tripped through codegen as
     * "return foo{1, 2};" with no parse exception anywhere, so the
     * existing "did this fail to parse" corpus sweep never caught it.
     * Confirmed this affected real corpus files too (Handles.pde,
     * Scrollbar.pde) once specifically checked for, not just the
     * synthetic case that surfaced it.
     */
    private static final java.util.Set<String> STATEMENT_KEYWORDS = java.util.Set.of(
        "return", "if", "for", "while", "do", "switch", "break", "continue",
        "try", "delete", "case", "default", "else", "throw"
    );

    /** Keyword type names that are valid cast-operator targets ("operator int()",
     * "operator bool()", etc.). Kept explicit and narrow to prevent arbitrary
     * keywords (like Processing's "color", or "auto", "return", etc.) from
     * being misidentified as cast-operator type names in parseFunctionOrVariableName. */
    private static final java.util.Set<String> CAST_OPERATOR_TYPE_KEYWORDS = java.util.Set.of(
        "int", "float", "double", "bool", "char", "long", "short",
        "unsigned", "signed", "void", "size_t"
    );

    private boolean looksLikeDeclaration() {
        int save = pos;
        try {
            if (check(TokenType.KEYWORD) && STATEMENT_KEYWORDS.contains(peek().text())) return false;
            // Tolerate a leading "static"/"const" (in either order) before
            // attempting to parse a type -- mirrors the actual parse path
            // in parseDeclStatementsDesugared, which consumes these same
            // qualifiers before calling parseTypeRef(). Without this,
            // "static int x = 5;" as a LOCAL variable was rejected right
            // here (parseTypeRef has no concept of consuming a leading
            // qualifier itself, so it choked on the literal token
            // "static"), before ever reaching parseDeclStatementsDesugared's
            // own (correct, but unreachable without this fix) handling of
            // that same qualifier.
            if (checkKeyword("static")) advance();
            if (checkKeyword("const")) advance();
            if (checkKeyword("constexpr")) advance();
            if (checkKeyword("inline")) advance();
            if (checkKeyword("static")) advance(); // tolerate either order
            if (!(check(TokenType.IDENTIFIER) || check(TokenType.KEYWORD))) return false;
            // Function-pointer-variable form: "Type (*name)(Params) = init;"
            // Must be checked before the ordinary TypeRef+identifier path,
            // since after the return type this shape has '(' where an
            // ordinary declarator would have an identifier.
            if (looksLikeFunctionPointerVariable()) return true;
            TypeRef type;
            try {
                type = parseTypeRef();
            } catch (ParseException e) {
                return false;
            }
            if (!check(TokenType.IDENTIFIER)
                && !(check(TokenType.KEYWORD) && PSEUDO_TYPE_KEYWORDS_USABLE_AS_NAMES.contains(peek().text()))) {
                return false;
            }
            advance(); // tentatively consume the name
            return checkOp("=") || checkPunct(";") || checkPunct("[") || checkPunct(",") || checkPunct("(") || checkPunct("{");
        } finally {
            pos = save;
        }
    }

    /**
     * Parses "Type name1 [=init1|dims1], name2 [=init2|dims2], ...;" and
     * desugars it into one DeclStatement per declarator, all sharing the
     * same TypeRef instance. This is the single implementation backing
     * every declaration-statement call site (block-level, for-init,
     * single-statement contexts) -- callers that require exactly one
     * declarator check decls.size() themselves and raise a clear error,
     * rather than this method silently dropping extras.
     */
    private List<DeclStatement> parseDeclStatementsDesugared(List<Token> leadingComments) {
        Token start = peek();

        // BUG FIX, found via adversarial stress-case probing: "static"
        // (and "const") were never consumed here at all -- only the
        // TOP-LEVEL declaration path consumed them. "static int x = 5;"
        // as a LOCAL variable failed to parse outright ("expected ';'
        // but found 'int'", since "static" was left sitting in the
        // token stream as if it were the start of an unrelated
        // statement, then "int" showed up where a ";" was expected).
        // Tolerate either order ("static const" or "const static"),
        // mirroring the top-level path's own tolerance.
        boolean isStatic = matchKeyword("static");
        boolean isConst = matchKeyword("const");
        if (matchKeyword("constexpr")) isConst = true; // constexpr implies const
        if (!isStatic) isStatic = matchKeyword("static");

        if (looksLikeFunctionPointerVariable()) {
            TypeRef returnType = parseTypeRef();
            NameAndFunctionPointerType decl = parseFunctionPointerDeclaratorTail(returnType);
            Expr initializer = null;
            if (matchOp("=")) {
                initializer = parseExpr();
            }
            expectPunct(";");
            return List.of(new DeclStatement(decl.type(), decl.name(), List.of(), initializer,
                isStatic, isConst, start.line(), start.col(), leadingComments));
        }

        TypeRef type = parseTypeRef();
        List<DeclStatement> result = new ArrayList<>();
        result.add(parseOneDeclarator(type, isStatic, isConst, start, leadingComments));
        while (matchPunct(",")) {
            // Same gap, same fix, as parseOneTopLevelDeclarator's comma-
            // continuation loop -- see its notes for the full rationale,
            // including why each declarator's type must be built from
            // the base type's name/templateArgs/const ONLY, never
            // reusing "type"'s own pointerDepth/isReference (which
            // belongs exclusively to the first declarator).
            int extraPointerDepth = 0;
            boolean extraIsReference = false;
            while (checkOp("*") || checkOp("&")) {
                if (matchOp("*")) extraPointerDepth++;
                else { matchOp("&"); extraIsReference = true; }
            }
            TypeRef declaratorType = type;
            if (type instanceof NamedType nt) {
                declaratorType = new NamedType(nt.baseName(), nt.templateArgs(),
                    extraPointerDepth, extraIsReference, nt.isConst());
            }
            result.add(parseOneDeclarator(declaratorType, isStatic, isConst, start, List.of()));
        }
        expectPunct(";");
        return result;
    }

    private DeclStatement parseOneDeclarator(TypeRef type, boolean isStatic, boolean isConst,
                                              Token start, List<Token> leadingComments) {
        String name = expectIdentifier().text();
        List<Expr> dims = parseOptionalArrayDims();
        Expr initializer = null;
        if (matchOp("=")) {
            if (checkPunct("{")) {
                initializer = parseInitializerList();
            } else {
                initializer = parseExpr();
            }
        } else if (checkPunct("(")) {
            initializer = parseDirectInitAsCall(name);
        } else if (checkPunct("{")) {
            // Brace-direct-init at statement scope, same rationale as the
            // top-level declarator case -- see parseOneTopLevelDeclarator's
            // notes.
            initializer = parseDirectInitAsCall(name);
        }
        return new DeclStatement(type, name, dims, initializer, isStatic, isConst,
            start.line(), start.col(), leadingComments);
    }

    /**
     * Parses zero or more "[expr]" / "[]" array-dimension suffixes after
     * a declarator name.
     *
     * BUG FIX, found via RealHeaderStressTest against the real engine
     * headers (Pie_Chart.pde's real "int angles[] = { 30, 10, 45, ... };"):
     * an EMPTY bracket pair (no size expression, size inferred from the
     * initializer -- valid, common C++/Java-array syntax) must still be
     * recorded as a real dimension, just with a null size expression --
     * NOT silently dropped as if no brackets were present at all. The
     * previous version of this method only ever called dims.add(...)
     * inside the "if (!checkPunct(\"]\"))" branch, meaning an empty "[]"
     * added NOTHING to the dims list at all, making
     * "int angles[] = {...}" structurally indistinguishable from
     * "int angles = {...}" (a scalar) once parsed -- confirmed directly:
     * vd.arrayDims() was an empty list, not a list containing one null
     * entry, for "int angles[] = { 30, 10, 45 };". This silently turned
     * a real top-level array declaration into a scalar declaration with
     * a brace-init initializer, which g++ correctly rejects ("cannot
     * convert '<brace-enclosed initializer list>' to 'int'").
     *
     * CodeGen.emitArrayDims already handled a null dim entry correctly
     * (rendering bare "[]" when dim is null) -- this representation was
     * already anticipated and supported on the rendering side; the
     * parser simply never produced it.
     */
    private List<Expr> parseOptionalArrayDims() {
        List<Expr> dims = new ArrayList<>();
        while (checkPunct("[")) {
            advance();
            if (!checkPunct("]")) {
                dims.add(parseExpr());
            } else {
                dims.add(null); // empty "[]" -- still a real dimension, size inferred from initializer
            }
            expectPunct("]");
        }
        return dims;
    }

    /**
     * Handles C++'s "most vexing parse" direct-init form, "Type name(args);",
     * confirmed real by CppBuild.java's existing OBJECT_DIRECT_INIT handling
     * in classifyTopLevelDecls. Represented as a CallExpr initializer whose
     * callee is the variable's own name, consistent with how bare-
     * construction-call ("Type()") is already represented elsewhere.
     *
     * Handles both "Type name(args);" (paren-direct-init) and
     * "Type name{args};" (brace-direct-init) -- the latter confirmed
     * necessary so the parser can read back CodeGen's own brace-rendered
     * output (see CodeGen.emitDeclaratorTail's most-vexing-parse notes).
     * Both forms produce the identical CallExpr shape; which punctuation
     * was used in the source is not preserved on the AST, since nothing
     * downstream has needed that distinction so far -- if it ever does,
     * this is the place to add it.
     */
    private Expr parseDirectInitAsCall(String varName) {
        Token t = peek();
        List<Expr> args = checkPunct("{") ? parseBraceArgList() : parseArgList();
        return new CallExpr(new Identifier(varName, t.line(), t.col(), List.of()), args, t.line(), t.col(), List.of());
    }

    private List<Expr> parseBraceArgList() {
        expectPunct("{");
        List<Expr> args = new ArrayList<>();
        if (!checkPunct("}")) {
            args.add(parseExpr());
            while (matchPunct(",")) {
                args.add(parseExpr());
            }
        }
        expectPunct("}");
        return args;
    }
}
