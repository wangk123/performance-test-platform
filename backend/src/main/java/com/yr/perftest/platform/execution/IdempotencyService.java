package com.yr.perftest.platform.execution;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final PersistentIdempotencyRepository repository;

    public IdempotencyService(PersistentIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public IdempotentExecution execute(String idemKey, String requestHash, Supplier<Long> action) {
        if (idemKey == null || idemKey.isBlank()) {
            return new IdempotentExecution(action.get(), false);
        }
        if (idemKey.length() > 128) {
            throw new ExecutionValidationException("idempotency key exceeds 128 characters");
        }
        return repository.findByIdemKey(idemKey)
                .map(existing -> replay(existing, requestHash))
                .orElseGet(() -> create(idemKey, requestHash, action));
    }

    private IdempotentExecution replay(PersistentIdempotencyRecord existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("idempotency key was used with a different request");
        }
        return new IdempotentExecution(existing.getExecutionId(), true);
    }

    private IdempotentExecution create(String idemKey, String requestHash, Supplier<Long> action) {
        long executionId = action.get();
        try {
            repository.saveAndFlush(new PersistentIdempotencyRecord(idemKey, requestHash, executionId));
        } catch (DataIntegrityViolationException exception) {
            throw new IdempotencyConflictException("concurrent request with the same idempotency key");
        }
        return new IdempotentExecution(executionId, false);
    }

    public record IdempotentExecution(long executionId, boolean replayed) {
    }
}
