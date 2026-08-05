package processing.mode.cpp;

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/**
 * Standalone CLI translator for the web compile server.
 * Reads PDE/Java-style sketch from stdin, outputs C++ to stdout.
 */
public class CppCLI {

    // Single source of truth for lifecycle method names -- defined in CppBuild.
    private static final Set<String> LIFECYCLE = CppBuild.LIFECYCLE_METHOD_NAMES;

    public static void main(String[] args) throws Exception {
        String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        try {
            System.out.print(translate(input));
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static String translate(String input) throws Exception {
        // Delegate all pre-AST transforms to prepareCode() -- single source of truth.
        CppBuild.PreparedCode prepared = CppBuild.prepareCode(input);
        String code = prepared.code;

        boolean hasSetup = code.contains("void setup(");
        boolean hasDraw  = code.contains("void draw(");

        // __has_include blocks go to file scope directly -- never into the parser.
        StringBuilder hasIncludePreNs = new StringBuilder();
        for (String block : prepared.hasIncludeBlocks.values()) {
            hasIncludePreNs.append(block);
        }

        StringBuilder out = new StringBuilder();
        StringBuilder preNs = new StringBuilder();
        appendHeader(out);

        if (hasSetup || hasDraw) {
            String result = runPipeline(code, preNs);
            System.out.print(hasIncludePreNs);
            System.out.print(preNs);
            System.out.print(out);
            System.out.print(result);
            if (!code.contains("int main")) {
                System.out.print("\nint main() { Processing::_PSketch sketch; sketch.run(); return 0; }\n");
            }
            return "";
        } else if (code.contains("int main")) {
            return hasIncludePreNs.toString() + preNs.toString() + out.toString() + code;
        } else {
            out.append("\nnamespace Processing {\n\n");
            out.append(code);
            out.append("\n} // namespace Processing\n\n");
            out.append("int main() { Processing::_PSketch sketch; sketch.run(); return 0; }\n");
            return hasIncludePreNs.toString() + preNs.toString() + out.toString();
        }
    }

    private static String runPipeline(String code, StringBuilder preNs) throws Exception {
        CompilationUnit cu = Parser.parse(code);
        CppBuild.AstPipelineResult pipe = CppBuild.runAstPipeline(cu);

        StringBuilder sb = new StringBuilder();
        sb.append("\nnamespace Processing {\n\n");

        // Route preprocessor directives and namespace decls to file scope or namespace body.
        List<TopLevelItem> effectiveRest = new ArrayList<>();
        for (TopLevelItem item : pipe.filteredRest) {
            if (item instanceof PreprocessorLine pl) {
                if (CppBuild.isFileScopePreprocessorLine(pl)) preNs.append(CodeGen.generateNode(item, 0));
                else sb.append(CodeGen.generateNode(item, 0));
                continue;
            }
            if (item instanceof NamespaceDecl nd) {
                if (nd.name().equals("std")) preNs.append(CodeGen.generateNode(nd, 0));
                else sb.append(CodeGen.generateNode(nd, 0));
                continue;
            }
            if (item instanceof UsingNamespaceDecl) {
                sb.append(CodeGen.generateNode(item, 0)); continue;
            }
            effectiveRest.add(item);
        }

        // Emit in same order as writeSketchImpl.
        for (TopLevelItem e : pipe.enumResult.enums) sb.append(CodeGen.generateNode(e, 0));
        for (FunctionDecl fd : pipe.forwardDecls) sb.append(CodeGen.generateNode(fd, 0));
        for (var v : pipe.arrayResult.hoistedSizingConstants) sb.append(CodeGen.generateNode(v, 0));
        java.util.Set<String> autoHoistedNames = new java.util.HashSet<>();
        for (TopLevelItem av : pipe.autoHoisted) if (av instanceof VariableDecl avd) autoHoistedNames.add(avd.name());
        for (var v : pipe.depResult.hoistedVariables) {
            if (!autoHoistedNames.contains(v.name())) sb.append(CodeGen.generateNode(v, 0));
        }
        for (TypeDef td : pipe.finalClasses) sb.append(CodeGen.generateNode(td, 0));
        for (TopLevelItem av : pipe.autoHoisted) sb.append(CodeGen.generateNode(av, 0));
        for (var v : pipe.arrayResult.hoistedArrays) sb.append(CodeGen.generateNode(v, 0));
        for (FunctionDecl fd : pipe.depResult.hoistedFunctions) sb.append(CodeGen.generateNode(fd, 0));

        // Collect lifecycle bodies; remaining items go into _PSketch.
        List<FunctionDecl> lifecycleBodies = new ArrayList<>();
        List<TopLevelItem> sketchMembers = new ArrayList<>();
        for (TopLevelItem item : effectiveRest) {
            if (item instanceof FunctionDecl fd && fd.body() != null && LIFECYCLE.contains(fd.name())) {
                lifecycleBodies.add(fd);
            } else {
                sketchMembers.add(item);
            }
        }

        // Strip redundant forward decls (same as writeSketchImpl).
        java.util.Set<String> definedFns = new java.util.HashSet<>();
        for (TopLevelItem item : sketchMembers)
            if (item instanceof FunctionDecl fd && fd.body() != null)
                definedFns.add(fd.name() + "/" + fd.params().size());
        sketchMembers.removeIf(item -> item instanceof FunctionDecl fd
            && fd.body() == null && !fd.isPureVirtual()
            && definedFns.contains(fd.name() + "/" + fd.params().size()));

        // Hoist TopLevelStatements to namespace scope.
        List<TopLevelItem> nsHoisted = new ArrayList<>(), realMembers = new ArrayList<>();
        for (TopLevelItem item : sketchMembers) {
            if (item instanceof TopLevelStatement) nsHoisted.add(item);
            else realMembers.add(item);
        }
        for (TopLevelItem item : nsHoisted) sb.append(CodeGen.generateNode(item, 0));

        // Build _PSketch with lifecycle methods inlined.
        sb.append("class _PSketch : public PApplet {\npublic:\n");
        for (TopLevelItem item : realMembers) sb.append(CodeGen.generateNode(item, 1));
        Map<String,FunctionDecl> bodyMap = new LinkedHashMap<>();
        for (FunctionDecl fd : lifecycleBodies) bodyMap.put(fd.name(), fd);
        for (String name : LIFECYCLE) {
            FunctionDecl fd = bodyMap.get(name);
            if (fd == null) continue;
            String rendered = CodeGen.generateNode(fd, 0).strip();
            if (!rendered.contains("override"))
                rendered = rendered.replaceFirst("(\\b" + name + "\\s*\\([^)]*\\)\\s*)", "$1 override ");
            for (String line : rendered.split("\n", -1))
                sb.append("    ").append(line).append("\n");
        }
        sb.append("};\n");
        sb.append("\n} // namespace Processing\n");
        return sb.toString();
    }

    /**
     * Produces the full C++ source string for a sketch, including #line directives,
     * for use by CppLinter (g++ -fsyntax-only).
     */
    public static String translateForLint(String sketchCode) throws Exception {
        CppBuild.PreparedCode prepared = CppBuild.prepareCode(sketchCode);
        String code = prepared.code;
        boolean hasSetup = code.contains("void setup(");
        boolean hasDraw  = code.contains("void draw(");

        StringBuilder hasIncludePreNs = new StringBuilder();
        for (String block : prepared.hasIncludeBlocks.values())
            hasIncludePreNs.append(block);

        StringBuilder preNs = new StringBuilder();
        StringBuilder result = new StringBuilder();

        if (hasSetup || hasDraw) {
            String body = runPipeline(code, preNs);
            result.append(hasIncludePreNs);
            result.append(preNs);
            result.append("#include \"Processing.h\"\n");
            result.append("using namespace std;\n");
            result.append(body);
        } else {
            result.append(hasIncludePreNs);
            result.append(preNs);
            result.append("#include \"Processing.h\"\n");
            result.append("using namespace std;\n");
            result.append("\nnamespace Processing {\n\n");
            result.append(code);
            result.append("\n} // namespace Processing\n");
        }
        return result.toString();
    }

    private static void appendHeader(StringBuilder out) {
        out.append("#include \"Processing.h\"\n");
        out.append("using namespace std;\n");
    }


}
