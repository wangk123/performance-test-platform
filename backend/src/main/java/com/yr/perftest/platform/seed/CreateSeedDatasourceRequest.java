package com.yr.perftest.platform.seed;

public record CreateSeedDatasourceRequest(
        String name,
        String host,
        Integer port,
        String databaseName,
        String username,
        String password
) {
}
