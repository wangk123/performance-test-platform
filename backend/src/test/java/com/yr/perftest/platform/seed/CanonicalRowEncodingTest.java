package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalRowEncodingTest {
    @Test
    void encodesNullTextNumericTemporalBinaryAndCompositeKeyStably() {
        Map<String, Object> row = row(
                "nullable", null,
                "name", "alice",
                "amount", new BigDecimal("12.3400"),
                "capturedAt", Instant.parse("2026-07-15T06:30:00Z"),
                "payload", new byte[]{0, 1, 2, 127},
                "tenantId", "tenant-a",
                "orderId", 42L
        );
        Map<String, Object> sameRow = row(
                "nullable", null,
                "name", "alice",
                "amount", new BigDecimal("12.3400"),
                "capturedAt", Instant.parse("2026-07-15T06:30:00Z"),
                "payload", new byte[]{0, 1, 2, 127},
                "tenantId", "tenant-a",
                "orderId", 42L
        );

        assertThat(LogicalFingerprint.rowFingerprint(row))
                .isEqualTo(LogicalFingerprint.rowFingerprint(sameRow));
        assertThat(CanonicalRowEncoding.encodeRow(List.of("tenant-a", 42L)))
                .isEqualTo(CanonicalRowEncoding.encodeRow(List.of("tenant-a", 42L)));
    }

    @Test
    void changesFingerprintWhenAFieldValueChanges() {
        Map<String, Object> original = row("id", 7, "name", "alice");
        Map<String, Object> changed = row("id", 7, "name", "bob");

        assertThat(LogicalFingerprint.rowFingerprint(original))
                .isNotEqualTo(LogicalFingerprint.rowFingerprint(changed));
    }

    @Test
    void preservesTheGivenColumnOrderInCanonicalEncoding() {
        Map<String, Object> firstOrder = row("tenantId", "tenant-a", "orderId", 42L);
        Map<String, Object> secondOrder = row("orderId", 42L, "tenantId", "tenant-a");

        assertThat(CanonicalRowEncoding.encodeRow(firstOrder))
                .isNotEqualTo(CanonicalRowEncoding.encodeRow(secondOrder));
    }

    @Test
    void normalizesEquivalentNumericValues() {
        assertThat(LogicalFingerprint.rowFingerprint(row("amount", new BigDecimal("12.3400"))))
                .isEqualTo(LogicalFingerprint.rowFingerprint(row("amount", new BigDecimal("12.34"))));
    }

    @Test
    void preservesTimestampPrecisionBeyondMilliseconds() {
        java.sql.Timestamp first = java.sql.Timestamp.valueOf("2026-07-15 06:30:00.000000001");
        java.sql.Timestamp second = java.sql.Timestamp.valueOf("2026-07-15 06:30:00.000000002");

        assertThat(LogicalFingerprint.rowFingerprint(row("capturedAt", first)))
                .isNotEqualTo(LogicalFingerprint.rowFingerprint(row("capturedAt", second)));
    }

    @Test
    void buildsStableChunkTableAndSchemaFingerprints() {
        Map<String, Object> first = row("id", 1, "name", "alice");
        Map<String, Object> second = row("id", 2, "name", "bob");
        List<Map<String, Object>> rows = List.of(first, second);
        List<String> schema = List.of("id:INTEGER", "name:VARCHAR");

        assertThat(LogicalFingerprint.chunkFingerprint(rows))
                .isEqualTo(LogicalFingerprint.chunkFingerprint(rows));
        assertThat(LogicalFingerprint.tableFingerprint(
                LogicalFingerprint.schemaFingerprint(schema),
                rows.size(),
                LogicalFingerprint.chunkFingerprint(rows)
        )).isEqualTo(LogicalFingerprint.tableFingerprint(
                LogicalFingerprint.schemaFingerprint(schema),
                rows.size(),
                LogicalFingerprint.chunkFingerprint(rows)
        ));
        assertThat(LogicalFingerprint.schemaFingerprint(schema))
                .isNotEqualTo(LogicalFingerprint.schemaFingerprint(List.of("name:VARCHAR", "id:INTEGER")));
    }

    private static LinkedHashMap<String, Object> row(Object... values) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put((String) values[i], values[i + 1]);
        }
        return row;
    }
}
