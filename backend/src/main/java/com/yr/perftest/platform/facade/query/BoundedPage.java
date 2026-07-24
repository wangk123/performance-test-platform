package com.yr.perftest.platform.facade.query;

import java.util.List;

public record BoundedPage<T>(
        List<T> items,
        boolean truncated,
        String nextCursor,
        List<String> warnings,
        Availability availability
) {
}
