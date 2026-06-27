package cppmode.parser.ast;

import java.util.List;

/**
 * The raw C-style function pointer declarator: "ReturnType (*)(ParamType, ...)".
 * Confirmed necessary by the corpus's useRawFunctionPointer fixture
 * ("int (*funcPtr)(int, int) = someFunc;") -- this does not fit NamedType's shape,
 * since the pointer applies to an entire function signature, not a named type.
 */
public record FunctionPointerType(
    TypeRef returnType,
    List<TypeRef> paramTypes
) implements TypeRef {

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder(returnType.describe());
        sb.append(" (*)(");
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramTypes.get(i).describe());
        }
        sb.append(")");
        return sb.toString();
    }
}
