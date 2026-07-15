package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotDiffEngineTest {
    @Test
    void detectsInsertAndUpdate() {
        Map<String, Map<String, String>> before = Map.of(
                "id=1", Map.of("id", "1", "name", "a")
        );
        Map<String, Map<String, String>> after = Map.of(
                "id=1", Map.of("id", "1", "name", "b"),
                "id=2", Map.of("id", "2", "name", "c")
        );
        var diffs = SnapshotDiffEngine.diff(before, after);
        assertThat(diffs.get("id=1").kind()).isEqualTo("UPDATE");
        assertThat(diffs.get("id=2").kind()).isEqualTo("INSERT");
    }
}
