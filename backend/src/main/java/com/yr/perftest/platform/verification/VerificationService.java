package com.yr.perftest.platform.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.analysis.AnalysisFact;
import com.yr.perftest.platform.analysis.ExecutionComparison;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.facade.DataFacade;
import com.yr.perftest.platform.facade.data.ExecutionSummary;
import com.yr.perftest.platform.facade.query.BoundedPage;
import com.yr.perftest.platform.facade.query.PageBudget;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.MachinePrincipal;
import com.yr.perftest.platform.identity.Principal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 优化验证流程（T9）：变更登记 → 基线 vs 候选可比性检查（复用 T7 execution-compare）→ 护栏判定
 * → 三态结论（IMPROVED / REGRESSED / INCONCLUSIVE）。与补充取证（{@link EvidenceCaptureService}）
 * 语义分离：验证无审批状态机，输出确定性结论。
 */
@Service
public class VerificationService {
    public static final String SCHEMA_VERSION = "1";

    private final PersistentChangeRepository changeRepository;
    private final PersistentVerificationRepository verificationRepository;
    private final DataFacade dataFacade;
    private final ObjectMapper objectMapper;
    private final VerificationProperties properties;

    public VerificationService(
            PersistentChangeRepository changeRepository,
            PersistentVerificationRepository verificationRepository,
            DataFacade dataFacade,
            ObjectMapper objectMapper,
            VerificationProperties properties
    ) {
        this.changeRepository = changeRepository;
        this.verificationRepository = verificationRepository;
        this.dataFacade = dataFacade;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public ChangeRecordView registerChange(ChangeType changeType, String changeRef, String description, Principal requester) {
        if (changeType == null) {
            throw new IllegalArgumentException("change type is required");
        }
        if (changeRef == null || changeRef.isBlank()) {
            throw new IllegalArgumentException("change reference is required");
        }
        PersistentChangeRecord record = changeRepository.save(new PersistentChangeRecord(
                changeType,
                changeRef.trim(),
                blankToNull(description),
                principalType(requester),
                principalName(requester)
        ));
        return new ChangeRecordView(
                SCHEMA_VERSION,
                record.getId(),
                record.getChangeType().name(),
                record.getChangeRef(),
                record.getDescription(),
                record.getRegisteredByName(),
                record.getCreatedAt()
        );
    }

    @Transactional
    public VerificationView verify(
            long baselineExecutionId,
            long candidateExecutionId,
            long changeRecordId,
            Principal requester
    ) {
        PersistentChangeRecord change = changeRepository.findById(changeRecordId)
                .orElseThrow(() -> new ExecutionValidationException("change record " + changeRecordId + " does not exist"));
        ExecutionComparison.ExecutionSide baseline = side(baselineExecutionId);
        ExecutionComparison.ExecutionSide candidate = side(candidateExecutionId);
        AnalysisFact fact = new ExecutionComparison().compare(
                baseline,
                candidate,
                List.of("aggregate#" + baselineExecutionId, "aggregate#" + candidateExecutionId)
        );
        String verdict = verdictOf(fact);
        List<String> reasons = reasonsOf(fact, verdict);
        PersistentVerificationRecord record = verificationRepository.save(new PersistentVerificationRecord(
                baselineExecutionId,
                candidateExecutionId,
                change.getId(),
                verdict,
                toJson(reasons),
                toJson(fact.data()),
                principalType(requester),
                principalName(requester)
        ));
        return view(record, fact);
    }

    @Transactional(readOnly = true)
    public VerificationView get(long verificationId, Principal requester) {
        PersistentVerificationRecord record = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ExecutionValidationException("verification " + verificationId + " does not exist"));
        return view(record, null);
    }

