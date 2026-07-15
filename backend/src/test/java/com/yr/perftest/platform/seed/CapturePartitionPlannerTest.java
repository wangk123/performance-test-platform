package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CapturePartitionPlannerTest {
    private final CapturePartitionPlanner planner = new CapturePartitionPlanner();

    @Test
    void usesOneTableWorkUnitByDefault() {
        TableMetadata metadata = metadata("shop.orders", List.of("id"));

        List<CapturePartitionPlanner.Partition> partitions = planner.plan(metadata, 100);

        assertThat(partitions).singleElement()
                .extracting(CapturePartitionPlanner.Partition::mode)
                .isEqualTo(CapturePartitionPlanner.Mode.TABLE);
        assertThat(partitions.get(0).maxRows()).isEqualTo(100);
    }

    @Test
    void splitsSingleNumericPrimaryKeyIntoBoundedRanges() {
        TableMetadata metadata = metadata("shop.orders", List.of("id"));

        List<CapturePartitionPlanner.Partition> partitions = planner.plan(
                metadata,
                new CapturePartitionPlanner.NumericPrimaryKeyRange("id", 1L, 2500L),
                1000
        );

        assertThat(partitions).hasSize(3);
        assertThat(partitions).allMatch(partition -> partition.mode() == CapturePartitionPlanner.Mode.RANGE);
        assertThat(partitions).extracting(CapturePartitionPlanner.Partition::lowerBound)
                .containsExactly("1", "1001", "2001");
        assertThat(partitions).extracting(CapturePartitionPlanner.Partition::upperBound)
                .containsExactly("1000", "2000", "2500");
        assertThat(partitions).allMatch(partition -> partition.maxRows() <= 1000);
    }

    @Test
    void usesKeysetBatchesForCompositeAndStringPrimaryKeys() {
        TableMetadata composite = metadata("shop.order_items", List.of("order_id", "item_id"));
        TableMetadata string = metadata("shop.users", List.of("username"));

        List<CapturePartitionPlanner.Partition> compositePlan = planner.plan(
                composite,
                List.of(
                        new CapturePartitionPlanner.PrimaryKeyColumn("order_id", CapturePartitionPlanner.KeyType.NUMERIC),
                        new CapturePartitionPlanner.PrimaryKeyColumn("item_id", CapturePartitionPlanner.KeyType.NUMERIC)
                ),
                250
        );
        List<CapturePartitionPlanner.Partition> stringPlan = planner.plan(
                string,
                List.of(new CapturePartitionPlanner.PrimaryKeyColumn(
                        "username",
                        CapturePartitionPlanner.KeyType.STRING
                )),
                250
        );

        assertThat(compositePlan).singleElement()
                .extracting(CapturePartitionPlanner.Partition::mode)
                .isEqualTo(CapturePartitionPlanner.Mode.KEYSET);
        assertThat(stringPlan).singleElement()
                .extracting(CapturePartitionPlanner.Partition::mode)
                .isEqualTo(CapturePartitionPlanner.Mode.KEYSET);
        assertThat(compositePlan.get(0).riskyNoPk()).isFalse();
        assertThat(compositePlan.get(0).maxRows()).isEqualTo(250);
    }

    @Test
    void usesOneRiskyStreamWhenTableHasNoPrimaryKey() {
        List<CapturePartitionPlanner.Partition> partitions = planner.plan(
                metadata("shop.audit_log", List.of()),
                100
        );

        assertThat(partitions).singleElement()
                .satisfies(partition -> {
                    assertThat(partition.mode()).isEqualTo(CapturePartitionPlanner.Mode.SINGLE_STREAM);
                    assertThat(partition.riskyNoPk()).isTrue();
                    assertThat(partition.maxRows()).isEqualTo(100);
                });
    }

    private static TableMetadata metadata(String table, List<String> primaryKeyColumns) {
        return new TableMetadata(table, primaryKeyColumns, Set.of(), Map.of());
    }
}
