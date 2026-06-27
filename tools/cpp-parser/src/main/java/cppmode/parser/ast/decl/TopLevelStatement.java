package cppmode.parser.ast.decl;

import cppmode.parser.Token;
import cppmode.parser.ast.stmt.Statement;

import java.util.List;

/**
 * Wraps a bare statement appearing directly at file (top) scope -- confirmed
 * necessary by real Processing "static mode" sketches in the example corpus
 * (e.g. Coordinates.pde), which have no setup()/draw() at all and consist of
 * a flat sequence of statements executed once, top to bottom. A call like
 * "size(640, 360);" at true top level doesn't fit any other TopLevelItem
 * shape (it's not a declaration), so it's wrapped here instead, carrying
 * the underlying Statement unchanged.
 *
 * This was NOT anticipated by the original corpus walk -- all four
 * hand-picked example sketches happened to use active mode with explicit
 * setup()/draw(). Found only by running the parser against the full real
 * example corpus.
 */
public record TopLevelStatement(
    Statement statement,
    int line,
    int col,
    List<Token> leadingComments
) implements TopLevelItem {
}
