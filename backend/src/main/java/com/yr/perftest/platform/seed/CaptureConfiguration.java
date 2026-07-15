package com.yr.perftest.platform.seed;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

record CaptureConfiguration(
        List<String> includes,
        List<String> excludes,
        int threadCount,
        int batchRows
) {
    static CaptureConfiguration from(PersistentSeedCaptureSampleRecord sample) {
        Map<String, Object> config = SeedJson.read(
                sample.getConfigSnapshotJson(),
                new TypeReference<Map<String, Object>>() {
                }
        );
        return new CaptureConfiguration(
                strings(config.get("includes")),
                strings(config.get("excludes")),
                number(config.get("threadCount"), 1),
                number(config.get("batchRows"), 1000)
        );
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
