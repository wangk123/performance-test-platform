package com.yr.perftest.platform.governance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestAuditService {
    private final PersistentRequestAuditRepository repository;

    public RequestAuditService(PersistentRequestAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
            String requestId,
            String principalType,
            String principalName,
            String method,
            String path,
            String query,
            int statusCode,
            long durationMs
    ) {
        repository.save(new PersistentRequestAuditRecord(
                requestId,
                principalType,
                principalName,
                method,
                path,
                query,
                statusCode,
                durationMs
        ));
    }
}
