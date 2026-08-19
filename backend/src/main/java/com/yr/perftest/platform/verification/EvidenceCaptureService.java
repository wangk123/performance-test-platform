package com.yr.perftest.platform.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionConflictException;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.data.ExecutionSummary;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.MachinePrincipal;
import com.yr.perftest.platform.identity.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 补充取证流程（T9）：预检（目的/影响/成本声明）→ 人工审批 → 执行有界证据快照 → 证据定位回流。
 * 与优化验证（{@link VerificationService}）语义分离：取证有审批与状态机，无结论判定。
 */
@Service
public class EvidenceCaptureService {
    public static final String SCHEMA_VERSION = "1";
    public static final String KIND_EVIDENCE_SNAPSHOT = "EVIDENCE_SNAPSHOT";
    private static final int PROMETHEUS_STEP_SECONDS = 15;

    private final PersistentEvidenceCaptureRepository captureRepository;
    private final DataFacade dataFacade;
    private final ObjectMapper objectMapper;
    private final String storageRoot;

    public EvidenceCaptureService(
            PersistentEvidenceCaptureRepository captureRepository,
            DataFacade dataFacade,
            ObjectMapper objectMapper,
            @Value("${platform.storage.root:./storage}") String storageRoot
    ) {
        this.captureRepository = captureRepository;
        this.dataFacade = dataFacade;
        this.objectMapper = objectMapper;
        this.storageRoot = storageRoot;
    }

