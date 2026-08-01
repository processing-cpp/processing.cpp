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

    private static final Set<String> LIFECYCLE = Set.of(
        "setup","draw","mousePressed","mouseReleased","mouseClicked",
        "mouseMoved","mouseDragged","mouseWheel",
        "keyPressed","keyReleased","keyTyped","settings"
    );

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
        CppBuild b = allocate();
        String code = input;
        code = invoke(b, "sanitize", code);
        code = invoke(b, "removeUserIncludes", code);
        code = code.replaceAll("(\\bfinal_suspend\\s*\\([^)]*\\))(\\s*\\{)", "$1 noexcept$2");
        code = code.replaceAll("(\\binitial_suspend\\s*\\([^)]*\\))(\\s*\\{)", "$1 noexcept$2");
        code = code.replaceAll("\\btranslate\\s*\\(\\s*0+\\.?0*f?\\s*,\\s*0+\\.?0*f?\\s*\\)\\s*;", "");
        code = code.replaceAll("\\btranslate\\s*\\(\\s*0+\\.?0*f?\\s*,\\s*0+\\.?0*f?\\s*,\\s*0+\\.?0*f?\\s*\\)\\s*;", "");
        code = invoke(b, "stripRawStringLiterals", code);
        code = code.replaceAll("(?<=[0-9a-fA-FxXbB])'(?=[0-9a-fA-F])", "");
        code = invoke(b, "javaToC", code);
        code = invoke(b, "stripNamespaceProcessing", code);
        code = invoke(b, "preprocessMacros", code);

        boolean hasSetup = code.contains("void setup(");
        boolean hasDraw  = code.contains("void draw(");

        StringBuilder out = new StringBuilder();
        StringBuilder preNs = new StringBuilder();
        appendHeader(out);

        if (hasSetup || hasDraw) {
            String result = runPipeline(code, preNs);
            System.out.print(preNs);
            System.out.print(out);
            System.out.print(result);
            System.out.print("\nint main() { Processing::_PSketch sketch; sketch.run(); return 0; }\n");
            return "";
        } else {
            out.append("\nnamespace Processing {\n\n");
            out.append(code);
            out.append("\n} // namespace Processing\n\n");
            out.append("int main() { Processing::_PSketch sketch; sketch.run(); return 0; }\n");
            return out.toString();
        }
    }

    private static String runPipeline(String code, StringBuilder preNs) throws Exception {
        CompilationUnit cu = Parser.parse(code);
        EnumScopeExtractor.Result enumResult = EnumScopeExtractor.extract(cu.items());
        List<TopLevelItem> afterLifecycle = LifecycleRewriter.rewrite(enumResult.rest, LIFECYCLE);
        ClassHoister.Result classResult = ClassHoister.hoist(afterLifecycle);
        List<PSketchInjector.Result> injected = PSketchInjector.injectAll(classResult.hoistedClasses);
        List<TypeDef> finalClasses = injected.stream().map(PSketchInjector.Result::typeDef).toList();
        ArrayHoister.Result arrayResult = ArrayHoister.hoist(classResult.rest);
        DependencyHoister.Result depResult = DependencyHoister.hoist(arrayResult.rest, finalClasses, LIFECYCLE);
        List<FunctionDecl> forwardDecls = ForwardDeclGenerator.generate(depResult.hoistedFunctions);

        StringBuilder sb = new StringBuilder();
        sb.append("\nnamespace Processing {\n\n");

        List<TopLevelItem> filteredRest = new ArrayList<>();
        for (TopLevelItem item : depResult.rest) {
            if (item instanceof PreprocessorLine pl) {
                if (pl.rawText().startsWith("#include")) preNs.append(CodeGen.generateNode(item, 0));
                else sb.append(CodeGen.generateNode(item, 0));
                continue;
            }
            if (item instanceof NamespaceDecl || item instanceof UsingNamespaceDecl) {
                sb.append(CodeGen.generateNode(item, 0)); continue;
            }
            filteredRest.add(item);
        }

        for (FunctionDecl fd : forwardDecls) sb.append(CodeGen.generateNode(fd, 0));
        for (TopLevelItem e : enumResult.enums) sb.append(CodeGen.generateNode(e, 0));

        // Collect lifecycle bodies from hoisted functions
        List<FunctionDecl> lifecycleBodies = new ArrayList<>();
        for (TopLevelItem item : depResult.hoistedFunctions) {
            if (item instanceof FunctionDecl fd && LIFECYCLE.contains(fd.name()) && fd.body() != null)
                lifecycleBodies.add(fd);
            else sb.append(CodeGen.generateNode(item, 0));
        }
        for (TopLevelItem item : depResult.hoistedVariables) sb.append(CodeGen.generateNode(item, 0));
        for (TopLevelItem item : arrayResult.hoistedArrays) sb.append(CodeGen.generateNode(item, 0));
        for (TypeDef td : finalClasses) sb.append(CodeGen.generateNode(td, 0));

        // Also collect from filteredRest
        List<TopLevelItem> nonLifecycle = new ArrayList<>();
        for (TopLevelItem item : filteredRest) {
            if (item instanceof FunctionDecl fd && LIFECYCLE.contains(fd.name()) && fd.body() != null)
                lifecycleBodies.add(fd);
            else nonLifecycle.add(item);
        }
        for (TopLevelItem item : nonLifecycle) sb.append(CodeGen.generateNode(item, 0));

        // Build _PSketch with inline lifecycle bodies
        sb.append("class _PSketch : public PApplet {\npublic:\n");
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

    private static void appendHeader(StringBuilder out) {
        out.append("#include \"Processing.h\"\n");
        out.append("using namespace std;\n");
    }

    private static String invoke(CppBuild b, String method, String code) throws Exception {
        Method m = getMethod(CppBuild.class, method, String.class);
        m.setAccessible(true);
        return (String) m.invoke(b, code);
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
