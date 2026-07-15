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
import java.util.Set;

@Service
public class SeedFactoryService {
    private final PersistentSeedDatasourceRepository datasourceRepository;
    private final PersistentSeedCaptureSessionRepository captureRepository;
    private final PersistentSeedTemplateRepository templateRepository;
    private final PersistentSeedCloneJobRepository cloneJobRepository;
    private final SeedCredentialCipher cipher;
    private final int maxCloneCount;

    public SeedFactoryService(
            PersistentSeedDatasourceRepository datasourceRepository,
            PersistentSeedCaptureSessionRepository captureRepository,
            PersistentSeedTemplateRepository templateRepository,
            PersistentSeedCloneJobRepository cloneJobRepository,
            SeedCredentialCipher cipher,
            @Value("${platform.seed.max-clone-count:10000}") int maxCloneCount
    ) {
        this.datasourceRepository = datasourceRepository;
        this.captureRepository = captureRepository;
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

    @Transactional
    public Map<String, Object> startCapture(long projectId, StartCaptureRequest request) {
        if ("BINLOG".equalsIgnoreCase(request.provider())) {
            throw new SeedValidationException("BINLOG capture provider is not supported in V1");
        }
        PersistentSeedDatasourceRecord ds = requireDatasource(projectId, request.datasourceId());
        List<String> includes = request.includes() == null ? List.of() : request.includes();
        List<String> excludes = request.excludes() == null ? List.of() : request.excludes();
        try (Connection connection = SeedJdbcSupport.open(ds, cipher)) {
            List<String> visible = SeedJdbcSupport.listTables(connection, ds.getDatabaseName());
            Set<String> tableSet = CaptureFilterEvaluator.evaluate(visible, includes, excludes);
            Map<String, Object> baseline = new LinkedHashMap<>();
            for (String table : tableSet) {
                TableMetadata meta = SeedJdbcSupport.readMetadata(connection, table);
                Map<String, Map<String, String>> rows = meta.primaryKeyColumns().isEmpty()
                        ? Map.of()
                        : SeedJdbcSupport.snapshotTable(connection, table, meta.primaryKeyColumns());
                baseline.put(table, Map.of(
                        "metadata", meta,
                        "rows", rows,
                        "riskyNoPk", meta.primaryKeyColumns().isEmpty()
                ));
            }
            PersistentSeedCaptureSessionRecord session = new PersistentSeedCaptureSessionRecord(
                    projectId,
                    ds.getId(),
                    "SNAPSHOT",
                    SeedJson.write(includes),
                    SeedJson.write(excludes),
                    SeedJson.write(tableSet),
                    SeedJson.write(baseline)
            );
            session = captureRepository.save(session);
            return Map.of(
                    "id", session.getId(),
                    "status", session.getStatus(),
                    "provider", session.getProvider(),
                    "tableSet", tableSet
            );
        } catch (SeedValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SeedValidationException("failed to start capture: " + ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> endSample(long projectId, long sessionId) {
        PersistentSeedCaptureSessionRecord session = requireSession(projectId, sessionId);
        if (!"RECORDING".equals(session.getStatus())) {
            throw new SeedValidationException("capture session is not recording");
        }
        PersistentSeedDatasourceRecord ds = requireDatasource(projectId, session.getDatasourceId());
        Map<String, Object> baseline = SeedJson.read(session.getBaselineJson(), new TypeReference<>() {
        });
        List<Map<String, Object>> samples = SeedJson.read(session.getSamplesJson(), new TypeReference<>() {
        });
        try (Connection connection = SeedJdbcSupport.open(ds, cipher)) {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("index", samples.size() + 1);
            Map<String, Object> tableDiffs = new LinkedHashMap<>();
            for (String table : SeedJson.stringList(session.getTableSetJson())) {
                Map<String, Object> baseTable = SeedTemplateInference.castMap(baseline.get(table));
                TableMetadata meta = SeedJson.read(SeedJson.write(baseTable.get("metadata")), new TypeReference<>() {
                });
                boolean risky = Boolean.TRUE.equals(baseTable.get("riskyNoPk")) || meta.primaryKeyColumns().isEmpty();
                if (risky) {
                    tableDiffs.put(table, Map.of("riskyNoPk", true, "diffs", List.of()));
                    continue;
                }
                Map<String, Map<String, String>> before = castRows(baseTable.get("rows"));
                Map<String, Map<String, String>> after = SeedJdbcSupport.snapshotTable(connection, table, meta.primaryKeyColumns());
                Map<String, SnapshotDiffEngine.RowDiff> diffs = SnapshotDiffEngine.diff(before, after);
                tableDiffs.put(table, Map.of(
                        "riskyNoPk", false,
                        "metadata", meta,
                        "diffs", diffs.values()
                ));
                baseTable.put("rows", after);
                baseline.put(table, baseTable);
            }
            sample.put("tables", tableDiffs);
            samples.add(sample);
            session.appendSample(SeedJson.write(samples));
            session.updateBaseline(SeedJson.write(baseline));
            captureRepository.save(session);
            return Map.of("sessionId", sessionId, "sampleCount", samples.size(), "sample", sample);
        } catch (SeedValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SeedValidationException("failed to end sample: " + ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> finishCapture(long projectId, long sessionId) {
        PersistentSeedCaptureSessionRecord session = requireSession(projectId, sessionId);
        List<Map<String, Object>> samples = SeedJson.read(session.getSamplesJson(), new TypeReference<>() {
        });
        if (samples.isEmpty()) {
            throw new SeedValidationException("at least one sample is required");
        }
        session.finish("FINISHED");
        captureRepository.save(session);
        SeedTemplateDraft draft = SeedTemplateInference.infer(session, samples);
        Map<String, Map<String, String>> seedRows = SeedTemplateInference.extractSeedRows(samples);
        PersistentSeedTemplateRecord template = new PersistentSeedTemplateRecord(
                projectId,
                sessionId,
                SeedJson.write(draft),
                SeedJson.write(seedRows)
        );
        template = templateRepository.save(template);
        return Map.of(
                "sessionId", sessionId,
                "templateId", template.getId(),
                "status", template.getStatus(),
                "draft", draft
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTemplates(long projectId) {
        return templateRepository.findByProjectIdOrderByIdDesc(projectId).stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(),
                        "captureSessionId", t.getCaptureSessionId(),
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

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, String>> castRows(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            result.put(String.valueOf(e.getKey()), SeedTemplateInference.castStringMap(e.getValue()));
        }
        return result;
    }

    private PersistentSeedDatasourceRecord requireDatasource(long projectId, long id) {
        return datasourceRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new SeedValidationException("datasource not found: " + id));
    }

    private PersistentSeedCaptureSessionRecord requireSession(long projectId, long id) {
        return captureRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new SeedValidationException("capture session not found: " + id));
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
