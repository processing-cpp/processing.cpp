package processing.mode.cpp;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/**
 * Standalone CLI translator for the web compile server.
 * Reads PDE/Java-style sketch from stdin, outputs C++ to stdout.
 */
public class CppCLI {

    private static final Set<String> LIFECYCLE_METHOD_NAMES = Set.of(
        "setup","draw","mousePressed","mouseReleased","mouseClicked",
        "mouseMoved","mouseDragged","mouseWheel",
        "keyPressed","keyReleased","keyTyped","settings"
    );

    public static void main(String[] args) throws Exception {
        String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        CppBuild build = allocate();

        // String-level pre-processing (mirrors writeSketchImpl)
        String code = input;
        code = invoke(build, "sanitize", code);
        code = invoke(build, "removeUserIncludes", code);
        code = code.replaceAll("(\\bfinal_suspend\\s*\\([^)]*\\))(\\s*\\{)", "$1 noexcept$2");
        code = code.replaceAll("(\\binitial_suspend\\s*\\([^)]*\\))(\\s*\\{)", "$1 noexcept$2");
        code = code.replaceAll("\\btranslate\\s*\\(\\s*0+\\.?0*f?\\s*,\\s*0+\\.?0*f?\\s*\\)\\s*;", "");
        code = code.replaceAll("\\btranslate\\s*\\(\\s*0+\\.?0*f?\\s*,\\s*0+\\.?0*f?\\s*,\\s*0+\\.?0*f?\\s*\\)\\s*;", "");
        code = invoke(build, "stripRawStringLiterals", code);
        code = code.replaceAll("(?<=[0-9a-fA-FxXbB])'(?=[0-9a-fA-F])", "");
        code = invoke(build, "javaToC", code);
        code = invoke(build, "stripNamespaceProcessing", code);
        code = invoke(build, "preprocessMacros", code);

        boolean hasSetup = code.contains("void setup(");
        boolean hasDraw  = code.contains("void draw(");

        StringBuilder preNs = new StringBuilder();
        StringBuilder header = new StringBuilder();
        appendHeader(header);

        if (hasSetup || hasDraw) {
            try {
                String result = runFullPipeline(code, preNs);
                System.out.print(preNs.toString());
                System.out.print(header.toString());
                System.out.print(result);
                System.out.print("\nint main() { Processing::_PSketch sketch; sketch.run(); return 0; }\n");
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        } else {
            System.out.print(header.toString());
            System.out.print("\nnamespace Processing {\n\n");
            System.out.print(code);
            System.out.print("\n} // namespace Processing\n\n");
            System.out.print("int main() { Processing::_PSketch sketch; sketch.run(); return 0; }\n");
        }
    }

    private static String runFullPipeline(String code, StringBuilder preNs) throws Exception {
        // Step 0: parse
        CompilationUnit cu = Parser.parse(code);

        // Step 1: enum extraction
        EnumScopeExtractor.Result enumResult = EnumScopeExtractor.extract(cu.items());

        // Step 2: lifecycle rewriting
        List<TopLevelItem> afterLifecycle = LifecycleRewriter.rewrite(enumResult.rest, LIFECYCLE_METHOD_NAMES);

        // Step 3: class hoisting
        ClassHoister.Result classResult = ClassHoister.hoist(afterLifecycle);

        // Step 4: _PSketch injection
        List<PSketchInjector.Result> injectedClasses = PSketchInjector.injectAll(classResult.hoistedClasses);
        List<TypeDef> finalClasses = injectedClasses.stream().map(PSketchInjector.Result::typeDef).toList();

        // Step 5: array hoisting
        ArrayHoister.Result arrayResult = ArrayHoister.hoist(classResult.rest);

        // Step 6: dependency hoisting
        DependencyHoister.Result depResult = DependencyHoister.hoist(arrayResult.rest, finalClasses, LIFECYCLE_METHOD_NAMES);

        // Step 7: forward declarations
        List<FunctionDecl> forwardDecls = ForwardDeclGenerator.generate(depResult.hoistedFunctions);

        // Build output
        StringBuilder sb = new StringBuilder();
        sb.append("\nnamespace Processing {\n\n");

        // Hoist preprocessor directives and namespaces to file scope
        List<TopLevelItem> filteredRest = new ArrayList<>();
        for (TopLevelItem item : depResult.rest) {
            if (item instanceof PreprocessorLine pl) {
                if (pl.rawText().startsWith("#include")) {
                    preNs.append(CodeGen.generateNode(item, 0));
                } else {
                    sb.append(CodeGen.generateNode(item, 0));
                }
                continue;
            }
            if (item instanceof NamespaceDecl || item instanceof UsingNamespaceDecl) {
                sb.append(CodeGen.generateNode(item, 0));
                continue;
            }
            filteredRest.add(item);
        }

        // Forward decls
        for (FunctionDecl fd : forwardDecls) {
            sb.append(CodeGen.generateNode(fd, 0));
        }

        // Enum extracted items
        for (TopLevelItem item : enumResult.enums) {
            sb.append(CodeGen.generateNode(item, 0));
        }

        // Hoisted functions and variables from dependency hoister
        for (TopLevelItem item : depResult.hoistedFunctions) {
            sb.append(CodeGen.generateNode(item, 0));
        }
        for (TopLevelItem item : depResult.hoistedVariables) {
            sb.append(CodeGen.generateNode(item, 0));
        }

        // Hoisted arrays
        for (TopLevelItem item : arrayResult.hoistedArrays) {
            sb.append(CodeGen.generateNode(item, 0));
        }

        // Final classes (_PSketch injected)
        for (TypeDef td : finalClasses) {
            sb.append(CodeGen.generateNode(td, 0));
        }

        // Remaining items
        for (TopLevelItem item : filteredRest) {
            sb.append(CodeGen.generateNode(item, 0));
        }

        sb.append("\n} // namespace Processing\n");
        return sb.toString();
    }

    private static void appendHeader(StringBuilder out) {
        out.append("#include \"Processing.h\"\n");
        out.append("using std::vector; using std::string; using std::wstring;\n");
        out.append("using std::pair; using std::make_pair; using std::tuple;\n");
        out.append("using std::deque; using std::list; using std::stack; using std::queue;\n");
        out.append("using std::unordered_map; using std::unordered_set;\n");
        out.append("using std::sort; using std::shuffle; using std::reverse;\n");
        out.append("using std::unique_ptr; using std::shared_ptr;\n");
        out.append("using std::make_unique; using std::make_shared;\n");
        out.append("using std::to_string; using std::stoi; using std::stof; using std::stod;\n");
        out.append("using std::function;\n");
        out.append("using std::map; using std::set;\n");
        out.append("using std::array; using std::optional;\n");
        out.append("using std::runtime_error; using std::logic_error; using std::exception;\n");
        out.append("using std::numeric_limits;\n");
    }

    private static String invoke(CppBuild build, String method, String code) throws Exception {
        Method m = getMethod(CppBuild.class, method, String.class);
        m.setAccessible(true);
        return (String) m.invoke(build, code);
    }

    private static Method getMethod(Class<?> cls, String name, Class<?>... params) throws NoSuchMethodException {
        while (cls != null) {
            try { return cls.getDeclaredMethod(name, params); }
            catch (NoSuchMethodException e) { cls = cls.getSuperclass(); }
        }
        throw new NoSuchMethodException(name);
    }

    private static CppBuild allocate() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (CppBuild) unsafe.allocateInstance(CppBuild.class);
    }
}
