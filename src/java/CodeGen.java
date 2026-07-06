package processing.mode.cpp;



import java.util.List;

/**
 * Renders an AST tree back into C++ source text. This is "stage 4" from
 * the original architecture plan -- every semantic pass that replaces a
 * regex transform in CppBuild.java works by producing a (rebuilt) list
 * of TopLevelItems, which this generator then turns back into text
 * CppBuild can hand to g++.
 *
 * Ported and EXTENDED from a parallel implementation's CppCodeGen
 * (package processing.mode.cpp). See DECISION_two_parser_implementations.md
 * for the porting rationale. Two deliberate extensions beyond the port
 * source, both because this codebase's AST design supports them where the
 * port source's apparently didn't:
 *
 *  1. COMMENT RE-EMISSION. The port source explicitly does not attempt
 *     comment preservation. This codebase's parser deliberately attaches
 *     leadingComments to every node specifically so they could survive a
 *     round trip -- this codegen is the first place that design decision
 *     actually pays off, so every emit method re-prints leadingComments
 *     immediately before its node's own text.
 *
 *  2. #line DIRECTIVES. The original architecture doc called for #line
 *     emission (so g++ errors map back to the original sketch.pde line
 *     numbers) as part of this exact stage. generate() takes an optional
 *     sourceFileName; when provided, a "#line N \"sourceFileName\"" is
 *     emitted before each top-level item using that item's own line
 *     number.
 */
final class CodeGen {
    private CodeGen() {}

    public static String generate(CompilationUnit cu) {
        return generate(cu, null);
    }

