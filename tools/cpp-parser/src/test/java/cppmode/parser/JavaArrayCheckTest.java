package cppmode.parser;

import cppmode.parser.passes.JavaArrayCheck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JavaArrayCheckTest {

    public static void main(String[] args) throws IOException {
        int failures = 0;

        failures += expectThrows("int[] bad = new int[5];", "int", 1);
        failures += expectThrows("float[][] grid = new float[10][10];", "float", 2);
        failures += expectThrows("  int [ ]   bad   =   new   int [ 5 ] ;  ", "int", 1); // whitespace tolerance
        failures += expectNoThrow("int arr[10];"); // C-style fixed array -- NOT the Java shape, must not trip
        failures += expectNoThrow("int* p = new int[10];"); // legitimate array-new on a pointer, no "[]" on the LHS name
        failures += expectNoThrow("ArrayList<Handle> handles;"); // legitimate CppMode container type
        failures += expectNoThrow("// int[] looksLikeIt = new int[5]; but it's a comment"); // must not false-positive inside a comment
        failures += expectNoThrowOnFile("fixtures/handles.cpp");
        failures += expectNoThrowOnFile("fixtures/storing_input.cpp");
        failures += expectNoThrowOnFile("fixtures/kitchen_sink.cpp");

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL JAVAARRAYCHECK TESTS PASSED");
        } else {
            System.out.println(failures + " JAVAARRAYCHECK TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int expectThrows(String src, String expectedElementType, int expectedDims) {
        try {
            JavaArrayCheck.check(src);
            System.out.println("FAIL  expected E0004 for: " + src);
            return 1;
        } catch (JavaArrayCheck.E0004Exception e) {
            if (e.elementType.equals(expectedElementType) && e.dimensions == expectedDims) {
                System.out.println("OK    correctly rejected: " + src.strip());
                return 0;
            } else {
                System.out.println("FAIL  wrong details for: " + src + "  (got elementType=" + e.elementType + " dims=" + e.dimensions + ")");
                return 1;
            }
        }
    }

    private static int expectNoThrow(String src) {
        try {
            JavaArrayCheck.check(src);
            System.out.println("OK    correctly allowed: " + src.strip());
            return 0;
        } catch (JavaArrayCheck.E0004Exception e) {
            System.out.println("FAIL  false positive on: " + src + "  (" + e.getMessage() + ")");
            return 1;
        }
    }

    private static int expectNoThrowOnFile(String path) throws IOException {
        String src = Files.readString(Path.of(path));
        try {
            JavaArrayCheck.check(src);
            System.out.println("OK    no false positive in real fixture: " + path);
            return 0;
        } catch (JavaArrayCheck.E0004Exception e) {
            System.out.println("FAIL  false positive in real fixture: " + path + "  (" + e.getMessage() + ")");
            return 1;
        }
    }
}
