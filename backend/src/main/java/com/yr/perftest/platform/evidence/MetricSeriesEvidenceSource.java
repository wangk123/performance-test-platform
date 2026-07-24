package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MetricSeriesEvidenceSource implements EvidenceSource {
    private final DataFacade dataFacade;

    public MetricSeriesEvidenceSource(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    @Override
    public boolean supports(CorrelationKey key) {
        return EvidenceSourceSupport.supports(key) && key.targetInstances().isEmpty();
    }

    @Override
    public EvidenceSummary summarize(CorrelationKey key, PageBudget budget) {
        try {
            BoundedPage<MetricTick> page = dataFacade.queryMetricSeries(
                    key.executionId(),
                    key.from(),
                    key.to(),
                    "1s",
                    null,
                    budget
            );
            List<MetricTick> ticks = page.items().stream()
                    .filter(tick -> matchesLabel(tick, key.requestLabel()))
                    .toList();
            long samples = ticks.stream().mapToLong(tick -> samples(tick, key.requestLabel())).sum();
            long errors = ticks.stream().mapToLong(tick -> errors(tick, key.requestLabel())).sum();
            return new EvidenceSummary(
                    key,
                    "series",
                    EvidenceSourceSupport.filteredAvailability(page.availability(), !ticks.isEmpty()),
                    Map.of("pointCount", ticks.size(), "samples", samples, "errors", errors),
                    page.availability().sourceRef(),
                    "load"
            );
        } catch (RuntimeException exception) {
            if (EvidenceSourceSupport.isDeletedExecution(exception)) {
                return EvidenceSourceSupport.deleted(key, "series", "load");
            }
            throw exception;
        }
    }

    private boolean matchesLabel(MetricTick tick, String requestLabel) {
        if (requestLabel == null || requestLabel.isBlank()) {
            return true;
        }
        return tick.labels() != null
                && tick.labels().stream().anyMatch(metric -> requestLabel.equals(metric.label()));
    }

    private long samples(MetricTick tick, String requestLabel) {
        if (requestLabel == null || requestLabel.isBlank()) {
            return tick.overall() == null ? 0 : tick.overall().samples();
        }
        return matchingLabels(tick, requestLabel).stream().mapToLong(MetricTick.LabelMetric::samples).sum();
    }

    private long errors(MetricTick tick, String requestLabel) {
        if (requestLabel == null || requestLabel.isBlank()) {
            return tick.overall() == null ? 0 : tick.overall().errorSamples();
        }
        return matchingLabels(tick, requestLabel).stream()
                .mapToLong(MetricTick.LabelMetric::errorSamples)
                .sum();
    }

    private List<MetricTick.LabelMetric> matchingLabels(MetricTick tick, String requestLabel) {
        if (tick.labels() == null) {
            return List.of();
        }
        return tick.labels().stream().filter(metric -> requestLabel.equals(metric.label())).toList();
    }
}
