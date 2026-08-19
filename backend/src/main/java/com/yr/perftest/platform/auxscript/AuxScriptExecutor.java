package com.yr.perftest.platform.auxscript;

import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 辅助脚本执行器（模块 09）：按绑定顺序执行前置/后置脚本，记录退出码/日志/起止时间，
 * 失败策略 STOP_TASK / CONTINUE / MANUAL_CONFIRM 生效。脚本在独立工作目录执行，
 * 默认超时 {@link #timeoutSeconds} 秒。执行权限受限于平台进程用户，敏感目录隔离
 * 依赖部署侧最小权限（平台自身不提供 OS 级沙箱）。
 */
@Service
public class AuxScriptExecutor {
    private final PersistentAuxScriptBindingRepository bindingRepository;
    private final PersistentAuxScriptVersionRepository versionRepository;
    private final PersistentAuxScriptRepository scriptRepository;
    private final PersistentAuxScriptExecutionRepository executionRecordRepository;
    private final PersistentScenarioExecutionRepository scenarioExecutionRepository;
    private final AuxScriptBindingService bindingService;
    private final String storageRoot;
    private final long timeoutSeconds;

    public AuxScriptExecutor(
            PersistentAuxScriptBindingRepository bindingRepository,
            PersistentAuxScriptVersionRepository versionRepository,
            PersistentAuxScriptRepository scriptRepository,
            PersistentAuxScriptExecutionRepository executionRecordRepository,
            PersistentScenarioExecutionRepository scenarioExecutionRepository,
            AuxScriptBindingService bindingService,
            @Value("${platform.storage.root:./storage}") String storageRoot,
            @Value("${platform.auxscript.timeout-seconds:60}") long timeoutSeconds
    ) {
        this.bindingRepository = bindingRepository;
        this.versionRepository = versionRepository;
        this.scriptRepository = scriptRepository;
        this.executionRecordRepository = executionRecordRepository;
        this.scenarioExecutionRepository = scenarioExecutionRepository;
        this.bindingService = bindingService;
        this.storageRoot = storageRoot;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Transactional
    public List<ExecutionView> runPhase(long executionId, AuxScriptPhase phase) {
        PersistentScenarioExecutionRecord execution = scenarioExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("execution " + executionId + " does not exist"));
        List<PersistentAuxScriptBindingRecord> bindings =
                bindingService.bindingsFor(execution.getScenarioId(), phase);
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<PersistentAuxScriptExecutionRecord> created = new ArrayList<>();
        for (PersistentAuxScriptBindingRecord binding : bindings) {
            created.add(executionRecordRepository.saveAndFlush(new PersistentAuxScriptExecutionRecord(
                    executionId,
                    binding.getId(),
                    binding.getScriptVersionId(),
                    phase
            )));
        }
        List<ExecutionView> views = new ArrayList<>();
        boolean halted = false;
        for (PersistentAuxScriptExecutionRecord record : created) {
            if (halted) {
                record.markFinished(AuxScriptExecutionStatus.SKIPPED, -1, null);
                views.add(executionView(executionRecordRepository.save(record)));
                continue;
            }
            PersistentAuxScriptBindingRecord binding = bindingRepository.findById(record.getBindingId()).orElseThrow();
            PersistentAuxScriptVersionRecord version = versionRepository
                    .findById(record.getScriptVersionId()).orElseThrow();
            RunResult result = run(executionId, record.getId(), phase, version);
            AuxScriptExecutionStatus finalStatus = result.status();
            boolean failed = result.status() == AuxScriptExecutionStatus.FAILED
                    || result.status() == AuxScriptExecutionStatus.TIMEOUT;
            if (failed) {
                if (binding.getFailurePolicy() == AuxScriptFailurePolicy.MANUAL_CONFIRM) {
                    finalStatus = AuxScriptExecutionStatus.AWAITING_CONFIRMATION;
                    halted = true;
                } else if (binding.getFailurePolicy() == AuxScriptFailurePolicy.STOP_TASK) {
                    halted = true;
                }
            }
            record.markFinished(finalStatus, result.exitCode(), result.logPath());
            views.add(executionView(executionRecordRepository.save(record)));
        }
        return List.copyOf(views);
    }

    /**
     * 人工确认后继续：从首个 AWAITING_CONFIRMATION 的下一条绑定继续执行同一阶段剩余脚本。
     */
    @Transactional
    public List<ExecutionView> confirmExecution(long executionId) {
        List<PersistentAuxScriptExecutionRecord> records =
                executionRecordRepository.findAllByExecutionIdOrderByIdAsc(executionId);
        PersistentAuxScriptExecutionRecord awaiting = records.stream()
                .filter(record -> record.getStatus() == AuxScriptExecutionStatus.AWAITING_CONFIRMATION)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no awaiting confirmation for execution " + executionId));
        AuxScriptPhase phase = awaiting.getPhase();
        int resumeSortOrder = bindingRepository.findById(awaiting.getBindingId())
                .map(PersistentAuxScriptBindingRecord::getSortOrder)
                .orElseThrow() + 1;
        PersistentScenarioExecutionRecord execution = scenarioExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("execution " + executionId + " does not exist"));
        List<PersistentAuxScriptBindingRecord> pending = bindingService.bindingsFor(execution.getScenarioId(), phase)
                .stream()
                .filter(binding -> binding.getSortOrder() >= resumeSortOrder)
                .toList();
        List<ExecutionView> views = new ArrayList<>();
        boolean halted = false;
        for (PersistentAuxScriptBindingRecord binding : pending) {
            if (halted) {
                break;
            }
            PersistentAuxScriptExecutionRecord record = executionRecordRepository.saveAndFlush(
                    new PersistentAuxScriptExecutionRecord(
                            executionId, binding.getId(), binding.getScriptVersionId(), phase));
            PersistentAuxScriptVersionRecord version = versionRepository
                    .findById(record.getScriptVersionId()).orElseThrow();
            RunResult result = run(executionId, record.getId(), phase, version);
            record.markFinished(result.status(), result.exitCode(), result.logPath());
            views.add(executionView(executionRecordRepository.save(record)));
            if (result.status() == AuxScriptExecutionStatus.FAILED
                    || result.status() == AuxScriptExecutionStatus.TIMEOUT) {
                if (binding.getFailurePolicy() == AuxScriptFailurePolicy.STOP_TASK
                        || binding.getFailurePolicy() == AuxScriptFailurePolicy.MANUAL_CONFIRM) {
                    halted = true;
                }
            }
        }
        return List.copyOf(views);
    }

    @Transactional(readOnly = true)
    public List<ExecutionView> listExecutions(long executionId) {
        return executionRecordRepository.findAllByExecutionIdOrderByIdAsc(executionId).stream()
                .map(this::executionView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExecutionView getExecutionRecord(long recordId) {
        return executionRecordRepository.findById(recordId)
                .map(this::executionView)
                .orElseThrow(() -> new IllegalArgumentException("aux script execution record " + recordId + " does not exist"));
    }

    private RunResult run(
            long executionId,
            long recordId,
            AuxScriptPhase phase,
            PersistentAuxScriptVersionRecord version
    ) {
        Path workDir = Path.of(storageRoot, "aux-script-work", Long.toString(executionId));
        Path logPath = Path.of(storageRoot, "aux-script-logs", Long.toString(executionId),
                phase.name().toLowerCase() + "-" + recordId + ".log");
        try {
            Files.createDirectories(workDir);
            Files.createDirectories(logPath.getParent());
            AuxScriptType type = scriptRepository.findById(version.getScriptId())
                    .map(PersistentAuxScriptRecord::getType)
                    .orElse(AuxScriptType.SHELL);
            String fileName = type == AuxScriptType.PYTHON ? "script.py" : "script.sh";
            String command = type == AuxScriptType.PYTHON ? "python3" : "bash";
            Path scriptFile = workDir.resolve(fileName);
            Files.writeString(scriptFile, version.getSourceCode());
            ProcessBuilder builder = new ProcessBuilder(command, fileName);
            builder.directory(workDir.toFile());
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return new RunResult(AuxScriptExecutionStatus.TIMEOUT, -1, relative(logPath));
            }
            AuxScriptExecutionStatus status = process.exitValue() == 0
                    ? AuxScriptExecutionStatus.SUCCESS
                    : AuxScriptExecutionStatus.FAILED;
            return new RunResult(status, process.exitValue(), relative(logPath));
        } catch (IOException exception) {
            return new RunResult(AuxScriptExecutionStatus.FAILED, -1,
                    "io-error: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new RunResult(AuxScriptExecutionStatus.TIMEOUT, -1, relative(logPath));
        }
    }

    private String relative(Path logPath) {
        return "aux-script-logs/" + logPath.getParent().getFileName() + "/" + logPath.getFileName();
    }

    private ExecutionView executionView(PersistentAuxScriptExecutionRecord record) {
        return new ExecutionView(
                record.getId(),
                record.getExecutionId(),
                record.getBindingId(),
                record.getScriptVersionId(),
                record.getPhase().name(),
                record.getStatus().name(),
                record.getExitCode(),
                record.getLogPath(),
                record.getStartedAt(),
                record.getEndedAt()
        );
    }

    public record RunResult(AuxScriptExecutionStatus status, int exitCode, String logPath) {
    }

    public record ExecutionView(
            long executionRecordId,
            long executionId,
            long bindingId,
            long scriptVersionId,
            String phase,
            String status,
            int exitCode,
            String logPath,
            Instant startedAt,
            Instant endedAt
    ) {
    }
}
