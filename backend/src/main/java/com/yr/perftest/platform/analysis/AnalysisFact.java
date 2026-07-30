package com.yr.perftest.platform.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AnalysisFact(
        String algorithmId,
        String algorithmVersion,
        String kind,
        String summary,
        Map<String, Object> data,
        List<String> evidenceRefs
) {
    public AnalysisFact {
        // 允许 null 值（如 kneePointMs、deltaPct），故不用 Map.copyOf
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
