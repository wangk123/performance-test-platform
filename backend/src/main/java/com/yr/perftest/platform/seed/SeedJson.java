package com.yr.perftest.platform.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public final class SeedJson {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private SeedJson() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new SeedValidationException("json serialize failed");
        }
    }

    public static <T> T read(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json == null ? "null" : json, type);
        } catch (Exception ex) {
            throw new SeedValidationException("json parse failed");
        }
    }

    public static SeedTemplateDraft draftFromJson(String json) {
        return read(json, new TypeReference<>() {
        });
    }

    public static List<String> stringList(String json) {
        return read(json, new TypeReference<>() {
        });
    }

    public static Map<String, Map<String, String>> stringMapMap(String json) {
        return read(json == null || json.isBlank() ? "{}" : json, new TypeReference<>() {
        });
    }
}
