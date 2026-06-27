package cppmode.parser;

import cppmode.parser.ast.TypeRef;

public final class TypeRefSmokeTest {

    public static void main(String[] args) {
        int failures = 0;

        failures += check("plain int", "int", "int");
        failures += check("plain float", "float", "float");
        failures += check("std::string", "std::string", "std::string");
        failures += check("pointer", "Handle*", "Handle*");
        failures += check("double pointer", "int**", "int**");
        failures += check("reference", "int&", "int&");
        failures += check("const reference", "const std::string&", "const std::string&");
        failures += check("simple template", "ArrayList<Handle>", "ArrayList<Handle>");
        failures += check("template pointer", "ArrayList<Handle>*", "ArrayList<Handle>*");
        failures += check("two-param template", "Pair<int, std::string>", "Pair<int, std::string>");
        failures += check("nested template (>> split)", "Pair<int, ArrayList<Handle>>",
                "Pair<int, ArrayList<Handle>>");
        failures += check("triple-nested template (>>> split)", "Box<Pair<int, ArrayList<Handle>>>",
                "Box<Pair<int, ArrayList<Handle>>>");
        failures += check("std::function template arg", "std::function<void(int)>",
                "std::function<void(int)>");
        failures += check("std::function with return type", "std::function<int(int)>",
                "std::function<int(int)>");
        failures += check("auto as type", "auto", "auto");
        failures += check("color pseudo-type", "color", "color");

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL TYPEREF SMOKE TESTS PASSED");
        } else {
            System.out.println(failures + " TYPEREF SMOKE TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int check(String name, String src, String expectedDescribe) {
        try {
            TypeRef result = Parser.parseTypeRefFromString(src);
            String actual = result.describe();
            if (actual.equals(expectedDescribe)) {
                System.out.println("OK    " + name + "  ->  " + actual);
                return 0;
            } else {
                System.out.println("FAIL  " + name);
                System.out.println("      expected: " + expectedDescribe);
                System.out.println("      actual:   " + actual);
                return 1;
            }
        } catch (Exception e) {
            System.out.println("FAIL  " + name + "  threw " + e);
            return 1;
        }
    }
}
