package com.yr.perftest.platform.seed;

import java.time.Instant;

public record SeedDatasourceView(
        Long id,
        Long projectId,
        String name,
        String host,
        Integer port,
        String databaseName,
        String username,
        boolean passwordConfigured,
        Instant createdAt,
        Instant updatedAt
) {
}
