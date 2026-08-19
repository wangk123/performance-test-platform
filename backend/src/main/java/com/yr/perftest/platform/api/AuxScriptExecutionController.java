package com.yr.perftest.platform.api;

import com.yr.perftest.platform.auxscript.AuxScriptExecutor;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 辅助脚本执行记录与日志接口（模块 09）。
 */
@RestController
public class AuxScriptExecutionController {
    private final AuxScriptExecutor executor;
    private final String storageRoot;

    public AuxScriptExecutionController(
            AuxScriptExecutor executor,
            @Value("${platform.storage.root:./storage}") String storageRoot
    ) {
        this.executor = executor;
        this.storageRoot = storageRoot;
    }

    @GetMapping("/api/executions/{executionId}/aux-script-executions")
    public List<AuxScriptExecutor.ExecutionView> list(@PathVariable long executionId) {
        return executor.listExecutions(executionId);
    }

    @PostMapping("/api/executions/{executionId}/aux-script/confirm")
    public List<AuxScriptExecutor.ExecutionView> confirm(@PathVariable long executionId) {
        return executor.confirmExecution(executionId);
    }

    @GetMapping(value = "/api/aux-script-executions/{recordId}/log",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String log(@PathVariable long recordId) {
        AuxScriptExecutor.ExecutionView view = executor.getExecutionRecord(recordId);
        if (view.logPath() == null) {
            throw new ExecutionValidationException("aux script log does not exist");
        }
        Path path = Path.of(storageRoot).resolve(view.logPath());
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new ExecutionValidationException("aux script log does not exist");
        }
    }
}
