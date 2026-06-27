package cppmode.parser.ast.stmt;

import cppmode.parser.ast.TypeRef;

/**
 * A single "catch" clause. exceptionType/varName are both null for the
 * catch-all "catch (...)" form confirmed in the corpus (tryCatchPassthrough
 * fixture); a typed catch ("catch (const std::exception&amp; e)") is not yet
 * exercised by any fixture but supported by the grammar regardless, since
 * pass-through parsing of try/catch was always the scope, not just the
 * catch-all form specifically.
 */
public record CatchClause(TypeRef exceptionType, String varName, Block body, boolean isCatchAll) {
}
