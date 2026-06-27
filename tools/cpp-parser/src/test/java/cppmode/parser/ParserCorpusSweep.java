package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Runs the full Parser (not just the Lexer) over every fixture file. */
public final class ParserCorpusSweep {

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "fixtures");
        List<Path> files = Files.list(dir)
            .filter(p -> p.toString().endsWith(".cpp") || p.toString().endsWith(".pde"))
            .sorted()
            .toList();

        int pass = 0, fail = 0;
        for (Path f : files) {
            String src = Files.readString(f);
            try {
                CompilationUnit cu = Parser.parse(src);
                System.out.printf("OK    %-50s  %3d top-level items%n", f.getFileName(), cu.items().size());
                pass++;
            } catch (ParseException e) {
                System.out.printf("FAIL  %-50s  %s%n", f.getFileName(), e.getMessage());
                fail++;
            } catch (Exception e) {
                System.out.printf("ERROR %-50s  %s: %s%n", f.getFileName(), e.getClass().getSimpleName(), e.getMessage());
                fail++;
            }
        }
        System.out.println();
        System.out.println("Pass: " + pass + "   Fail: " + fail + "   Total: " + (pass + fail));
    }
}
