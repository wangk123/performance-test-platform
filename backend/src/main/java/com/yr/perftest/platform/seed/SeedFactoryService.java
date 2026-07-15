package com.yr.perftest.platform.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeedFactoryService {
    private final PersistentSeedDatasourceRepository datasourceRepository;
    private final SeedCaptureStrategyService captureStrategyService;
    private final PersistentSeedTemplateRepository templateRepository;
    private final PersistentSeedCloneJobRepository cloneJobRepository;
    private final SeedCredentialCipher cipher;
    private final int maxCloneCount;

    public SeedFactoryService(
            PersistentSeedDatasourceRepository datasourceRepository,
            SeedCaptureStrategyService captureStrategyService,
            PersistentSeedTemplateRepository templateRepository,
            PersistentSeedCloneJobRepository cloneJobRepository,
            SeedCredentialCipher cipher,
            @Value("${platform.seed.max-clone-count:10000}") int maxCloneCount
    ) {
        this.datasourceRepository = datasourceRepository;
        this.captureStrategyService = captureStrategyService;
        this.templateRepository = templateRepository;
        this.cloneJobRepository = cloneJobRepository;
        this.cipher = cipher;
        this.maxCloneCount = maxCloneCount;
    }

    @Transactional(readOnly = true)
    public List<SeedDatasourceView> listDatasources(long projectId) {
        return datasourceRepository.findByProjectIdOrderByIdDesc(projectId).stream()
                .map(PersistentSeedDatasourceRecord::toView)
                .toList();
    }

    @Transactional
    public SeedDatasourceView createDatasource(long projectId, CreateSeedDatasourceRequest request) {
        required(request.name(), "name is required");
        required(request.host(), "host is required");
        required(request.databaseName(), "databaseName is required");
        required(request.username(), "username is required");
        required(request.password(), "password is required");
        int port = request.port() == null ? 3306 : request.port();
        PersistentSeedDatasourceRecord record = new PersistentSeedDatasourceRecord(
                projectId,
                request.name().trim(),
                request.host().trim(),
                port,
                request.databaseName().trim(),
                request.username().trim(),
                cipher.encrypt(request.password())
        );
        return datasourceRepository.save(record).toView();
    }

    @Transactional
    public SeedDatasourceView updateDatasource(long projectId, long id, CreateSeedDatasourceRequest request) {
        PersistentSeedDatasourceRecord record = requireDatasource(projectId, id);
        required(request.name(), "name is required");
        required(request.host(), "host is required");
        required(request.databaseName(), "databaseName is required");
        required(request.username(), "username is required");
        int port = request.port() == null ? 3306 : request.port();
        String passwordEnc = request.password() == null || request.password().isBlank()
                ? null
                : cipher.encrypt(request.password());
        record.update(request.name().trim(), request.host().trim(), port, request.databaseName().trim(), request.username().trim(), passwordEnc);
        return datasourceRepository.save(record).toView();
    }

    @Transactional
    public void deleteDatasource(long projectId, long id) {
        datasourceRepository.delete(requireDatasource(projectId, id));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> testDatasource(long projectId, long id) {
        PersistentSeedDatasourceRecord record = requireDatasource(projectId, id);
        String message = SeedJdbcSupport.testConnectionMessage(record, cipher);
        boolean ok = "OK".equals(message);
        return Map.of("ok", ok, "message", message);
    }

    public List<SeedCaptureStrategyView> listCaptureStrategies(long projectId) {
        return captureStrategyService.list(projectId);
    }

    public SeedCaptureStrategyView createCaptureStrategy(
            long projectId,
            CreateSeedCaptureStrategyRequest request
    ) {
        return captureStrategyService.create(projectId, request);
    }

    public SeedCaptureStrategyView getCaptureStrategy(long projectId, long strategyId) {
        return captureStrategyService.get(projectId, strategyId);
    }

    public SeedCaptureStrategyView updateCaptureStrategy(
            long projectId,
            long strategyId,
            CreateSeedCaptureStrategyRequest request
    ) {
        return captureStrategyService.update(projectId, strategyId, request);
    }

    public void deleteCaptureStrategy(long projectId, long strategyId) {
        captureStrategyService.delete(projectId, strategyId);
    }

    public Map<String, Object> executeCaptureStrategy(long projectId, long strategyId) {
        return captureStrategyService.execute(projectId, strategyId);
    }

    public Map<String, Object> getCaptureSample(long projectId, long sampleId) {
        return captureStrategyService.getSample(projectId, sampleId);
    }

    public Map<String, Object> cancelCaptureSample(long projectId, long sampleId) {
        return captureStrategyService.cancelSample(projectId, sampleId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTemplates(long projectId) {
        return templateRepository.findByProjectIdOrderByIdDesc(projectId).stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(),
                        "analysisId", t.getAnalysisId(),
                        "status", t.getStatus(),
                        "versionNo", t.getVersionNo(),
                        "confirmedBy", t.getConfirmedBy() == null ? "" : t.getConfirmedBy(),
                        "confirmedAt", t.getConfirmedAt() == null ? "" : t.getConfirmedAt().toString()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTemplate(long projectId, long templateId) {
        PersistentSeedTemplateRecord template = requireTemplate(projectId, templateId);
        return Map.of(
                "id", template.getId(),
                "analysisId", template.getAnalysisId(),
                "status", template.getStatus(),
                "versionNo", template.getVersionNo(),
                "body", SeedJson.draftFromJson(template.getBodyJson()),
                "confirmedBy", template.getConfirmedBy() == null ? "" : template.getConfirmedBy(),
                "confirmedAt", template.getConfirmedAt() == null ? "" : template.getConfirmedAt().toString()
        );
    }

    @Transactional
    public Map<String, Object> updateTemplateDraft(long projectId, long templateId, SeedTemplateDraft draft) {
        PersistentSeedTemplateRecord template = requireTemplate(projectId, templateId);
        template.updateDraft(SeedJson.write(draft));
        templateRepository.save(template);
        return getTemplate(projectId, templateId);
    }

    @Transactional
    public Map<String, Object> confirmTemplate(long projectId, long templateId, String operator) {
        PersistentSeedTemplateRecord template = requireTemplate(projectId, templateId);
        if ("CONFIRMED".equals(template.getStatus())) {
            throw new SeedValidationException("template already confirmed");
        }
        template.confirm(operator == null || operator.isBlank() ? "system" : operator);
        templateRepository.save(template);
        return getTemplate(projectId, templateId);
    }

    @Transactional
    public Map<String, Object> createCloneJob(long projectId, CreateCloneJobRequest request) {
        PersistentSeedTemplateRecord template = requireTemplate(projectId, request.templateId());
        if (!"CONFIRMED".equals(template.getStatus())) {
            throw new SeedValidationException("clone requires a confirmed template");
        }
        if (request.cloneCount() == null || request.cloneCount() < 1) {
            throw new SeedValidationException("cloneCount must be at least 1");
        }
        if (request.cloneCount() > maxCloneCount) {
            throw new SeedValidationException("cloneCount exceeds maximum: " + maxCloneCount);
        }
        requireDatasource(projectId, request.datasourceId());
        String policy = request.failurePolicy() == null || request.failurePolicy().isBlank()
                ? "CONTINUE"
                : request.failurePolicy().toUpperCase();
        if (!policy.equals("CONTINUE") && !policy.equals("STOP")) {
            throw new SeedValidationException("failurePolicy must be CONTINUE or STOP");
        }
        PersistentSeedCloneJobRecord job = new PersistentSeedCloneJobRecord(
                projectId,
                template.getId(),
                request.datasourceId(),
                request.cloneCount(),
                policy,
                request.operator() == null || request.operator().isBlank() ? "system" : request.operator()
        );
        job = cloneJobRepository.save(job);
        runCloneJob(projectId, job.getId());
        return getCloneJob(projectId, job.getId());
    }

    @Transactional
    public Map<String, Object> runCloneJob(long projectId, long jobId) {
        PersistentSeedCloneJobRecord job = requireJob(projectId, jobId);
        PersistentSeedTemplateRecord template = requireTemplate(projectId, job.getTemplateId());
        PersistentSeedDatasourceRecord ds = requireDatasource(projectId, job.getDatasourceId());
        SeedTemplateDraft draft = SeedJson.draftFromJson(template.getBodyJson());
        Map<String, Map<String, String>> seedRows = SeedJson.stringMapMap(template.getSeedRowsJson());
        job.markRunning();
        cloneJobRepository.save(job);
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        try (Connection connection = SeedJdbcSupport.open(ds, cipher)) {
            for (int i = 0; i < job.getCloneCount(); i++) {
                Map<String, String> idMap = new LinkedHashMap<>();
                try {
                    connection.setAutoCommit(false);
                    List<PlannedStatement> statements = CloneBatchPlanner.plan(
                            draft.operations(),
                            seedRows,
                            key -> {
                                TemplateColumn col = findColumn(draft, key);
                                String generator = col == null || col.generator() == null || col.generator().isBlank()
                                        ? "seq"
                                        : col.generator();
                                return SeedValueGenerators.generate(generator);
                            },
                            idMap
                    );
                    for (PlannedStatement statement : statements) {
                        if ("UPDATE".equals(statement.type())) {
                            SeedJdbcSupport.executeUpdate(connection, statement);
                        } else {
                            SeedJdbcSupport.executeInsert(connection, statement);
                        }
                    }
                    connection.commit();
                    success++;
                } catch (Exception ex) {
                    try {
                        connection.rollback();
                    } catch (Exception ignored) {
                    }
                    failed++;
                    errors.add("batch " + (i + 1) + ": " + ex.getMessage());
                    if ("STOP".equals(job.getFailurePolicy())) {
                        break;
                    }
                } finally {
                    try {
                        connection.setAutoCommit(true);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ex) {
            job.complete(success, failed, SeedJson.write(List.of(ex.getMessage())), "FAILED");
            cloneJobRepository.save(job);
            return getCloneJob(projectId, jobId);
        }
        String status = failed == 0 ? "SUCCEEDED" : (success == 0 ? "FAILED" : "PARTIAL");
        job.complete(success, failed, SeedJson.write(errors), status);
        cloneJobRepository.save(job);
        return getCloneJob(projectId, jobId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCloneJobs(long projectId) {
        return cloneJobRepository.findByProjectIdOrderByIdDesc(projectId).stream()
                .map(this::jobView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCloneJob(long projectId, long jobId) {
        return jobView(requireJob(projectId, jobId));
    }

    private Map<String, Object> jobView(PersistentSeedCloneJobRecord job) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", job.getId());
        view.put("templateId", job.getTemplateId());
        view.put("datasourceId", job.getDatasourceId());
        view.put("cloneCount", job.getCloneCount());
        view.put("failurePolicy", job.getFailurePolicy());
        view.put("status", job.getStatus());
        view.put("successBatches", job.getSuccessBatches());
        view.put("failedBatches", job.getFailedBatches());
        view.put("errors", SeedJson.read(job.getErrorJson() == null ? "[]" : job.getErrorJson(), new TypeReference<List<String>>() {
        }));
        view.put("createdBy", job.getCreatedBy());
        view.put("createdAt", job.getCreatedAt().toString());
        view.put("finishedAt", job.getFinishedAt() == null ? "" : job.getFinishedAt().toString());
        return view;
    }

    private TemplateColumn findColumn(SeedTemplateDraft draft, String mapKey) {
        int idx = mapKey.lastIndexOf('.');
        if (idx < 0) {
            return null;
        }
        String column = mapKey.substring(idx + 1);
        String table = mapKey.substring(0, idx);
        for (TemplateOperation op : draft.operations()) {
            if (!op.table().equals(table)) {
                continue;
            }
            for (TemplateColumn col : op.columns()) {
                if (col.name().equals(column)) {
                    return col;
                }
            }
        }
        return null;
    }

    private PersistentSeedDatasourceRecord requireDatasource(long projectId, long id) {
        return datasourceRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new SeedValidationException("datasource not found: " + id));
    }

    private PersistentSeedTemplateRecord requireTemplate(long projectId, long id) {
        return templateRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new SeedValidationException("template not found: " + id));
    }

    private PersistentSeedCloneJobRecord requireJob(long projectId, long id) {
        return cloneJobRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new SeedValidationException("clone job not found: " + id));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new SeedValidationException(message);
        }
        return value.trim();
    }
}