    /** @param sourceFileName if non-null, emits a "#line N \"file\"" directive before each top-level item. */
    public static String generate(CompilationUnit cu, String sourceFileName) {
        StringBuilder sb = new StringBuilder();
        for (TopLevelItem item : cu.items()) {
            if (sourceFileName != null) {
                sb.append("#line ").append(item.line()).append(" \"").append(sourceFileName).append("\"\n");
            }
            emitTopLevelItem(sb, item, 0);
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Render a single top-level item (or class member) at the given indent depth. */
    public static String generateNode(TopLevelItem n, int indent) {
        StringBuilder sb = new StringBuilder();
        emitTopLevelItem(sb, n, indent);
        return sb.toString();
    }

    private static void indent(StringBuilder sb, int depth) {
        sb.append("    ".repeat(depth));
    }

    private static void emitComments(StringBuilder sb, List<CppLexerToken> comments, int depth) {
        for (CppLexerToken c : comments) {
            indent(sb, depth);
            sb.append(c.text()).append('\n');
        }
    }

    // =====================================================================
    // Top-level / member items
    // =====================================================================

    private static void emitTopLevelItem(StringBuilder sb, TopLevelItem item, int depth) {
        emitComments(sb, item.leadingComments(), depth);
        if (item instanceof PreprocessorLine pl) {
            indent(sb, depth);
            sb.append(pl.rawText()).append('\n');
        } else if (item instanceof EnumDecl e) {
            emitEnumDecl(sb, e, depth);
        } else if (item instanceof TypeDef td) {
            emitTypeDef(sb, td, depth);
        } else if (item instanceof FunctionDecl fd) {
            emitFunctionDecl(sb, fd, depth);
        } else if (item instanceof VariableDecl vd) {
            emitVariableDecl(sb, vd, depth);
        } else if (item instanceof NamespaceDecl nd) {
            emitNamespaceDecl(sb, nd, depth);
        } else if (item instanceof UsingNamespaceDecl und) {
            indent(sb, depth);
            sb.append("using namespace ").append(und.name()).append(";\n");
        } else if (item instanceof TopLevelStatement ts) {
            emitStmt(sb, ts.statement(), depth);
        } else {
            throw new IllegalArgumentException("CodeGen: don't know how to emit " + item.getClass());
        }
    }

    private static void emitEnumDecl(StringBuilder sb, EnumDecl e, int depth) {
        indent(sb, depth);
        sb.append("enum ");
        if (e.isScoped()) sb.append("class ");
        sb.append(e.name()).append(" {\n");
        for (int i = 0; i < e.values().size(); i++) {
            indent(sb, depth + 1);
            sb.append(e.values().get(i));
            if (i < e.values().size() - 1) sb.append(",");
            sb.append("\n");
        }
        indent(sb, depth);
        sb.append("};\n");
    }

    private static void emitNamespaceDecl(StringBuilder sb, NamespaceDecl nd, int depth) {
        indent(sb, depth);
        sb.append("namespace ").append(nd.name()).append(" {\n");
        for (TopLevelItem item : nd.items()) {
            emitTopLevelItem(sb, item, depth + 1);
        }
        indent(sb, depth);
        sb.append("}\n");
    }

    private static void emitTypeDef(StringBuilder sb, TypeDef td, int depth) {
        indent(sb, depth);
        if (!td.templateParams().isEmpty()) {
            sb.append("template<");
            for (int i = 0; i < td.templateParams().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("typename ").append(td.templateParams().get(i));
            }
            sb.append(">\n");
            indent(sb, depth);
        }
        sb.append(td.kind()).append(' ').append(td.name());
        if (!td.baseClasses().isEmpty()) {
            sb.append(" : ");
            for (int i = 0; i < td.baseClasses().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("public ").append(td.baseClasses().get(i));
            }
        }
        sb.append(" {\n");
        if (td.kind().equals("class") && !td.members().isEmpty()) {
            indent(sb, depth + 1);
            sb.append("public:\n");
        }
        for (TopLevelItem member : td.members()) {
            emitTopLevelItem(sb, member, depth + 1);
        }
        indent(sb, depth);
        sb.append("};\n");
    }

    /**
     * Renders "Type name" for a declaration, handling the one shape where
     * type and name don't simply concatenate: a FunctionPointerType
     * declarator, where real C++ syntax requires the name to appear INSIDE
     * the parens around the '*' -- "ReturnType (*name)(ParamTypes)" -- not
     * appended after the whole rendered type the way every other
     * declaration shape works ("int x", "ArrayList<Handle>* p", etc.).
     *
     * Found by the round-trip test: naively doing
     * renderTypeRef(type) + " " + name for a FunctionPointerType produces
     * "int (*)(int, int) funcPtr", which is NOT valid C++ (the name lands
     * in the wrong position entirely) -- confirmed by g++ rejecting it,
     * not just by re-parse failing. This is the one declarator shape that
     * needs its own emission path rather than the generic "type then
     * name" pattern every other VariableDecl/DeclStatement/Param uses.
     */
    private static String renderTypeAndName(TypeRef type, String name) {
        if (type instanceof FunctionPointerType fpt) {
            StringBuilder sb = new StringBuilder(renderTypeRef(fpt.returnType()));
            sb.append(" (*").append(name).append(")(");
            for (int i = 0; i < fpt.paramTypes().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderTypeRef(fpt.paramTypes().get(i)));
            }
            sb.append(")");
            return sb.toString();
        }
        return renderTypeRef(type) + " " + name;
    }

    private static void emitVariableDecl(StringBuilder sb, VariableDecl vd, int depth) {
        indent(sb, depth);
        if (vd.isStatic()) sb.append("static ");
        if (vd.isConst() && !(vd.type() instanceof NamedType nt && nt.isConst())) {
            sb.append("const ");
        }
        sb.append(renderTypeAndName(vd.type(), vd.name()));
        emitArrayDims(sb, vd.arrayDims());
        emitDeclaratorTail(sb, vd.type(), vd.name(), vd.initializer(), true);
        sb.append(";\n");
    }

    private static void emitArrayDims(StringBuilder sb, List<Expr> dims) {
        for (Expr dim : dims) {
            sb.append('[');
            if (dim != null) sb.append(renderExpr(dim));
            sb.append(']');
        }
    }

    /**
     * Renders the "= initializer" tail of a declaration, with one special
     * case: if initializer is a CallExpr whose callee is an Identifier
     * matching the declarator's OWN name, this is the direct-init pattern
     * ("Handle a(5);") that the parser represents as a self-named CallExpr
     * rather than a real assignment (see Parser.parseDirectInitAsCall's
     * notes, and CallExpr's design notes on why -- the parser can't tell
     * "constructor call" from "function call" without a symbol table, so
     * it just records the call shape and leaves disambiguation to a later
     * pass; this is that later pass, for codegen's purposes).
     *
     * Renders this as BRACE-init ("Handle a{5};"), not paren-init
     * ("Handle a(5);"), matching CppBuild.java's original
     * "fix C++'s most vexing parse" rewrite exactly (parens to braces).
     * Confirmed why this matters with a direct g++ test: "Eye e1(Bar());"
     * with a bare-type-constructing argument genuinely parses as a
     * FUNCTION DECLARATION ("Eye(Bar(*)())", not an Eye object) -- g++
     * accepts it silently and only fails later when something tries to
     * use e1 as an actual object, e.g. "e1.member" ->
     * "request for member 'member' in 'e1', which is of non-class type
     * 'Eye(Bar (*)())'". Plain-literal-argument direct-inits (the only
     * shape confirmed present in this project's real/synthetic corpus,
     * e.g. "Eye e1(250, 16, 120);") do NOT trigger this -- confirmed by a
     * separate direct g++ check -- but brace-init is unambiguous in
     * EVERY case, including ones with no corpus evidence yet, so there's
     * no reason to keep the narrower, parens-based rendering once the
     * risk is understood. Matches the original's blanket safety margin
     * rather than only the narrower behavior this codebase's own test
     * data happened to require.
     *
     * Found by reading codegen output during round-trip testing: naively
     * rendering this shape as "= initializer" produces "Handle a = a(5);"
     * -- syntactically different from (and semantically nonsensical
     * compared to) the original "Handle a(5);" direct-init the user
     * actually wrote. The round-trip-stability test alone didn't catch
     * this (a = a(5) round-trips perfectly stably, it's just wrong), which
     * is itself a useful reminder that stability and correctness are
     * different properties -- this was caught by reading the output, not
     * by an automated check, and a real test for it was added after.
     */
    /**
     * Standard-library container/wrapper types with a real
     * initializer_list constructor, meaning brace-init and paren-init
     * are NOT interchangeable -- they have genuinely different
     * SEMANTICS. Found as a real bug via a real user-reported sketch
     * (Wolfram.pde): "std::vector<int> nextgen(cells.size(), 0);" was
     * being unconditionally brace-wrapped into
     * "std::vector<int> nextgen{cells.size(), 0};" -- confirmed via g++
     * that these produce different .size() results (5 vs 2 for
     * "(5, 0)" vs "{5, 0}"). The most-vexing-parse protection this
     * brace-wrapping exists for only matters for actual user-defined
     * class types; a template instantiation like "std::vector<int>" was
     * never at risk of that ambiguity (confirmed via g++: no competing
     * function-declaration interpretation exists for it).
     */
    private static final java.util.Set<String> INITIALIZER_LIST_AMBIGUOUS_TYPES = java.util.Set.of(
        // C++ stdlib types (original set -- see comment above for full rationale)
        "vector", "std::vector",
        "string", "std::string",
        "wstring", "std::wstring",
        "array", "std::array",
        "deque", "std::deque",
        "list", "std::list",
        "set", "std::set",
        "map", "std::map",
        "unordered_set", "std::unordered_set",
        "unordered_map", "std::unordered_map",
        "initializer_list", "std::initializer_list",
        // CppMode's own Java-mimicking container types -- all have BOTH a
        // length constructor AND an initializer_list constructor, so brace-init
        // and paren-init are NOT interchangeable:
        //   IntList hist(256)  -> 256-element zeroed list  (correct)
        //   IntList hist{256}  -> 1-element list, hist[0]==256  (WRONG)
        // Found via Histogram.pde crash: "IntList index 206 out of bounds for length 1"
        "IntList", "FloatList", "StringList", "ArrayList", "Array",
        "color"
    );

    /** Convenience overload for statement-scope (local variable) declarations. */
    private static void emitDeclaratorTail(StringBuilder sb, TypeRef declaratorType, String declaratorName, Expr initializer) {
        emitDeclaratorTail(sb, declaratorType, declaratorName, initializer, false);
    }

    /**
     * Emits the initializer portion of a variable declaration.
     * atMemberOrGlobalScope=true for VariableDecl (class members and globals),
     * false for DeclStatement (local variables inside function bodies).
     * At member/global scope, paren-init is always a function declaration in C++.
     * "Array<PVector> coords(0)" at member scope = "coords(int)" not a variable.
     * Fix: use copy-init "= Type(args)" which is unambiguous at all scopes.
     */
    private static void emitDeclaratorTail(StringBuilder sb, TypeRef declaratorType, String declaratorName, Expr initializer, boolean atMemberOrGlobalScope) {
        if (initializer == null) return;
        if (initializer instanceof CallExpr ce && ce.callee() instanceof Identifier id
            && id.name().equals(declaratorName)) {
            if (atMemberOrGlobalScope) {
                sb.append(" = ").append(renderTypeRef(declaratorType)).append('(');
                for (int i = 0; i < ce.args().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(renderExpr(ce.args().get(i)));
                }
                sb.append(')');
                return;
            }
            if (!(declaratorType instanceof NamedType nt && INITIALIZER_LIST_AMBIGUOUS_TYPES.contains(nt.baseName()))) {
                sb.append('{');
                for (int i = 0; i < ce.args().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(renderExpr(ce.args().get(i)));
                }
                sb.append('}');
                return;
            }
            sb.append('(');
            for (int i = 0; i < ce.args().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderExpr(ce.args().get(i)));
            }
            sb.append(')');
            return;
        }
        sb.append(" = ").append(renderExpr(initializer));
    }

