package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdjacentDiffInferenceTest {
    @Test
    void aggregatesRepeatedOperationsAcrossAdjacentIntervalsWithEvidence() {
        TableMetadata metadata = new TableMetadata(
                "app.orders",
                List.of("id"),
                Set.of("id"),
                Map.of()
        );
        DiffChainResult chain = AdjacentSampleDiffEngine.analyze(List.of(
                sample(1L, 1, "a", null, metadata),
                sample(2L, 2, "b", "inserted", metadata),
                sample(3L, 3, "c", "inserted", metadata),
                sample(4L, 4, "d", null, metadata)
        ));

        AdjacentInferenceResult result = AdjacentDiffInference.infer(chain);

        AdjacentTableInference table = result.tables().stream()
                .filter(candidate -> candidate.table().equals("app.orders"))
                .findFirst()
                .orElseThrow();
        assertThat(table.coverage()).isEqualTo(1.0);
        assertThat(table.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(table.rationale()).contains("3/3");
        assertThat(table.risks()).anyMatch(risk -> risk.contains("DELETE"));

        AdjacentOperationInference update = table.operations().stream()
                .filter(operation -> operation.type().equals("UPDATE"))
                .findFirst()
                .orElseThrow();
        assertThat(update.coverage()).isEqualTo(1.0);
        assertThat(update.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(update.columns())
                .extracting(InferredColumn::column)
                .contains("name");

        assertThat(table.operations())
                .extracting(AdjacentOperationInference::type)
                .contains("INSERT", "UPDATE")
                .doesNotContain("DELETE");
        assertThat(result.templateDraft().operations())
                .extracting(TemplateOperation::type)
                .contains("INSERT", "UPDATE")
                .doesNotContain("DELETE");
    }

    @Test
    void marksNoPrimaryKeyInferenceRiskAndSuppressesUpdateOperations() {
        TableMetadata metadata = new TableMetadata("app.events", List.of(), Set.of(), Map.of());
        DiffChainResult chain = AdjacentSampleDiffEngine.analyze(List.of(
                noPkSample(1L, 1, "a", metadata),
                noPkSample(2L, 2, "b", metadata),
                noPkSample(3L, 3, "c", metadata)
        ));

        AdjacentTableInference table = AdjacentDiffInference.infer(chain).tables().get(0);

        assertThat(table.risky()).isTrue();
        assertThat(table.risks()).anyMatch(risk -> risk.contains("primary key"));
        assertThat(table.operations())
                .extracting(AdjacentOperationInference::type)
                .doesNotContain("UPDATE");
        assertThat(table.operations())
                .allMatch(AdjacentOperationInference::risky);
    }

    private static SampleSnapshotView sample(
            long id,
            int seq,
            String name,
            String insertedName,
            TableMetadata metadata
    ) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.add(row(1, name));
        if (insertedName != null) {
            rows.add(row(2, insertedName));
        }
        return new SampleSnapshotView(
                id,
                7L,
                seq,
                Instant.parse("2026-07-15T06:0" + seq + ":00Z"),
                1,
                "SUCCEEDED",
                false,
                Map.of(
                        "app.orders",
                        TableSnapshotView.fromRows("app.orders", metadata, rows, 10)
                )
        );
    }

    private static Map<String, Object> row(int id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private static SampleSnapshotView noPkSample(
            long id,
            int seq,
            String value,
            TableMetadata metadata
    ) {
        return new SampleSnapshotView(
                id,
                7L,
                seq,
                Instant.parse("2026-07-15T06:0" + seq + ":00Z"),
                1,
                "SUCCEEDED",
                false,
                Map.of(
                        "app.events",
                        TableSnapshotView.fromRows(
                                "app.events",
                                metadata,
                                List.of(Map.of("value", value)),
                                10
                        )
                )
        );
    }
}
