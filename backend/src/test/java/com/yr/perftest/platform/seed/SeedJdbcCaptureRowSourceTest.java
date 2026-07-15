package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeedJdbcCaptureRowSourceTest {
    @Test
    void streamsRowsInConfiguredBatchSizes() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:seed-capture-source;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS shop");
            statement.execute("""
                    CREATE TABLE shop.orders (
                        id INT PRIMARY KEY,
                        name VARCHAR(40)
                    )
                    """);
            statement.execute("""
                    INSERT INTO shop.orders(id, name)
                    VALUES (1, 'a'), (2, 'b'), (3, 'c'), (4, 'd'), (5, 'e')
                    """);

            SeedJdbcCaptureRowSource source = new SeedJdbcCaptureRowSource(connection);
            CapturePartitionPlanner.Partition partition = new CapturePartitionPlanner.Partition(
                    "shop.orders",
                    CapturePartitionPlanner.Mode.TABLE,
                    0,
                    2,
                    List.of("id"),
                    null,
                    null,
                    false
            );
            List<Integer> batchSizes = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();

            source.readBatches(partition, 2, batch -> {
                batchSizes.add(batch.size());
                rows.addAll(batch);
                return true;
            });

            assertThat(batchSizes).containsExactly(2, 2, 1);
            assertThat(rows).extracting(row -> row.get("ID"))
                    .containsExactly(1, 2, 3, 4, 5);
        }
    }
}