    private static void emitInitializer(StringBuilder sb, Expr initializer) {
        if (initializer == null) return;
        sb.append(" = ").append(renderExpr(initializer));
    }

    private static void emitFunctionDecl(StringBuilder sb, FunctionDecl fd, int depth) {
        indent(sb, depth);
        if (!fd.templateParams().isEmpty()) {
            sb.append("template<");
            for (int i = 0; i < fd.templateParams().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("typename ").append(fd.templateParams().get(i));
            }
            sb.append(">\n");
            indent(sb, depth);
        }
        if (fd.isStatic()) sb.append("static ");
        if (fd.isVirtual()) sb.append("virtual ");

        if (fd.isDestructor()) {
            sb.append(fd.name()).append("()");
        } else if (fd.isConstructor()) {
            sb.append(fd.name()).append('(');
            emitParamList(sb, fd.params());
            sb.append(')');
        } else {
            boolean isCastOp = fd.name().startsWith("operator ")
                && fd.returnType() instanceof NamedType nt
                && nt.baseName().equals("operator")
                && nt.pointerDepth() == 0;
            if (!isCastOp) {
                sb.append(renderTypeRef(fd.returnType())).append(' ');
            }
            sb.append(fd.name()).append('(');
            emitParamList(sb, fd.params());
            sb.append(')');
        }

        if (fd.isConst()) sb.append(" const");
        if (fd.isOverride()) sb.append(" override");

        if (!fd.initializerList().isEmpty()) {
            sb.append(" : ");
            for (int i = 0; i < fd.initializerList().size(); i++) {
                if (i > 0) sb.append(", ");
                FunctionDecl.ConstructorInit e = fd.initializerList().get(i);
                sb.append(e.memberName()).append('(');
                for (int j = 0; j < e.args().size(); j++) {
                    if (j > 0) sb.append(", ");
                    sb.append(renderExpr(e.args().get(j)));
                }
                sb.append(')');
            }
        }

        if (fd.body() == null) {
            if (fd.isPureVirtual()) sb.append(" = 0");
            sb.append(";\n");
            return;
        }
        sb.append(" ");
        emitBlock(sb, fd.body(), depth);
        sb.append('\n');
    }

