package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FailureSampleEvidenceSource implements EvidenceSource {
    private final DataFacade dataFacade;

    public FailureSampleEvidenceSource(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    @Override
    public boolean supports(CorrelationKey key) {
        return EvidenceSourceSupport.supports(key) && key.targetInstances().isEmpty();
    }

    @Override
    public EvidenceSummary summarize(CorrelationKey key, PageBudget budget) {
        try {
            BoundedPage<TaskExecutionResult.Sample> page =
                    dataFacade.queryFailureSamples(key.executionId(), null, budget);
            List<TaskExecutionResult.Sample> samples = page.items().stream()
                    .filter(sample -> key.requestLabel() == null
                            || key.requestLabel().isBlank()
                            || key.requestLabel().equals(sample.label()))
                    .toList();
            return new EvidenceSummary(
                    key,
                    "failure-sample",
                    EvidenceSourceSupport.filteredAvailability(page.availability(), !samples.isEmpty()),
                    Map.of(
                            "sampleCount", samples.size(),
                            "failedCount", samples.stream().filter(sample -> !sample.success()).count()
                    ),
                    page.availability().sourceRef(),
                    "load"
            );
        } catch (RuntimeException exception) {
            if (EvidenceSourceSupport.isDeletedExecution(exception)) {
                return EvidenceSourceSupport.deleted(key, "failure-sample", "load");
            }
            throw exception;
        }
    }
}
