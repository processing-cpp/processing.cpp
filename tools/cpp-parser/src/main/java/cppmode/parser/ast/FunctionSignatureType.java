package cppmode.parser.ast;

import java.util.List;

/**
 * A bare function signature with no pointer declarator, used only as a template
 * argument inside std::function&lt;...&gt; (e.g. the "void(int)" inside
 * "std::function&lt;void(int)&gt;"). Distinct from FunctionPointerType because no
 * '(*)' appears in the source -- confirmed as a separate shape during corpus
 * validation rather than reusing FunctionPointerType for both cases.
 */
public record FunctionSignatureType(
    TypeRef returnType,
    List<TypeRef> paramTypes
) implements TypeRef {

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder(returnType.describe());
        sb.append("(");
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramTypes.get(i).describe());
        }
        sb.append(")");
        return sb.toString();
    }
}
