package net.everla.reimaginedysm.compat.curios;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Literal item/tag queries used by model-owned accessory animations. */
public final class CurioRenderQueries {
    private static final Pattern QUERY = Pattern.compile(
            "\\bhas_any_curios(_with_all_tags|_with_any_tag)?\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION = Pattern.compile("'([a-z0-9_.-]+:[a-z0-9_./-]+)'");

    private CurioRenderQueries() {}

    public static Set<String> find(String source) {
        var references = new HashSet<String>();
        var queries = QUERY.matcher(source);
        while (queries.find()) {
            int end = queries.end();
            int depth = 1;
            boolean quoted = false;
            while (end < source.length() && depth > 0) {
                char c = source.charAt(end++);
                if (c == '\'') quoted = !quoted;
                if (!quoted) {
                    if (c == '(') depth++;
                    if (c == ')') depth--;
                }
            }
            if (depth != 0) continue;
            var locations = LOCATION.matcher(source.substring(queries.end(), end - 1));
            while (locations.find()) {
                references.add((queries.group(1) == null ? "" : "#") + locations.group(1));
            }
        }
        return Set.copyOf(references);
    }
}
