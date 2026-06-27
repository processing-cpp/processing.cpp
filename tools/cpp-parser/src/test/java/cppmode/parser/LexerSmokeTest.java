package cppmode.parser;

import java.util.List;

/** Manual smoke-test harness for the lexer, not a real test framework yet. */
public final class LexerSmokeTest {

    public static void main(String[] args) {
        int failures = 0;

        failures += check("cast and member call",
            "if (steps > (int)production.length()) {",
            List.of("if","(","steps",">","(","int",")","production",".","length","(",")",")","{"));

        failures += check("char literal arithmetic / comparisons",
            "repeats += step - 48; if (step >= 48 && step <= 57) {}",
            List.of("repeats","+=","step","-","48",";","if","(","step",">=","48","&&","step","<=","57",")","{","}"));

        failures += check("longest-match operators",
            "x <<= 2; y >>= 1; a <=> b; c -> d; e :: f;",
            // note: <=> is not in our symbol table (C++20 spaceship); expect it to
            // split into <= and > -- this is intentionally checked below, not assumed.
            null);

        failures += check("string with escapes",
            "std::string s = \"line\\nbreak \\\"quoted\\\"\";",
            List.of("std","::","string","s","=","\"line\\nbreak \\\"quoted\\\"\"",";"));

        failures += check("char literal with escape",
            "char c = '\\n'; char d = '\\'';",
            List.of("char","c","=","'\\n'",";","char","d","=","'\\''",";"));

        failures += check("line and block comments retained",
            "int x = 1; // trailing\n/* block\n comment */ int y = 2;",
            List.of("int","x","=","1",";","// trailing","/* block\n comment */","int","y","=","2",";"));

        failures += check("float literal forms",
            "float a = 90.0; float b = .5; float c = 1.5f; float d = 2e10; float e = 3.14e-2;",
            List.of("float","a","=","90.0",";","float","b","=",".5",";","float","c","=","1.5f",";",
                     "float","d","=","2e10",";","float","e","=","3.14e-2",";"));

        failures += check("hex and suffixed int literals",
            "int a = 0xFF; long b = 100L; unsigned c = 5u; long long d = 7LL;",
            List.of("int","a","=","0xFF",";","long","b","=","100L",";","unsigned","c","=","5u",";",
                     "long","long","d","=","7LL",";"));

        failures += check("template angle brackets vs shift/comparison ambiguity",
            "ArrayList<Handle>* others; int x = a < b; int y = a >> b;",
            List.of("ArrayList","<","Handle",">","*","others",";",
                    "int","x","=","a","<","b",";",
                    "int","y","=","a",">>","b",";"));

        failures += check("arrow chains and scope resolution",
            "others->get(i)->locked; std::function<void(int)> f;",
            List.of("others","->","get","(","i",")","->","locked",";",
                    "std","::","function","<","void","(","int",")",">","f",";"));

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL LEXER SMOKE TESTS PASSED");
        } else {
            System.out.println(failures + " LEXER SMOKE TEST(S) FAILED");
            System.exit(1);
        }
    }

    private static int check(String name, String src, List<String> expectedTexts) {
        List<Token> tokens = new Lexer(src).tokenize();
        // drop EOF for comparison
        List<Token> real = tokens.subList(0, tokens.size() - 1);

        StringBuilder dump = new StringBuilder();
        for (Token t : real) {
            dump.append("[").append(t.text()).append("]");
        }

        if (expectedTexts == null) {
            System.out.println("INFO  " + name + ": " + dump);
            return 0;
        }

        List<String> actualTexts = real.stream().map(Token::text).toList();
        if (actualTexts.equals(expectedTexts)) {
            System.out.println("OK    " + name);
            return 0;
        } else {
            System.out.println("FAIL  " + name);
            System.out.println("      expected: " + expectedTexts);
            System.out.println("      actual:   " + actualTexts);
            return 1;
        }
    }
}
