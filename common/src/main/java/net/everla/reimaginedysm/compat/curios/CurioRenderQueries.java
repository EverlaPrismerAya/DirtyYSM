package net.everla.reimaginedysm.compat.curios;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Literal Curios and head-equipment queries used by model-owned accessory animations. */
public final class CurioRenderQueries {
    private static final Pattern QUERY = Pattern.compile(
            "\\b(has_any_curios(?:_with_all_tags|_with_any_tag)?|is_item_name_any|equipped_item_all_tags|equipped_item_any_tag)\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEAD_SLOT = Pattern.compile("^\\s*'head'\\s*,", Pattern.CASE_INSENSITIVE);
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
            String function = queries.group(1).toLowerCase(Locale.ROOT);
            String arguments = source.substring(queries.end(), end - 1);
            if (!function.startsWith("has_any_curios") && !HEAD_SLOT.matcher(arguments).find()) continue;
            boolean tag = !function.equals("has_any_curios") && !function.equals("is_item_name_any");
            var locations = LOCATION.matcher(arguments);
            while (locations.find()) {
                references.add((tag ? "#" : "") + locations.group(1));
            }
        }
        return Set.copyOf(references);
    }
}
