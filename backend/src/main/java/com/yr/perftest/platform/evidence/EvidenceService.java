package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.facade.query.PageBudget;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EvidenceService {
    private final List<EvidenceSource> sources;

    public EvidenceService(List<EvidenceSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public List<EvidenceSummary> collect(CorrelationKey key, PageBudget budget) {
        Objects.requireNonNull(key, "correlation key is required");
        Objects.requireNonNull(budget, "page budget is required").validate();

        List<EvidenceSummary> summaries = new ArrayList<>();
        for (EvidenceSource source : sources) {
            if (!source.supports(key)) {
                continue;
            }
            EvidenceSummary summary = Objects.requireNonNull(
                    source.summarize(key, budget),
                    "evidence source returned no summary"
            );
            if (!key.equals(summary.key())) {
                throw new IllegalStateException("evidence source returned a different correlation key");
            }
            summaries.add(withClockWarning(summary));
        }
        return List.copyOf(summaries);
    }

    private EvidenceSummary withClockWarning(EvidenceSummary summary) {
        if (!"prometheus".equals(summary.sourceClock())
                || summary.availability() == null
                || !summary.availability().present()
                || summary.availability().from() == null
                || summary.availability().to() == null) {
            return summary;
        }
        long stepSeconds = parseStepSeconds(summary.availability().granularity());
        if (stepSeconds <= 0) {
            return summary;
        }
        Duration threshold = Duration.ofSeconds(stepSeconds * 2);
        boolean skewed = Duration.between(summary.key().from(), summary.availability().from())
                .abs()
                .compareTo(threshold) > 0
                || Duration.between(summary.key().to(), summary.availability().to())
                .abs()
                .compareTo(threshold) > 0;
        if (!skewed) {
            return summary;
        }

        Map<String, Object> values = new LinkedHashMap<>(summary.summary());
        List<String> warnings = new ArrayList<>();
        Object existingWarnings = values.get("warnings");
        if (existingWarnings instanceof Iterable<?> iterable) {
            iterable.forEach(value -> warnings.add(String.valueOf(value)));
        }
        if (!warnings.contains("clock:skew-suspected")) {
            warnings.add("clock:skew-suspected");
        }
        values.put("warnings", List.copyOf(warnings));
        return new EvidenceSummary(
                summary.key(),
                summary.sourceType(),
                summary.availability(),
                values,
                summary.sourceRef(),
                summary.sourceClock()
        );
    }

    private long parseStepSeconds(String granularity) {
        if (granularity == null || !granularity.endsWith("s")) {
            return -1;
        }
        try {
            return Long.parseLong(granularity.substring(0, granularity.length() - 1));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
