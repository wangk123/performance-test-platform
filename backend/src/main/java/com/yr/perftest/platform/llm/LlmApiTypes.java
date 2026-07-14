package com.yr.perftest.platform.llm;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LlmApiTypes {
    private LlmApiTypes() {
    }

    public static List<LlmApiType> normalize(List<LlmApiType> apiTypes, LlmApiType legacySingle) {
        LinkedHashSet<LlmApiType> set = new LinkedHashSet<>();
        if (apiTypes != null) {
            for (LlmApiType type : apiTypes) {
                if (type != null) {
                    set.add(type);
                }
            }
        }
        if (set.isEmpty() && legacySingle != null) {
            set.add(legacySingle);
        }
        if (set.isEmpty()) {
            set.add(LlmApiType.OPENAI);
        }
        return List.copyOf(set);
    }

    public static String encode(List<LlmApiType> apiTypes) {
        List<LlmApiType> normalized = normalize(apiTypes, null);
        StringBuilder sb = new StringBuilder();
        for (LlmApiType type : normalized) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(type.name());
        }
        return sb.toString();
    }

    public static List<LlmApiType> decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(LlmApiType.OPENAI);
        }
        LinkedHashSet<LlmApiType> set = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String token = part.trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            set.add(LlmApiType.valueOf(token));
        }
        if (set.isEmpty()) {
            return List.of(LlmApiType.OPENAI);
        }
        return List.copyOf(set);
    }

    public static List<LlmApiType> merge(List<LlmApiType> current, LlmApiType extra) {
        LinkedHashSet<LlmApiType> set = new LinkedHashSet<>(normalize(current, null));
        if (extra != null) {
            set.add(extra);
        }
        return List.copyOf(set);
    }

    public static boolean supports(List<LlmApiType> apiTypes, LlmApiType type) {
        return normalize(apiTypes, null).contains(type);
    }

    public static LlmApiType resolve(List<LlmApiType> apiTypes, LlmApiType requested) {
        List<LlmApiType> supported = normalize(apiTypes, null);
        if (requested != null) {
            if (!supported.contains(requested)) {
                throw new LlmValidationException("model does not support apiType " + requested);
            }
            return requested;
        }
        if (supported.contains(LlmApiType.OPENAI)) {
            return LlmApiType.OPENAI;
        }
        return supported.get(0);
    }

    public static Set<LlmApiType> asSet(List<LlmApiType> apiTypes) {
        return EnumSet.copyOf(normalize(apiTypes, null));
    }

    public static List<LlmApiType> mutableCopy(List<LlmApiType> apiTypes) {
        return new ArrayList<>(normalize(apiTypes, null));
    }
}
