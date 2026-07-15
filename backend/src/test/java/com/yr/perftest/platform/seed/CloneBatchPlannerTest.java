package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CloneBatchPlannerTest {

    @Test
    void rewritesChildFkFromParentIdMap() {
        Map<String, String> idMap = new LinkedHashMap<>();
        ValueGenerator generator = key -> {
            if ("app.users.id".equals(key)) {
                return "9001";
            }
            return "x";
        };
        List<PlannedStatement> statements = CloneBatchPlanner.plan(
                List.of(
                        new TemplateOperation("INSERT", "app.users", false, List.of(
                                new TemplateColumn("id", FieldRole.UNIQUE_REGEN, Confidence.HIGH, "uk", null, null, "seq", true),
                                new TemplateColumn("name", FieldRole.LITERAL, Confidence.HIGH, "stable", null, null, null, true)
                        )),
                        new TemplateOperation("INSERT", "app.orders", false, List.of(
                                new TemplateColumn("id", FieldRole.UNIQUE_REGEN, Confidence.HIGH, "uk", null, null, "seq", true),
                                new TemplateColumn("user_id", FieldRole.FK_REF, Confidence.HIGH, "fk", "app.users", "id", null, true)
                        ))
                ),
                Map.of(
                        "app.users", Map.of("id", "1", "name", "张三"),
                        "app.orders", Map.of("id", "10", "user_id", "1")
                ),
                generator,
                idMap
        );
        assertThat(statements).hasSize(2);
        assertThat(statements.get(0).values().get("id")).isEqualTo("9001");
        assertThat(statements.get(1).values().get("user_id")).isEqualTo("9001");
    }
}