    private static void emitParamList(StringBuilder sb, List<Param> params) {
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            Param p = params.get(i);
            if (!p.innerArrayDims().isEmpty()) {
                TypeRef base = p.type();
                if (base instanceof NamedType nt && nt.pointerDepth() > 0) {
                    base = new NamedType(nt.baseName(), nt.templateArgs(),
                        nt.pointerDepth() - 1, nt.isReference(), nt.isConst());
                }
                sb.append(renderTypeRef(base)).append(" (*").append(p.name()).append(")");
                for (int dim : p.innerArrayDims()) {
                    sb.append("[").append(dim > 0 ? dim : "").append("]");
                }
            } else if (p.name() != null && !p.name().isEmpty()) {
                sb.append(renderTypeAndName(p.type(), p.name()));
            } else {
                sb.append(renderTypeRef(p.type()));
            }
            if (p.defaultValue() != null) sb.append(" = ").append(renderExpr(p.defaultValue()));
        }
    }

    // =====================================================================
    // Statements
    // =====================================================================

    private static void emitBlock(StringBuilder sb, Block b, int depth) {
        sb.append("{\n");
        for (Statement s : b.statements()) {
            emitStmt(sb, s, depth + 1);
        }
        indent(sb, depth);
        sb.append("}");
    }

    private static void emitStmt(StringBuilder sb, Statement s, int depth) {
        emitComments(sb, s.leadingComments(), depth);
        if (s instanceof Block b) {
            indent(sb, depth);
            emitBlock(sb, b, depth);
            sb.append('\n');
        } else if (s instanceof DeclStatement ds) {
            indent(sb, depth);
            if (ds.isStatic()) sb.append("static ");
            if (ds.isConst() && !(ds.type() instanceof NamedType nt && nt.isConst())) {
                sb.append("const ");
            }
            sb.append(renderTypeAndName(ds.type(), ds.name()));
            emitArrayDims(sb, ds.arrayDims());
            emitDeclaratorTail(sb, ds.type(), ds.name(), ds.initializer());
            sb.append(";\n");
        } else if (s instanceof ExprStatement es) {
            indent(sb, depth);
            sb.append(renderExpr(es.expr())).append(";\n");
        } else if (s instanceof IfStatement ifs) {
            emitIfStmt(sb, ifs, depth, true);
        } else if (s instanceof ForStatement f) {
            emitForStmt(sb, f, depth);
        } else if (s instanceof RangeForStatement rf) {
            emitRangeForStmt(sb, rf, depth);
        } else if (s instanceof WhileStatement w) {
            indent(sb, depth);
            sb.append("while (").append(renderExpr(w.condition())).append(") ");
            emitStmtInline(sb, w.body(), depth);
            sb.append('\n');
        } else if (s instanceof DoWhileStatement dw) {
            indent(sb, depth);
            sb.append("do ");
            emitStmtInline(sb, dw.body(), depth);
            sb.append(" while (").append(renderExpr(dw.condition())).append(");\n");
        } else if (s instanceof SwitchStatement sw) {
            emitSwitchStmt(sb, sw, depth);
        } else if (s instanceof BreakStatement) {
            indent(sb, depth);
            sb.append("break;\n");
        } else if (s instanceof ContinueStatement) {
            indent(sb, depth);
            sb.append("continue;\n");
        } else if (s instanceof ReturnStatement r) {
            indent(sb, depth);
            sb.append("return");
            if (r.value() != null) sb.append(' ').append(renderExpr(r.value()));
            sb.append(";\n");
        } else if (s instanceof TryStatement t) {
            emitTryStmt(sb, t, depth);
        } else if (s instanceof DeleteStatement d) {
            indent(sb, depth);
            sb.append("delete").append(d.isArray() ? "[] " : " ").append(renderExpr(d.target())).append(";\n");
        } else {
            throw new IllegalArgumentException("CodeGen: don't know how to emit statement " + s.getClass());
        }
    }

    /** Emits a statement that follows "if (...) ", "while (...) ", etc. on the
     * same line when it's a Block, or on its own indented line otherwise. */
    private static void emitStmtInline(StringBuilder sb, Statement body, int depth) {
        if (body instanceof Block b) {
            emitBlock(sb, b, depth);
        } else {
            sb.append("\n");
            emitStmt(sb, body, depth + 1);
        }
    }

    private static void emitIfStmt(StringBuilder sb, IfStatement s, int depth, boolean withLeadingIndent) {
        if (withLeadingIndent) indent(sb, depth);
        sb.append("if (").append(renderExpr(s.condition())).append(") ");
        emitStmtInline(sb, s.thenBranch(), depth);
        if (s.elseBranch() != null) {
            if (s.thenBranch() instanceof Block) sb.append(" ");
            else indent(sb, depth);
            sb.append("else ");
            if (s.elseBranch() instanceof IfStatement elseIf) {
                // else-if chain: render the nested if at the SAME depth (so its
                // own block body indents correctly), but suppress ITS leading
                // indent specifically, since "else " on this line already
                // provides the visual indent and the nested "if (" continues
                // directly after it rather than starting a new indented line.
                emitIfStmt(sb, elseIf, depth, false);
            } else {
                emitStmtInline(sb, s.elseBranch(), depth);
                sb.append('\n');
            }
            return;
        }
        sb.append('\n');
    }

    private static void emitForStmt(StringBuilder sb, ForStatement s, int depth) {
        indent(sb, depth);
        sb.append("for (");
        if (s.init() instanceof DeclStatement ds) {
            if (ds.isConst() && !(ds.type() instanceof NamedType nt && nt.isConst())) {
                sb.append("const ");
            }
            sb.append(renderTypeAndName(ds.type(), ds.name()));
            emitArrayDims(sb, ds.arrayDims());
            emitDeclaratorTail(sb, ds.type(), ds.name(), ds.initializer());
        } else if (s.init() instanceof ExprStatement es) {
            sb.append(renderExpr(es.expr()));
        }
        sb.append("; ");
        if (s.condition() != null) sb.append(renderExpr(s.condition()));
        sb.append("; ");
        if (s.update() != null) sb.append(renderExpr(s.update()));
        sb.append(") ");
        emitStmtInline(sb, s.body(), depth);
        sb.append('\n');
    }

    private static void emitRangeForStmt(StringBuilder sb, RangeForStatement s, int depth) {
        indent(sb, depth);
        sb.append("for (").append(renderTypeRef(s.declType()));
        if (s.isReference()) sb.append('&');
        sb.append(' ').append(s.declName())
          .append(" : ").append(renderExpr(s.iterableExpr())).append(") ");
        emitStmtInline(sb, s.body(), depth);
        sb.append('\n');
    }

    private static void emitSwitchStmt(StringBuilder sb, SwitchStatement s, int depth) {
        indent(sb, depth);
        sb.append("switch (").append(renderExpr(s.subject())).append(") {\n");
        for (SwitchCase c : s.cases()) {
            indent(sb, depth + 1);
            if (c.matchValue() == null) {
                sb.append("default:\n");
            } else {
                sb.append("case ").append(renderExpr(c.matchValue())).append(":\n");
            }
            for (Statement stmt : c.body()) {
                emitStmt(sb, stmt, depth + 2);
            }
        }
        indent(sb, depth);
        sb.append("}\n");
    }

    private static void emitTryStmt(StringBuilder sb, TryStatement s, int depth) {
        indent(sb, depth);
        sb.append("try ");
        emitBlock(sb, s.tryBlock(), depth);
        sb.append('\n');
        for (CatchClause c : s.catchClauses()) {
            indent(sb, depth);
            sb.append("catch (");
            if (c.isCatchAll()) {
                sb.append("...");
            } else {
                sb.append(renderTypeRef(c.exceptionType()));
                if (c.varName() != null) sb.append(' ').append(c.varName());
            }
            sb.append(") ");
            if (c.body() != null) emitBlock(sb, c.body(), depth);
            sb.append('\n');
        }
    }

    // =====================================================================
    // Types
    // =====================================================================

    public static String renderTypeRef(TypeRef t) {
        if (t == null) return "void"; // constructors/destructors carry no returnType
        if (t instanceof NamedType nt) {
            StringBuilder sb = new StringBuilder();
            if (nt.isConst()) sb.append("const ");
            sb.append(nt.baseName());
            if (!nt.templateArgs().isEmpty()) {
                sb.append('<');
                for (int i = 0; i < nt.templateArgs().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(renderTypeRef(nt.templateArgs().get(i)));
                }
                sb.append('>');
            }
            sb.append("*".repeat(nt.pointerDepth()));
            if (nt.isReference()) sb.append('&');
            return sb.toString();
        }
        if (t instanceof FunctionPointerType fpt) {
            StringBuilder sb = new StringBuilder(renderTypeRef(fpt.returnType()));
            sb.append(" (*)(");
            for (int i = 0; i < fpt.paramTypes().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderTypeRef(fpt.paramTypes().get(i)));
            }
            sb.append(")");
            return sb.toString();
        }
        if (t instanceof FunctionSignatureType fst) {
            StringBuilder sb = new StringBuilder(renderTypeRef(fst.returnType()));
            sb.append("(");
            for (int i = 0; i < fst.paramTypes().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderTypeRef(fst.paramTypes().get(i)));
            }
            sb.append(")");
            return sb.toString();
        }
        throw new IllegalArgumentException("CodeGen: don't know how to render type " + t.getClass());
    }

    // =====================================================================
    // Expressions
    // =====================================================================

    public static String renderExpr(Expr e) {
        if (e instanceof Literal lit) return lit.text();
        if (e instanceof Identifier id) return id.name();
        if (e instanceof ScopedName sn) return sn.joined();

        if (e instanceof InitializerListExpr il) {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < il.elements().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderExpr(il.elements().get(i)));
            }
            return sb.append('}').toString();
        }
        if (e instanceof BinaryExpr b) {
            String leftRendered = renderExpr(b.left());
            // A bare string literal on the LEFT of a "+" is genuinely
            // ambiguous in real C++ when the right operand isn't itself
            // a std::string/char: a raw string literal is `const char*`,
            // and pointer arithmetic (always preferred over a user-
            // defined overload requiring an implicit conversion)
            // silently wins over the engine's intended "Java-style
            // string + number concatenation" operator+ overloads.
            // Confirmed real via a direct g++ test on
            // Characters_Strings.pde's actual
            // "The String is " + words.length() + " characters long":
            // the first "+" produces a const char* (pointer arithmetic),
            // not a std::string, so the SUBSEQUENT "+ moreText" then
            // fails to compile. Fix: wrap the leftmost string literal in
            // std::string(...) whenever it's the direct left operand of
            // a "+" -- confirmed via direct g++ tests this is
            // UNCONDITIONALLY safe (doesn't change behavior for any case
            // that already worked: literal + std::string, literal +
            // char, int + literal all compile identically wrapped or
            // unwrapped), so no type inference is needed to decide when
            // to apply it.
            if (b.op().equals("+") && b.left() instanceof Literal lit && lit.kind() == Literal.Kind.STRING) {
                leftRendered = "std::string(" + leftRendered + ")";
            }
            return "(" + leftRendered + " " + b.op() + " " + renderExpr(b.right()) + ")";
        }
        if (e instanceof UnaryExpr u) {
            return u.op() + renderExpr(u.operand());
        }
        if (e instanceof PostfixExpr p) {
            return renderExpr(p.operand()) + p.op();
        }
        if (e instanceof AssignExpr a) {
            return renderExpr(a.target()) + " = " + renderExpr(a.value());
        }
        if (e instanceof TernaryExpr t) {
            return "(" + renderExpr(t.condition()) + " ? " + renderExpr(t.thenExpr()) + " : " + renderExpr(t.elseExpr()) + ")";
        }
        if (e instanceof CallExpr c) {
            char open = c.isBraceInit() ? '{' : '(';
            char close = c.isBraceInit() ? '}' : ')';
            StringBuilder sb = new StringBuilder(renderExpr(c.callee())).append(open);
            for (int i = 0; i < c.args().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderExpr(c.args().get(i)));
            }
            return sb.append(close).toString();
        }
        if (e instanceof MemberAccessExpr m) {
            return renderExpr(m.target()) + (m.isArrow() ? "->" : ".") + m.memberName();
        }
        if (e instanceof IndexExpr ix) {
            return renderExpr(ix.target()) + "[" + renderExpr(ix.index()) + "]";
        }
        if (e instanceof CastExpr c) {
            return "(" + renderTypeRef(c.targetType()) + ")" + renderExpr(c.expr());
        }
        if (e instanceof NewExpr n) {
            StringBuilder sb = new StringBuilder("new ").append(renderTypeRef(n.type())).append('(');
            for (int i = 0; i < n.args().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderExpr(n.args().get(i)));
            }
            return sb.append(')').toString();
        }
        if (e instanceof ArrayNewExpr an) {
            return "new " + renderTypeRef(an.elementType()) + "[" + renderExpr(an.sizeExpr()) + "]";
        }
        if (e instanceof LambdaExpr l) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.captures().size(); i++) {
                if (i > 0) sb.append(", ");
                Capture cap = l.captures().get(i);
                if (cap.byRef()) sb.append('&');
                sb.append(cap.name());
            }
            sb.append("](");
            for (int i = 0; i < l.params().size(); i++) {
                if (i > 0) sb.append(", ");
                Param p = l.params().get(i);
                sb.append(renderTypeRef(p.type()));
                if (p.name() != null) sb.append(' ').append(p.name());
            }
            sb.append(")");
            if (l.returnType() != null) sb.append(" -> ").append(renderTypeRef(l.returnType()));
            sb.append(" ");
            StringBuilder bodySb = new StringBuilder();
            emitBlock(bodySb, l.body(), 0);
            sb.append(bodySb);
            return sb.toString();
        }

        throw new IllegalArgumentException("CodeGen: don't know how to render expression " + e.getClass());
    }
}
