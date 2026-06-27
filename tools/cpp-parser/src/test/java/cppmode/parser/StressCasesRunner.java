package cppmode.parser;

import cppmode.parser.ast.decl.CompilationUnit;
import cppmode.parser.passes.CodeGen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs every .cpp file in stress-cases/ against the parser and checks
 * its result against a declared expectation, read from a header comment
 * at the top of the file:
 *
 *   // EXPECT: PASS    -- this MUST parse (and codegen) successfully.
 *                          A failure here is a REAL REGRESSION.
 *   // EXPECT: FAIL    -- this is a KNOWN, currently-unsupported
 *                          construct. If it suddenly starts passing,
 *                          that's a pleasant surprise worth noting (the
 *                          file's own header should be updated to PASS
 *                          once that's confirmed deliberate, not just
 *                          left stale), but it is NOT treated as a
 *                          failure of this test run.
 *
 * This is designed to grow forever: drop a new .cpp file into
 * stress-cases/ with an EXPECT header and a short explanation, and it's
 * automatically picked up next run -- no code changes needed here. Two
 * kinds of file belong in this folder:
 *
 *   - Cases that probe a construct NOT YET CONFIRMED to work, recording
 *     today's real, honest answer (PASS or FAIL) rather than assuming.
 *   - REGRESSION GUARDS for real bugs already found and fixed elsewhere
 *     in this project (multi-declarator pointer markers, scoped
 *     templated construction, etc.) -- these should all say EXPECT: PASS,
 *     and exist specifically so a future change that reintroduces one of
 *     those bugs gets caught immediately, loudly, by this runner.
 *
 * Every file's outcome is checked two ways when EXPECT is PASS: it must
 * both PARSE without throwing, AND the parsed AST must round-trip
 * through CodeGen without throwing -- catching the "parses fine but
 * codegen chokes on this shape" class of bug too, not just pure parse
 * failures.
 */
public final class StressCasesRunner {

    private static final Pattern EXPECT_PATTERN = Pattern.compile("//\\s*EXPECT:\\s*(PASS|FAIL)");

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "stress-cases");
        if (!Files.isDirectory(dir)) {
            System.out.println("ERROR: " + dir + " is not a directory.");
            System.exit(1);
        }

        List<Path> files = Files.list(dir)
            .filter(p -> p.toString().endsWith(".cpp"))
            .sorted()
            .toList();

        if (files.isEmpty()) {
            System.out.println("No .cpp files found in " + dir + " -- nothing to run.");
            return;
        }

        int matched = 0;
        int mismatched = 0;
        int malformed = 0;
        List<String> mismatchDetails = new ArrayList<>();

        for (Path f : files) {
            String src = Files.readString(f);
            String expectation = readExpectation(src);
            if (expectation == null) {
                System.out.println("MALFORMED " + f.getFileName() + ": no \"// EXPECT: PASS\" or \"// EXPECT: FAIL\" header found");
                malformed++;
                continue;
            }

            boolean actuallyPasses = actuallyPasses(src);
            boolean expectedToPass = expectation.equals("PASS");

            if (actuallyPasses == expectedToPass) {
                matched++;
                System.out.println((actuallyPasses ? "PASS    " : "FAIL(ok)") + " " + f.getFileName());
            } else if (actuallyPasses && !expectedToPass) {
                // A known-FAIL case started passing. Not a failure of
                // this run, but worth a clear, distinct message -- this
                // file's header should be reviewed and likely updated.
                matched++;
                System.out.println("NOW-PASSES " + f.getFileName()
                    + "  (declared EXPECT: FAIL, but it parses and codegens cleanly now --"
                    + " update this file's header to EXPECT: PASS once confirmed deliberate)");
            } else {
                // Declared PASS but actually fails -- a REAL regression.
                mismatched++;
                String detail = f.getFileName() + ": declared EXPECT: PASS but failed";
                mismatchDetails.add(detail);
                System.out.println("REGRESSION " + detail);
            }
        }

        System.out.println();
        System.out.println("=== STRESS CASES ===");
        System.out.println("Total files:        " + files.size());
        System.out.println("Matched expectation: " + matched);
        System.out.println("REGRESSIONS (declared PASS, now fails): " + mismatched);
        System.out.println("Malformed (no EXPECT header):           " + malformed);

        if (!mismatchDetails.isEmpty()) {
            System.out.println();
            System.out.println("Regression details:");
            for (String d : mismatchDetails) System.out.println("  " + d);
            System.exit(1);
        }
    }

    private static String readExpectation(String src) {
        for (String line : src.lines().toList()) {
            Matcher m = EXPECT_PATTERN.matcher(line);
            if (m.find()) return m.group(1);
            // Header comments are expected at the very top -- stop
            // looking once we hit a non-comment, non-blank line, so a
            // stray "// EXPECT: PASS" buried later in an example string
            // or similar can't be mistaken for the real header.
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
                break;
            }
        }
        return null;
    }

    private static boolean actuallyPasses(String src) {
        try {
            CompilationUnit cu = Parser.parse(src);
            CodeGen.generate(cu); // also confirm codegen doesn't choke on this shape
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
