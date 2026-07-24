package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.facade.query.PageBudget;

public interface EvidenceSource {
    boolean supports(CorrelationKey key);

    EvidenceSummary summarize(CorrelationKey key, PageBudget budget);
}
