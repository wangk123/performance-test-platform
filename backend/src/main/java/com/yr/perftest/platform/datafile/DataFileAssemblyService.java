package com.yr.perftest.platform.datafile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.script.ScriptStepType;
import com.yr.perftest.platform.task.ScenarioDataFileBinding;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行前数据文件装配（P1-5）：解析脚本 CSV 步骤，按显式绑定或项目内裸文件名兜底解析数据文件版本，
 * 校验目标文件名冲突后把版本文件复制进执行目录并写 {@code data-files.json} 清单。不改写 JMX。
 */
@Service
public class DataFileAssemblyService {
    private final DataFileService dataFileService;
    private final DataFileRepository fileRepository;
    private final ScenarioThreadGroupConfigSupport scenarioSupport;
    private final ObjectMapper objectMapper;

    public DataFileAssemblyService(
            DataFileService dataFileService,
            DataFileRepository fileRepository,
            ScenarioThreadGroupConfigSupport scenarioSupport,
            ObjectMapper objectMapper
    ) {
        this.dataFileService = dataFileService;
        this.fileRepository = fileRepository;
        this.scenarioSupport = scenarioSupport;
        this.objectMapper = objectMapper;
    }

    public List<CsvAssemblyPlan> plan(long projectId, String bindingsJson, Path scriptPath, Path executionDirectory) {
        Map<String, ScriptStepDefinition> csvSteps = new LinkedHashMap<>();
        collectCsvSteps(scenarioSupport.loadScriptSteps(scriptPath), csvSteps);
        Map<String, ScenarioDataFileBinding> bindings = readBindings(bindingsJson);
        List<CsvAssemblyPlan> plans = new ArrayList<>();
        for (ScriptStepDefinition step : csvSteps.values()) {
            String fileName = fileNameOf(step);
            if (fileName == null) {
                continue;
            }
            ScenarioDataFileBinding binding = bindings.get(step.id());
            if (binding != null) {
                plans.add(boundPlan(projectId, binding, step, fileName));
            } else if (Files.notExists(executionDirectory.resolve(fileName))) {
                CsvAssemblyPlan fallback = fallbackPlan(projectId, step, fileName);
                if (fallback != null) {
                    plans.add(fallback);
                }
            }
        }
        rejectDuplicateTargetFileNames(plans);
        return plans;
    }

    public void materialize(List<CsvAssemblyPlan> plans, Path executionDirectory) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(executionDirectory);
            for (CsvAssemblyPlan plan : plans) {
                Files.copy(plan.sourcePath(), executionDirectory.resolve(plan.targetFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            objectMapper.writeValue(executionDirectory.resolve("data-files.json").toFile(), plans);
        } catch (Exception exception) {
            throw new ExecutionValidationException("failed to assemble data files: " + exception.getMessage());
        }
    }

    private Map<String, ScenarioDataFileBinding> readBindings(String bindingsJson) {
        List<ScenarioDataFileBinding> bindings = scenarioSupport.readStoredDataFileBindings(bindingsJson);
        Map<String, ScenarioDataFileBinding> byStepId = new LinkedHashMap<>();
        for (ScenarioDataFileBinding binding : bindings) {
            if (binding != null && binding.stepId() != null && binding.dataFileId() != null) {
                byStepId.put(binding.stepId(), binding);
            }
        }
        return byStepId;
    }

    private CsvAssemblyPlan boundPlan(
            long projectId, ScenarioDataFileBinding binding, ScriptStepDefinition step, String fileName) {
        try {
            if (!belongsToProject(projectId, binding.dataFileId())) {
                throw new DataFileValidationException("data file does not belong to project " + projectId);
            }
            DataFileVersion version = dataFileService.latestVersion(binding.dataFileId());
            return toPlan(step, version, fileName);
        } catch (DataFileValidationException exception) {
            throw new ExecutionValidationException("步骤 [" + step.name() + "] 绑定的数据文件 ["
                    + binding.dataFileId() + "] 不存在");
        }
    }

    private CsvAssemblyPlan fallbackPlan(long projectId, ScriptStepDefinition step, String fileName) {
        List<DataFileVersion> candidates = dataFileService.findByOriginalFilename(fileName).stream()
                .filter(version -> belongsToProject(projectId, version.dataFileId()))
                .toList();
        return candidates.size() == 1 ? toPlan(step, candidates.get(0), fileName) : null;
    }

    private CsvAssemblyPlan toPlan(ScriptStepDefinition step, DataFileVersion version, String fileName) {
        return new CsvAssemblyPlan(
                step.id(),
                step.name(),
                version.dataFileId(),
                version.versionNo(),
                version.sha256(),
                fileName,
                Path.of(version.storedPath())
        );
    }

    private boolean belongsToProject(long projectId, long dataFileId) {
        return fileRepository.findById(dataFileId)
                .map(record -> record.getProjectId() != null && record.getProjectId() == projectId)
                .orElse(false);
    }

    private void rejectDuplicateTargetFileNames(List<CsvAssemblyPlan> plans) {
        Map<String, List<String>> stepNamesByTarget = new LinkedHashMap<>();
        for (CsvAssemblyPlan plan : plans) {
            stepNamesByTarget.computeIfAbsent(plan.targetFileName(), ignored -> new ArrayList<>()).add(plan.stepName());
        }
        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : stepNamesByTarget.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add("[" + entry.getKey() + "] 步骤 " + entry.getValue());
            }
        }
        if (!conflicts.isEmpty()) {
            throw new ExecutionValidationException("数据文件目标文件名冲突: " + String.join("; ", conflicts));
        }
    }

    private String fileNameOf(ScriptStepDefinition step) {
        Object fileName = step.config().get("fileName");
        if (fileName == null || String.valueOf(fileName).isBlank()) {
            return null;
        }
        return String.valueOf(fileName);
    }

    private void collectCsvSteps(List<ScriptStepDefinition> steps, Map<String, ScriptStepDefinition> csvSteps) {
        for (ScriptStepDefinition step : steps) {
            if (ScriptStepType.CSV_DATA.code().equals(step.type())) {
                csvSteps.put(step.id(), step);
            }
            if (!step.children().isEmpty()) {
                collectCsvSteps(step.children(), csvSteps);
            }
        }
    }
}
