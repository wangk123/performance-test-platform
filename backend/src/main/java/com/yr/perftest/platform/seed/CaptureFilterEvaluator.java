package com.yr.perftest.platform.seed;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class CaptureFilterEvaluator {
    private CaptureFilterEvaluator() {
    }

    public static Set<String> evaluate(List<String> visibleTables, List<String> includes, List<String> excludes) {
        if (includes == null || includes.isEmpty()) {
            throw new SeedValidationException("include filter is required");
        }
        List<String> visible = visibleTables == null ? List.of() : visibleTables;
        Set<String> included = visible.stream()
                .filter(table -> matchesAny(table, includes))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return included.stream()
                .filter(table -> !matchesAny(table, excludes == null ? List.of() : excludes))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean matchesAny(String table, List<String> patterns) {
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isBlank() && matches(table, pattern.trim())) {
                return true;
            }
        }
        return false;
    }

    static boolean matches(String table, String pattern) {
        if (pattern.regionMatches(true, 0, "regex:", 0, 6)) {
            return Pattern.compile(pattern.substring(6)).matcher(table).matches();
        }
        if (pattern.contains("*") || pattern.contains("?")) {
            return wildcardToRegex(pattern).matcher(table).matches();
        }
        return table.equalsIgnoreCase(pattern);
    }

    private static Pattern wildcardToRegex(String wildcard) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '.' -> sb.append("\\.");
                default -> {
                    if ("\\^$|[](){}".indexOf(c) >= 0) {
                        sb.append('\\');
                    }
                    sb.append(c);
                }
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }
}
