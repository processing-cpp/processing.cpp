package cppmode.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Runs the lexer over every fixture file and reports basic sanity stats. */
public final class LexerCorpusSweep {

    public static void main(String[] args) throws IOException {
        Path fixturesDir = Path.of(args.length > 0 ? args[0] : "fixtures");
        List<Path> files = Files.list(fixturesDir)
            .filter(p -> p.toString().endsWith(".cpp"))
            .sorted()
            .toList();

        if (files.isEmpty()) {
            System.out.println("No .cpp fixtures found in " + fixturesDir.toAbsolutePath());
            return;
        }

        int totalFiles = 0;
        int totalTokens = 0;

        for (Path f : files) {
            String src = Files.readString(f);
            List<Token> tokens = new Lexer(src).tokenize();
            totalFiles++;
            totalTokens += tokens.size();

            int unknownPunct = 0;
            int maxLine = 1;
            for (Token t : tokens) {
                maxLine = Math.max(maxLine, t.line());
            }

            System.out.printf("%-40s  %5d tokens  %4d lines%n",
                f.getFileName(), tokens.size(), maxLine);
        }

        System.out.println();
        System.out.println("Files: " + totalFiles + "   Total tokens: " + totalTokens);
    }
}
