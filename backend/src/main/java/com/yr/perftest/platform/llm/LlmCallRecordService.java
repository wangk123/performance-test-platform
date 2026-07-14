package com.yr.perftest.platform.llm;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LlmCallRecordService {
    private final PersistentModelCallRecordRepository repository;

    public LlmCallRecordService(PersistentModelCallRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LlmCallRecord save(PersistentModelCallRecord record) {
        return repository.save(record).toCallRecord();
    }

    @Transactional(readOnly = true)
    public Page<LlmCallRecord> page(
            Long providerId,
            Long modelId,
            LlmCallScene scene,
            LlmCallStatus status,
            int page,
            int size
    ) {
        Specification<PersistentModelCallRecord> spec = (root, query, cb) -> cb.conjunction();
        if (providerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("providerId"), providerId));
        }
        if (modelId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("modelId"), modelId));
        }
        if (scene != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("scene"), scene));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "id"));
        return repository.findAll(spec, pageable).map(PersistentModelCallRecord::toCallRecord);
    }
}
