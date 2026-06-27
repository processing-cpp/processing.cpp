package cppmode.parser.ast;

import java.util.List;

/**
 * The common-case type reference.
 *
 * @param baseName      e.g. "int", "ArrayList", "std::string", "T", "auto"
 * @param templateArgs  empty for non-template types; each element is itself a
 *                       TypeRef so nested templates (Pair&lt;int, ArrayList&lt;T&gt;&gt;)
 *                       and function-signature template args (std::function&lt;...&gt;)
 *                       both work without a separate node
 * @param pointerDepth  number of '*' suffixes; 0 for a plain value type
 * @param isReference   true for a '&amp;' suffix
 * @param isConst       true if a leading 'const' qualifier was present
 */
public record NamedType(
    String baseName,
    List<TypeRef> templateArgs,
    int pointerDepth,
    boolean isReference,
    boolean isConst
) implements TypeRef {

    public static NamedType simple(String baseName) {
        return new NamedType(baseName, List.of(), 0, false, false);
    }

    @Override
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (isConst) sb.append("const ");
        sb.append(baseName);
        if (!templateArgs.isEmpty()) {
            sb.append("<");
            for (int i = 0; i < templateArgs.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(templateArgs.get(i).describe());
            }
            sb.append(">");
        }
        sb.append("*".repeat(pointerDepth));
        if (isReference) sb.append("&");
        return sb.toString();
    }
}
