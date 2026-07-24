package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.data.ExecutionSummary;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ExecutionEvidenceSource implements EvidenceSource {
    private final DataFacade dataFacade;

    public ExecutionEvidenceSource(DataFacade dataFacade) {
        this.dataFacade = dataFacade;
    }

    @Override
    public boolean supports(CorrelationKey key) {
        return EvidenceSourceSupport.supports(key);
    }

    @Override
    public EvidenceSummary summarize(CorrelationKey key, PageBudget budget) {
        try {
            ExecutionSummary execution = dataFacade.getExecutionSummary(key.executionId());
            String sourceRef = "execution:" + key.executionId();
            Availability availability = new Availability(
                    true,
                    execution.startedAt(),
                    execution.endedAt(),
                    null,
                    false,
                    sourceRef,
                    null
            );
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("status", execution.status().name());
            summary.put("samples", execution.samples());
            summary.put("throughput", execution.throughput());
            if (execution.durationMs() != null) {
                summary.put("durationMs", execution.durationMs());
            }
            return new EvidenceSummary(key, "execution", availability, summary, sourceRef, "load");
        } catch (RuntimeException exception) {
            if (EvidenceSourceSupport.isDeletedExecution(exception)) {
                return EvidenceSourceSupport.deleted(key, "execution", "load");
            }
            throw exception;
        }
    }
}
