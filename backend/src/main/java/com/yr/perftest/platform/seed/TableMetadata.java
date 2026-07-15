package com.yr.perftest.platform.seed;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record TableMetadata(
        String table,
        List<String> primaryKeyColumns,
        Set<String> uniqueColumns,
        Map<String, FkRef> foreignKeys
) {
}
