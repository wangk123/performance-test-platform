package com.yr.perftest.platform.governance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionAuditService {
    private final PersistentExecutionAuditRepository repository;

    public ExecutionAuditService(PersistentExecutionAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
            long executionId,
            String action,
            boolean replayed,
            String principalType,
            String principalName
    ) {
        repository.save(new PersistentExecutionAuditRecord(
                executionId,
                action,
                replayed,
                principalType,
                principalName
        ));
    }
}
