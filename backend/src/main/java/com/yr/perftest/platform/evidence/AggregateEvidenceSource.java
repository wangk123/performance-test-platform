package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AggregateEvidenceSource implements EvidenceSource {
    private final DataFacade dataFacade;

    public AggregateEvidenceSource(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    @Override
    public boolean supports(CorrelationKey key) {
        return EvidenceSourceSupport.supports(key) && key.targetInstances().isEmpty();
    }

    @Override
    public EvidenceSummary summarize(CorrelationKey key, PageBudget budget) {
        try {
            BoundedPage<TaskExecutionResult.AggregateRow> page =
                    dataFacade.queryAggregateRows(key.executionId(), null, budget);
            List<TaskExecutionResult.AggregateRow> rows = page.items().stream()
                    .filter(row -> key.requestLabel() == null
                            || key.requestLabel().isBlank()
                            || key.requestLabel().equals(row.label()))
                    .toList();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("rowCount", rows.size());
            summary.put("samples", rows.stream().mapToLong(TaskExecutionResult.AggregateRow::samples).sum());
            summary.put("errors", rows.stream()
                    .mapToDouble(row -> row.samples() * row.errorRate())
                    .sum());
            return new EvidenceSummary(
                    key,
                    "aggregate",
                    EvidenceSourceSupport.filteredAvailability(page.availability(), !rows.isEmpty()),
                    summary,
                    page.availability().sourceRef(),
                    "load"
            );
        } catch (RuntimeException exception) {
            if (EvidenceSourceSupport.isDeletedExecution(exception)) {
                return EvidenceSourceSupport.deleted(key, "aggregate", "load");
            }
            throw exception;
        }
    }
}
