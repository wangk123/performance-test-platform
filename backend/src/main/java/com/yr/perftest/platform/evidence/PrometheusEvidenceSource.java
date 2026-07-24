package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.data.PrometheusMetricPoint;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PrometheusEvidenceSource implements EvidenceSource {
    private static final String DEFAULT_METRIC_SELECTOR = "SERVER_CPU";
    private static final int DEFAULT_STEP_SECONDS = 15;

    private final DataFacade dataFacade;
    private final String metricSelector;
    private final int stepSeconds;

    @Autowired
    public PrometheusEvidenceSource(DataFacade dataFacade) {
        this(dataFacade, DEFAULT_METRIC_SELECTOR, DEFAULT_STEP_SECONDS);
    }

    public PrometheusEvidenceSource(DataFacade dataFacade, String metricSelector, int stepSeconds) {
        this.dataFacade = dataFacade;
        this.metricSelector = metricSelector;
        this.stepSeconds = stepSeconds;
    }

    @Override
    public boolean supports(CorrelationKey key) {
        return EvidenceSourceSupport.supports(key);
    }

    @Override
    public EvidenceSummary summarize(CorrelationKey key, PageBudget budget) {
        try {
            BoundedPage<PrometheusMetricPoint> page = dataFacade.queryPrometheus(
                    key.executionId(),
                    metricSelector,
                    key.from(),
                    key.to(),
                    stepSeconds,
                    null,
                    budget
            );
            List<PrometheusMetricPoint> points = page.items().stream()
                    .filter(point -> matchesTarget(point, key.targetInstances()))
                    .toList();
            long seriesCount = points.stream().map(PrometheusMetricPoint::displayName).distinct().count();
            return new EvidenceSummary(
                    key,
                    "prometheus",
                    EvidenceSourceSupport.filteredAvailability(page.availability(), !points.isEmpty()),
                    Map.of("pointCount", points.size(), "seriesCount", seriesCount),
                    page.availability().sourceRef(),
                    "prometheus"
            );
        } catch (RuntimeException exception) {
            if (EvidenceSourceSupport.isDeletedExecution(exception)) {
                return EvidenceSourceSupport.deleted(key, "prometheus", "prometheus");
            }
            throw exception;
        }
    }

    private boolean matchesTarget(PrometheusMetricPoint point, List<String> targetInstances) {
        if (targetInstances.isEmpty()) {
            return true;
        }
        Map<String, String> labels = point.labels() == null ? Map.of() : point.labels();
        return targetInstances.stream().anyMatch(labels::containsValue);
    }
}