    @Transactional
    public CaptureView create(
            long executionId,
            String purpose,
            CaptureImpact impactLevel,
            String costNote,
            Principal requester
    ) {
        dataFacade.getExecutionSummary(executionId);
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("capture purpose is required");
        }
        CaptureImpact impact = impactLevel == null ? CaptureImpact.LOW : impactLevel;
        if (impact != CaptureImpact.NONE && (costNote == null || costNote.isBlank())) {
            throw new IllegalArgumentException("cost note is required for impact level " + impact);
        }
        PersistentEvidenceCaptureRecord record = captureRepository.save(new PersistentEvidenceCaptureRecord(
                executionId,
                KIND_EVIDENCE_SNAPSHOT,
                purpose.trim(),
                impact,
                costNote == null ? null : costNote.trim(),
                principalType(requester),
                principalName(requester)
        ));
        return view(record);
    }

    @Transactional
    public CaptureView approve(long captureId, Principal approver) {
        requireHumanApprover(approver);
        PersistentEvidenceCaptureRecord record = requireCapture(captureId);
        if (record.getStatus() != CaptureStatus.PENDING_APPROVAL) {
            throw new ExecutionConflictException("evidence capture is not pending approval");
        }
        record.markApproved(principalType(approver), principalName(approver));
        return view(captureRepository.save(record));
    }

    @Transactional
    public CaptureView reject(long captureId, Principal approver) {
        requireHumanApprover(approver);
        PersistentEvidenceCaptureRecord record = requireCapture(captureId);
        if (record.getStatus() != CaptureStatus.PENDING_APPROVAL) {
            throw new ExecutionConflictException("evidence capture is not pending approval");
        }
        record.markRejected(principalType(approver), principalName(approver));
        return view(captureRepository.save(record));
    }

    /**
     * 执行取证快照。刻意不加 @Transactional：源采集需要吞掉单源异常继续收集，
     * 若与落库共用事务，内部只读事务抛异常会把共享事务标记为 rollback-only。
     */
    public CaptureView execute(long captureId, Principal executor) {
        PersistentEvidenceCaptureRecord record = requireCapture(captureId);
        if (record.getStatus() != CaptureStatus.APPROVED) {
            throw new ExecutionConflictException("evidence capture must be approved before execution");
        }
        ExecutionSummary summary = dataFacade.getExecutionSummary(record.getExecutionId());
        Instant from = summary.startedAt() != null ? summary.startedAt() : summary.createdAt();
        Instant to = summary.endedAt() != null ? summary.endedAt() : Instant.now();
        List<CaptureSource> sources = collectSources(record.getExecutionId(), from, to);
        String bundlePath = writeBundle(record, sources);
        record.markCompleted(bundlePath, toJson(sources));
        return view(captureRepository.save(record));
    }

    @Transactional(readOnly = true)
    public CaptureView get(long captureId, Principal requester) {
        return view(requireCapture(captureId));
    }

    private List<CaptureSource> collectSources(long executionId, Instant from, Instant to) {
        PageBudget budget = PageBudget.defaults();
        List<CaptureSource> sources = new ArrayList<>();
        sources.add(collect("aggregate", () -> dataFacade.queryAggregateRows(executionId, null, budget)));
        sources.add(collect("series", () -> dataFacade.queryMetricSeries(executionId, from, to, "15s", null, budget)));
        sources.add(collect("failure-sample", () -> dataFacade.queryFailureSamples(executionId, null, budget)));
        sources.add(collect("prometheus", () ->
                dataFacade.queryPrometheus(executionId, "SERVER_CPU", from, to, PROMETHEUS_STEP_SECONDS, null, budget)));
        return sources;
    }

    private CaptureSource collect(String sourceType, Supplier<BoundedPage<?>> query) {
        try {
            BoundedPage<?> page = query.get();
            Availability availability = page.availability();
            return new CaptureSource(
                    sourceType,
                    availability != null && availability.present(),
                    availability != null && availability.truncated(),
                    page.items().size(),
                    availability == null ? null : availability.sourceRef(),
                    availability == null || availability.missingReason() == null
                            ? null
                            : availability.missingReason().name()
            );
        } catch (RuntimeException exception) {
            return new CaptureSource(sourceType, false, false, 0, null,
                    Availability.MissingReason.SOURCE_UNAVAILABLE.name());
        }
    }

    private String writeBundle(PersistentEvidenceCaptureRecord record, List<CaptureSource> sources) {
        Path directory = Path.of(storageRoot, "evidence-bundles", Long.toString(record.getExecutionId()));
        try {
            Files.createDirectories(directory);
            Map<String, Object> bundle = new LinkedHashMap<>();
            bundle.put("schemaVersion", SCHEMA_VERSION);
            bundle.put("captureId", record.getId());
            bundle.put("executionId", record.getExecutionId());
            bundle.put("kind", record.getKind());
            bundle.put("collectedAt", Instant.now().toString());
            bundle.put("sources", sources);
            Path file = directory.resolve("capture-" + record.getId() + ".json");
            objectMapper.writeValue(Files.newBufferedWriter(file), bundle);
            return Path.of("evidence-bundles", Long.toString(record.getExecutionId()), file.getFileName().toString())
                    .toString();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot write evidence bundle", exception);
        }
    }

    private String toJson(List<CaptureSource> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot serialize capture sources", exception);
        }
    }

    private CaptureView view(PersistentEvidenceCaptureRecord record) {
        return new CaptureView(
                SCHEMA_VERSION,
                record.getId(),
                record.getExecutionId(),
                record.getKind(),
                record.getStatus().name(),
                record.getPurpose(),
                record.getImpactLevel().name(),
                record.getCostNote(),
                record.getBundlePath(),
                parseSources(record.getSummaryJson()),
                record.getRequestedByName(),
                record.getApprovedByName(),
                record.getCreatedAt(),
                record.getApprovedAt(),
                record.getCompletedAt()
        );
    }

    private List<CaptureSource> parseSources(String summaryJson) {
        if (summaryJson == null || summaryJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    summaryJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CaptureSource.class)
            );
        } catch (IOException exception) {
            return List.of();
        }
    }

    private PersistentEvidenceCaptureRecord requireCapture(long captureId) {
        return captureRepository.findById(captureId)
                .orElseThrow(() -> new ExecutionValidationException(
                        "evidence capture " + captureId + " does not exist"));
    }

    private void requireHumanApprover(Principal approver) {
        if (!(approver instanceof HumanPrincipal)) {
            throw new AccessDeniedException("evidence capture approval requires a human principal");
        }
    }

    private String principalType(Principal principal) {
        if (principal instanceof HumanPrincipal) {
            return "HUMAN";
        }
        if (principal instanceof MachinePrincipal) {
            return "MACHINE";
        }
        return "OTHER";
    }

    private String principalName(Principal principal) {
        if (principal instanceof HumanPrincipal human) {
            return human.username();
        }
        if (principal instanceof MachinePrincipal machine) {
            return Long.toString(machine.apiKeyId());
        }
        return "other";
    }

    public record CaptureView(
            String schemaVersion,
            long captureId,
            long executionId,
            String kind,
            String status,
            String purpose,
            String impactLevel,
            String costNote,
            String bundleRef,
            List<CaptureSource> sources,
            String requestedByName,
            String approvedByName,
            Instant createdAt,
            Instant approvedAt,
            Instant completedAt
    ) {
    }
}
