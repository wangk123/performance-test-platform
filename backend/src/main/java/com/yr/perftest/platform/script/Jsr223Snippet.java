package com.yr.perftest.platform.script;

import java.util.List;

public record Jsr223Snippet(
        String key,
        String name,
        String category,
        String description,
        List<String> params,
        String code
) {
}
