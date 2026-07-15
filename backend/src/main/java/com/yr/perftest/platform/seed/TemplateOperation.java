package com.yr.perftest.platform.seed;

import java.util.List;

public record TemplateOperation(
        String type,
        String table,
        boolean riskyNoPk,
        List<TemplateColumn> columns
) {
}
