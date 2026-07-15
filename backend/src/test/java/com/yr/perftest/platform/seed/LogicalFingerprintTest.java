package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogicalFingerprintTest {
    @Test
    void computesOrderIndependentMultisetFingerprintsForNoPrimaryKeyRows() {
        Map<String, Object> first = row("value", "a");
        Map<String, Object> second = row("value", "b");

        String original = LogicalFingerprint.multisetFingerprint(List.of(first, second));
        String reordered = LogicalFingerprint.multisetFingerprint(List.of(second, first));
        String duplicated = LogicalFingerprint.multisetFingerprint(List.of(first, second, second));

        assertThat(reordered).isEqualTo(original);
        assertThat(duplicated).isNotEqualTo(original);
    }

    private static Map<String, Object> row(String key, Object value) {
        return new LinkedHashMap<>(Map.of(key, value));
    }
}