    @SuppressWarnings("unchecked")
    private String verdictOf(AnalysisFact fact) {
        Map<String, Object> data = fact.data();
        if (!Boolean.TRUE.equals(data.get("comparable"))) {
            return VerificationVerdict.INCONCLUSIVE.name();
        }
        List<Map<String, Object>> labels = (List<Map<String, Object>>) data.get("labels");
        double maxRegress = 0;
        double maxImprove = 0;
        boolean guardrailViolated = false;
        for (Map<String, Object> label : labels) {
            Double errorRateDelta = (Double) label.get("errorRateDelta");
            if (errorRateDelta != null && errorRateDelta > properties.getErrorRateIncreasePct()) {
                guardrailViolated = true;
            }
            Double p95DeltaPct = (Double) label.get("p95DeltaPct");
            if (p95DeltaPct == null) {
                guardrailViolated = true;
                continue;
            }
            if (p95DeltaPct > properties.getP95RegressionPct()) {
                maxRegress = Math.max(maxRegress, p95DeltaPct);
            }
            if (p95DeltaPct < -properties.getSignificantPct()) {
                maxImprove = Math.max(maxImprove, -p95DeltaPct);
            }
        }
        if (guardrailViolated) {
            return VerificationVerdict.REGRESSED.name();
        }
        if (maxRegress > 0) {
            // 波动过大：改善与退化并存且改善幅度更大时无法判定
            return maxImprove > maxRegress
                    ? VerificationVerdict.INCONCLUSIVE.name()
                    : VerificationVerdict.REGRESSED.name();
        }
        if (maxImprove > 0) {
            return VerificationVerdict.IMPROVED.name();
        }
        return VerificationVerdict.INCONCLUSIVE.name();
    }

    @SuppressWarnings("unchecked")
    private List<String> reasonsOf(AnalysisFact fact, String verdict) {
        Map<String, Object> data = fact.data();
        List<String> reasons = new ArrayList<>((List<String>) data.getOrDefault("reasons", List.of()));
        if (Boolean.TRUE.equals(data.get("comparable"))) {
            if (VerificationVerdict.REGRESSED.name().equals(verdict)) {
                reasons.add("guardrail or p95 regression exceeds thresholds");
            } else if (VerificationVerdict.IMPROVED.name().equals(verdict)) {
                reasons.add("uniform p95 improvement beyond " + properties.getSignificantPct() + "%");
            } else {
                reasons.add("no significant change or fluctuations too large to judge");
            }
        }
        return List.copyOf(reasons);
    }

    private ExecutionComparison.ExecutionSide side(long executionId) {
        ExecutionSummary summary = dataFacade.getExecutionSummary(executionId);
        BoundedPage<TaskExecutionResult.AggregateRow> page =
                dataFacade.queryAggregateRows(executionId, null, PageBudget.defaults());
        return new ExecutionComparison.ExecutionSide(
                executionId,
                summary.scenarioId(),
                summary.durationMs(),
                page.items()
        );
    }

    @SuppressWarnings("unchecked")
    private VerificationView view(PersistentVerificationRecord record, AnalysisFact fact) {
        return new VerificationView(
                SCHEMA_VERSION,
                record.getId(),
                record.getBaselineExecutionId(),
                record.getCandidateExecutionId(),
                record.getChangeRecordId(),
                record.getVerdict(),
                fact == null ? null : fact.algorithmId(),
                fact == null ? null : fact.algorithmVersion(),
                parseJsonList(record.getReasonsJson()),
                parseJsonMapList(record.getDetailsJson()),
                record.getRequestedByName(),
                record.getCreatedAt()
        );
    }

    private List<String> parseJsonList(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<Map<String, Object>> parseJsonMapList(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot serialize verification record", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    public record ChangeRecordView(
            String schemaVersion,
            long changeRecordId,
            String changeType,
            String changeRef,
            String description,
            String registeredByName,
            Instant createdAt
    ) {
    }

    public record VerificationView(
            String schemaVersion,
            long verificationId,
            long baselineExecutionId,
            long candidateExecutionId,
            long changeRecordId,
            String verdict,
            String algorithmId,
            String algorithmVersion,
            List<String> reasons,
            List<Map<String, Object>> labels,
            String requestedByName,
            Instant createdAt
    ) {
    }
}
