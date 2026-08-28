package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.governance.PersistentExecutionAuditRecord;
import com.yr.perftest.platform.governance.PersistentExecutionAuditRepository;
import com.yr.perftest.platform.governance.PersistentRequestAuditRecord;
import com.yr.perftest.platform.governance.PersistentRequestAuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计轨迹查询入口（T13 审计校验用）：请求审计 + 执行审计，可重建平台侧操作轨迹。
 */
@Service
public class AuditFacade {
    public static final String SCHEMA_VERSION = "1";

    private final FacadeGuard guard;
    private final PersistentRequestAuditRepository requestAuditRepository;
    private final PersistentExecutionAuditRepository executionAuditRepository;

    public AuditFacade(
            FacadeGuard guard,
            PersistentRequestAuditRepository requestAuditRepository,
            PersistentExecutionAuditRepository executionAuditRepository
    ) {
        this.guard = guard;
        this.requestAuditRepository = requestAuditRepository;
        this.executionAuditRepository = executionAuditRepository;
    }

    public List<PersistentRequestAuditRecord> recentRequests(int limit) {
        return guard.requirePrincipal(() -> requestAuditRepository.findAll(
                PageRequest.of(0, Math.max(1, Math.min(limit, 200)), Sort.by(Sort.Direction.DESC, "id"))
        ).getContent());
    }

    public List<PersistentExecutionAuditRecord> executions(long executionId) {
        return guard.requirePrincipal(() -> executionAuditRepository.findByExecutionIdOrderByIdDesc(executionId));
    }
}
