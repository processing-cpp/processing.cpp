import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * CLI wrapper for the CppMode parser+codegen pipeline.
 * Reads Processing C++ sketch code from stdin, writes translated C++ to stdout.
 * Exit 0 = success, exit 1 = error (message on stderr).
 *
 * Usage: java -cp CppMode.jar ParserCLI
 */
public class ParserCLI {
    public static void main(String[] args) throws Exception {
        String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        try {
            var cu  = Parser.parse(input);
            var cpp = CodeGen.generate(cu);
            System.out.print(cpp);
            System.exit(0);
        } catch (Exception e) {
            System.err.println(e.getMessage() != null ? e.getMessage() : e.toString());
            System.exit(1);
        }
    }
}
