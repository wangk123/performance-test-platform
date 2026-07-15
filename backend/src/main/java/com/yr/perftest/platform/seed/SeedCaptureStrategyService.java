package com.yr.perftest.platform.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SeedCaptureStrategyService {
    private final PersistentSeedDatasourceRepository datasourceRepository;
    private final PersistentSeedCaptureStrategyRepository strategyRepository;
    private final PersistentSeedCaptureSampleRepository sampleRepository;
    private final DatasourceCaptureLeaseService leaseService;
    private final SampleCaptureExecutor captureExecutor;

    @Autowired
    public SeedCaptureStrategyService(
            PersistentSeedDatasourceRepository datasourceRepository,
            PersistentSeedCaptureStrategyRepository strategyRepository,
            PersistentSeedCaptureSampleRepository sampleRepository,
            DatasourceCaptureLeaseService leaseService,
            SampleCaptureExecutor captureExecutor
    ) {
        this.datasourceRepository = datasourceRepository;
        this.strategyRepository = strategyRepository;
        this.sampleRepository = sampleRepository;
        this.leaseService = leaseService;
        this.captureExecutor = captureExecutor;
    }

    public SeedCaptureStrategyService(
            PersistentSeedDatasourceRepository datasourceRepository,
            PersistentSeedCaptureStrategyRepository strategyRepository,
            PersistentSeedCaptureSampleRepository sampleRepository
    ) {
        this(datasourceRepository, strategyRepository, sampleRepository, null, null);
    }

    @Transactional(readOnly = true)
    public List<SeedCaptureStrategyView> list(long projectId) {
        return strategyRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public SeedCaptureStrategyView create(long projectId, CreateSeedCaptureStrategyRequest request) {
        StrategyConfiguration configuration = validate(projectId, request);
        PersistentSeedCaptureStrategyRecord strategy = new PersistentSeedCaptureStrategyRecord(
                projectId,
                configuration.name(),
                configuration.datasourceId(),
                SeedJson.write(configuration.includes()),
                SeedJson.write(configuration.excludes()),
                configuration.threadCount(),
                configuration.batchRows()
        );
        return toView(strategyRepository.save(strategy));
    }

    @Transactional(readOnly = true)
    public SeedCaptureStrategyView get(long projectId, long strategyId) {
        return toView(requireStrategy(projectId, strategyId));
    }

    @Transactional
    public SeedCaptureStrategyView update(
            long projectId,
            long strategyId,
            CreateSeedCaptureStrategyRequest request
    ) {
        PersistentSeedCaptureStrategyRecord strategy = requireStrategy(projectId, strategyId);
        StrategyConfiguration configuration = validate(projectId, request);
        strategy.updateConfiguration(
                configuration.name(),
                configuration.datasourceId(),
                SeedJson.write(configuration.includes()),
                SeedJson.write(configuration.excludes()),
                configuration.threadCount(),
                configuration.batchRows()
        );
        return toView(strategyRepository.save(strategy));
    }

    @Transactional
    public void delete(long projectId, long strategyId) {
        strategyRepository.delete(requireStrategy(projectId, strategyId));
    }

    @Transactional
    public Map<String, Object> execute(long projectId, long strategyId) {
        PersistentSeedCaptureStrategyRecord strategy = strategyRepository
                .findByIdAndProjectIdForUpdate(strategyId, projectId)
                .orElseThrow(() -> new SeedValidationException("capture strategy not found: " + strategyId));
        requireDatasource(projectId, strategy.getDatasourceId());

        int sampleSeq = sampleRepository.findNextSampleSeq(strategy.getId());
        Instant captureStartedAt = Instant.now();
        PersistentSeedCaptureSampleRecord sample = new PersistentSeedCaptureSampleRecord(
                projectId,
                strategy.getId(),
                strategy.getDatasourceId(),
                sampleSeq,
                "QUEUED",
                captureStartedAt,
                null,
                snapshot(strategy),
                strategy.getConfigVersion()
        );
        sample = sampleRepository.saveAndFlush(sample);
        if (leaseService != null) {
            try {
                leaseService.acquire(strategy.getDatasourceId(), sample.getId());
            } catch (DatasourceCaptureLeaseService.ActiveCaptureException ex) {
                sampleRepository.delete(sample);
                sampleRepository.flush();
                throw ex;
            }
            submitAfterCommit(sample.getId());
        }
        return toSampleView(sample);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSample(long projectId, long sampleId) {
        return toSampleView(sampleRepository.findByIdAndProjectId(sampleId, projectId)
                .orElseThrow(() -> new SeedValidationException("capture sample not found: " + sampleId)));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pageSamples(
            long projectId,
            long strategyId,
            String status,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        requireStrategy(projectId, strategyId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new SeedValidationException("from must not be after to");
        }
        Specification<PersistentSeedCaptureSampleRecord> specification =
                (root, query, criteriaBuilder) -> criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("projectId"), projectId),
                        criteriaBuilder.equal(root.get("strategyId"), strategyId)
                );
        if (status != null && !status.isBlank()) {
            Set<String> statuses = Arrays.stream(status.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(String::toUpperCase)
                    .collect(java.util.stream.Collectors.toSet());
            if (!statuses.isEmpty()) {
                specification = specification.and(
                        (root, query, criteriaBuilder) -> root.get("status").in(statuses)
                );
            }
        }
        if (from != null) {
            specification = specification.and(
                    (root, query, criteriaBuilder) ->
                            criteriaBuilder.greaterThanOrEqualTo(root.get("captureStartedAt"), from)
            );
        }
        if (to != null) {
            specification = specification.and(
                    (root, query, criteriaBuilder) ->
                            criteriaBuilder.lessThanOrEqualTo(root.get("captureStartedAt"), to)
            );
        }
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                Sort.by(
                        Sort.Order.desc("captureStartedAt"),
                        Sort.Order.desc("sampleSeq"),
                        Sort.Order.desc("id")
                )
        );
        Page<PersistentSeedCaptureSampleRecord> result = sampleRepository.findAll(specification, pageable);
        return Map.of(
                "content", result.getContent().stream().map(SeedCaptureStrategyService::toSampleView).toList(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        );
    }

    @Transactional
    public Map<String, Object> cancelSample(long projectId, long sampleId) {
        PersistentSeedCaptureSampleRecord sample = sampleRepository.findByIdAndProjectId(sampleId, projectId)
                .orElseThrow(() -> new SeedValidationException("capture sample not found: " + sampleId));
        if (captureExecutor != null) {
            captureExecutor.requestCancel(sampleId);
        } else if (CaptureSampleStateMachine.isActive(sample.getStatus())
                && !"CANCEL_REQUESTED".equals(sample.getStatus())) {
            sample.requestCancel();
            sampleRepository.saveAndFlush(sample);
        }
        return toSampleView(sampleRepository.findByIdAndProjectId(sampleId, projectId).orElseThrow());
    }

    private void submitAfterCommit(long sampleId) {
        if (captureExecutor == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            captureExecutor.submit(sampleId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                captureExecutor.submit(sampleId);
            }
        });
    }

    private StrategyConfiguration validate(long projectId, CreateSeedCaptureStrategyRequest request) {
        if (request == null) {
            throw new SeedValidationException("capture strategy request is required");
        }
        String name = required(request.name(), "name is required");
        if (request.datasourceId() == null) {
            throw new SeedValidationException("datasourceId is required");
        }
        List<String> includes = normalizeFilters(request.includes(), true);
        List<String> excludes = normalizeFilters(request.excludes(), false);
        int threadCount = bounded(request.threadCount(), 1, 32, "threadCount");
        int batchRows = bounded(request.batchRows(), 100, 100000, "batchRows");
        requireDatasource(projectId, request.datasourceId());
        return new StrategyConfiguration(name, request.datasourceId(), includes, excludes, threadCount, batchRows);
    }

    private PersistentSeedCaptureStrategyRecord requireStrategy(long projectId, long strategyId) {
        return strategyRepository.findByIdAndProjectId(strategyId, projectId)
                .orElseThrow(() -> new SeedValidationException("capture strategy not found: " + strategyId));
    }

    private void requireDatasource(long projectId, long datasourceId) {
        datasourceRepository.findByIdAndProjectId(datasourceId, projectId)
                .orElseThrow(() -> new SeedValidationException("datasource not found: " + datasourceId));
    }

    private SeedCaptureStrategyView toView(PersistentSeedCaptureStrategyRecord strategy) {
        return new SeedCaptureStrategyView(
                strategy.getId(),
                strategy.getProjectId(),
                strategy.getName(),
                strategy.getDatasourceId(),
                SeedJson.stringList(strategy.getIncludeJson()),
                SeedJson.stringList(strategy.getExcludeJson()),
                strategy.getThreadCount(),
                strategy.getBatchRows(),
                strategy.getConfigVersion(),
                strategy.getCreatedAt(),
                strategy.getUpdatedAt()
        );
    }

    private static String snapshot(PersistentSeedCaptureStrategyRecord strategy) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("strategyId", strategy.getId());
        snapshot.put("projectId", strategy.getProjectId());
        snapshot.put("name", strategy.getName());
        snapshot.put("datasourceId", strategy.getDatasourceId());
        snapshot.put("includes", SeedJson.stringList(strategy.getIncludeJson()));
        snapshot.put("excludes", SeedJson.stringList(strategy.getExcludeJson()));
        snapshot.put("threadCount", strategy.getThreadCount());
        snapshot.put("batchRows", strategy.getBatchRows());
        snapshot.put("configVersion", strategy.getConfigVersion());
        return SeedJson.write(snapshot);
    }

    private static Map<String, Object> toSampleView(PersistentSeedCaptureSampleRecord sample) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", sample.getId());
        view.put("strategyId", sample.getStrategyId());
        view.put("datasourceId", sample.getDatasourceId());
        view.put("sampleSeq", sample.getSampleSeq());
        view.put("status", sample.getStatus());
        view.put("phase", sample.getPhase());
        view.put("captureStartedAt", sample.getCaptureStartedAt());
        view.put("captureFinishedAt", sample.getCaptureFinishedAt());
        view.put("configVersion", sample.getConfigVersion());
        view.put("completedTables", sample.getCompletedTables());
        view.put("totalTables", sample.getTotalTables());
        view.put("currentTables", SeedJson.read(
                sample.getCurrentTablesJson(),
                new TypeReference<List<String>>() {
                }
        ));
        view.put("capturedRows", sample.getCapturedRows());
        view.put("writtenBytes", sample.getWrittenBytes());
        view.put("activeWorkers", sample.getActiveWorkers());
        view.put("heartbeatAt", sample.getHeartbeatAt());
        view.put("errorMessage", sample.getErrorMessage());
        view.put("incomplete", sample.getIncomplete());
        view.put("configSnapshot", SeedJson.read(
                sample.getConfigSnapshotJson(),
                new TypeReference<Map<String, Object>>() {
                }
        ));
        return view;
    }

    private static List<String> normalizeFilters(List<String> values, boolean required) {
        List<String> normalized = values == null
                ? List.of()
                : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (required && normalized.isEmpty()) {
            throw new SeedValidationException("include filter is required");
        }
        return normalized;
    }

    private static int bounded(Integer value, int min, int max, String field) {
        if (value == null || value < min || value > max) {
            throw new SeedValidationException(field + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new SeedValidationException(message);
        }
        return value.trim();
    }

    private record StrategyConfiguration(
            String name,
            long datasourceId,
            List<String> includes,
            List<String> excludes,
            int threadCount,
            int batchRows
    ) {
    }
}
