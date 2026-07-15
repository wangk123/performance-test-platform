package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleInferenceEngineTest {

    @Test
    void stableValueIsLiteralHigh() {
        TableMetadata meta = new TableMetadata("app.users", List.of("id"), Set.of("id"), Map.of());
        ColumnSamples samples = new ColumnSamples("name", List.of("张三", "张三", "张三"));
        InferredColumn column = RoleInferenceEngine.inferColumn(samples, meta, List.of());
        assertThat(column.role()).isEqualTo(FieldRole.LITERAL);
        assertThat(column.confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void uniqueChangingColumnIsUniqueRegen() {
        TableMetadata meta = new TableMetadata("app.users", List.of("id"), Set.of("id"), Map.of());
        ColumnSamples samples = new ColumnSamples("id", List.of("1", "2", "3"));
        InferredColumn column = RoleInferenceEngine.inferColumn(samples, meta, List.of());
        assertThat(column.role()).isEqualTo(FieldRole.UNIQUE_REGEN);
        assertThat(column.confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void formalFkIsFkRefHigh() {
        TableMetadata meta = new TableMetadata(
                "app.orders",
                List.of("id"),
                Set.of("id"),
                Map.of("user_id", new FkRef("app.users", "id"))
        );
        ColumnSamples samples = new ColumnSamples("user_id", List.of("10", "20", "30"));
        InferredColumn column = RoleInferenceEngine.inferColumn(samples, meta, List.of());
        assertThat(column.role()).isEqualTo(FieldRole.FK_REF);
        assertThat(column.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(column.refTable()).isEqualTo("app.users");
        assertThat(column.refColumn()).isEqualTo("id");
    }

    @Test
    void mobileMorphologySuggestsFormattedRand() {
        TableMetadata meta = new TableMetadata("app.users", List.of("id"), Set.of("id"), Map.of());
        ColumnSamples samples = new ColumnSamples("mobile", List.of("13800138000", "13900139000", "13700137000"));
        InferredColumn column = RoleInferenceEngine.inferColumn(samples, meta, List.of());
        assertThat(column.role()).isEqualTo(FieldRole.FORMATTED_RAND);
        assertThat(column.suggestedGenerator()).isEqualTo("randomMobile");
        assertThat(column.confidence()).isEqualTo(Confidence.MEDIUM);
    }
}
