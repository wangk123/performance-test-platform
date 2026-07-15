package com.yr.perftest.platform.seed;

import java.util.List;

public record StartCaptureRequest(
        Long datasourceId,
        String provider,
        List<String> includes,
        List<String> excludes
) {
}
